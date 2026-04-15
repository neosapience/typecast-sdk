<?php

declare(strict_types=1);

namespace Neosapience\Typecast;

use GuzzleHttp\Client;
use GuzzleHttp\ClientInterface;
use Neosapience\Typecast\Exceptions\BadRequestException;
use Neosapience\Typecast\Exceptions\InternalServerException;
use Neosapience\Typecast\Exceptions\NotFoundException;
use Neosapience\Typecast\Exceptions\PaymentRequiredException;
use Neosapience\Typecast\Exceptions\RateLimitException;
use Neosapience\Typecast\Exceptions\TypecastException;
use Neosapience\Typecast\Exceptions\UnauthorizedException;
use Neosapience\Typecast\Exceptions\UnprocessableEntityException;
use Neosapience\Typecast\Models\SubscriptionResponse;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSRequestStream;
use Neosapience\Typecast\Models\TTSResponse;
use Neosapience\Typecast\Models\Voice;
use Neosapience\Typecast\Models\VoiceV2;
use Neosapience\Typecast\Models\VoicesV2Filter;

/**
 * Synchronous client for the Typecast Text-to-Speech API.
 */
class TypecastClient
{
    private ClientInterface $httpClient;

    public function __construct(
        private readonly string $apiKey,
        private readonly string $baseUrl = 'https://api.typecast.ai',
        ?ClientInterface $httpClient = null,
    ) {
        $this->httpClient = $httpClient ?? new Client([
            'base_uri' => $this->baseUrl,
            'http_errors' => false,
            'headers' => [
                'X-API-KEY' => $this->apiKey,
                'Content-Type' => 'application/json',
            ],
        ]);
    }

    /**
     * Convert text to speech.
     *
     * @throws TypecastException
     */
    public function textToSpeech(TTSRequest $request): TTSResponse
    {
        $response = $this->httpClient->request('POST', '/v1/text-to-speech', [
            'json' => $request->toArray(),
        ]);

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        $duration = (float) ($response->getHeaderLine('X-Audio-Duration') ?: '0');
        $contentType = $response->getHeaderLine('Content-Type') ?: 'audio/wav';
        $format = explode('/', $contentType);
        $format = end($format);

        return new TTSResponse(
            audioData: (string) $response->getBody(),
            duration: $duration,
            format: $format,
        );
    }

    /**
     * Stream synthesized audio via POST /v1/text-to-speech/stream.
     *
     * Reads the response in chunks and invokes the callback for each chunk.
     *
     * @param callable(string): void $onChunk Callback invoked per audio chunk
     * @throws TypecastException
     */
    public function textToSpeechStream(TTSRequestStream $request, callable $onChunk): void
    {
        $response = $this->httpClient->request('POST', '/v1/text-to-speech/stream', [
            'json' => $request->toArray(),
            'stream' => true,
        ]);

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        $body = $response->getBody();
        while (!$body->eof()) {
            $chunk = $body->read(8192);
            if ($chunk !== '') {
                $onChunk($chunk);
            }
        }
    }

    /**
     * Get the authenticated user's current subscription.
     *
     * @throws TypecastException
     */
    public function getMySubscription(): SubscriptionResponse
    {
        $response = $this->httpClient->request('GET', '/v1/users/me/subscription');

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<string, mixed> $data */
        $data = json_decode((string) $response->getBody(), true);

        return SubscriptionResponse::fromArray($data);
    }

    /**
     * Get available voices (V1 API).
     *
     * @return Voice[]
     * @throws TypecastException
     */
    public function getVoices(?string $model = null): array
    {
        $query = ['page' => 0, 'page_size' => 100];
        if ($model !== null) {
            $query['model'] = $model;
        }

        $response = $this->httpClient->request('GET', '/v1/voices', [
            'query' => $query,
        ]);

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = json_decode((string) $response->getBody(), true);

        return array_map(
            static fn(array $item): Voice => Voice::fromArray($item),
            $items,
        );
    }

    /**
     * Get voices with enhanced metadata (V2 API).
     *
     * @return VoiceV2[]
     * @throws TypecastException
     */
    public function getVoicesV2(?VoicesV2Filter $filter = null): array
    {
        $query = [];
        if ($filter !== null) {
            $query = $filter->toQueryParams();
        }

        $response = $this->httpClient->request('GET', '/v2/voices', [
            'query' => $query,
        ]);

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = json_decode((string) $response->getBody(), true);

        return array_map(
            static fn(array $item): VoiceV2 => VoiceV2::fromArray($item),
            $items,
        );
    }

    /**
     * Get a specific voice by ID (V2 API).
     *
     * @throws NotFoundException
     * @throws TypecastException
     */
    public function getVoiceV2(string $voiceId, ?string $model = null): VoiceV2
    {
        $query = ['voice_id' => $voiceId];
        if ($model !== null) {
            $query['model'] = $model;
        }

        $response = $this->httpClient->request('GET', '/v2/voices', [
            'query' => $query,
        ]);

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = json_decode((string) $response->getBody(), true);

        if (empty($items)) {
            throw new NotFoundException("Voice not found: {$voiceId}");
        }

        return VoiceV2::fromArray($items[0]);
    }

    /**
     * Handle HTTP error responses with specific exception types.
     *
     * @throws TypecastException
     */
    private function handleError(int $statusCode, string $responseText): never
    {
        throw match ($statusCode) {
            400 => new BadRequestException("Bad request: {$responseText}"),
            401 => new UnauthorizedException("Unauthorized: {$responseText}"),
            402 => new PaymentRequiredException("Payment required: {$responseText}"),
            404 => new NotFoundException("Not found: {$responseText}"),
            422 => new UnprocessableEntityException("Validation error: {$responseText}"),
            429 => new RateLimitException("Rate limit exceeded: {$responseText}"),
            500 => new InternalServerException("Internal server error: {$responseText}"),
            default => new TypecastException("API request failed: {$statusCode}, {$responseText}", $statusCode),
        };
    }
}
