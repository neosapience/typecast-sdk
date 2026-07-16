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
use Neosapience\Typecast\Models\CustomVoice;
use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\PresetPrompt;
use Neosapience\Typecast\Models\Prompt;
use Neosapience\Typecast\Models\RecommendedVoice;
use Neosapience\Typecast\Models\SmartPrompt;
use Neosapience\Typecast\Models\SubscriptionResponse;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSRequestStream;
use Neosapience\Typecast\Models\TTSRequestWithTimestamps;
use Neosapience\Typecast\Models\TTSResponse;
use Neosapience\Typecast\Models\TTSWithTimestampsResponse;
use Neosapience\Typecast\Models\Voice;
use Neosapience\Typecast\Models\VoiceV2;
use Neosapience\Typecast\Models\VoicesV2Filter;

/**
 * Synchronous client for the Typecast Text-to-Speech API.
 */
class TypecastClient
{
    private const DEFAULT_BASE_URL = 'https://api.typecast.ai';
    private const SDK_VERSION = '1.2.8';

    private ClientInterface $httpClient;

    public function __construct(
        private readonly ?string $apiKey = null,
        private readonly string $baseUrl = self::DEFAULT_BASE_URL,
        ?ClientInterface $httpClient = null,
    ) {
        $normalizedBaseUrl = rtrim(trim($this->baseUrl), '/');
        if (trim((string) $this->apiKey) === '' && strcasecmp($normalizedBaseUrl, self::DEFAULT_BASE_URL) === 0) {
            throw new \InvalidArgumentException('API key must not be empty');
        }
        if (!str_starts_with($normalizedBaseUrl, 'https://') && !str_starts_with($normalizedBaseUrl, 'http://localhost')) {
            throw new \InvalidArgumentException('Base URL must use HTTPS');
        }
        $this->httpClient = $httpClient ?? new Client([
            'base_uri' => $normalizedBaseUrl,
            'http_errors' => false,
            'headers' => $this->authHeaders() + ['Content-Type' => 'application/json'],
        ]);
    }

