<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Response of POST /v1/custom-voices/instant-clone — custom voice (created via instant cloning) metadata.
 *
 * The voiceId has the "uc_" prefix and can be used directly as the voiceId
 * parameter in textToSpeech calls.
 */
final class CustomVoice
{
    /** Maximum audio file size accepted by cloneVoice (25 MB). */
    public const CLONING_MAX_FILE_SIZE = 25 * 1024 * 1024;

    /** Minimum allowed length for a custom voice name. */
    public const NAME_MIN_LENGTH = 1;

    /** Maximum allowed length for a custom voice name. */
    public const NAME_MAX_LENGTH = 30;

    public function __construct(
        public readonly string $voiceId,
        public readonly string $name,
        public readonly string $model,
        public readonly ?string $source = null,
        public readonly ?string $status = null,
        public readonly ?string $error = null,
        public readonly ?string $createdAt = null,
    ) {}

    /**
     * Create from an API JSON response array.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            voiceId: (string) ($data['voice_id'] ?? ''),
            name:    (string) ($data['name']     ?? ''),
            model:   (string) ($data['model']    ?? ''),
            source:  isset($data['source']) ? (string) $data['source'] : null,
            status:  isset($data['status']) ? (string) $data['status'] : null,
            error:   isset($data['error']) ? (string) $data['error'] : null,
            createdAt: isset($data['created_at']) ? (string) $data['created_at'] : null,
        );
    }
}
