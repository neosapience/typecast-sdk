<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Integration;

use Neosapience\Typecast\Models\PresetPrompt;
use Neosapience\Typecast\Models\TTSRequestWithTimestamps;
use Neosapience\Typecast\TypecastClient;
use PHPUnit\Framework\TestCase;

/**
 * Real-API E2E tests for text-to-speech with timestamps.
 * Skipped when TYPECAST_API_KEY environment variable is not set.
 */
class TimestampTTSE2ETest extends TestCase
{
    private const VOICE = 'tc_60e5426de8b95f1d3000d7b5';

    private ?TypecastClient $client = null;

    protected function setUp(): void
    {
        $apiKey = getenv('TYPECAST_API_KEY');
        if (!$apiKey) {
            $this->markTestSkipped('TYPECAST_API_KEY environment variable is not set');
        }

        $host = getenv('TYPECAST_API_HOST') ?: 'https://api.typecast.ai';

        $this->client = new TypecastClient(
            apiKey: $apiKey,
            baseUrl: $host,
        );
    }

    private function buildRequest(string $text, string $language): TTSRequestWithTimestamps
    {
        return new TTSRequestWithTimestamps(
            voiceId: self::VOICE,
            text: $text,
            model: 'ssfm-v30',
            language: $language,
            prompt: new PresetPrompt(emotionPreset: 'normal', emotionIntensity: 1.0),
            seed: 42,
        );
    }

    public function testNoGranularity_ReturnsWordsAndCharacters(): void
    {
        $req = $this->buildRequest('Hello.', 'eng');
        $resp = $this->client->textToSpeechWithTimestamps($req, null);

        $this->assertNotNull($resp);
        $this->assertGreaterThan(0, $resp->audioDuration, 'audio_duration should be > 0');
        $this->assertNotNull($resp->words, 'words should not be null');
        $this->assertNotEmpty($resp->words, 'words should be non-empty');
        $this->assertNotNull($resp->characters, 'characters should not be null');
        $this->assertNotEmpty($resp->characters, 'characters should be non-empty');
        echo "no_granularity: duration={$resp->audioDuration} words=" . count($resp->words) . " chars=" . count($resp->characters) . PHP_EOL;
    }

    public function testWordGranularity_ReturnsWordsOnlyCharactersNull(): void
    {
        $req = $this->buildRequest('Hello.', 'eng');
        $resp = $this->client->textToSpeechWithTimestamps($req, 'word');

        $this->assertNotNull($resp);
        $this->assertNotNull($resp->words, 'words should not be null');
        $this->assertNotEmpty($resp->words, 'words should be non-empty for word granularity');
        $this->assertNull($resp->characters, 'characters should be null for word granularity');
        echo "word granularity: words=" . count($resp->words) . PHP_EOL;
    }

    public function testCharGranularity_ReturnsCharactersOnlyWordsNull(): void
    {
        $req = $this->buildRequest('Hello.', 'eng');
        $resp = $this->client->textToSpeechWithTimestamps($req, 'char');

        $this->assertNotNull($resp);
        $this->assertNotNull($resp->characters, 'characters should not be null');
        $this->assertNotEmpty($resp->characters, 'characters should be non-empty for char granularity');
        $this->assertNull($resp->words, 'words should be null for char granularity');
        echo "char granularity: chars=" . count($resp->characters) . PHP_EOL;
    }

    public function testJpnChar_ReturnsAtLeastFiveSegments(): void
    {
        $req = $this->buildRequest('こんにちは。お元気ですか?', 'jpn');
        $resp = $this->client->textToSpeechWithTimestamps($req, 'char');

        $this->assertNotNull($resp);
        $this->assertNotNull($resp->characters, 'characters should not be null for jpn+char');
        $this->assertGreaterThanOrEqual(5, count($resp->characters),
            'Expected >= 5 character segments for Japanese');
        echo "jpn+char: chars=" . count($resp->characters) . PHP_EOL;
    }
}
