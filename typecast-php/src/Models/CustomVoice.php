<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Response of POST /v1/voices/clone — custom voice (created via instant cloning) metadata.
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
        );
    }
}
