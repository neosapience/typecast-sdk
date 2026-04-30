<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Unit;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Psr7\Response;
use Neosapience\Typecast\Models\AlignmentSegmentCharacter;
use Neosapience\Typecast\Models\AlignmentSegmentWord;
use Neosapience\Typecast\Models\TTSRequestWithTimestamps;
use Neosapience\Typecast\Models\TTSWithTimestampsResponse;
use Neosapience\Typecast\TypecastClient;
use PHPUnit\Framework\TestCase;

final class TimestampTTSTest extends TestCase
{
    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    private static function fixtureDir(): string
    {
        $dir = __DIR__;
        for ($i = 0; $i < 8; $i++) {
            $cand = $dir . '/test-fixtures/with-timestamps';
            if (is_dir($cand)) {
                return $cand;
            }
            $dir = dirname($dir);
        }
        throw new \RuntimeException('test-fixtures not found');
    }

    /** @return array<string, mixed> */
    private function loadFixture(string $name): array
    {
        $path = self::fixtureDir() . "/{$name}.json";
        $content = file_get_contents($path);
        if ($content === false) {
            throw new \RuntimeException("Cannot read fixture: {$path}");
        }
        /** @var array<string, mixed> $data */
        $data = json_decode($content, true);
        return $data;
    }

    private function loadExpected(string $name): string
    {
        $path = self::fixtureDir() . "/expected/{$name}";
        $content = file_get_contents($path);
        if ($content === false) {
            throw new \RuntimeException("Cannot read expected file: {$path}");
        }
        return $content;
    }

    // -------------------------------------------------------------------------
    // Data providers
    // -------------------------------------------------------------------------

    /** @return array<string, array{string}> */
    public static function fixtureProvider(): array
    {
        return [
            'both' => ['both'],
            'word_only' => ['word_only'],
            'char_only' => ['char_only'],
            'jpn_char' => ['jpn_char'],
        ];
    }

    // -------------------------------------------------------------------------
    // SRT / VTT fixture tests
    // -------------------------------------------------------------------------

    /** @dataProvider fixtureProvider */
    public function testToSrtMatches(string $name): void
    {
        $resp = TTSWithTimestampsResponse::fromArray($this->loadFixture($name));
        $this->assertSame($this->loadExpected("{$name}.srt"), $resp->toSrt());
    }

    /** @dataProvider fixtureProvider */
    public function testToVttMatches(string $name): void
    {
        $resp = TTSWithTimestampsResponse::fromArray($this->loadFixture($name));
        $this->assertSame($this->loadExpected("{$name}.vtt"), $resp->toVtt());
    }

    // -------------------------------------------------------------------------
    // Audio helpers
    // -------------------------------------------------------------------------

    public function testAudioBytesDecodes(): void
    {
        $resp = TTSWithTimestampsResponse::fromArray($this->loadFixture('both'));
        $this->assertNotEmpty($resp->audioBytes());
    }

    public function testSaveAudio(): void
    {
        $resp = TTSWithTimestampsResponse::fromArray($this->loadFixture('both'));
        $tmp = tempnam(sys_get_temp_dir(), 'tts_php_');
        $this->assertNotFalse($tmp);
        try {
            $resp->saveAudio($tmp);
            $this->assertGreaterThan(0, filesize($tmp));
        } finally {
            @unlink($tmp);
        }
    }

    public function testAudioBytesInvalidBase64Throws(): void
    {
        $resp = new TTSWithTimestampsResponse(
            audio: '!!!not-base64!!!',
            audioFormat: 'wav',
            audioDuration: 0.0,
            words: null,
            characters: null,
        );
        $this->expectException(\RuntimeException::class);
        $resp->audioBytes();
    }

    // -------------------------------------------------------------------------
    // Edge cases — no segments
    // -------------------------------------------------------------------------

    public function testNoSegmentsThrowsOnSrt(): void
    {
        $resp = new TTSWithTimestampsResponse(
            audio: base64_encode('fake'),
            audioFormat: 'wav',
            audioDuration: 0.0,
            words: null,
            characters: null,
        );
        $this->expectException(\RuntimeException::class);
        $resp->toSrt();
    }

    public function testNoSegmentsThrowsOnVtt(): void
    {
        $resp = new TTSWithTimestampsResponse(
            audio: base64_encode('fake'),
            audioFormat: 'wav',
            audioDuration: 0.0,
            words: null,
            characters: null,
        );
        $this->expectException(\RuntimeException::class);
        $resp->toVtt();
    }

    // -------------------------------------------------------------------------
    // Model classes
    // -------------------------------------------------------------------------

