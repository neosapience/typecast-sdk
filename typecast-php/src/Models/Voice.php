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
        if (!isset($data['voice_id']) || !is_string($data['voice_id'])) {
            throw new \InvalidArgumentException('voice_id is required and must be a string');
        }
        if (!isset($data['voice_name']) || !is_string($data['voice_name'])) {
            throw new \InvalidArgumentException('voice_name is required and must be a string');
        }

        return new self(
            voiceId: $data['voice_id'],
            voiceName: $data['voice_name'],
            model: isset($data['model']) && is_string($data['model']) ? $data['model'] : '',
            emotions: isset($data['emotions']) && is_array($data['emotions']) ? $data['emotions'] : [],
        );
    }
}