    /**
     * Convert text to speech.
     *
     * @throws TypecastException
     */
    public function textToSpeech(TTSRequest $request): TTSResponse
    {
        try {
            $response = $this->httpClient->request('POST', '/v1/text-to-speech', $this->requestOptions([
                'json' => $request->toArray(),
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        $duration = (float) ($response->getHeaderLine('X-Audio-Duration') ?: '0');
        $contentType = strtolower($response->getHeaderLine('Content-Type') ?: 'audio/wav');
        $format = str_contains($contentType, 'mp3') || str_contains($contentType, 'mpeg') ? 'mp3' : 'wav';

        return new TTSResponse(
            audioData: (string) $response->getBody(),
            duration: $duration,
            format: $format,
        );
    }

    public function composeSpeech(): SpeechComposer
    {
        return new SpeechComposer($this);
    }

    /** @param array<int, array<string, mixed>> $segments */
    public function composeTextToSpeech(array $segments): TTSResponse
    {
        try {
            $response = $this->httpClient->request('POST', '/v1/text-to-speech/compose', $this->requestOptions(['json' => ['segments' => $segments]]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }
        if ($response->getStatusCode() !== 200) $this->handleError($response->getStatusCode(), (string) $response->getBody());
        $contentType = $response->getHeaderLine('Content-Type') ?: 'audio/wav';
        $format = str_contains($contentType, 'mp3') || str_contains($contentType, 'mpeg') ? 'mp3' : 'wav';
        return new TTSResponse((string) $response->getBody(), (float) ($response->getHeaderLine('X-Audio-Duration') ?: '0'), $format);
    }

    /**
     * Convert text to speech and save the audio bytes to a file.
     *
     * Browse available API voices at https://typecast.ai/developers/api/voices.
     *
     * @throws TypecastException
     */
    public function generateToFile(
        string $path,
        string $text,
        string $voiceId,
        string $model = 'ssfm-v30',
        ?string $language = null,
        Prompt|PresetPrompt|SmartPrompt|null $prompt = null,
        ?Output $output = null,
        ?int $seed = null,
    ): TTSResponse {
        if (trim($path) === '') {
            throw new \InvalidArgumentException('path must be non-empty');
        }
        $response = $this->textToSpeech(new TTSRequest(
            voiceId: $voiceId,
            text: $text,
            model: $model,
            language: $language,
            prompt: $prompt,
            output: $output ?? $this->inferOutput($path),
            seed: $seed,
        ));
        if (file_put_contents($path, $response->audioData) === false) {
            throw new TypecastException('Failed to write audio file');
        }
        return $response;
    }

    /**
     * Convert text to speech with word- and character-level timestamps.
     *
     * @param string|null $granularity Optional granularity override: 'word' or 'char'.
     *                                 When null the server uses its default.
     * @throws \InvalidArgumentException on invalid granularity value
     * @throws TypecastException
     */
    public function textToSpeechWithTimestamps(
        TTSRequestWithTimestamps $request,
        ?string $granularity = null,
    ): TTSWithTimestampsResponse {
        if ($granularity !== null && !in_array($granularity, ['word', 'char'], true)) {
            throw new \InvalidArgumentException("granularity must be 'word' or 'char', got '{$granularity}'");
        }

        $uri = '/v1/text-to-speech/with-timestamps';
        if ($granularity !== null) {
            $uri .= '?granularity=' . $granularity;
        }

        try {
            $response = $this->httpClient->request('POST', $uri, $this->requestOptions([
                'json' => $request->toArray(),
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<string, mixed> $data */
        $data = $this->decodeJson((string) $response->getBody());

        return TTSWithTimestampsResponse::fromArray($data);
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
        try {
            $response = $this->httpClient->request('POST', '/v1/text-to-speech/stream', $this->requestOptions([
                'json' => $request->toArray(),
                'stream' => true,
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

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
        try {
            $response = $this->httpClient->request('GET', '/v1/users/me/subscription', $this->requestOptions());
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<string, mixed> $data */
        $data = $this->decodeJson((string) $response->getBody());

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
        if ($model !== null && trim($model) === '') {
            throw new \InvalidArgumentException('model must not be empty');
        }
        $query = ['page' => 0, 'page_size' => 100];
        if ($model !== null) {
            $query['model'] = $model;
        }

        try {
            $response = $this->httpClient->request('GET', '/v1/voices', $this->requestOptions([
                'query' => $query,
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = $this->decodeJson((string) $response->getBody());

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

        try {
            $response = $this->httpClient->request('GET', '/v2/voices', $this->requestOptions([
                'query' => $query,
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = $this->decodeJson((string) $response->getBody());

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
        if (trim($voiceId) === '') {
            throw new \InvalidArgumentException('voiceId must not be empty');
        }
        $query = ['voice_id' => $voiceId];
        if ($model !== null) {
            $query['model'] = $model;
        }

        try {
            $response = $this->httpClient->request('GET', '/v2/voices', $this->requestOptions([
                'query' => $query,
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = $this->decodeJson((string) $response->getBody());

        if (empty($items)) {
            throw new NotFoundException("Voice not found: {$voiceId}");
        }

        return VoiceV2::fromArray($items[0]);
    }

    /**
     * Recommend voices from a text description.
     *
     * Results only include voiceId, voiceName, and score. Use getVoiceV2() or
     * getVoicesV2() to fetch detailed voice metadata for returned voice IDs.
     *
     * @return RecommendedVoice[]
     * @throws TypecastException
     */
    public function recommendVoices(string $query, int $count = 5): array
    {
        if ($count < 1 || $count > 10) {
            throw new \InvalidArgumentException('count must be between 1 and 10');
        }

        try {
            $response = $this->httpClient->request('GET', '/v1/voices/recommendations', $this->requestOptions([
                'query' => ['query' => $query, 'count' => $count],
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<int, array<string, mixed>> $items */
        $items = $this->decodeJson((string) $response->getBody());

        return array_map(
            static fn(array $item): RecommendedVoice => RecommendedVoice::fromArray($item),
            $items,
        );
    }

    /**
     * Create a custom voice (created via instant cloning) from an audio sample.
     *
     * @param string|resource $audio Audio bytes or a readable resource.
     *                               String file paths are NOT auto-detected —
     *                               read via file_get_contents() and pass bytes.
     * @param string $filename       Multipart filename hint (e.g., "sample.wav").
     * @param string $name           Voice name, 1–30 characters.
     * @param string $model          SSFM model version, e.g. "ssfm-v21" or "ssfm-v30".
     * @return CustomVoice
     * @throws \InvalidArgumentException on validation failure.
     * @throws TypecastException on HTTP error.
     */
    public function cloneVoice($audio, string $filename, string $name, string $model): CustomVoice
    {
        $nameLen = strlen($name);
        if ($nameLen < CustomVoice::NAME_MIN_LENGTH || $nameLen > CustomVoice::NAME_MAX_LENGTH) {
            throw new \InvalidArgumentException(sprintf(
                'name must be %d-%d characters; got %d',
                CustomVoice::NAME_MIN_LENGTH,
                CustomVoice::NAME_MAX_LENGTH,
                $nameLen,
            ));
        }

        $bytes = is_resource($audio) ? stream_get_contents($audio) : (string) $audio;
        if (strlen($bytes) > CustomVoice::CLONING_MAX_FILE_SIZE) {
            throw new \InvalidArgumentException(sprintf(
                'audio file exceeds 25MB limit; got %d bytes',
                strlen($bytes),
            ));
        }

        try {
            $response = $this->httpClient->request('POST', '/v1/voices/clone', $this->requestOptions([
                'multipart' => [
                    ['name' => 'name',  'contents' => $name],
                    ['name' => 'model', 'contents' => $model],
                    [
                        'name'     => 'file',
                        'contents' => $bytes,
                        'filename' => $filename,
                        'headers'  => ['Content-Type' => self::guessAudioMime($filename)],
                    ],
                ],
            ]));
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 200) {
            $this->handleError($statusCode, (string) $response->getBody());
        }

        /** @var array<string, mixed> $data */
        $data = $this->decodeJson((string) $response->getBody());

        return CustomVoice::fromArray($data);
    }

    /**
     * Delete a custom voice by its voice ID.
     *
     * @param string $voiceId The "uc_"-prefixed voice ID returned by cloneVoice.
     * @throws TypecastException on HTTP error.
     */
    public function deleteVoice(string $voiceId): void
    {
        try {
            $response = $this->httpClient->request('DELETE', '/v1/voices/' . $voiceId, $this->requestOptions());
        } catch (\GuzzleHttp\Exception\GuzzleException $e) {
            throw new TypecastException('Network error: ' . $e->getMessage(), 0, $e);
        }

        $statusCode = $response->getStatusCode();
        if ($statusCode !== 204) {
            $this->handleError($statusCode, (string) $response->getBody());
        }
    }

    /**
     * Guess the MIME type of an audio file from its extension.
     */
    private static function guessAudioMime(string $filename): string
    {
        $lower = strtolower($filename);
        if (str_ends_with($lower, '.wav'))  return 'audio/wav';
        if (str_ends_with($lower, '.mp3'))  return 'audio/mpeg';
        if (str_ends_with($lower, '.ogg'))  return 'audio/ogg';
        if (str_ends_with($lower, '.flac')) return 'audio/flac';
        if (str_ends_with($lower, '.m4a'))  return 'audio/mp4';
        return 'application/octet-stream';
    }

    /**
     * @return array<string, string>
     */
    private function authHeaders(): array
    {
        $apiKey = trim((string) $this->apiKey);
        $headers = ['User-Agent' => $this->userAgent()];
        if ($apiKey !== '') {
            $headers['X-API-KEY'] = $apiKey;
        }
        return $headers;
    }

    /**
     * @param array<string, mixed> $options
     * @return array<string, mixed>
     */
    private function requestOptions(array $options = []): array
    {
        /** @var array<string, string> $existingHeaders */
        $existingHeaders = $options['headers'] ?? [];
        $options['headers'] = $this->authHeaders() + $existingHeaders;
        return $options;
    }

    private function userAgent(): string
    {
        $base = strcasecmp(rtrim(trim($this->baseUrl), '/'), self::DEFAULT_BASE_URL) === 0 ? 'default' : 'custom';
        return sprintf(
            'typecast-php/%s PHP/%s Guzzle (base=%s; os=%s; arch=%s; sdk_env=php; platform=server)',
            self::SDK_VERSION,
            PHP_MAJOR_VERSION . '.' . PHP_MINOR_VERSION,
            $base,
            $this->osName(),
            $this->archName(),
        );
    }

    private function osName(): string
    {
        return match (PHP_OS_FAMILY) {
            'Darwin' => 'macos',
            'Windows' => 'windows',
            'Linux' => 'linux',
            default => 'unknown',
        };
    }

    private function archName(): string
    {
        $machine = strtolower(php_uname('m'));
        return match (true) {
            in_array($machine, ['arm64', 'aarch64'], true) => 'arm64',
            in_array($machine, ['x86_64', 'amd64'], true) => 'x64',
            in_array($machine, ['i386', 'i686', 'x86'], true) => 'x86',
            str_starts_with($machine, 'arm') => 'arm',
            default => 'unknown',
        };
    }

    /**
     * Decode a JSON response body, throwing on failure.
     *
     * @return array<string|int, mixed>
     * @throws TypecastException
     */
    private function decodeJson(string $body): array
    {
        $data = json_decode($body, true);
        if (!is_array($data)) {
            throw new TypecastException(
                'Failed to decode JSON response: ' . (json_last_error_msg() ?: 'unknown error'),
            );
        }

        return $data;
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

    private function inferOutput(string $path): ?Output
    {
        return match (strtolower(pathinfo($path, PATHINFO_EXTENSION))) {
            'mp3' => new Output(audioFormat: 'mp3'),
            'wav' => new Output(audioFormat: 'wav'),
            default => null,
        };
    }
}
