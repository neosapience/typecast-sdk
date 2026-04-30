import { TypecastClient } from '../src';

async function main(): Promise<void> {
  const client = new TypecastClient();
  const result = await client.textToSpeechWithTimestamps({
    voice_id: 'tc_60e5426de8b95f1d3000d7b5',
    text: 'Hello. How are you?',
    model: 'ssfm-v30',
    language: 'eng',
  });
  await result.saveAudio('hello.wav');
  await import('node:fs/promises').then((fs) =>
    Promise.all([
      fs.writeFile('hello.srt', result.toSrt()),
      fs.writeFile('hello.vtt', result.toVtt()),
    ]),
  );
  console.log(`audio: hello.wav (${result.audio_duration}s, ${result.audio_format})`);
  console.log(`words: ${result.words?.length ?? 0}, characters: ${result.characters?.length ?? 0}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
