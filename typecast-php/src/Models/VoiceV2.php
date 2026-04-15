<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Voice from V2 API (GET /v2/voices).
 */
class VoiceV2
{
    /**
     * @param array<array{version: string, emotions: string[]}> $models
     * @param string[]|null $useCases
     */
    public function __construct(
        public string $voiceId,
        public string $voiceName,
        public array $models = [],
        public ?string $gender = null,
        public ?string $age = null,
        public ?array $useCases = null,
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
            models: $data['models'] ?? [],
            gender: $data['gender'] ?? null,
            age: $data['age'] ?? null,
            useCases: $data['use_cases'] ?? null,
        );
    }
}
