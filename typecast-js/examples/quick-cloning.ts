/**
 * Instant cloning: clone -> speak -> delete.
 *
 * Usage:
 *   export TYPECAST_API_KEY="your-api-key"
 *   export TYPECAST_API_HOST="https://api.icepeak.in"  // dev only
 *   tsx examples/quick-cloning.ts ./sample.wav
 */
import fs from 'node:fs/promises';
import { TypecastClient } from '../src';

async function main(): Promise<void> {
  const audioPath = process.argv[2];
  if (!audioPath) {
    console.error('usage: tsx examples/quick-cloning.ts <audio.wav>');
    process.exit(1);
  }

  const client = new TypecastClient();

  console.log(`[1/3] Cloning voice from ${audioPath}...`);
  const voice = await client.cloneVoice({
    audio: audioPath,
    name: 'demo-voice',
    model: 'ssfm-v30',
  });
  console.log(`  -> ${voice.voiceId}`);

  try {
    console.log('[2/3] Synthesizing greeting with the cloned voice...');
    const out = await client.textToSpeech({
      text: 'Hello! This is my cloned voice.',
      voice_id: voice.voiceId,
      model: 'ssfm-v30',
    });
    await fs.writeFile('cloned_output.wav', new Uint8Array(out.audioData));
    console.log('  -> cloned_output.wav');
  } finally {
    console.log('[3/3] Deleting cloned voice...');
    await client.deleteVoice(voice.voiceId);
    console.log('  -> done');
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
