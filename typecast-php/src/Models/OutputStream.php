<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Audio output settings for streaming TTS request.
 *
 * The streaming endpoint does not support volume or target_lufs.
 */
class OutputStream
{
    public function __construct(
        public ?int $audioPitch = 0,
        public ?float $audioTempo = 1.0,
        public ?string $audioFormat = 'wav',
    ) {
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        $data = [];

        if ($this->audioPitch !== null && $this->audioPitch !== 0) {
            $data['audio_pitch'] = $this->audioPitch;
        }
        if ($this->audioTempo !== null && $this->audioTempo !== 0.0 && $this->audioTempo !== 1.0) {
            $data['audio_tempo'] = $this->audioTempo;
        }
        if ($this->audioFormat !== null) {
            $data['audio_format'] = $this->audioFormat;
        }

        return $data;
    }
}
