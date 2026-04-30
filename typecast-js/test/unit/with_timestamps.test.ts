import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  WithTimestampsResult,
  type TTSWithTimestampsResponse,
} from '../../src/types/Timestamps';

const FIX = resolve(__dirname, '../../../test-fixtures/with-timestamps');
const load = (n: string): TTSWithTimestampsResponse =>
  JSON.parse(readFileSync(`${FIX}/${n}.json`, 'utf-8'));
const loadText = (n: string): string =>
  readFileSync(`${FIX}/expected/${n}`, 'utf-8');

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
});
