<?php

/**
 * Quick Voice Cloning — PHP SDK Example
 *
 * Demonstrates how to clone a voice from an audio sample, use it for TTS,
 * and delete it when done.
 *
 * Usage:
 *   TYPECAST_API_KEY=your-key php examples/quick-cloning.php [path/to/sample.wav]
 *
 * If no audio file is specified, a small synthetic WAV is generated in-memory
 * so the example runs without any external files.
 */

require_once __DIR__ . '/../vendor/autoload.php';

use Neosapience\Typecast\Exceptions\TypecastException;
use Neosapience\Typecast\Models\CustomVoice;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\TypecastClient;

// ---------------------------------------------------------------------- //
// Configuration                                                           //
// ---------------------------------------------------------------------- //

$apiKey = getenv('TYPECAST_API_KEY');
if (!$apiKey) {
    echo "Error: set the TYPECAST_API_KEY environment variable.\n";
    exit(1);
}

$audioPath = $argv[1] ?? null;

// ---------------------------------------------------------------------- //
// Prepare audio bytes                                                     //
// ---------------------------------------------------------------------- //

if ($audioPath !== null) {
    if (!file_exists($audioPath)) {
        echo "Error: file not found — {$audioPath}\n";
        exit(1);
    }
    $audioBytes = file_get_contents($audioPath);
    $filename   = basename($audioPath);
    echo "Using audio file: {$audioPath} (" . strlen($audioBytes) . " bytes)\n";
} else {
    // Generate a minimal valid WAV in memory (44-byte header + 1 s of silence).
    $filename   = 'sample.wav';
    $audioBytes = makeSilentWav(sampleRate: 16000, durationSeconds: 1);
    echo "No audio file provided — using 1-second silent WAV (" . strlen($audioBytes) . " bytes)\n";
}

// Validate size before calling the API.
if (strlen($audioBytes) > CustomVoice::CLONING_MAX_FILE_SIZE) {
    echo "Error: audio exceeds 25 MB limit.\n";
    exit(1);
}

// ---------------------------------------------------------------------- //
// Clone voice                                                             //
// ---------------------------------------------------------------------- //

$client = new TypecastClient(apiKey: $apiKey);

echo "\n--- Cloning voice ---\n";
try {
    $voice = $client->cloneVoice(
        audio: $audioBytes,
        filename: $filename,
        name: 'PHP Example Voice',
        model: 'ssfm-v21',
    );
} catch (\InvalidArgumentException $e) {
    echo "Validation error: {$e->getMessage()}\n";
    exit(1);
} catch (TypecastException $e) {
    echo "API error: {$e->getMessage()}\n";
    exit(1);
}

echo "Cloned voice ID : {$voice->voiceId}\n";
echo "Name            : {$voice->name}\n";
echo "Model           : {$voice->model}\n";

// ---------------------------------------------------------------------- //
// Use the cloned voice for TTS                                            //
// ---------------------------------------------------------------------- //

echo "\n--- Synthesising speech with the cloned voice ---\n";
try {
    $tts = $client->textToSpeech(new TTSRequest(
        voiceId: $voice->voiceId,
        text: 'Hello! This is my quick-cloned voice speaking.',
        model: $voice->model,
    ));

    $outPath = __DIR__ . '/cloned_output.wav';
    file_put_contents($outPath, $tts->audioData);
    echo "Saved TTS output to {$outPath} ({$tts->duration}s)\n";
} catch (TypecastException $e) {
    echo "TTS error: {$e->getMessage()}\n";
    // Attempt cleanup even after TTS failure.
}

// ---------------------------------------------------------------------- //
// Delete the cloned voice                                                 //
// ---------------------------------------------------------------------- //

echo "\n--- Deleting cloned voice ---\n";
try {
    $client->deleteVoice($voice->voiceId);
    echo "Deleted voice {$voice->voiceId} — done.\n";
} catch (TypecastException $e) {
    echo "Delete error: {$e->getMessage()}\n";
    exit(1);
}

echo "\nExample completed successfully.\n";

// ---------------------------------------------------------------------- //
// Helper: build a minimal 1-channel 16-bit PCM WAV in memory             //
// ---------------------------------------------------------------------- //

function makeSilentWav(int $sampleRate, int $durationSeconds): string
{
    $numChannels   = 1;
    $bitsPerSample = 16;
    $numSamples    = $sampleRate * $durationSeconds;
    $dataSize      = $numSamples * $numChannels * ($bitsPerSample / 8);
    $byteRate      = $sampleRate * $numChannels * ($bitsPerSample / 8);
    $blockAlign    = $numChannels * ($bitsPerSample / 8);

    $header  = 'RIFF';
    $header .= pack('V', 36 + $dataSize);   // chunk size
    $header .= 'WAVE';
    $header .= 'fmt ';
    $header .= pack('V', 16);               // subchunk1 size
    $header .= pack('v', 1);                // PCM = 1
    $header .= pack('v', $numChannels);
    $header .= pack('V', $sampleRate);
    $header .= pack('V', $byteRate);
    $header .= pack('v', $blockAlign);
    $header .= pack('v', $bitsPerSample);
    $header .= 'data';
    $header .= pack('V', $dataSize);
    $header .= str_repeat("\x00", $dataSize);

    return $header;
}
