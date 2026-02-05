/**
 * Test script for ssfm-v30 model support
 *
 * Usage:
 *   TYPECAST_API_HOST=<your_api_host> TYPECAST_API_KEY=<your_api_key> npx ts-node examples/ssfm-v30-test.ts
 *
 * Or set in .env file:
 *   TYPECAST_API_HOST=<your_api_host>
 *   TYPECAST_API_KEY=<your_api_key>
 */

import {
  TypecastClient,
  TTSModel,
  SmartPrompt,
  PresetPrompt,
  VoicesV2Filter,
} from '@neosapience/typecast-js';
import dotenv from 'dotenv';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.join(__dirname, '../.env') });

async function testVoicesV2(client: TypecastClient) {
  console.log('1. Testing /v2/voices API...');
  const voicesV2 = await client.getVoicesV2();
  console.log(`   Found ${voicesV2.length} voices (V2)`);

  if (voicesV2.length > 0) {
    const sample = voicesV2[0];
    console.log(`   Sample: ${sample.voice_name} (${sample.voice_id})`);
    console.log(`   Models: ${sample.models.map(m => m.version).join(', ')}`);
    console.log(`   Gender: ${sample.gender ?? 'N/A'}, Age: ${sample.age ?? 'N/A'}`);
  }
}

async function testVoicesV2WithFilter(client: TypecastClient) {
  console.log('\n2. Testing /v2/voices with filter (ssfm-v30)...');
  const filter: VoicesV2Filter = { model: 'ssfm-v30' };
  const voices = await client.getVoicesV2(filter);
  console.log(`   Found ${voices.length} voices supporting ssfm-v30`);
  return voices;
}

async function testPresetPrompt(client: TypecastClient, voiceId: string) {
  console.log('\n3. Testing TTS with PresetPrompt (ssfm-v30)...');
  const prompt: PresetPrompt = {
    emotion_type: 'preset',
    emotion_preset: 'happy',
    emotion_intensity: 1.5,
  };

  const response = await client.textToSpeech({
    voice_id: voiceId,
    text: 'Hello! This is a test using preset emotion control.',
    model: 'ssfm-v30' as TTSModel,
    language: 'eng',
    prompt,
    output: { audio_format: 'wav' },
  });

  await fs.promises.writeFile('output_preset_v30.wav', Buffer.from(response.audioData));
  console.log(`   Success! Duration: ${response.duration}s, Format: ${response.format}`);
  console.log('   Saved: output_preset_v30.wav');
}

async function testSmartPrompt(client: TypecastClient, voiceId: string) {
  console.log('\n4. Testing TTS with SmartPrompt (ssfm-v30)...');
  const prompt: SmartPrompt = {
    emotion_type: 'smart',
    previous_text: 'I just got the best news ever!',
    next_text: 'I cannot wait to share this with everyone!',
  };

  const response = await client.textToSpeech({
    voice_id: voiceId,
    text: 'Everything is so incredibly perfect that I feel like I am dreaming.',
    model: 'ssfm-v30' as TTSModel,
    language: 'eng',
    prompt,
    output: { audio_format: 'mp3' },
  });

  await fs.promises.writeFile('output_smart_v30.mp3', Buffer.from(response.audioData));
  console.log(`   Success! Duration: ${response.duration}s, Format: ${response.format}`);
  console.log('   Saved: output_smart_v30.mp3');
}

async function testBackwardCompatibility(client: TypecastClient) {
  console.log('\n5. Testing backward compatibility (ssfm-v21)...');
  const voices = await client.getVoices('ssfm-v21');
  console.log(`   Found ${voices.length} voices (V1 API)`);

  if (voices.length > 0) {
    const response = await client.textToSpeech({
      voice_id: voices[0].voice_id,
      text: 'This is a backward compatibility test.',
      model: 'ssfm-v21' as TTSModel,
      language: 'eng',
      prompt: { emotion_preset: 'normal', emotion_intensity: 1.0 },
    });
    console.log(`   Success! Duration: ${response.duration}s`);
  }
}

async function main() {
  const client = new TypecastClient();

  console.log('=== Testing ssfm-v30 Support ===\n');

  try {
    await testVoicesV2(client);

    const v30Voices = await testVoicesV2WithFilter(client);
    const v30Voice = v30Voices.find(v => v.models.some(m => m.version === 'ssfm-v30'));

    if (v30Voice) {
      console.log(`   Using: ${v30Voice.voice_name} (${v30Voice.voice_id})`);
      await testPresetPrompt(client, v30Voice.voice_id);
      await testSmartPrompt(client, v30Voice.voice_id);
    } else {
      console.log('   No ssfm-v30 voice found, skipping TTS tests');
    }

    await testBackwardCompatibility(client);
  } catch (error) {
    console.error('Test failed:', error);
  }

  console.log('\n=== Tests completed ===');
}

main();