    public function testAlignmentSegmentWordProperties(): void
    {
        $seg = new AlignmentSegmentWord('hello', 0.1, 0.5);
        $this->assertSame('hello', $seg->text);
        $this->assertSame(0.1, $seg->start);
        $this->assertSame(0.5, $seg->end);
    }

    public function testAlignmentSegmentCharacterProperties(): void
    {
        $seg = new AlignmentSegmentCharacter('あ', 1.0, 1.2);
        $this->assertSame('あ', $seg->text);
        $this->assertSame(1.0, $seg->start);
        $this->assertSame(1.2, $seg->end);
    }

    // -------------------------------------------------------------------------
    // TTSRequestWithTimestamps validation
    // -------------------------------------------------------------------------

    public function testRequestWithTimestampsRejectsEmptyVoiceId(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        new TTSRequestWithTimestamps(voiceId: '', text: 'hello', model: 'ssfm-v30');
    }

    public function testRequestWithTimestampsRejectsEmptyText(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        new TTSRequestWithTimestamps(voiceId: 'tc_x', text: '', model: 'ssfm-v30');
    }

    public function testRequestWithTimestampsToArray(): void
    {
        $req = new TTSRequestWithTimestamps(
            voiceId: 'tc_abc',
            text: 'Hi',
            model: 'ssfm-v30',
            language: 'eng',
        );
        $arr = $req->toArray();
        $this->assertSame('tc_abc', $arr['voice_id']);
        $this->assertSame('Hi', $arr['text']);
        $this->assertSame('ssfm-v30', $arr['model']);
        $this->assertSame('eng', $arr['language']);
    }

    // -------------------------------------------------------------------------
    // HTTP client tests (Guzzle MockHandler)
    // -------------------------------------------------------------------------

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

    public function testTextToSpeechWithTimestampsSuccess(): void
    {
        $fixture = $this->loadFixture('both');
        $body = json_encode($fixture);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $request = new TTSRequestWithTimestamps(
            voiceId: 'tc_123',
            text: 'Hello world!',
            model: 'ssfm-v30',
        );

        $resp = $client->textToSpeechWithTimestamps($request);

        $this->assertNotNull($resp->words);
        $this->assertNotNull($resp->characters);
        $this->assertNotEmpty($resp->audio);
    }

    public function testTextToSpeechWithTimestampsWordGranularity(): void
    {
        $fixture = $this->loadFixture('word_only');
        $body = json_encode($fixture);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $request = new TTSRequestWithTimestamps(
            voiceId: 'tc_123',
            text: 'Hello world!',
            model: 'ssfm-v30',
        );

        $resp = $client->textToSpeechWithTimestamps($request, 'word');

        $this->assertNotNull($resp->words);
        $this->assertNotEmpty($resp->toSrt());
    }

    public function testTextToSpeechWithTimestampsCharGranularity(): void
    {
        $fixture = $this->loadFixture('char_only');
        $body = json_encode($fixture);

        $mock = new MockHandler([
            new Response(200, ['Content-Type' => 'application/json'], $body),
        ]);

        $client = $this->createClient($mock);
        $request = new TTSRequestWithTimestamps(
            voiceId: 'tc_123',
            text: 'Hello!',
            model: 'ssfm-v30',
        );

        $resp = $client->textToSpeechWithTimestamps($request, 'char');

        $this->assertNotNull($resp->characters);
        $this->assertNotEmpty($resp->toVtt());
    }

    public function testTextToSpeechWithTimestampsInvalidGranularityThrows(): void
    {
        $mock = new MockHandler([]);
        $client = $this->createClient($mock);
        $request = new TTSRequestWithTimestamps(
            voiceId: 'tc_123',
            text: 'Hello!',
            model: 'ssfm-v30',
        );

        $this->expectException(\InvalidArgumentException::class);
        $client->textToSpeechWithTimestamps($request, 'sentence');
    }

    public function testFromArrayThrowsOnMissingRequiredField(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessageMatches('/Missing required field/');
        TTSWithTimestampsResponse::fromArray([
            // 'audio' is intentionally missing
            'audio_format' => 'wav',
            'audio_duration' => 1.0,
        ]);
    }

    public function testTextToSpeechWithTimestampsError401(): void
    {
        $mock = new MockHandler([
            new Response(401, [], 'unauthorized'),
        ]);

        $client = $this->createClient($mock);
        $request = new TTSRequestWithTimestamps(
            voiceId: 'tc_123',
            text: 'Hello!',
            model: 'ssfm-v30',
        );

        $this->expectException(\Neosapience\Typecast\Exceptions\UnauthorizedException::class);
        $client->textToSpeechWithTimestamps($request);
    }
}
