import { describe, it, expect, beforeEach } from 'vitest';
import dotenv from 'dotenv';
import { TypecastClient } from '../src/client.js';
import { TTSModel } from '../src/types/TextToSpeech.js';
import fs from 'fs';
// Load environment variables from .env file
dotenv.config();

describe('TypecastClient Integration', () => {
  let client: TypecastClient;

  beforeEach(() => {
    // Create real client instance without mocking
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
        emotion_intensity: 1.0
      },
      output: {
        audio_format: 'wav' as const,
        audio_tempo: 1.0
      },
    };

    const response = await client.textToSpeech(request);

    // Verify the response from real API
    expect(response.format).toBe('wav');
    expect(response.audioData).toBeInstanceOf(Buffer);
    expect(response.audioData.byteLength).toBeGreaterThan(0);

    // Write audio file to disk for manual verification
    const outputPath = './test-output.wav';
    await fs.promises.writeFile(outputPath, Buffer.from(response.audioData));
    expect(fs.existsSync(outputPath)).toBe(true);

    // later
    // expect(response.duration).toBeGreaterThan(0);
  }, 30000); // Increase timeout to 30 seconds for API call
});
