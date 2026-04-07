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
});
