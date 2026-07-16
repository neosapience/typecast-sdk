<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Unit;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use Neosapience\Typecast\ComposerSettings;
use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\SpeechPart;
use Neosapience\Typecast\TypecastClient;
use function Neosapience\Typecast\parsePauseMarkup;
use PHPUnit\Framework\TestCase;

class SpeechComposerTest extends TestCase
{
    /** @param array<int, array<string, mixed>> $history */
    private function createClient(MockHandler $mock, array &$history = []): TypecastClient
    {
        $history = [];
        $handler = HandlerStack::create($mock);
        $handler->push(Middleware::history($history));
        return new TypecastClient(
            apiKey: 'test-api-key',
            baseUrl: 'https://api.typecast.ai',
            httpClient: new Client(['handler' => $handler, 'http_errors' => false]),
        );
    }

    public function testComposeSpeechUsesComposeApiAndMergesOverrides(): void
    {
        $history = [];
        $client = $this->createClient(new MockHandler([
            new Response(200, ['Content-Type' => 'audio/mpeg', 'X-Audio-Duration' => '1.25'], 'composed-audio'),
        ]), $history);

        $response = $client->composeSpeech()
            ->defaults(new ComposerSettings(
                voiceId: 'voice-a',
                model: 'ssfm-v30',
                output: new Output(volume: null, audioPitch: 1, audioFormat: 'mp3'),
            ))
            ->say('Hello<|0.3s|>world', new ComposerSettings(
                voiceId: 'voice-b',
                output: new Output(volume: null, audioPitch: null, audioTempo: 1.1, audioFormat: null),
            ))
            ->generate();

        $this->assertCount(1, $history);
        $request = $history[0]['request'];
        $this->assertSame('/v1/text-to-speech/compose', $request->getUri()->getPath());
        $body = json_decode((string) $request->getBody(), true);
        $this->assertSame(['tts', 'pause', 'tts'], array_column($body['segments'], 'type'));
        $this->assertSame('Hello', $body['segments'][0]['text']);
        $this->assertSame('voice-b', $body['segments'][0]['voice_id']);
        $this->assertSame('mp3', $body['segments'][0]['output']['audio_format']);
        $this->assertSame(1, $body['segments'][0]['output']['audio_pitch']);
        $this->assertSame(1.1, $body['segments'][0]['output']['audio_tempo']);
        $this->assertSame(0.3, $body['segments'][1]['duration_seconds']);
        $this->assertSame('world', $body['segments'][2]['text']);
        $this->assertSame('composed-audio', $response->audioData);
        $this->assertSame('mp3', $response->format);
        $this->assertSame(1.25, $response->duration);
    }

    public function testComposeSpeechValidatesBeforeNetwork(): void
    {
        $history = [];
        $client = $this->createClient(new MockHandler([]), $history);
        try {
            $client->composeSpeech()->say('Hello')->generate();
            $this->fail('Expected validation error');
        } catch (\InvalidArgumentException $error) {
            $this->assertStringContainsString('voiceId is required', $error->getMessage());
        }
        $this->assertCount(0, $history);
    }

    public function testComposeSpeechRejectsBadPauseBeforeNetwork(): void
    {
        $client = $this->createClient(new MockHandler([]));
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('pause seconds must be greater than 0');
        $client->composeSpeech()->pause(0)->generate();
    }

    public function testParsePauseMarkupIsLenientForInvalidTokens(): void
    {
        $this->assertEquals([
            new SpeechPart('text', 'a'),
            new SpeechPart('pause', seconds: 0.3),
            new SpeechPart('text', 'b<|abc|>c'),
            new SpeechPart('pause', seconds: 3.0),
        ], parsePauseMarkup('a<|0.3s|>b<|abc|>c<|3s|>'));
    }
}
