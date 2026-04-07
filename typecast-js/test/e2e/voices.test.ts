import { describe, it, expect, beforeEach } from 'vitest';
import dotenv from 'dotenv';
import { TypecastClient } from '../../src/client.js';

dotenv.config();

const hasApiKey = Boolean(process.env.TYPECAST_API_KEY);

describe.skipIf(!hasApiKey)('TypecastClient e2e: voices', () => {
  let client: TypecastClient;

  beforeEach(() => {
    client = new TypecastClient();
  });

  it('should return voices with the expected schema', async () => {
    const voices = await client.getVoices();
    expect(voices).toBeDefined();
    expect(Array.isArray(voices)).toBe(true);
    expect(voices.length).toBeGreaterThan(0);

    const voice = voices[0];
    expect(voice).toHaveProperty('voice_name');
    expect(voice).toHaveProperty('voice_id');
    expect(voice).toHaveProperty('model');
    expect(voice).toHaveProperty('emotions');
  }, 30000);

  it('should filter voices by model', async () => {
    const targetModel = 'ssfm-v21';
    const voices = await client.getVoices(targetModel);

    expect(voices.length).toBeGreaterThan(0);
    voices.forEach((voice) => {
      expect(voice.model).toBe(targetModel);
    });
  }, 30000);

  it('should throw TypecastAPIError for non-existent model', async () => {
    await expect(client.getVoices('non-existent-model')).rejects.toMatchObject({
      name: 'TypecastAPIError',
      statusCode: 422,
    });
  }, 30000);
});
