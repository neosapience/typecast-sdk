import { afterEach, describe, expect, it, vi } from 'vitest';
import { TypecastClient } from '../../src/client';

afterEach(() => vi.unstubAllGlobals());

describe('legacy seed retirement', () => {
  it.each([0, 42])('omits seed %s from every TTS endpoint without mutating input', async (seed) => {
    const fetch = vi.fn(async (url: string, _init?: RequestInit) =>
      url.includes('with-timestamps')
        ? new Response(
            JSON.stringify({ audio: '', audio_format: 'wav', audio_duration: 0, words: [] }),
            { headers: { 'content-type': 'application/json' } },
          )
        : new Response(new Uint8Array([1, 2]), { headers: { 'content-type': 'audio/wav' } }),
    );
    vi.stubGlobal('fetch', fetch);
    const client = new TypecastClient({ apiKey: 'test-key' });
    const request = { voice_id: 'tc_test', text: 'hello', model: 'ssfm-v30' as const, seed };
    await client.textToSpeech(request);
    await client.textToSpeechStream(request);
    await client.textToSpeechWithTimestamps(request);
    await client.composeTextToSpeech([
      { type: 'tts', ...request },
      { type: 'pause', duration_seconds: 0.3 },
    ]);
    expect(fetch).toHaveBeenCalledTimes(4);
    for (const call of fetch.mock.calls) {
      const body = JSON.parse((call[1] as RequestInit).body as string);
      expect(body).not.toHaveProperty('seed');
      for (const segment of body.segments ?? []) expect(segment).not.toHaveProperty('seed');
    }
    expect(request.seed).toBe(seed);
  });
});
