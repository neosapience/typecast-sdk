import { afterEach, expect, it, vi } from 'vitest';
import { TypecastClient } from '../../src/client';

afterEach(() => vi.unstubAllGlobals());

it('rejects invalid silence before any request', async () => {
  const fetch = vi.fn();
  vi.stubGlobal('fetch', fetch);
  const client = new TypecastClient({ apiKey: 'test' });
  for (const value of [-1, 1001, 0.5, NaN, Infinity, true, '100']) {
    const request = {
      voice_id: 'test',
      text: 'test',
      model: 'ssfm-v30' as const,
      output: { remove_silence_ms: value as number },
    };
    await expect(client.textToSpeech(request)).rejects.toThrow('remove_silence_ms');
    await expect(client.textToSpeechStream(request)).rejects.toThrow('remove_silence_ms');
    await expect(client.textToSpeechWithTimestamps(request)).rejects.toThrow('remove_silence_ms');
    await expect(client.composeTextToSpeech([{ type: 'tts', ...request }])).rejects.toThrow(
      'remove_silence_ms',
    );
  }
  expect(fetch).not.toHaveBeenCalled();
});

it('preserves silence settings for TTS, streaming, timestamps, and composed segments', async () => {
  const fetch = vi.fn().mockImplementation(
    async () =>
      new Response(
        JSON.stringify({
          audio: '',
          audio_format: 'wav',
          audio_duration: 0,
          words: [],
          characters: [],
        }),
        { status: 200, headers: { 'content-type': 'audio/wav' } },
      ),
  );
  vi.stubGlobal('fetch', fetch);
  const client = new TypecastClient({ apiKey: 'test' });
  for (const value of [undefined, null, 0, 300, 1000]) {
    const request = {
      voice_id: 'test',
      text: 'test',
      model: 'ssfm-v30' as const,
      output: { remove_silence_ms: value },
    };
    await client.textToSpeech(request);
    await client.textToSpeechStream(request);
    await client.textToSpeechWithTimestamps(request);
    for (const [, options] of fetch.mock.calls.slice(-3)) {
      expect(JSON.parse(options.body).output.remove_silence_ms).toBe(value);
    }
  }
  await client
    .composeSpeech()
    .defaults({ voice_id: 'test', model: 'ssfm-v30', output: { remove_silence_ms: 300 } })
    .say('first')
    .pause(1)
    .say('second', { output: { remove_silence_ms: 0 } })
    .generate();
  const segments = JSON.parse(fetch.mock.lastCall![1].body).segments;
  expect(segments[0].output.remove_silence_ms).toBe(300);
  expect(segments[1]).toEqual({ type: 'pause', duration_seconds: 1 });
  expect(segments[2].output.remove_silence_ms).toBe(0);
});
