import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TypecastClient } from '../src/client';
import { TTSModel } from '../src/types/TextToSpeech';

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

describe('TypecastClient', () => {
  let client: TypecastClient;

  beforeEach(() => {
    // Reset mocks before each test
    vi.clearAllMocks();

    client = new TypecastClient({
      baseHost: 'https://dummy-api.ai',
      apiKey: 'test-api-key',
    });
  });

  it('textToSpeech should successfully convert text to speech', async () => {
    // Setup mock response
    const mockAudioData = new ArrayBuffer(16);
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({
        'x-audio-duration': '1.5',
        'content-type': 'audio/wav',
      }),
      arrayBuffer: () => Promise.resolve(mockAudioData),
    });

    const request = {
      text: 'Hello',
      voice_id: 'default',
      model: 'ssfm-v21' as TTSModel,
      language: 'ko',
      seed: 12345,
      prompt: {
        emotion_preset: 'normal' as const,
        emotion_intensity: 1.0
      },
      output: {
        volume: 100,
        audio_pitch: 0,
        audio_tempo: 1.0,
        audio_format: 'wav' as const,
      },
    };

    const response = await client.textToSpeech(request);

    expect(response.duration).toBe(1.5);
    expect(response.format).toBe('wav');
    expect(response.audioData).toBeTruthy();
    expect(mockFetch).toHaveBeenCalledWith(
      'https://dummy-api.ai/v1/text-to-speech',
      expect.objectContaining({
        method: 'POST',
        headers: {
          'X-API-KEY': 'test-api-key',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(request),
      })
    );
  });

  it('getVoices should return list of voices', async () => {
    const mockVoices = [
      { voice_id: 'voice1', voice_name: 'Voice 1', emotions: ['normal', 'happy'] },
      { voice_id: 'voice2', voice_name: 'Voice 2', emotions: ['normal', 'sad'] },
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
      })
    );
  });

  it('getVoices should pass model parameter', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve([]),
    });

    await client.getVoices('ssfm-v21');

    expect(mockFetch).toHaveBeenCalledWith(
      'https://dummy-api.ai/v1/voices?model=ssfm-v21',
      expect.anything()
    );
  });

  it('getVoicesV2 should return list of voices with enhanced metadata', async () => {
    const mockVoices = [
      {
        voice_id: 'voice1',
        voice_name: 'Voice 1',
        models: [{ version: 'ssfm-v21', emotions: ['normal', 'happy'] }],
        gender: 'female',
        age: 'young_adult',
      },
    ];

    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve(mockVoices),
    });

    const voices = await client.getVoicesV2();

    expect(voices).toHaveLength(1);
    expect(voices[0].voice_id).toBe('voice1');
    expect(voices[0].models).toHaveLength(1);
  });

  it('should throw TypecastAPIError on error response', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      json: () => Promise.resolve({ message: 'Invalid API key' }),
    });

    await expect(client.getVoices()).rejects.toThrow('Unauthorized');
  });
});
