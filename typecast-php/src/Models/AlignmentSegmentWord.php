<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * A word-level alignment segment from the TTS with-timestamps response.
 */
final class AlignmentSegmentWord
{
    public function __construct(
        public readonly string $text,
        public readonly float $start,
        public readonly float $end,
    ) {
    }
}
