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
