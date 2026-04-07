import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
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
          headers: {
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
          },
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
  });
});
