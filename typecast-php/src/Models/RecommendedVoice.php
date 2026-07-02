<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Voice recommendation result.
 *
 * Recommendation results only include the matched voice ID, voice name, and
 * similarity score. Use getVoiceV2() or getVoicesV2() to fetch detailed voice
 * metadata for a returned voice ID.
 */
class RecommendedVoice
{
    public function __construct(
        public string $voiceId,
        public string $voiceName,
        public float $score,
    ) {
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            voiceId: $data['voice_id'] ?? '',
            voiceName: $data['voice_name'] ?? '',
            score: (float) ($data['score'] ?? 0),
        );
    }
}
