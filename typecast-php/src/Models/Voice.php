<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Voice from V1 API (GET /v1/voices).
 */
class Voice
{
    /**
     * @param string[] $emotions
     */
    public function __construct(
        public string $voiceId,
        public string $voiceName,
        public string $model,
        public array $emotions = [],
    ) {
    }

    /**
     * Create from API JSON response.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            voiceId: $data['voice_id'] ?? '',
            voiceName: $data['voice_name'] ?? '',
            model: $data['model'] ?? '',
            emotions: $data['emotions'] ?? [],
        );
    }
}
