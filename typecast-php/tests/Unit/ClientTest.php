<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Unit;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use Neosapience\Typecast\Exceptions\BadRequestException;
use Neosapience\Typecast\Exceptions\InternalServerException;
use Neosapience\Typecast\Exceptions\NotFoundException;
use Neosapience\Typecast\Exceptions\PaymentRequiredException;
use Neosapience\Typecast\Exceptions\RateLimitException;
use Neosapience\Typecast\Exceptions\TypecastException;
use Neosapience\Typecast\Exceptions\UnauthorizedException;
use Neosapience\Typecast\Exceptions\UnprocessableEntityException;
use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\OutputStream;
use Neosapience\Typecast\Models\Prompt;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSRequestStream;
use Neosapience\Typecast\Models\VoicesV2Filter;
use Neosapience\Typecast\TypecastClient;
use PHPUnit\Framework\TestCase;

class ClientTest extends TestCase
{
    private function createClient(MockHandler $mock): TypecastClient
    {
        $handler = HandlerStack::create($mock);
        $httpClient = new Client(['handler' => $handler, 'http_errors' => false]);

        return new TypecastClient(
            apiKey: 'test-api-key',
            baseUrl: 'https://api.typecast.ai',
            httpClient: $httpClient,
        );
    }

    public function testTextToSpeech(): void
    {
        $history = [];
        $mock = new MockHandler([
            new Response(200, [
                'Content-Type' => 'audio/wav',
                'X-Audio-Duration' => '1.5',
            ], 'fake-audio-data'),
        ]);
        $handler = HandlerStack::create($mock);
        $handler->push(Middleware::history($history));
        $httpClient = new Client(['handler' => $handler, 'http_errors' => false]);

        $client = new TypecastClient(
            apiKey: 'test-api-key',
            baseUrl: 'https://api.typecast.ai',
            httpClient: $httpClient,
        );

        $request = new TTSRequest(
            voiceId: 'tc_123',
            text: 'Hello world',
            model: 'ssfm-v21',
        );

        $response = $client->textToSpeech($request);

        $this->assertSame('fake-audio-data', $response->audioData);
        $this->assertSame(1.5, $response->duration);
        $this->assertSame('wav', $response->format);
        $this->assertStringContainsString('typecast-php/', $history[0]['request']->getHeaderLine('User-Agent'));
        $this->assertStringContainsString('sdk_env=php', $history[0]['request']->getHeaderLine('User-Agent'));
    }

    public function testTextToSpeechMp3(): void
    {
        $mock = new MockHandler([
            new Response(200, [
                'Content-Type' => 'audio/mp3',
                'X-Audio-Duration' => '2.3',
            ], 'mp3-data'),
        ]);

        $client = $this->createClient($mock);

        $request = new TTSRequest(
            voiceId: 'tc_123',
            text: 'Hello',
            model: 'ssfm-v30',
            output: new Output(audioFormat: 'mp3'),
        );

        $response = $client->textToSpeech($request);

        $this->assertSame('mp3-data', $response->audioData);
        $this->assertSame(2.3, $response->duration);
        $this->assertSame('mp3', $response->format);
    }

