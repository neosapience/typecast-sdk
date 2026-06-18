<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Unit;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\ComposerSettings;
use Neosapience\Typecast\SpeechPart;
use Neosapience\Typecast\TypecastClient;
use function Neosapience\Typecast\parsePauseMarkup;
use PHPUnit\Framework\TestCase;

class SpeechComposerTest extends TestCase
{
    /**
     * @param array<int, array<string, mixed>> $history
     */
    private function createClient(MockHandler $mock, array &$history = []): TypecastClient
    {
        $history = [];
        $handler = HandlerStack::create($mock);
        $handler->push(Middleware::history($history));
        $httpClient = new Client(['handler' => $handler, 'http_errors' => false]);

        return new TypecastClient(
            apiKey: 'test-api-key',
            baseUrl: 'https://api.typecast.ai',
            httpClient: $httpClient,
        );
    }

    public function testComposeSpeechComposesWavAndMergesOverrides(): void
    {
        $history = [];
        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'audio/wav'], self::makeTestWav([0, 1000, 2000, 0], 1000)),
            new Response(200, ['Content-Type' => 'audio/wav'], self::makeTestWav([0, -1000, -2000, 0], 1000)),
        ]);
        $client = $this->createClient($mock, $history);

        $response = $client
            ->composeSpeech()
            ->defaults(new ComposerSettings(
                voiceId: 'voice-a',
                model: 'ssfm-v30',
                language: 'eng',
                output: new Output(volume: null, audioPitch: 1, audioTempo: null, audioFormat: 'wav'),
            ))
            ->say('Hello<|0.001s|>world', new ComposerSettings(
                voiceId: 'voice-b',
                output: new Output(volume: null, audioPitch: null, audioTempo: 1.1, audioFormat: null),
            ))
            ->generate();

        $this->assertCount(2, $history);
        $first = json_decode((string) $history[0]['request']->getBody(), true);
        $second = json_decode((string) $history[1]['request']->getBody(), true);
        $this->assertSame('Hello', $first['text']);
        $this->assertSame('world', $second['text']);
        $this->assertSame('voice-b', $first['voice_id']);
        $this->assertSame('wav', $first['output']['audio_format']);
        $this->assertSame(1, $first['output']['audio_pitch']);
        $this->assertSame(1.1, $first['output']['audio_tempo']);

        $this->assertSame('wav', $response->format);
        $this->assertSame([1000, 2000, 0, -1000, -2000], self::samplesFromWav($response->audioData));
        $this->assertSame(0.005, $response->duration);
    }

    public function testComposeSpeechValidatesBeforeNetwork(): void
    {
        $history = [];
        $client = $this->createClient(new MockHandler([]), $history);

        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('voiceId is required');
        try {
            $client->composeSpeech()->say('Hello')->generate();
        } finally {
            $this->assertCount(0, $history);
        }
    }

    public function testComposeSpeechRejectsBadPauseBeforeNetwork(): void
    {
        $history = [];
        $client = $this->createClient(new MockHandler([]), $history);

        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('pause seconds must be greater than 0');
        try {
            $client->composeSpeech()->pause(0)->generate();
        } finally {
            $this->assertCount(0, $history);
        }
    }

    public function testParsePauseMarkupIsLenientForInvalidTokens(): void
    {
        $this->assertEquals(
            [
                new SpeechPart('text', 'a'),
                new SpeechPart('pause', seconds: 0.3),
                new SpeechPart('text', 'b<|abc|>c'),
                new SpeechPart('pause', seconds: 3.0),
            ],
            parsePauseMarkup('a<|0.3s|>b<|abc|>c<|3s|>'),
        );
    }

    public function testComposeSpeechRejectsBadWavMismatchedSpecsAndMp3(): void
    {
        $client = $this->createClient(new MockHandler([
            new Response(200, ['Content-Type' => 'audio/wav'], 'not wav'),
        ]));

        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('unsupported WAV data');
        $client
            ->composeSpeech()
            ->defaults(new ComposerSettings(voiceId: 'voice-a', model: 'ssfm-v30'))
            ->say('Hello')
            ->generate();
    }

    public function testComposeSpeechRejectsMismatchedSpecs(): void
    {
        $client = $this->createClient(new MockHandler([
            new Response(200, ['Content-Type' => 'audio/wav'], self::makeTestWav([1000], 1000)),
            new Response(200, ['Content-Type' => 'audio/wav'], self::makeTestWav([1000], 2000)),
        ]));

        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('same PCM format');
        $client
            ->composeSpeech()
            ->defaults(new ComposerSettings(voiceId: 'voice-a', model: 'ssfm-v30'))
            ->say('one<|0.001s|>two')
            ->generate();
    }

    public function testComposeSpeechRejectsMp3AfterRequestingInternalWav(): void
    {
        $history = [];
        $client = $this->createClient(new MockHandler([
            new Response(200, ['Content-Type' => 'audio/wav'], self::makeTestWav([1000], 1000)),
        ]), $history);

        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('MP3 conversion is app-level responsibility');
        try {
            $client
                ->composeSpeech()
                ->defaults(new ComposerSettings(
                    voiceId: 'voice-a',
                    model: 'ssfm-v30',
                    output: new Output(volume: null, audioFormat: 'mp3'),
                ))
                ->say('Hello')
                ->generate();
        } finally {
            $body = json_decode((string) $history[0]['request']->getBody(), true);
            $this->assertSame('wav', $body['output']['audio_format']);
        }
    }

    /**
     * @param int[] $samples
     */
    private static function makeTestWav(array $samples, int $sampleRate): string
    {
        $payload = '';
        foreach ($samples as $sample) {
            $payload .= pack('v', $sample & 0xffff);
        }

        return 'RIFF'
            . pack('V', 36 + strlen($payload))
            . 'WAVEfmt '
            . pack('VvvVVvv', 16, 1, 1, $sampleRate, $sampleRate * 2, 2, 16)
            . 'data'
            . pack('V', strlen($payload))
            . $payload;
    }

    /**
     * @return int[]
     */
    private static function samplesFromWav(string $data): array
    {
        $offset = strpos($data, 'data') + 8;
        $samples = [];
        for ($i = $offset; $i + 1 < strlen($data); $i += 2) {
            $value = unpack('v', substr($data, $i, 2))[1];
            $samples[] = $value >= 0x8000 ? $value - 0x10000 : $value;
        }
        return $samples;
    }
}
