import { describe, it, expect } from 'vitest';
import { TypecastClient } from '../../src/client.js';

const HAS_KEY = !!process.env.TYPECAST_API_KEY;
const VOICE = 'tc_60e5426de8b95f1d3000d7b5';

describe.skipIf(!HAS_KEY)('with-timestamps E2E', () => {
  const client = new TypecastClient();
  const base = {
    voice_id: VOICE,
    text: 'Hello. How are you?',
    model: 'ssfm-v30' as const,
    language: 'eng' as const,
    prompt: { emotion_type: 'preset', emotion_preset: 'normal', emotion_intensity: 1.0 },
    seed: 42,
  };

  it('no granularity returns words+characters', async () => {
    const r = await client.textToSpeechWithTimestamps(base);
    expect(r.audio_duration).toBeGreaterThan(0);
    expect(r.words).not.toBeNull();
    expect(r.characters).not.toBeNull();
  }, 30000);

  it('word granularity returns words only', async () => {
    const r = await client.textToSpeechWithTimestamps(base, { granularity: 'word' });
    expect(r.words).not.toBeNull();
    expect(r.characters).toBeNull();
  }, 30000);

  it('char granularity returns characters only', async () => {
    const r = await client.textToSpeechWithTimestamps(base, { granularity: 'char' });
    expect(r.characters).not.toBeNull();
    expect(r.words).toBeNull();
  }, 30000);

  it('jpn + char returns multi-character segments', async () => {
    const r = await client.textToSpeechWithTimestamps(
      { ...base, text: 'こんにちは。お元気ですか?', language: 'jpn' as const },
      { granularity: 'char' },
    );
    expect(r.characters!.length).toBeGreaterThanOrEqual(5);
  }, 30000);
});
