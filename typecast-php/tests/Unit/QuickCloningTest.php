<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Tests\Unit;

use GuzzleHttp\Client;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use Neosapience\Typecast\Exceptions\NotFoundException;
use Neosapience\Typecast\Models\CustomVoice;
use Neosapience\Typecast\TypecastClient;
use PHPUnit\Framework\TestCase;

class QuickCloningTest extends TestCase
{
    /**
     * Create a TypecastClient backed by a MockHandler.
     * Returns a tuple of [TypecastClient, MockHandler].
     */
    private function createClient(MockHandler $mock, ?array &$history = null): TypecastClient
    {
        $handler = HandlerStack::create($mock);

        if ($history !== null) {
            $handler->push(Middleware::history($history));
        }

        $httpClient = new Client(['handler' => $handler, 'http_errors' => false]);

        return new TypecastClient(
            apiKey: 'test-api-key',
            baseUrl: 'https://api.typecast.ai',
            httpClient: $httpClient,
        );
    }

    // ------------------------------------------------------------------ //
    // cloneVoice — happy path                                             //
    // ------------------------------------------------------------------ //

    public function testCloneVoiceReturnsCustomVoice(): void
    {
        $body = json_encode([
            'voice_id' => 'uc_abc123',
            'name'     => 'My Voice',
            'model'    => 'ssfm-v21',
        ]);

        $mock   = new MockHandler([new Response(200, ['Content-Type' => 'application/json'], $body)]);
        $client = $this->createClient($mock);

        $result = $client->cloneVoice(
            audio: str_repeat('x', 1024),
            filename: 'sample.wav',
            name: 'My Voice',
            model: 'ssfm-v21',
        );

        $this->assertInstanceOf(CustomVoice::class, $result);
        $this->assertSame('uc_abc123', $result->voiceId);
        $this->assertSame('My Voice', $result->name);
        $this->assertSame('ssfm-v21', $result->model);
    }

    // ------------------------------------------------------------------ //
    // cloneVoice — request shape                                          //
    // ------------------------------------------------------------------ //

    public function testCloneVoiceSendsMultipartBody(): void
    {
        $body = json_encode([
            'voice_id' => 'uc_xyz',
            'name'     => 'Test',
            'model'    => 'ssfm-v30',
        ]);

        $mock    = new MockHandler([new Response(200, ['Content-Type' => 'application/json'], $body)]);
        $history = [];
        $client  = $this->createClient($mock, $history);

        $client->cloneVoice(
            audio: str_repeat('a', 2048),
            filename: 'test.wav',
            name: 'Test',
            model: 'ssfm-v30',
        );

        $this->assertCount(1, $history);

        /** @var \Psr\Http\Message\RequestInterface $request */
        $request = $history[0]['request'];

        // URL
        $this->assertStringEndsWith('/v1/voices/clone', (string) $request->getUri());

        // Method
        $this->assertSame('POST', $request->getMethod());

        // Content-Type starts with multipart/form-data
        $contentType = $request->getHeaderLine('Content-Type');
        $this->assertStringStartsWith('multipart/form-data', $contentType);

        // Body contains part names
        $bodyStr = (string) $request->getBody();
        $this->assertStringContainsString('name="name"', $bodyStr);
        $this->assertStringContainsString('name="model"', $bodyStr);
        $this->assertStringContainsString('name="file"', $bodyStr);
    }

    // ------------------------------------------------------------------ //
    // cloneVoice — validation guards                                      //
    // ------------------------------------------------------------------ //

    public function testCloneVoiceRejectsOversizedAudio(): void
    {
        $mock   = new MockHandler([]);         // no response queued — must not reach HTTP
        $client = $this->createClient($mock);

        $oversized = str_repeat('z', CustomVoice::CLONING_MAX_FILE_SIZE + 1);

        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessageMatches('/25MB/i');

        $client->cloneVoice(
            audio: $oversized,
            filename: 'big.wav',
            name: 'BigVoice',
            model: 'ssfm-v21',
        );
    }

    public function testCloneVoiceRejectsBadNameLength(): void
    {
        $mock   = new MockHandler([]);
        $client = $this->createClient($mock);

        // Empty name
        try {
            $client->cloneVoice(
                audio: 'bytes',
                filename: 'f.wav',
                name: '',
                model: 'ssfm-v21',
            );
            $this->fail('Expected InvalidArgumentException for empty name');
        } catch (\InvalidArgumentException $e) {
            $this->assertStringContainsString('name', strtolower($e->getMessage()));
        }

        // Name exceeding NAME_MAX_LENGTH
        $longName = str_repeat('a', CustomVoice::NAME_MAX_LENGTH + 1);
        try {
            $client->cloneVoice(
                audio: 'bytes',
                filename: 'f.wav',
                name: $longName,
                model: 'ssfm-v21',
            );
            $this->fail('Expected InvalidArgumentException for name too long');
        } catch (\InvalidArgumentException $e) {
            $this->assertStringContainsString('name', strtolower($e->getMessage()));
        }
    }

    // ------------------------------------------------------------------ //
    // deleteVoice                                                         //
    // ------------------------------------------------------------------ //

    public function testDeleteVoiceSucceedsOn204(): void
    {
        $history = [];
        $mock    = new MockHandler([new Response(204)]);
        $client  = $this->createClient($mock, $history);

        $client->deleteVoice('uc_xxx');

        $this->assertCount(1, $history);

        /** @var \Psr\Http\Message\RequestInterface $request */
        $request = $history[0]['request'];

        $this->assertStringEndsWith('/v1/voices/uc_xxx', (string) $request->getUri());
        $this->assertSame('DELETE', $request->getMethod());
    }

    public function testDeleteVoiceThrowsOn404(): void
    {
        $mock   = new MockHandler([new Response(404, [], 'not found')]);
        $client = $this->createClient($mock);

        $this->expectException(NotFoundException::class);
        $client->deleteVoice('uc_nonexistent');
    }
}
