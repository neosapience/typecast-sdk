import { TypecastClient, TTSModel } from '@neosapience/typecast-js';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// Load .env from parent directory
const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(__dirname, '../.env') });

async function main() {
  const client = new TypecastClient();

  const request = {
    text: "Hello. It's a beautiful day today.",
    voice_id: "tc_62a8975e695ad26f7fb514d1",
    model: "ssfm-v21" as TTSModel,
    prompt: {
      emotion_preset: 'happy' as const,
      emotion_intensity: 1.0
    },
    output: {
      audio_format: 'wav' as const,
      audio_tempo: 1.0
    }
  };
  
  try {
    const response = await client.textToSpeech(request);
    
    await fs.promises.writeFile('output.wav', Buffer.from(response.audioData));
    console.log('Audio file saved as output.wav');
  } catch (error) {
    console.error('TTS request failed:', error);
  }
}

main();