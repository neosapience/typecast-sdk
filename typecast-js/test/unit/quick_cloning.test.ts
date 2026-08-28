import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  validateCloneInputs,
  CLONING_MAX_FILE_SIZE,
  guessAudioMime,
} from '../../src/types/QuickCloning';

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

const SUCCESS_V30 = JSON.parse(
  fs2.readFileSync(path.join(FIXTURE_DIR, 'success_v30.json'), 'utf-8'),
);
const SUCCESS_V21 = JSON.parse(
  fs2.readFileSync(path.join(FIXTURE_DIR, 'success_v21.json'), 'utf-8'),
);

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
    await expect(validateCloneInputsAsync('/no/such/file.wav', 'demo')).rejects.toThrow(
      /audio file not found/,
    );
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
    expect(url).toContain('/v1/custom-voices/instant-clone');
    expect(init.method).toBe('POST');
    // Critical: Content-Type must NOT be in headers, so fetch can set multipart boundary
    expect(init.headers).not.toHaveProperty('Content-Type');
    expect(init.headers).toHaveProperty('X-API-KEY', 'test-api-key');
    expect(init.headers).toHaveProperty(
      'User-Agent',
      expect.stringMatching(
        /^typecast-js\/0\.4\.11 Node\/\d+\.\d+ fetch \(runtime=node; base=custom; os=[a-z0-9_-]+; arch=[a-z0-9_-]+; sdk_env=node; platform=server\)$/,
      ),
    );
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
    expect(url).toContain('/v1/custom-voices/uc_xxx');
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

// --- Coverage gap closers ---

describe('TypecastClient.cloneVoice rejects invalid model', () => {
  it('throws TypeError for unknown model string', async () => {
    const client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
    await expect(
      client.cloneVoice({
        audio: new Uint8Array(1024),
        name: 'demo',
        model: 'ssfm-v99' as any,
      }),
    ).rejects.toThrow(/ssfm-v21.*ssfm-v30/);
  });
});

describe('TypecastClient.deleteVoice rejects bad voiceId', () => {
  it('throws TypeError for empty voiceId', async () => {
    const client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
    await expect(client.deleteVoice('')).rejects.toThrow(/uc_/);
  });

  it('throws TypeError for voiceId without uc_ prefix', async () => {
    const client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
    await expect(client.deleteVoice('tc_xxx')).rejects.toThrow(/uc_/);
  });
});

describe('TypecastClient.deleteVoice surfaces non-ok responses', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('throws via handleResponse on 500', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      json: () => Promise.resolve({ error_code: 'INTERNAL', message: 'boom' }),
    });
    const client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
    await expect(client.deleteVoice('uc_xxx')).rejects.toThrow();
  });
});

describe('TypecastClient custom voice workflows', () => {
  const professionalVoice = {
    voice_id: 'uc_professional',
    name: 'narrator',
    model: 'ssfm-v30',
    source: 'professional',
    status: 'processing',
  };
  let client: TypecastClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new TypecastClient({ baseHost: 'https://dummy-api.ai', apiKey: 'test-api-key' });
  });

  it('starts a professional clone with the documented multipart fields', async () => {
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve(professionalVoice) });

    const voice = await client.createProfessionalVoice({
      audio: new Uint8Array(1024),
      name: 'narrator',
      language: 'en',
      model: 'ssfm-v30',
    });

    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit & { body: FormData }];
    expect(url).toContain('/v1/custom-voices/professional-clone');
    expect(init.method).toBe('POST');
    expect(init.headers).not.toHaveProperty('Content-Type');
    expect(init.body.get('language')).toBe('en');
    expect(init.body.get('files')).toBeTruthy();
    expect(voice).toMatchObject({ voiceId: 'uc_professional', status: 'processing' });
  });

  it('lists and gets custom voices with their clone status', async () => {
    mockFetch
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve([professionalVoice]) })
      .mockResolvedValueOnce({ ok: true, json: () => Promise.resolve(professionalVoice) });

    expect((await client.getCustomVoices())[0]).toMatchObject({ source: 'professional' });
    expect(await client.getCustomVoice('uc_professional')).toMatchObject({
      voiceId: 'uc_professional',
    });
    expect(mockFetch.mock.calls[0][0]).toContain('/v1/custom-voices');
    expect(mockFetch.mock.calls[1][0]).toContain('/v1/custom-voices/uc_professional');
  });

  it('rejects malformed custom voice ids before fetching', async () => {
    await expect(client.getCustomVoice('tc_voice')).rejects.toThrow(/uc_/);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('rejects unsupported professional-clone models before fetching', async () => {
    await expect(
      client.createProfessionalVoice({
        audio: new Uint8Array(1024),
        name: 'narrator',
        language: 'en',
        model: 'ssfm-v99' as any,
      }),
    ).rejects.toThrow(/ssfm-v21.*ssfm-v30/);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('keeps optional clone metadata undefined when the API omits it', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([{ voice_id: 'uc_instant', name: 'instant', model: 'ssfm-v30' }]),
    });

    await expect(client.getCustomVoices()).resolves.toEqual([
      {
        voiceId: 'uc_instant',
        name: 'instant',
        model: 'ssfm-v30',
        source: undefined,
        status: undefined,
        error: undefined,
        createdAt: undefined,
      },
    ]);
  });
});

describe('TypecastClient V3 voice list', () => {
  it('uses the V3 endpoint and forwards filters', async () => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({ ok: true, json: () => Promise.resolve([]) });
    const client = new TypecastClient({ baseHost: 'https://dummy-api.ai' });

    await client.getVoicesV3({ model: 'ssfm-v30' });

    expect(mockFetch.mock.calls[0][0]).toBe('https://dummy-api.ai/v3/voices?model=ssfm-v30');
  });
});
