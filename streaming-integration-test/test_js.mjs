#!/usr/bin/env node
/**
 * Streaming TTS integration test for typecast-js SDK.
 */
import { createRequire } from 'module';
import { writeFile } from 'fs/promises';

// Load SDK from local source
const require = createRequire(import.meta.url);
const { TypecastClient } = require('../typecast-js/lib/index.cjs');

const API_KEY = '__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3';
const HOST = 'https://api.icepeak.in';
const VOICE_ID = 'tc_68d259f809700d8ac76e8567';
const OUTPUT_FILE = '/tmp/streaming_test_js.wav';

async function main() {
  const client = new TypecastClient({ apiKey: API_KEY, baseHost: HOST });

  const request = {
    voice_id: VOICE_ID,
    text: 'Hello, this is a streaming integration test from the JavaScript SDK.',
    model: 'ssfm-v30',
    language: 'eng',
    output: { audio_format: 'wav' },
  };

  console.log('[JS] Calling textToSpeechStream...');
  const stream = await client.textToSpeechStream(request);
  const reader = stream.getReader();
  const chunks = [];
  let totalBytes = 0;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    totalBytes += value.length;
  }

  const buffer = Buffer.concat(chunks);
  await writeFile(OUTPUT_FILE, buffer);
  console.log(`[JS] SUCCESS - ${chunks.length} chunks, ${totalBytes} bytes -> ${OUTPUT_FILE}`);

  if (totalBytes === 0) throw new Error('No audio data received');
}

main().catch((err) => {
  console.error('[JS] FAILED:', err.message);
  process.exit(1);
});
