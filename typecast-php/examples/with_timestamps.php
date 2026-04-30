<?php
/**
 * Example: text-to-speech with word/character timestamps, SRT and VTT export.
 *
 * Usage: TYPECAST_API_KEY=your-key php examples/with_timestamps.php
 */

require_once __DIR__ . '/../vendor/autoload.php';

use Neosapience\Typecast\Models\TTSRequestWithTimestamps;
use Neosapience\Typecast\TypecastClient;

$apiKey = getenv('TYPECAST_API_KEY');
if (!$apiKey) {
    fwrite(STDERR, "Error: TYPECAST_API_KEY not set\n");
    exit(1);
}

$client = new TypecastClient(apiKey: $apiKey);

$request = new TTSRequestWithTimestamps(
    voiceId: 'tc_60e5426de8b95f1d3000d7b5',
    text: 'Hello. How are you?',
    model: 'ssfm-v30',
    language: 'eng',
);

$resp = $client->textToSpeechWithTimestamps($request);

$resp->saveAudio('/tmp/with_timestamps_php.wav');
file_put_contents('/tmp/with_timestamps_php.srt', $resp->toSrt());
file_put_contents('/tmp/with_timestamps_php.vtt', $resp->toVtt());

printf("audio: /tmp/with_timestamps_php.wav (%.2fs, format=%s)\n",
    $resp->audioDuration, $resp->audioFormat);
$wordCount = count($resp->words ?? []);
$charCount = count($resp->characters ?? []);
printf("words: %d, characters: %d\n", $wordCount, $charCount);
$srt = $resp->toSrt();
$lines = explode("\n", $srt);
$firstCue = implode("\n", array_slice($lines, 0, 4));
echo "SRT first cue:\n$firstCue\n";
