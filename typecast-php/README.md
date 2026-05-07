# Typecast PHP SDK

Official PHP SDK for the [Typecast](https://typecast.ai) Text-to-Speech API.

## Requirements

- PHP 8.1 or higher
- Composer

## Installation

```bash
composer require neosapience/typecast-php
```

## Quick Start

```php
<?php

use Neosapience\Typecast\TypecastClient;
use Neosapience\Typecast\Models\TTSRequest;

$client = new TypecastClient(apiKey: 'your-api-key');

// Convert text to speech
$response = $client->textToSpeech(new TTSRequest(
    voiceId: 'tc_62a8975e695ad26f7fb514d1',
    text: 'Hello, world!',
    model: 'ssfm-v30',
));

file_put_contents('output.wav', $response->audioData);
echo "Duration: {$response->duration}s\n";
```

## Features

- Text-to-Speech (binary response)
- Text-to-Speech with word/character timestamps (SRT/VTT caption generation)
- Streaming TTS (chunked audio)
- Voice listing (V1 and V2 APIs)
- Subscription info
- Emotion control (preset and smart prompts)
- Audio output customization (format, pitch, tempo, volume)

## API Methods

### Text-to-Speech

```php
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\PresetPrompt;

$response = $client->textToSpeech(new TTSRequest(
    voiceId: 'tc_xxx',
    text: 'Hello!',
    model: 'ssfm-v30',
    language: 'eng',
    prompt: new PresetPrompt(emotionPreset: 'happy', emotionIntensity: 1.5),
    output: new Output(volume: 100, audioFormat: 'mp3'),
));
```

### Text-to-Speech with Timestamps

```php
use Neosapience\Typecast\Models\TTSRequestWithTimestamps;

$response = $client->textToSpeechWithTimestamps(
    new TTSRequestWithTimestamps(
        voiceId: 'tc_xxx',
        text: 'Hello, world!',
        model: 'ssfm-v30',
    ),
    // Optional: 'word' or 'char' granularity (default: server decides)
);

// Save audio
$response->saveAudio('output.wav');

// Generate subtitles
file_put_contents('output.srt', $response->toSrt());
file_put_contents('output.vtt', $response->toVtt());

// Access raw alignment data
foreach ($response->words ?? [] as $word) {
    echo "{$word->text}: {$word->start}s — {$word->end}s\n";
}
```

### Streaming TTS

```php
use Neosapience\Typecast\Models\TTSRequestStream;

$client->textToSpeechStream(
    new TTSRequestStream(
        voiceId: 'tc_xxx',
        text: 'Streamed audio.',
        model: 'ssfm-v30',
    ),
    function (string $chunk): void {
        // Write chunk to file or output
        fwrite($fp, $chunk);
    },
);
```

### Subscription

```php
$sub = $client->getMySubscription();
echo "Plan: {$sub->plan}, Credits: {$sub->usedCredits}/{$sub->planCredits}\n";
```

### Voices

```php
// V1 API
$voices = $client->getVoices(model: 'ssfm-v21');

// V2 API with filters
use Neosapience\Typecast\Models\VoicesV2Filter;

$voices = $client->getVoicesV2(new VoicesV2Filter(
    model: 'ssfm-v30',
    gender: 'female',
));

// Get specific voice
$voice = $client->getVoiceV2('tc_xxx');
```

### Quick Voice Cloning

Clone a voice from a short audio sample (up to 25 MB). The returned `CustomVoice`
object contains a `voiceId` with the `uc_` prefix that can be used directly in
`textToSpeech` calls.

```php
use Neosapience\Typecast\Models\CustomVoice;

// Read an audio sample (WAV, MP3, OGG, FLAC, or M4A; max 25 MB)
$audioBytes = file_get_contents('sample.wav');

$voice = $client->cloneVoice(
    audio: $audioBytes,
    filename: 'sample.wav',
    name: 'My Custom Voice',
    model: 'ssfm-v21',
);

echo "Cloned voice ID: {$voice->voiceId}\n";  // e.g. "uc_abc123"

// Use the cloned voice for TTS
$tts = $client->textToSpeech(new \Neosapience\Typecast\Models\TTSRequest(
    voiceId: $voice->voiceId,
    text: 'Hello from my cloned voice!',
    model: 'ssfm-v21',
));
file_put_contents('cloned.wav', $tts->audioData);

// Delete the voice when no longer needed
$client->deleteVoice($voice->voiceId);
```

**Validation rules** (checked before any network call):

- `name` must be 1–30 characters (`CustomVoice::NAME_MIN_LENGTH` / `CustomVoice::NAME_MAX_LENGTH`)
- `audio` must not exceed 25 MB (`CustomVoice::CLONING_MAX_FILE_SIZE`)

You can also pass a PHP resource instead of raw bytes:

```php
$fp = fopen('sample.wav', 'rb');
$voice = $client->cloneVoice(audio: $fp, filename: 'sample.wav', name: 'Resource Voice', model: 'ssfm-v30');
fclose($fp);
```

## Error Handling

The SDK throws specific exceptions for each HTTP error:

| Status | Exception |
|--------|-----------|
| 400 | `BadRequestException` |
| 401 | `UnauthorizedException` |
| 402 | `PaymentRequiredException` |
| 404 | `NotFoundException` |
| 422 | `UnprocessableEntityException` |
| 429 | `RateLimitException` |
| 500 | `InternalServerException` |

All exceptions extend `TypecastException`.

```php
use Neosapience\Typecast\Exceptions\UnauthorizedException;

try {
    $client->getMySubscription();
} catch (UnauthorizedException $e) {
    echo "Invalid API key: {$e->getMessage()}\n";
}
```

## Testing

```bash
# Unit tests
vendor/bin/phpunit --testsuite Unit

# Integration tests (requires API key)
TYPECAST_API_KEY=your-key vendor/bin/phpunit --testsuite Integration
```

## License

MIT License. See [LICENSE](LICENSE) for details.
