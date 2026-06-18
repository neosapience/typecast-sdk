import type { Output, TTSRequest, TTSResponse } from './types/TextToSpeech';

type TextToSpeechFn = (request: TTSRequest) => Promise<TTSResponse>;
type ComposerSettings = Omit<Partial<TTSRequest>, 'text'>;

type SpeechPart =
  | { kind: 'speech'; text: string; overrides?: ComposerSettings }
  | { kind: 'pause'; seconds: number };

type ParsedPart =
  | { kind: 'text'; text: string }
  | { kind: 'pause'; seconds: number };

const PAUSE_TOKEN = /<\|(\d+(?:\.\d+)?)s\|>/g;
const PCM_SILENCE_THRESHOLD = 0;

export class SpeechComposer {
  private defaultSettings: ComposerSettings = {};
  private parts: SpeechPart[] = [];

  constructor(private readonly textToSpeech: TextToSpeechFn) {}

  defaults(settings: ComposerSettings): this {
    this.defaultSettings = mergeSettings(this.defaultSettings, settings);
    return this;
  }

  say(text: string, overrides?: ComposerSettings): this {
    this.parts.push({ kind: 'speech', text, overrides });
    return this;
  }

  /**
   * Inserts silence between speech segments.
   *
   * @param seconds Duration in seconds. Examples: 0.3 for 300 ms, 3 for 3 seconds.
   */
  pause(seconds: number): this {
    if (!Number.isFinite(seconds) || seconds <= 0) {
      throw new Error('pause seconds must be greater than 0');
    }
    this.parts.push({ kind: 'pause', seconds });
    return this;
  }

  async generate(): Promise<TTSResponse> {
    const plan = this.buildPlan();
    if (!plan.some((part) => part.kind === 'speech')) {
      throw new Error('at least one speech segment is required');
    }

    const outputFormat = this.defaultSettings.output?.audio_format ?? 'wav';
    if (outputFormat !== 'wav' && outputFormat !== 'mp3') {
      throw new Error(`unsupported composed speech output format: ${String(outputFormat)}`);
    }

    let wavSpec: WavSpec | null = null;
    const outputSamples: number[] = [];

    for (const part of plan) {
      if (part.kind === 'pause') {
        if (!wavSpec) {
          throw new Error('pause cannot be the first composed part');
        }
        outputSamples.push(...Array<number>(secondsToSamples(part.seconds, wavSpec.sampleRate)).fill(0));
        continue;
      }

      const response = await this.textToSpeech(part.request);
      const wav = parseWav(response.audioData);
      if (wavSpec && !sameSpec(wav.spec, wavSpec)) {
        throw new Error('all composed WAV segments must use the same PCM format');
      }
      wavSpec = wav.spec;
      outputSamples.push(...trimSilence(wav.samples));
    }

    const finalSpec = wavSpec as WavSpec;
    const wavBytes = encodeWav(outputSamples, finalSpec);
    if (outputFormat === 'mp3') {
      throw new Error('ffmpeg is required to encode composed speech as mp3');
    }
    return {
      audioData: wavBytes,
      duration: outputSamples.length / finalSpec.sampleRate,
      format: 'wav',
    };
  }

  private buildPlan(): Array<{ kind: 'speech'; request: TTSRequest } | { kind: 'pause'; seconds: number }> {
    const plan: Array<{ kind: 'speech'; request: TTSRequest } | { kind: 'pause'; seconds: number }> = [];
    for (const part of this.parts) {
      if (part.kind === 'pause') {
        plan.push(part);
        continue;
      }

      const settings = mergeSettings(this.defaultSettings, part.overrides ?? {});
      for (const parsed of parsePauseMarkup(part.text)) {
        if (parsed.kind === 'pause') {
          plan.push(parsed);
          continue;
        }
        if (!parsed.text.trim()) {
          continue;
        }
        if (!settings.voice_id) {
          throw new Error('voice_id is required for composed speech segments');
        }
        if (!settings.model) {
          throw new Error('model is required for composed speech segments');
        }
        plan.push({
          kind: 'speech',
          request: {
            ...settings,
            text: parsed.text,
            voice_id: settings.voice_id,
            model: settings.model,
            output: {
              ...settings.output,
              audio_format: 'wav',
            },
          },
        });
      }
    }
    return plan;
  }
}

