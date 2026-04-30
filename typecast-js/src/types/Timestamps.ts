import { writeFile } from 'node:fs/promises';
import type { TTSModel, LanguageCode } from './TextToSpeech';

export interface AlignmentSegmentWord {
  text: string;
  start: number;
  end: number;
}

export interface AlignmentSegmentCharacter {
  text: string;
  start: number;
  end: number;
}

export interface TTSRequestWithTimestamps {
  voice_id: string;
  text: string;
  model: TTSModel;
  language?: LanguageCode | string;
  prompt?: unknown;
  output?: unknown;
  seed?: number;
}

export interface TTSWithTimestampsResponse {
  audio: string;
  audio_format: 'wav' | 'mp3';
  audio_duration: number;
  words: AlignmentSegmentWord[] | null;
  characters: AlignmentSegmentCharacter[] | null;
}

const SENTENCE_TERMINATORS = ['.', '?', '!', '。', '？', '！'];
const MAX_CAPTION_SECONDS = 7.0;
const MAX_CAPTION_CHARS = 42;

type Segment = AlignmentSegmentWord | AlignmentSegmentCharacter;
type Cue = { text: string; start: number; end: number };

function pickSegments(
  words: AlignmentSegmentWord[] | null,
  characters: AlignmentSegmentCharacter[] | null,
): { segments: Segment[]; wordMode: boolean } {
  if (words && words.length >= 2) return { segments: words, wordMode: true };
  if (characters && characters.length >= 1) return { segments: characters, wordMode: false };
  if (words && words.length === 1 && !characters) return { segments: words, wordMode: true };
  throw new Error('no alignment segments to caption from');
}

function joinParts(parts: string[], wordMode: boolean): string {
  return wordMode ? parts.join(' ').trim() : parts.join('').trim();
}

function groupIntoCues(segments: Segment[], wordMode: boolean): Cue[] {
  const cues: Cue[] = [];
  let parts: string[] = [];
  let curStart: number | null = null;
  let lastEnd: number | null = null;

  const joined = (): string =>
    wordMode ? parts.join(' ').trim() : parts.join('').trim();

  const flush = (endTime: number): void => {
    const text = joined();
    if (text && curStart !== null) cues.push({ text, start: curStart, end: endTime });
  };

  for (const seg of segments) {
    // If adding this segment would overflow an existing cue, flush first.
    if (parts.length > 0 && curStart !== null && lastEnd !== null) {
      const wouldBeText = wordMode
        ? [...parts, seg.text].join(' ').trim()
        : [...parts, seg.text].join('').trim();
      const wouldExceedSeconds = seg.end - curStart > MAX_CAPTION_SECONDS;
      const wouldExceedChars = wouldBeText.length > MAX_CAPTION_CHARS;
      if (wouldExceedSeconds || wouldExceedChars) {
        flush(lastEnd);
        parts = [];
        curStart = null;
      }
    }

    if (curStart === null) curStart = seg.start;
    parts.push(seg.text);
    lastEnd = seg.end;

    // Sentence terminator splits AFTER appending.
    const endsInSentence = SENTENCE_TERMINATORS.some((t) => seg.text.trimEnd().endsWith(t));
    if (endsInSentence) {
      flush(seg.end);
      parts = [];
      curStart = null;
    }
  }
  if (parts.length > 0 && lastEnd !== null) flush(lastEnd);
  return cues;
}

function pad(n: number, width: number): string {
  return n.toString().padStart(width, '0');
}

function formatSrtTime(seconds: number): string {
  const totalMs = Math.round(seconds * 1000);
  const ms = totalMs % 1000;
  const totalSec = Math.floor(totalMs / 1000);
  const ss = totalSec % 60;
  const totalMin = Math.floor(totalSec / 60);
  const mm = totalMin % 60;
  const hh = Math.floor(totalMin / 60);
  return `${pad(hh, 2)}:${pad(mm, 2)}:${pad(ss, 2)},${pad(ms, 3)}`;
}

function formatVttTime(seconds: number): string {
  return formatSrtTime(seconds).replace(',', '.');
}

export class WithTimestampsResult implements TTSWithTimestampsResponse {
  audio: string;
  audio_format: 'wav' | 'mp3';
  audio_duration: number;
  words: AlignmentSegmentWord[] | null;
  characters: AlignmentSegmentCharacter[] | null;

  constructor(payload: TTSWithTimestampsResponse) {
    this.audio = payload.audio;
    this.audio_format = payload.audio_format;
    this.audio_duration = payload.audio_duration;
    this.words = payload.words;
    this.characters = payload.characters;
  }

  get audioBytes(): Uint8Array {
    return Uint8Array.from(Buffer.from(this.audio, 'base64'));
  }

  async saveAudio(path: string): Promise<void> {
    await writeFile(path, this.audioBytes);
  }

  toSrt(): string {
    const { segments, wordMode } = pickSegments(this.words, this.characters);
    const cues = groupIntoCues(segments, wordMode);
    if (cues.length === 0) throw new Error('no alignment segments to caption from');
    const lines: string[] = [];
    cues.forEach((cue, idx) => {
      lines.push(String(idx + 1));
      lines.push(`${formatSrtTime(cue.start)} --> ${formatSrtTime(cue.end)}`);
      lines.push(cue.text);
      lines.push('');
    });
    return lines.join('\n') + '\n';
  }

  toVtt(): string {
    const { segments, wordMode } = pickSegments(this.words, this.characters);
    const cues = groupIntoCues(segments, wordMode);
    if (cues.length === 0) throw new Error('no alignment segments to caption from');
    const lines: string[] = ['WEBVTT', ''];
    for (const cue of cues) {
      lines.push(`${formatVttTime(cue.start)} --> ${formatVttTime(cue.end)}`);
      lines.push(cue.text);
      lines.push('');
    }
    return lines.join('\n') + '\n';
  }
}
