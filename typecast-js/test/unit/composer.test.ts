import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TypecastClient } from '../../src/client';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function makeWav(samples: number[], sampleRate = 8000): ArrayBuffer {
  const bytes = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(bytes);
  const writeAscii = (offset: number, value: string): void => {
    for (let i = 0; i < value.length; i += 1) {
      view.setUint8(offset + i, value.charCodeAt(i));
    }
  };
  writeAscii(0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  writeAscii(8, 'WAVE');
  writeAscii(12, 'fmt ');
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true);
  view.setUint16(22, 1, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeAscii(36, 'data');
  view.setUint32(40, samples.length * 2, true);
  samples.forEach((sample, index) => {
    view.setInt16(44 + index * 2, sample, true);
  });
  return bytes;
}

function readPcmSamples(wav: ArrayBuffer): number[] {
  const view = new DataView(wav);
  const samples: number[] = [];
  for (let offset = 44; offset < wav.byteLength; offset += 2) {
    samples.push(view.getInt16(offset, true));
  }
  return samples;
}

function corruptAscii(buffer: ArrayBuffer, offset: number, value: string): ArrayBuffer {
  const copy = buffer.slice(0);
  const view = new DataView(copy);
  for (let i = 0; i < value.length; i += 1) {
    view.setUint8(offset + i, value.charCodeAt(i));
  }
  return copy;
}

describe('SpeechComposer', () => {
  let client: TypecastClient;

  beforeEach(() => {
    mockFetch.mockReset();
    client = new TypecastClient({
      baseHost: 'https://dummy-api.ai',
      apiKey: 'test-api-key',
    });
  });

  it('splits valid pause markup, preserves invalid tokens, merges overrides, and composes WAV', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          'content-type': 'audio/wav',
          'x-audio-duration': '0.0005',
        }),
        arrayBuffer: () => Promise.resolve(makeWav([0, 1000, 0], 8000)),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          'content-type': 'audio/wav',
          'x-audio-duration': '0.0005',
        }),
        arrayBuffer: () => Promise.resolve(makeWav([0, 2000, 0], 8000)),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({
          'content-type': 'audio/wav',
          'x-audio-duration': '0.0005',
        }),
        arrayBuffer: () => Promise.resolve(makeWav([0, 3000, 0], 8000)),
      });

    const result = await (client as any)
      .composeSpeech()
      .defaults({
        model: 'ssfm-v30',
        voice_id: 'tc_voice_a',
        output: { audio_format: 'wav', audio_pitch: 1, audio_tempo: 1.0 },
      })
      .say('Hello<|0.001s|><|abc|>')
      .pause(0.001)
      .say('World', {
        voice_id: 'tc_voice_b',
        output: { audio_pitch: 3 },
      })
      .generate();

    expect(result.format).toBe('wav');
    expect(readPcmSamples(result.audioData)).toEqual([
      1000,
      0, 0, 0, 0, 0, 0, 0, 0,
      2000,
      0, 0, 0, 0, 0, 0, 0, 0,
      3000,
    ]);
    expect(result.duration).toBe(19 / 8000);

    expect(mockFetch).toHaveBeenCalledTimes(3);
    const firstBody = JSON.parse(mockFetch.mock.calls[0][1].body as string);
    const secondBody = JSON.parse(mockFetch.mock.calls[1][1].body as string);
    const thirdBody = JSON.parse(mockFetch.mock.calls[2][1].body as string);
    expect(firstBody).toMatchObject({
      text: 'Hello',
      model: 'ssfm-v30',
      voice_id: 'tc_voice_a',
      output: { audio_format: 'wav', audio_pitch: 1, audio_tempo: 1.0 },
    });
    expect(secondBody).toMatchObject({
      text: '<|abc|>',
      model: 'ssfm-v30',
      voice_id: 'tc_voice_a',
      output: { audio_format: 'wav', audio_pitch: 1, audio_tempo: 1.0 },
    });
    expect(thirdBody).toMatchObject({
      text: 'World',
      model: 'ssfm-v30',
      voice_id: 'tc_voice_b',
      output: { audio_format: 'wav', audio_pitch: 3, audio_tempo: 1.0 },
    });
  });

  it('rejects invalid builder state before network calls', async () => {
    expect(() => (client as any).composeSpeech().pause(0)).toThrow(
      'pause seconds must be greater than 0',
    );
    await expect((client as any).composeSpeech().generate()).rejects.toThrow(
      'at least one speech segment is required',
    );
    await expect(
      (client as any).composeSpeech().defaults({ model: 'ssfm-v30' }).say('Hello').generate(),
    ).rejects.toThrow('voice_id is required');
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('raises a clear error when mp3 output cannot be encoded', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({
        'content-type': 'audio/wav',
        'x-audio-duration': '0.0005',
      }),
      arrayBuffer: () => Promise.resolve(makeWav([1000], 8000)),
    });

    await expect(
      (client as any)
        .composeSpeech()
        .defaults({
          model: 'ssfm-v30',
          voice_id: 'tc_voice_a',
          output: { audio_format: 'mp3' },
        })
        .say('Hello')
        .generate(),
    ).rejects.toThrow('ffmpeg is required to encode composed speech as mp3');
  });

  it('rejects unsupported output formats and pauses before audio exists', async () => {
    await expect(
      (client as any)
        .composeSpeech()
        .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a', output: { audio_format: 'flac' } })
        .say('Hello')
        .generate(),
    ).rejects.toThrow('unsupported composed speech output format');

    await expect(
      (client as any)
        .composeSpeech()
        .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a' })
        .pause(0.001)
        .say('Hello')
        .generate(),
    ).rejects.toThrow('pause cannot be the first composed part');
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('requires model before making network calls', async () => {
    await expect(
      (client as any).composeSpeech().defaults({ voice_id: 'tc_voice_a' }).say('Hello').generate(),
    ).rejects.toThrow('model is required');
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('skips empty text parts created around pause markup', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'audio/wav' }),
      arrayBuffer: () => Promise.resolve(makeWav([1000], 8000)),
    });

    await (client as any)
      .composeSpeech()
      .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a' })
      .say('Hello<|0.001s|>   ')
      .generate();

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const body = JSON.parse(mockFetch.mock.calls[0][1].body as string);
    expect(body.text).toBe('Hello');
  });

  it('rejects invalid and unsupported WAV segments', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'audio/wav' }),
      arrayBuffer: () => Promise.resolve(new ArrayBuffer(8)),
    });
    await expect(
      (client as any)
        .composeSpeech()
        .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a' })
        .say('Hello')
        .generate(),
    ).rejects.toThrow('unsupported WAV data');

    mockFetch.mockReset();
    const stereoWav = makeWav([1000], 8000);
    new DataView(stereoWav).setUint16(22, 2, true);
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'audio/wav' }),
      arrayBuffer: () => Promise.resolve(stereoWav),
    });
    await expect(
      (client as any)
        .composeSpeech()
        .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a' })
        .say('Hello')
        .generate(),
    ).rejects.toThrow('only mono 16-bit PCM WAV is supported');

    mockFetch.mockReset();
    mockFetch.mockResolvedValueOnce({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'audio/wav' }),
      arrayBuffer: () => Promise.resolve(corruptAscii(makeWav([1000], 8000), 36, 'JUNK')),
    });
    await expect(
      (client as any)
        .composeSpeech()
        .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a' })
        .say('Hello')
        .generate(),
    ).rejects.toThrow('unsupported WAV data');
  });

  it('rejects WAV segments with mismatched sample settings', async () => {
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'audio/wav' }),
        arrayBuffer: () => Promise.resolve(makeWav([1000], 8000)),
      })
      .mockResolvedValueOnce({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'audio/wav' }),
        arrayBuffer: () => Promise.resolve(makeWav([2000], 16000)),
      });

    await expect(
      (client as any)
        .composeSpeech()
        .defaults({ model: 'ssfm-v30', voice_id: 'tc_voice_a' })
        .say('Hello')
        .say('World')
        .generate(),
    ).rejects.toThrow('all composed WAV segments must use the same PCM format');
  });
});
