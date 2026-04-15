<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Integration;

use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSRequestStream;
use Neosapience\Typecast\Models\VoicesV2Filter;
use Neosapience\Typecast\TypecastClient;
use PHPUnit\Framework\TestCase;

class LiveApiTest extends TestCase
{
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

    public function testGetMySubscription(): void
    {
        $sub = $this->client->getMySubscription();

        $this->assertNotEmpty($sub->plan);
        $this->assertIsInt($sub->planCredits);
        $this->assertIsInt($sub->usedCredits);
        $this->assertIsInt($sub->concurrencyLimit);
    }

    public function testGetVoices(): void
    {
        $voices = $this->client->getVoices();

        $this->assertNotEmpty($voices);
        $this->assertNotEmpty($voices[0]->voiceId);
        $this->assertNotEmpty($voices[0]->voiceName);
    }

    public function testGetVoicesV2(): void
    {
        $voices = $this->client->getVoicesV2();

        $this->assertNotEmpty($voices);
        $this->assertNotEmpty($voices[0]->voiceId);
        $this->assertNotEmpty($voices[0]->voiceName);
    }

    public function testGetVoicesV2WithFilter(): void
    {
        $voices = $this->client->getVoicesV2(new VoicesV2Filter(model: 'ssfm-v30'));

        $this->assertNotEmpty($voices);
    }

    public function testGetVoiceV2(): void
    {
        // First get a voice ID from the list
        $voices = $this->client->getVoicesV2();
        $this->assertNotEmpty($voices);

        $voiceId = $voices[0]->voiceId;
        $voice = $this->client->getVoiceV2($voiceId);

        $this->assertSame($voiceId, $voice->voiceId);
    }

    public function testTextToSpeech(): void
    {
        $voices = $this->client->getVoicesV2();
        $this->assertNotEmpty($voices);

        $voiceId = $voices[0]->voiceId;
        $model = $voices[0]->models[0]['version'] ?? 'ssfm-v30';

        $request = new TTSRequest(
            voiceId: $voiceId,
            text: 'Hello, this is a test.',
            model: $model,
        );

        $response = $this->client->textToSpeech($request);

        $this->assertNotEmpty($response->audioData);
        $this->assertGreaterThanOrEqual(0, $response->duration);
        $this->assertContains($response->format, ['wav', 'mp3']);
    }

    public function testTextToSpeechStream(): void
    {
        $voices = $this->client->getVoicesV2();
        $this->assertNotEmpty($voices);

        $voiceId = $voices[0]->voiceId;
        $model = $voices[0]->models[0]['version'] ?? 'ssfm-v30';

        $request = new TTSRequestStream(
            voiceId: $voiceId,
            text: 'Hello, streaming test.',
            model: $model,
        );

        $totalSize = 0;
        $this->client->textToSpeechStream($request, function (string $chunk) use (&$totalSize): void {
            $totalSize += strlen($chunk);
        });

        $this->assertGreaterThan(0, $totalSize);
    }
}
