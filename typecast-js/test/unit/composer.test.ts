import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TypecastClient } from '../../src/client';
import { parsePauseMarkup } from '../../src/composer';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

describe('SpeechComposer', () => {
  let client: TypecastClient;

  beforeEach(() => {
    mockFetch.mockReset();
    client = new TypecastClient({
      baseHost: 'https://dummy-api.ai',
      apiKey: 'test-api-key',
    });
  });

  it('sends speech and pause segments to the Compose API once', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      headers: new Headers({
        'content-type': 'audio/mpeg',
        'x-audio-duration': '2.5',
      }),
      arrayBuffer: async () => new ArrayBuffer(3),
    });
    const response = await client
      .composeSpeech()
      .defaults({
        voice_id: 'voice-a',
        model: 'ssfm-v30',
        output: { audio_format: 'mp3', audio_pitch: 1 },
      })
      .say('Hello<|0.3s|>world')
      .pause(1)
      .say('Again', { voice_id: 'voice-b' })
      .generate();

    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockFetch.mock.calls[0][0]).toContain('/v1/text-to-speech/compose');
    expect(JSON.parse(mockFetch.mock.calls[0][1].body)).toMatchObject({
      segments: [
        {
          type: 'tts',
          voice_id: 'voice-a',
          text: 'Hello',
          output: { audio_format: 'mp3' },
        },
        { type: 'pause', duration_seconds: 0.3 },
        { type: 'tts', voice_id: 'voice-a', text: 'world' },
        { type: 'pause', duration_seconds: 1 },
        { type: 'tts', voice_id: 'voice-b', text: 'Again' },
      ],
    });
    expect(response).toMatchObject({ format: 'mp3', duration: 2.5 });
  });

  it('validates required builder state before the request', async () => {
    expect(() => client.composeSpeech().pause(0)).toThrow('greater than 0');
    await expect(client.composeSpeech().generate()).rejects.toThrow('at least one speech');
    await expect(
      client.composeSpeech().defaults({ model: 'ssfm-v30' }).say('Hello').generate(),
    ).rejects.toThrow('voice_id');
    await expect(
      client.composeSpeech().defaults({ voice_id: 'voice' }).say('Hello').generate(),
    ).rejects.toThrow('model');
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('preserves invalid pause markup', () => {
    expect(parsePauseMarkup('a<|0.5s|>b<|bad|>')).toEqual([
      { kind: 'text', text: 'a' },
      { kind: 'pause', seconds: 0.5 },
      { kind: 'text', text: 'b<|bad|>' },
    ]);
  });
});
