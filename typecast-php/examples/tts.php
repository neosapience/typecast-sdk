<?php

/**
 * Typecast TTS PHP SDK Example
 *
 * Usage:
 *   TYPECAST_API_KEY=your-key php examples/tts.php
 */

require_once __DIR__ . '/../vendor/autoload.php';

use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\OutputStream;
use Neosapience\Typecast\Models\PresetPrompt;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSRequestStream;
use Neosapience\Typecast\TypecastClient;

$apiKey = getenv('TYPECAST_API_KEY');
if (!$apiKey) {
    echo "Set TYPECAST_API_KEY environment variable\n";
    exit(1);
}

$client = new TypecastClient(apiKey: $apiKey);

// 1. Check subscription
echo "=== Subscription ===\n";
$sub = $client->getMySubscription();
echo "Plan: {$sub->plan}\n";
echo "Credits: {$sub->usedCredits}/{$sub->planCredits}\n";
echo "Concurrency limit: {$sub->concurrencyLimit}\n\n";

// 2. List voices (V2)
echo "=== Voices (V2) ===\n";
$voices = $client->getVoicesV2();
foreach (array_slice($voices, 0, 3) as $voice) {
    echo "  {$voice->voiceId}: {$voice->voiceName}\n";
}
echo "  ... (" . count($voices) . " total)\n\n";

// 3. Text-to-Speech
echo "=== Text-to-Speech ===\n";
$voiceId = $voices[0]->voiceId;
$model = $voices[0]->models[0]['version'] ?? 'ssfm-v30';

$request = new TTSRequest(
    voiceId: $voiceId,
    text: 'Hello! This is a test of the Typecast TTS API.',
    model: $model,
    prompt: new PresetPrompt(emotionPreset: 'happy'),
    output: new Output(audioFormat: 'wav'),
);

$response = $client->textToSpeech($request);
$outputFile = __DIR__ . '/output.wav';
file_put_contents($outputFile, $response->audioData);
echo "Saved to {$outputFile}\n";
echo "Duration: {$response->duration}s, Format: {$response->format}\n\n";

// 4. Streaming TTS
echo "=== Streaming TTS ===\n";
$streamRequest = new TTSRequestStream(
    voiceId: $voiceId,
    text: 'This audio is streamed chunk by chunk.',
    model: $model,
    output: new OutputStream(audioFormat: 'wav'),
);

$streamFile = __DIR__ . '/output_stream.wav';
$fp = fopen($streamFile, 'wb');
$totalBytes = 0;

$client->textToSpeechStream($streamRequest, function (string $chunk) use ($fp, &$totalBytes): void {
    fwrite($fp, $chunk);
    $totalBytes += strlen($chunk);
});

fclose($fp);
echo "Streamed {$totalBytes} bytes to {$streamFile}\n";
echo "\nDone!\n";
