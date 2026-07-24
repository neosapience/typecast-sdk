import type { Output, TTSRequest, TTSResponse } from './types/TextToSpeech';

export type ComposeSegment =
  ({ type: 'tts' } & TTSRequest) | { type: 'pause'; duration_seconds: number };
type ComposeFn = (segments: ComposeSegment[]) => Promise<TTSResponse>;
type ComposerSettings = Omit<Partial<TTSRequest>, 'text'>;

type SpeechPart =
  | { kind: 'speech'; text: string; overrides?: ComposerSettings }
  | { kind: 'pause'; seconds: number };

type ParsedPart = { kind: 'text'; text: string } | { kind: 'pause'; seconds: number };

const PAUSE_TOKEN = /<\|(\d+(?:\.\d+)?)s\|>/g;
export class SpeechComposer {
  private defaultSettings: ComposerSettings = {};
  private parts: SpeechPart[] = [];

  constructor(private readonly compose: ComposeFn) {}

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
    const outputFormat = this.resolveOutputFormat();
    const plan = this.buildPlan(outputFormat);
    if (!plan.some((part) => part.kind === 'speech')) {
      throw new Error('at least one speech segment is required');
    }

    return this.compose(
      plan.map((part) =>
        part.kind === 'pause'
          ? { type: 'pause', duration_seconds: part.seconds }
          : { type: 'tts', ...part.request },
      ),
    );
  }

  private resolveOutputFormat(): 'wav' | 'mp3' {
    const formats = new Set<'wav' | 'mp3'>();
    for (const part of this.parts) {
      if (part.kind !== 'speech' || !part.text.trim()) continue;
      const format = mergeSettings(this.defaultSettings, part.overrides ?? {}).output?.audio_format;
      if (format) formats.add(format);
    }
    if (formats.size > 1) throw new Error('composed speech segments must use one audio format');
    return formats.values().next().value ?? 'wav';
  }

  private buildPlan(
    outputFormat: 'wav' | 'mp3',
  ): Array<{ kind: 'speech'; request: TTSRequest } | { kind: 'pause'; seconds: number }> {
    const plan: Array<
      { kind: 'speech'; request: TTSRequest } | { kind: 'pause'; seconds: number }
    > = [];
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
              audio_format: outputFormat,
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
    const seconds = Number(match[1]);
    if (!Number.isFinite(seconds) || seconds <= 0) continue;
    if (index > lastIndex) {
      parts.push({ kind: 'text', text: text.slice(lastIndex, index) });
    }
    parts.push({ kind: 'pause', seconds });
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