export function parsePauseMarkup(text: string): ParsedPart[] {
  const parts: ParsedPart[] = [];
  let lastIndex = 0;
  for (const match of text.matchAll(PAUSE_TOKEN)) {
    const index = match.index;
    if (index > lastIndex) {
      parts.push({ kind: 'text', text: text.slice(lastIndex, index) });
    }
    parts.push({ kind: 'pause', seconds: Number(match[1]) });
    lastIndex = index + match[0].length;
  }
  if (lastIndex < text.length) {
    parts.push({ kind: 'text', text: text.slice(lastIndex) });
  }
  return parts;
}

function mergeSettings(base: ComposerSettings, override: ComposerSettings): ComposerSettings {
  return {
    ...base,
    ...override,
    output: mergeOutput(base.output, override.output),
  };
}

function mergeOutput(base?: Output, override?: Output): Output | undefined {
  if (!base && !override) return undefined;
  return { ...(base ?? {}), ...(override ?? {}) };
}

type WavSpec = {
  sampleRate: number;
  channels: number;
  bitsPerSample: number;
};

function parseWav(buffer: ArrayBuffer): { spec: WavSpec; samples: number[] } {
  const view = new DataView(buffer);
  if (buffer.byteLength < 44 || ascii(view, 0, 4) !== 'RIFF' || ascii(view, 8, 4) !== 'WAVE') {
    throw new Error('unsupported WAV data');
  }
  let offset = 12;
  let spec: WavSpec | null = null;
  let dataOffset = -1;
  let dataSize = 0;

  while (offset + 8 <= view.byteLength) {
    const chunkId = ascii(view, offset, 4);
    const chunkSize = view.getUint32(offset + 4, true);
    const chunkDataOffset = offset + 8;
    if (chunkId === 'fmt ') {
      const audioFormat = view.getUint16(chunkDataOffset, true);
      const channels = view.getUint16(chunkDataOffset + 2, true);
      const sampleRate = view.getUint32(chunkDataOffset + 4, true);
      const bitsPerSample = view.getUint16(chunkDataOffset + 14, true);
      if (audioFormat !== 1 || bitsPerSample !== 16 || channels !== 1) {
        throw new Error('only mono 16-bit PCM WAV is supported for composed speech');
      }
      spec = { sampleRate, channels, bitsPerSample };
    } else if (chunkId === 'data') {
      dataOffset = chunkDataOffset;
      dataSize = chunkSize;
    }
    offset = chunkDataOffset + chunkSize + (chunkSize % 2);
  }

  if (!spec || dataOffset < 0) {
    throw new Error('unsupported WAV data');
  }
  const samples: number[] = [];
  for (let i = 0; i < dataSize; i += 2) {
    samples.push(view.getInt16(dataOffset + i, true));
  }
  return { spec, samples };
}

function encodeWav(samples: number[], spec: WavSpec): ArrayBuffer {
  const dataSize = samples.length * 2;
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);
  writeAscii(view, 0, 'RIFF');
  view.setUint32(4, 36 + dataSize, true);
  writeAscii(view, 8, 'WAVE');
  writeAscii(view, 12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, spec.channels, true);
  view.setUint32(24, spec.sampleRate, true);
  view.setUint32(28, spec.sampleRate * spec.channels * (spec.bitsPerSample / 8), true);
  view.setUint16(32, spec.channels * (spec.bitsPerSample / 8), true);
  view.setUint16(34, spec.bitsPerSample, true);
  writeAscii(view, 36, 'data');
  view.setUint32(40, dataSize, true);
  samples.forEach((sample, index) => {
    view.setInt16(44 + index * 2, sample, true);
  });
  return buffer;
}

function trimSilence(samples: number[]): number[] {
  let start = 0;
  let end = samples.length;
  while (start < end && Math.abs(samples[start]) <= PCM_SILENCE_THRESHOLD) start += 1;
  while (end > start && Math.abs(samples[end - 1]) <= PCM_SILENCE_THRESHOLD) end -= 1;
  return samples.slice(start, end);
}

function secondsToSamples(seconds: number, sampleRate: number): number {
  return Math.round(seconds * sampleRate);
}

function sameSpec(a: WavSpec, b: WavSpec): boolean {
  return a.sampleRate === b.sampleRate
    && a.channels === b.channels
    && a.bitsPerSample === b.bitsPerSample;
}

function ascii(view: DataView, offset: number, length: number): string {
  let out = '';
  for (let i = 0; i < length; i += 1) {
    out += String.fromCharCode(view.getUint8(offset + i));
  }
  return out;
}

function writeAscii(view: DataView, offset: number, value: string): void {
  for (let i = 0; i < value.length; i += 1) {
    view.setUint8(offset + i, value.charCodeAt(i));
  }
}
