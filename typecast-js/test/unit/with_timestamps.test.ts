import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { WithTimestampsResult, type TTSWithTimestampsResponse } from '../../src/types/Timestamps';
import { TypecastClient } from '../../src/client';
import { TypecastAPIError } from '../../src/errors';

const FIX = resolve(__dirname, '../../../test-fixtures/with-timestamps');
const load = (n: string): TTSWithTimestampsResponse =>
  JSON.parse(readFileSync(`${FIX}/${n}.json`, 'utf-8'));
const loadText = (n: string): string => readFileSync(`${FIX}/expected/${n}`, 'utf-8');

describe('WithTimestampsResult — SRT/VTT byte-equivalence', () => {
  it.each(['both', 'word_only', 'char_only', 'jpn_char'])(
    'toSrt matches expected for %s',
    (name) => {
      const result = new WithTimestampsResult(load(name));
      expect(result.toSrt()).toBe(loadText(`${name}.srt`));
    },
  );

  it.each(['both', 'word_only', 'char_only', 'jpn_char'])(
    'toVtt matches expected for %s',
    (name) => {
      const result = new WithTimestampsResult(load(name));
      expect(result.toVtt()).toBe(loadText(`${name}.vtt`));
    },
  );

  it('audioBytes decodes base64', () => {
    const result = new WithTimestampsResult(load('both'));
    expect(result.audioBytes).toBeInstanceOf(Uint8Array);
    expect(result.audioBytes.byteLength).toBeGreaterThan(0);
  });

  it('saveAudio writes file', async () => {
    const { mkdtempSync, readFileSync, rmSync } = await import('node:fs');
    const { tmpdir } = await import('node:os');
    const { join } = await import('node:path');
    const dir = mkdtempSync(join(tmpdir(), 'tts-'));
    try {
      const result = new WithTimestampsResult(load('both'));
      const out = join(dir, 'out.wav');
      await result.saveAudio(out);
      expect(readFileSync(out).byteLength).toBe(result.audioBytes.byteLength);
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it('throws when both arrays are missing', () => {
    const result = new WithTimestampsResult({
      audio: 'UklGRgAAAA==',
      audio_format: 'wav',
      audio_duration: 0,
      words: null,
      characters: null,
    });
    expect(() => result.toSrt()).toThrow(/no alignment segments/);
  });

  it('audioBytes throws on invalid base64', () => {
    const result = new WithTimestampsResult({
      audio: '!!! not base64 !!!',
      audio_format: 'wav',
      audio_duration: 0,
      words: null,
      characters: null,
    });
    expect(() => result.audioBytes).toThrow(/[Ii]nvalid base64/);
  });

  it('falls back to single-word when characters is empty array', () => {
    const result = new WithTimestampsResult({
      audio: 'UklGRgAAAA==',
      audio_format: 'wav',
      audio_duration: 1.0,
      words: [{ text: 'Hello.', start: 0, end: 1 }],
      characters: [],
    });
    const srt = result.toSrt();
    expect(srt).toContain('Hello.');
  });
});

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

describe('TypecastClient.textToSpeechWithTimestamps', () => {
  let client: TypecastClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'k' });
  });

  it('calls /v1/text-to-speech/with-timestamps without granularity', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => load('both'),
    });
    const result = await client.textToSpeechWithTimestamps({
      voice_id: 'tc_x',
      text: 'Hi',
      model: 'ssfm-v30',
    });
    expect(result.toSrt()).toBe(loadText('both.srt'));
    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toBe('https://dummy-api.ai/v1/text-to-speech/with-timestamps');
  });

  it('appends granularity query', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers(),
      json: async () => load('word_only'),
    });
    await client.textToSpeechWithTimestamps(
      { voice_id: 'tc_x', text: 'Hi', model: 'ssfm-v30' },
      { granularity: 'word' },
    );
    const url = mockFetch.mock.calls[0][0] as string;
    expect(url).toBe('https://dummy-api.ai/v1/text-to-speech/with-timestamps?granularity=word');
  });

  it('rejects invalid granularity', async () => {
    await expect(
      client.textToSpeechWithTimestamps(
        { voice_id: 'tc_x', text: 'Hi', model: 'ssfm-v30' },
        // @ts-expect-error invalid value
        { granularity: 'words' },
      ),
    ).rejects.toThrow(/granularity/);
  });

  it('maps 402 to TypecastAPIError', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 402,
      statusText: 'Payment Required',
      json: async () => ({ detail: 'Insufficient credit' }),
    });
    await expect(
      client.textToSpeechWithTimestamps({ voice_id: 'tc_x', text: 'Hi', model: 'ssfm-v30' }),
    ).rejects.toThrow(TypecastAPIError);
  });
});

describe('WithTimestampsResult — caption limit overrides', () => {
  it('toSrt with smaller maxChars splits more cues', () => {
    const result = new WithTimestampsResult(load('both'));
    const defaultSrt = result.toSrt();
    const tightSrt = result.toSrt({ maxChars: 8 });
    expect(tightSrt.split('\n\n').length).toBeGreaterThan(defaultSrt.split('\n\n').length);
  });

  it('toVtt maxSeconds override is accepted and changes output', () => {
    const result = new WithTimestampsResult(load('both'));
    const shortCue = result.toVtt({ maxSeconds: 0.5 });
    const defaultCue = result.toVtt({ maxSeconds: 7.0 });
    expect(shortCue).not.toBe(defaultCue);
  });

  it('throws when majority of word segments have empty text', () => {
    const result = new WithTimestampsResult({
      audio: 'UklGRgAAAA==',
      audio_format: 'wav',
      audio_duration: 1.0,
      words: [
        { text: '', start: 0.0, end: 0.5 },
        { text: '', start: 0.5, end: 1.0 },
        { text: 'hi', start: 0.0, end: 1.0 },
      ],
      characters: null,
    });
    expect(() => result.toSrt()).toThrow(/alignment segments contain empty text/);
  });
});
