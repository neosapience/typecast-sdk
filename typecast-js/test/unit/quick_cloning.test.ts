import { describe, it, expect } from 'vitest';
import { validateCloneInputs, CLONING_MAX_FILE_SIZE, guessAudioMime } from '../../src/types/QuickCloning';

describe('validateCloneInputs', () => {
  it('rejects file too large', () => {
    const big = new Uint8Array(CLONING_MAX_FILE_SIZE + 1);
    expect(() => validateCloneInputs(big, 'demo')).toThrow(/exceeds 25MB/);
  });

  it('rejects empty name', () => {
    const buf = new Uint8Array(1024);
    expect(() => validateCloneInputs(buf, '')).toThrow(/1-30 characters/);
  });

  it('rejects long name', () => {
    const buf = new Uint8Array(1024);
    expect(() => validateCloneInputs(buf, 'x'.repeat(31))).toThrow(/1-30 characters/);
  });

  it('accepts Uint8Array with default filename', () => {
    const buf = new Uint8Array(1024);
    const { audioBytes, filename } = validateCloneInputs(buf, 'demo');
    expect(audioBytes.byteLength).toBe(1024);
    expect(filename).toBe('audio.wav');
  });

  it('accepts Buffer with default filename', () => {
    const buf = Buffer.alloc(2048);
    const { audioBytes, filename } = validateCloneInputs(buf, 'demo');
    expect(audioBytes.byteLength).toBe(2048);
    expect(filename).toBe('audio.wav');
  });
});

describe('guessAudioMime', () => {
  it('detects wav', () => expect(guessAudioMime('foo.wav')).toBe('audio/wav'));
  it('detects mp3', () => expect(guessAudioMime('foo.mp3')).toBe('audio/mpeg'));
  it('falls back to octet-stream', () =>
    expect(guessAudioMime('foo.bin')).toBe('application/octet-stream'));
  it('case-insensitive', () => expect(guessAudioMime('FOO.WAV')).toBe('audio/wav'));
});

import { promises as fs } from 'node:fs';
import path from 'node:path';
import { tmpdir } from 'node:os';
import { validateCloneInputsAsync } from '../../src/types/QuickCloning';

describe('validateCloneInputsAsync', () => {
  it('reads from a file path and returns basename', async () => {
    const tmp = path.join(tmpdir(), `tc-clone-${Date.now()}.wav`);
    await fs.writeFile(tmp, new Uint8Array(2048));
    try {
      const { audioBytes, filename } = await validateCloneInputsAsync(tmp, 'demo');
      expect(audioBytes.byteLength).toBe(2048);
      expect(filename).toBe(path.basename(tmp));
    } finally {
      await fs.unlink(tmp);
    }
  });

  it('throws audio file not found for missing path', async () => {
    await expect(
      validateCloneInputsAsync('/no/such/file.wav', 'demo'),
    ).rejects.toThrow(/audio file not found/);
  });

  it('accepts Uint8Array via async path', async () => {
    const buf = new Uint8Array(1024);
    const { audioBytes, filename } = await validateCloneInputsAsync(buf, 'demo');
    expect(audioBytes.byteLength).toBe(1024);
    expect(filename).toBe('audio.wav');
  });
});