    public function testTextToSpeechStream(): void
    {
        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'audio/wav'], 'chunk1chunk2chunk3'),
        ]);

        $client = $this->createClient($mock);

        $request = new TTSRequestStream(
            voiceId: 'tc_123',
            text: 'Hello stream',
            model: 'ssfm-v30',
        );

        $chunks = [];
        $client->textToSpeechStream($request, function (string $chunk) use (&$chunks): void {
            $chunks[] = $chunk;
        });

        $this->assertNotEmpty($chunks);
        $this->assertSame('chunk1chunk2chunk3', implode('', $chunks));
    }

    public function testGetMySubscription(): void
    {
        $body = json_encode([
            'plan' => 'plus',
            'credits' => [
                'plan_credits' => 10000,
                'used_credits' => 250,
            ],
            'limits' => [
                'concurrency_limit' => 5,
            ],
        ]);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $sub = $client->getMySubscription();

        $this->assertSame('plus', $sub->plan);
        $this->assertSame(10000, $sub->planCredits);
        $this->assertSame(250, $sub->usedCredits);
        $this->assertSame(5, $sub->concurrencyLimit);
    }

    public function testGetVoices(): void
    {
        $body = json_encode([
            [
                'voice_id' => 'tc_abc',
                'voice_name' => 'Voice A',
                'model' => 'ssfm-v21',
                'emotions' => ['normal', 'happy'],
            ],
            [
                'voice_id' => 'tc_def',
                'voice_name' => 'Voice B',
                'model' => 'ssfm-v21',
                'emotions' => ['normal'],
            ],
        ]);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $voices = $client->getVoices();

        $this->assertCount(2, $voices);
        $this->assertSame('tc_abc', $voices[0]->voiceId);
        $this->assertSame('Voice B', $voices[1]->voiceName);
    }

    public function testGetVoicesV2(): void
    {
        $body = json_encode([
            [
                'voice_id' => 'tc_v2',
                'voice_name' => 'V2 Voice',
                'models' => [
                    ['version' => 'ssfm-v30', 'emotions' => ['normal', 'happy', 'whisper']],
                ],
                'gender' => 'female',
                'age' => 'young_adult',
                'use_cases' => ['Podcast'],
            ],
        ]);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $voices = $client->getVoicesV2(new VoicesV2Filter(model: 'ssfm-v30'));

        $this->assertCount(1, $voices);
        $this->assertSame('tc_v2', $voices[0]->voiceId);
        $this->assertSame('female', $voices[0]->gender);
    }

    public function testGetVoiceV2(): void
    {
        $body = json_encode([
            [
                'voice_id' => 'tc_specific',
                'voice_name' => 'Specific Voice',
                'models' => [],
                'gender' => 'male',
                'age' => 'middle_age',
            ],
        ]);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $voice = $client->getVoiceV2('tc_specific');

        $this->assertSame('tc_specific', $voice->voiceId);
        $this->assertSame('Specific Voice', $voice->voiceName);
        $this->assertSame('male', $voice->gender);
    }

    public function testGetVoiceV2NotFound(): void
    {
        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], '[]'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(NotFoundException::class);
        $client->getVoiceV2('tc_nonexistent');
    }

    public function testError400(): void
    {
        $mock = new MockHandler([
            new Response(400, [], 'invalid request'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(BadRequestException::class);
        $client->getMySubscription();
    }

    public function testError401(): void
    {
        $mock = new MockHandler([
            new Response(401, [], 'bad key'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(UnauthorizedException::class);
        $client->getMySubscription();
    }

    public function testError402(): void
    {
        $mock = new MockHandler([
            new Response(402, [], 'no credits'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(PaymentRequiredException::class);
        $client->textToSpeech(new TTSRequest(voiceId: 'tc_x', text: 'hi', model: 'ssfm-v21'));
    }

    public function testError404(): void
    {
        $mock = new MockHandler([
            new Response(404, [], 'not found'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(NotFoundException::class);
        $client->getMySubscription();
    }

    public function testError422(): void
    {
        $mock = new MockHandler([
            new Response(422, [], 'validation error'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(UnprocessableEntityException::class);
        $client->textToSpeech(new TTSRequest(voiceId: 'tc_x', text: 'hi', model: 'ssfm-v21'));
    }

    public function testError429(): void
    {
        $mock = new MockHandler([
            new Response(429, [], 'rate limited'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(RateLimitException::class);
        $client->getMySubscription();
    }

    public function testError500(): void
    {
        $mock = new MockHandler([
            new Response(500, [], 'server error'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(InternalServerException::class);
        $client->getMySubscription();
    }

    public function testUnknownError(): void
    {
        $mock = new MockHandler([
            new Response(503, [], 'service unavailable'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(TypecastException::class);
        $client->getMySubscription();
    }

    public function testTextToSpeechWithPromptAndOutput(): void
    {
        $mock = new MockHandler([
            new Response(200, [
                'Content-Type' => 'audio/wav',
                'X-Audio-Duration' => '3.0',
            ], 'audio-bytes'),
        ]);

        $client = $this->createClient($mock);

        $request = new TTSRequest(
            voiceId: 'tc_123',
            text: 'Hello with options',
            model: 'ssfm-v21',
            language: 'eng',
            prompt: new Prompt(emotionPreset: 'happy', emotionIntensity: 1.5),
            output: new Output(volume: 150, audioPitch: 2, audioTempo: 1.2, audioFormat: 'wav'),
            seed: 42,
        );

        $response = $client->textToSpeech($request);

        $this->assertSame('audio-bytes', $response->audioData);
        $this->assertSame(3.0, $response->duration);
    }

    public function testTextToSpeechStreamWithOutput(): void
    {
        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'audio/mp3'], 'streamed-audio'),
        ]);

        $client = $this->createClient($mock);

        $request = new TTSRequestStream(
            voiceId: 'tc_456',
            text: 'Stream test',
            model: 'ssfm-v30',
            output: new OutputStream(audioFormat: 'mp3'),
        );

        $received = '';
        $client->textToSpeechStream($request, function (string $chunk) use (&$received): void {
            $received .= $chunk;
        });

        $this->assertSame('streamed-audio', $received);
    }

    public function testStreamError401(): void
    {
        $mock = new MockHandler([
            new Response(401, [], 'unauthorized'),
        ]);

        $client = $this->createClient($mock);

        $this->expectException(UnauthorizedException::class);
        $client->textToSpeechStream(
            new TTSRequestStream(voiceId: 'tc_x', text: 'hi', model: 'ssfm-v21'),
            function (string $chunk): void {},
        );
    }
}
