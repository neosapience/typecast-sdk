/**
 * E2E tests for quick voice cloning against a real Typecast API.
 *
 * Skipped unless TYPECAST_API_KEY is set. Targets `api.icepeak.in` (dev) by
 * default; override with TYPECAST_API_HOST.
 */
import { describe, it, expect, beforeAll, afterEach } from 'vitest';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { TypecastClient } from '../../src/client.js';

const HAS_KEY = !!process.env.TYPECAST_API_KEY;
const SAMPLE = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '../../../test-fixtures/quick-cloning/sample.wav',
);

function uniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}`;
}

describe.skipIf(!HAS_KEY)('quick voice cloning E2E', () => {
  let client: TypecastClient;
  const created: string[] = [];

  beforeAll(() => {
    client = new TypecastClient();
  });

  afterEach(async () => {
    while (created.length) {
      const id = created.pop()!;
      try {
        await client.deleteVoice(id);
      } catch {
        // best-effort cleanup
      }
    }
  });

  it('clone v21', async () => {
    const v = await client.cloneVoice({
      audio: SAMPLE,
      name: uniqueName('e2e-v21'),
      model: 'ssfm-v21',
    });
    created.push(v.voiceId);
    expect(v.voiceId.startsWith('uc_')).toBe(true);
    expect(v.model).toBe('ssfm-v21');
  }, 30000);

  it('clone v30', async () => {
    const v = await client.cloneVoice({
      audio: SAMPLE,
      name: uniqueName('e2e-v30'),
      model: 'ssfm-v30',
    });
    created.push(v.voiceId);
    expect(v.voiceId.startsWith('uc_')).toBe(true);
    expect(v.model).toBe('ssfm-v30');
  }, 30000);

  it('clone then synthesize', async () => {
    const v = await client.cloneVoice({
      audio: SAMPLE,
      name: uniqueName('e2e-tts'),
      model: 'ssfm-v30',
    });
    created.push(v.voiceId);
    const out = await client.textToSpeech({
      text: 'Cloned voice E2E test.',
      voice_id: v.voiceId,
      model: 'ssfm-v30',
      language: 'eng',
      prompt: { emotion_preset: 'normal', emotion_intensity: 1.0 },
    });
    expect(out.audioData.byteLength).toBeGreaterThan(1024);
  }, 30000);

  it('delete twice raises 404 on second call', async () => {
    const v = await client.cloneVoice({
      audio: SAMPLE,
      name: uniqueName('e2e-del'),
      model: 'ssfm-v30',
    });
    await client.deleteVoice(v.voiceId);
    await expect(client.deleteVoice(v.voiceId)).rejects.toThrow();
  }, 30000);
});
