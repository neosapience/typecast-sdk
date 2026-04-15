<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Response from POST /v1/text-to-speech.
 */
class TTSResponse
{
    public function __construct(
        public string $audioData,
        public float $duration,
        public string $format = 'wav',
    ) {
    }
}
