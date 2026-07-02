import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { TypecastClient } from '../../src/client';
import { TypecastAPIError } from '../../src/errors';
import { TTSModel } from '../../src/types/TextToSpeech';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const baseRequest = {
  text: 'Hello',
  voice_id: 'tc_mock_001',
  model: 'ssfm-v21' as TTSModel,
  language: 'eng' as const,
  seed: 12345,
  prompt: {
    emotion_preset: 'normal' as const,
    emotion_intensity: 1.0,
  },
  output: {
    volume: 100,
    audio_pitch: 0,
    audio_tempo: 1.0,
    audio_format: 'wav' as const,
  },
};

describe('TypecastClient', () => {
  let client: TypecastClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new TypecastClient({
      baseHost: 'https://dummy-api.ai',
      apiKey: 'test-api-key',
    });
  });

  describe('textToSpeech', () => {
    it('returns wav audio with duration from headers', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({
          'x-audio-duration': '1.5',
          'content-type': 'audio/wav',
        }),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(16)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.duration).toBe(1.5);
      expect(response.format).toBe('wav');
      expect(response.audioData.byteLength).toBe(16);
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/text-to-speech',
        expect.objectContaining({
          method: 'POST',
          headers: {
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
            'User-Agent': expect.stringMatching(
              /^typecast-js\/0\.4\.6 Node\/\d+\.\d+ fetch \(runtime=node; base=custom; os=[a-z0-9_-]+; arch=[a-z0-9_-]+; sdk_env=node; platform=server\)$/,
            ),
          },
          body: JSON.stringify(baseRequest),
        }),
      );
    });

    it('returns mp3 format when content-type header says audio/mp3', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({
          'x-audio-duration': '2.0',
          'content-type': 'audio/mp3',
        }),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(8)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.format).toBe('mp3');
      expect(response.duration).toBe(2.0);
    });

    it('falls back to wav and duration 0 when headers are missing', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(4)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.format).toBe('wav');
      expect(response.duration).toBe(0);
    });

    it('falls back to wav when content-type has no slash subtype', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'audio' }),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(4)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.format).toBe('wav');
    });

    it('throws TypecastAPIError when the API returns a JSON error body', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 422,
        statusText: 'Unprocessable Entity',
        json: () =>
          Promise.resolve({ detail: 'voice_id is required' }),
      });

      await expect(client.textToSpeech(baseRequest)).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 422,
      });
    });

    it('throws TypecastAPIError when the error body is not JSON', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new SyntaxError('not json')),
      });

      await expect(client.textToSpeech(baseRequest)).rejects.toBeInstanceOf(
        TypecastAPIError,
      );
    });
  });

  describe('generateToFile', () => {
    it('defaults model, infers mp3 from path, and writes audio bytes', async () => {
      const tempDir = await mkdtemp(join(tmpdir(), 'typecast-js-'));
      const outputPath = join(tempDir, 'speech.mp3');
      const payload = new Uint8Array([1, 2, 3]);
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({
          'content-type': 'audio/mp3',
          'x-audio-duration': '1.25',
        }),
        arrayBuffer: () => Promise.resolve(payload.buffer.slice(0)),
      });

      try {
        const response = await client.generateToFile(outputPath, {
          text: 'Hello',
          voice_id: 'tc_mock_001',
        });

        expect(response.format).toBe('mp3');
        expect(Array.from(await readFile(outputPath))).toEqual([1, 2, 3]);
        const body = JSON.parse(mockFetch.mock.calls[0][1].body as string);
        expect(body.model).toBe('ssfm-v30');
        expect(body.output.audio_format).toBe('mp3');
      } finally {
        await rm(tempDir, { recursive: true, force: true });
      }
    });

    it('keeps explicit output format and handles wav or unknown extensions', async () => {
      const tempDir = await mkdtemp(join(tmpdir(), 'typecast-js-'));
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        arrayBuffer: () => Promise.resolve(new Uint8Array([4]).buffer),
      });

      try {
        await client.generateToFile(join(tempDir, 'speech.wav'), {
          text: 'Hello',
          voice_id: 'tc_mock_001',
          output: { audio_format: 'mp3' },
        });
        await client.generateToFile(join(tempDir, 'speech'), {
          text: 'Hello',
          voice_id: 'tc_mock_001',
        });

        const explicitBody = JSON.parse(mockFetch.mock.calls[0][1].body as string);
        const unknownBody = JSON.parse(mockFetch.mock.calls[1][1].body as string);
        expect(explicitBody.output.audio_format).toBe('mp3');
        expect(unknownBody.output).toBeUndefined();
      } finally {
        await rm(tempDir, { recursive: true, force: true });
      }
    });

    it('rejects blank paths before requesting or writing', async () => {
      await expect(
        client.generateToFile('   ', {
          text: 'Hello',
          voice_id: 'tc_mock_001',
        }),
      ).rejects.toThrow('path cannot be empty');
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe('textToSpeechStream', () => {
    const streamRequest = {
      text: 'Hello stream',
      voice_id: 'tc_mock_001',
      model: 'ssfm-v21' as TTSModel,
      language: 'eng' as const,
      seed: 12345,
      prompt: {
        emotion_preset: 'normal' as const,
        emotion_intensity: 1.0,
      },
      output: {
        audio_pitch: 0,
        audio_tempo: 1.0,
        audio_format: 'wav' as const,
      },
    };

    it('returns a ReadableStream that yields the audio bytes', async () => {
      const payload = new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8]);
      const body = new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(payload);
          controller.close();
        },
      });

      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        body,
      });

      const stream = await client.textToSpeechStream(streamRequest);
      const reader = stream.getReader();
      const chunks: number[] = [];
      // Read until done
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        if (value) chunks.push(...value);
      }

      expect(chunks).toEqual(Array.from(payload));
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/text-to-speech/stream',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
          }),
          body: JSON.stringify(streamRequest),
        }),
      );
    });

    it('throws TypecastAPIError when response.body is null', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        body: null,
      });

      await expect(
        client.textToSpeechStream(streamRequest),
      ).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 500,
      });
    });

    it.each([
      [400, 'Bad Request'],
      [401, 'Unauthorized'],
      [402, 'Payment Required'],
      [404, 'Not Found'],
      [422, 'Unprocessable Entity'],
      [429, 'Too Many Requests'],
      [500, 'Internal Server Error'],
    ])(
      'throws TypecastAPIError on %i',
      async (status, statusText) => {
        mockFetch.mockResolvedValue({
          ok: false,
          status,
          statusText,
          json: () => Promise.resolve({ detail: `err ${status}` }),
        });

        await expect(
          client.textToSpeechStream(streamRequest),
        ).rejects.toMatchObject({
          name: 'TypecastAPIError',
          statusCode: status,
        });
      },
    );

    it('throws TypecastAPIError when the error body is not JSON', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new SyntaxError('not json')),
      });

      await expect(
        client.textToSpeechStream(streamRequest),
      ).rejects.toBeInstanceOf(TypecastAPIError);
    });
  });

  describe('voices V1', () => {
    it('getVoices returns the array on success', async () => {
      const mockVoices = [
        { voice_id: 'voice1', voice_name: 'Voice 1', model: 'ssfm-v21', emotions: ['normal', 'happy'] },
        { voice_id: 'voice2', voice_name: 'Voice 2', model: 'ssfm-v30', emotions: ['normal', 'sad'] },
      ];
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVoices),
      });

      const voices = await client.getVoices();

      expect(voices).toHaveLength(2);
      expect(voices[0].voice_id).toBe('voice1');
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices',
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
          }),
        }),
      );
    });

    it('getVoices forwards the model query parameter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.getVoices('ssfm-v21');

      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices?model=ssfm-v21',
        expect.anything(),
      );
    });

    it('getVoiceById hits the by-id endpoint', async () => {
      const single = [
        { voice_id: 'tc_001', voice_name: 'Voice 1', model: 'ssfm-v21', emotions: ['normal'] },
      ];
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(single),
      });

      const result = await client.getVoiceById('tc_001');

      expect(result).toEqual(single);
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices/tc_001',
        expect.anything(),
      );
    });

    it('getVoiceById forwards the model query parameter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.getVoiceById('tc_001', 'ssfm-v30');

      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices/tc_001?model=ssfm-v30',
        expect.anything(),
      );
    });

    it('getVoices propagates a JSON error response as TypecastAPIError', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: () => Promise.resolve({ message: 'Invalid API key' }),
      });

      await expect(client.getVoices()).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 401,
      });
    });

    it('getVoices propagates a non-JSON error body as TypecastAPIError', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new SyntaxError('not json')),
      });

      await expect(client.getVoices()).rejects.toBeInstanceOf(TypecastAPIError);
    });
  });

  describe('voices V2', () => {
    const mockVoiceV2 = {
      voice_id: 'tc_v2_001',
      voice_name: 'V2 Voice',
      models: [{ version: 'ssfm-v30', emotions: ['normal', 'happy'] }],
      gender: 'female',
      age: 'young_adult',
      use_cases: ['Audiobook'],
    };

    it('getVoicesV2 returns the array on success without filter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([mockVoiceV2]),
      });

      const voices = await client.getVoicesV2();

      expect(voices).toHaveLength(1);
      expect(voices[0].voice_id).toBe('tc_v2_001');
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v2/voices',
        expect.anything(),
      );
    });

    it('getVoicesV2 forwards filter parameters in the query string', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.getVoicesV2({
        model: 'ssfm-v30',
        gender: 'female',
        age: 'young_adult',
        use_cases: 'Audiobook',
      });

      const [calledUrl] = mockFetch.mock.calls[0];
      const url = new URL(calledUrl as string);
      expect(url.pathname).toBe('/v2/voices');
      expect(url.searchParams.get('model')).toBe('ssfm-v30');
      expect(url.searchParams.get('gender')).toBe('female');
      expect(url.searchParams.get('age')).toBe('young_adult');
      expect(url.searchParams.get('use_cases')).toBe('Audiobook');
    });

    it('getVoiceV2 hits the by-id endpoint and returns the single voice', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVoiceV2),
      });

      const voice = await client.getVoiceV2('tc_v2_001');

      expect(voice.voice_id).toBe('tc_v2_001');
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v2/voices/tc_v2_001',
        expect.anything(),
      );
    });

    it('getVoiceV2 throws TypecastAPIError on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        json: () => Promise.resolve({ detail: 'voice not found' }),
      });

      await expect(client.getVoiceV2('tc_unknown')).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 404,
      });
    });

    it('recommendVoices sends query and count, then returns scored voice ids', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve([
            { voice_id: 'tc_v2_001', voice_name: 'V2 Voice', score: 0.98 },
          ]),
      });

      const recommendations = await client.recommendVoices('warm narrator', 2);

      expect(recommendations).toEqual([
        { voice_id: 'tc_v2_001', voice_name: 'V2 Voice', score: 0.98 },
      ]);
      const [calledUrl] = mockFetch.mock.calls[0];
      const url = new URL(calledUrl as string);
      expect(url.pathname).toBe('/v1/voices/recommendations');
      expect(url.searchParams.get('query')).toBe('warm narrator');
      expect(url.searchParams.get('count')).toBe('2');
    });

    it('recommendVoices defaults count to 5 and validates the range', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.recommendVoices('voice');

      const [calledUrl] = mockFetch.mock.calls[0];
      expect(new URL(calledUrl as string).searchParams.get('count')).toBe('5');
      await expect(client.recommendVoices('voice', 11)).rejects.toThrow(
        'count must be between 1 and 10',
      );
    });
  });

  describe('subscription', () => {
    const subscriptionPayload = {
      plan: 'plus' as const,
      credits: { plan_credits: 100000, used_credits: 1234 },
      limits: { concurrency_limit: 5 },
    };

    it('getMySubscription returns the parsed subscription payload', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(subscriptionPayload),
      });

      const sub = await client.getMySubscription();

      expect(sub.plan).toBe('plus');
      expect(sub.credits.plan_credits).toBe(100000);
      expect(sub.credits.used_credits).toBe(1234);
      expect(sub.limits.concurrency_limit).toBe(5);
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/users/me/subscription',
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
          }),
        }),
      );
    });

    it.each([
      [401, 'Unauthorized'],
      [429, 'Too Many Requests'],
      [500, 'Internal Server Error'],
    ])(
      'getMySubscription throws TypecastAPIError on %i',
      async (status, statusText) => {
        mockFetch.mockResolvedValue({
          ok: false,
          status,
          statusText,
          json: () => Promise.resolve({ detail: `err ${status}` }),
        });

        await expect(client.getMySubscription()).rejects.toMatchObject({
          name: 'TypecastAPIError',
          statusCode: status,
        });
      },
    );
  });

  describe('constructor defaults', () => {
    afterEach(() => {
      vi.unstubAllEnvs();
    });

    it('falls back to env vars when no config is provided', async () => {
      vi.stubEnv('TYPECAST_API_HOST', 'https://env-host.example');
      vi.stubEnv('TYPECAST_API_KEY', 'env-api-key');

      const envClient = new TypecastClient();
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await envClient.getVoices();

      expect(mockFetch).toHaveBeenCalledWith(
        'https://env-host.example/v1/voices',
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-API-KEY': 'env-api-key',
            'Content-Type': 'application/json',
          }),
        }),
      );
    });

    it('normalizes trailing slashes in the base host', async () => {
      const trailingSlashClient = new TypecastClient({
        baseHost: 'https://dummy-api.ai///',
        apiKey: 'test-api-key',
      });
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await trailingSlashClient.getVoices();

      const [calledUrl] = mockFetch.mock.calls[0];
      expect(calledUrl).toBe('https://dummy-api.ai/v1/voices');
    });

    it('falls back to the production host when no env var is set', async () => {
      vi.stubEnv('TYPECAST_API_HOST', '');
      vi.stubEnv('TYPECAST_API_KEY', '');

      const envClient = new TypecastClient();
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await envClient.getVoices();

      const [calledUrl] = mockFetch.mock.calls[0];
      expect(calledUrl).toBe('https://api.typecast.ai/v1/voices');
    });

    it.each([
      ['x64', 'x64'],
      ['arm64', 'arm64'],
      ['ia32', 'x86'],
      ['ppc64', 'ppc64'],
      ['', 'unknown'],
    ])('reports normalized architecture context for %s', async (arch, expectedArch) => {
      const originalArch = Object.getOwnPropertyDescriptor(process, 'arch');
      Object.defineProperty(process, 'arch', { value: arch, configurable: true });
      try {
        const archClient = new TypecastClient({
          baseHost: 'https://dummy-api.ai',
          apiKey: 'test-api-key',
        });
        mockFetch.mockResolvedValue({
          ok: true,
          status: 200,
          json: () => Promise.resolve([]),
        });

        await archClient.getVoices();

        const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
        expect(init.headers).toEqual(
          expect.objectContaining({
            'User-Agent': expect.stringContaining(`arch=${expectedArch}`),
          }),
        );
      } finally {
        if (originalArch) {
          Object.defineProperty(process, 'arch', originalArch);
        }
        mockFetch.mockClear();
      }
    });

    it.each([
      ['darwin', 'macos'],
      ['win32', 'windows'],
      ['', 'unknown'],
    ])('reports normalized operating system context for %s', async (platform, expectedOS) => {
      const originalPlatform = Object.getOwnPropertyDescriptor(process, 'platform');
      Object.defineProperty(process, 'platform', { value: platform, configurable: true });
      try {
        const osClient = new TypecastClient({
          baseHost: 'https://dummy-api.ai',
          apiKey: 'test-api-key',
        });
        mockFetch.mockResolvedValue({
          ok: true,
          status: 200,
          json: () => Promise.resolve([]),
        });

        await osClient.getVoices();

        const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
        expect(init.headers).toEqual(
          expect.objectContaining({
            'User-Agent': expect.stringContaining(`os=${expectedOS}`),
          }),
        );
      } finally {
        if (originalPlatform) {
          Object.defineProperty(process, 'platform', originalPlatform);
        }
        mockFetch.mockClear();
      }
    });
  });
});
