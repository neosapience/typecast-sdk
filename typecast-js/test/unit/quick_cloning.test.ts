import { describe, it, expect, beforeEach, vi } from 'vitest';
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
import fs2 from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { tmpdir } from 'node:os';
import { validateCloneInputsAsync } from '../../src/types/QuickCloning';
import { TypecastClient } from '../../src/client';

const FIXTURE_DIR = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../test-fixtures/quick-cloning',
);

const SUCCESS_V30 = JSON.parse(fs2.readFileSync(path.join(FIXTURE_DIR, 'success_v30.json'), 'utf-8'));
const SUCCESS_V21 = JSON.parse(fs2.readFileSync(path.join(FIXTURE_DIR, 'success_v21.json'), 'utf-8'));

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

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

describe('TypecastClient.cloneVoice', () => {
  let client: TypecastClient;
  beforeEach(() => {
    vi.clearAllMocks();
    client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
  });

  it('returns CustomVoice on 200 (camelCase mapping)', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: () => Promise.resolve(SUCCESS_V30),
    });
    const voice = await client.cloneVoice({
      audio: new Uint8Array(1024),
      name: 'demo',
      model: 'ssfm-v30',
    });
    expect(voice.voiceId).toBe(SUCCESS_V30.voice_id);
    expect(voice.name).toBe(SUCCESS_V30.name);
    expect(voice.model).toBe(SUCCESS_V30.model);
  });

  it('sends multipart body without pre-set Content-Type', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: () => Promise.resolve(SUCCESS_V21),
    });
    await client.cloneVoice({
      audio: new Uint8Array(1024),
      name: 'demo',
      model: 'ssfm-v21',
    });
    expect(mockFetch).toHaveBeenCalledOnce();
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit & { body: FormData }];
    expect(url).toContain('/v1/voices/clone');
    expect(init.method).toBe('POST');
    // Critical: Content-Type must NOT be in headers, so fetch can set multipart boundary
    expect(init.headers).not.toHaveProperty('Content-Type');
    expect(init.headers).toHaveProperty('X-API-KEY', 'test-api-key');
    expect(init.body).toBeInstanceOf(FormData);
    const form = init.body;
    expect(form.get('name')).toBe('demo');
    expect(form.get('model')).toBe('ssfm-v21');
    expect(form.get('file')).toBeTruthy(); // Blob present
  });

  it('pre-validates size before fetch', async () => {
    const big = new Uint8Array(CLONING_MAX_FILE_SIZE + 1);
    await expect(
      client.cloneVoice({ audio: big, name: 'demo', model: 'ssfm-v30' }),
    ).rejects.toThrow(/exceeds 25MB/);
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

describe('TypecastClient.deleteVoice', () => {
  let client: TypecastClient;
  beforeEach(() => {
    vi.clearAllMocks();
    client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
  });

  it('resolves to undefined on 204', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers(),
      json: () => Promise.resolve(null),
    });
    await expect(client.deleteVoice('uc_xxx')).resolves.toBeUndefined();
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/v1/voices/uc_xxx');
    expect(init.method).toBe('DELETE');
  });

  it('throws on 404', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: () => Promise.resolve({ error_code: 'NOT_FOUND', message: 'voice not found' }),
    });
    await expect(client.deleteVoice('uc_xxx')).rejects.toThrow();
  });
});
