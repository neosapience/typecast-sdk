import { describe, it, expect, beforeEach } from 'vitest';
import dotenv from 'dotenv';
import { TypecastClient } from '../../src/client.js';
import { TTSModel } from '../../src/types/TextToSpeech.js';
import fs from 'fs';

dotenv.config();

const hasApiKey = Boolean(process.env.TYPECAST_API_KEY);

describe.skipIf(!hasApiKey)('TypecastClient e2e: textToSpeech', () => {
  let client: TypecastClient;

  beforeEach(() => {
    client = new TypecastClient();
  });

  it('should convert text to speech with real API', async () => {
    const voices = await client.getVoices();
    const voice = voices.filter((voice) => voice.model === 'ssfm-v21')[0];

    const request = {
      text: 'Hello, how are you?',
      voice_id: voice.voice_id,
      model: voice.model as TTSModel,
      prompt: {
        emotion_preset: 'normal' as const,
        emotion_intensity: 1.0,
      },
      output: {
        audio_format: 'wav' as const,
        audio_tempo: 1.0,
      },
    };

    const response = await client.textToSpeech(request);

    expect(response.format).toBe('wav');
    expect(response.audioData).toBeInstanceOf(ArrayBuffer);
    expect(response.audioData.byteLength).toBeGreaterThan(0);

    const outputPath = './test-output.wav';
    await fs.promises.writeFile(outputPath, Buffer.from(response.audioData));
    expect(fs.existsSync(outputPath)).toBe(true);
  }, 30000);
});
