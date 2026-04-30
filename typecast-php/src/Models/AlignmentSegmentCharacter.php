<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * A character-level alignment segment from the TTS with-timestamps response.
 */
final class AlignmentSegmentCharacter
{
    public function __construct(
        public readonly string $text,
        public readonly float $start,
        public readonly float $end,
    ) {
    }
}
