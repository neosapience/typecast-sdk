<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Audio output settings for streaming TTS request.
 *
 * The streaming endpoint does not support volume, but supports target_lufs.
 */
class OutputStream
{
    public function __construct(
        public ?int $audioPitch = 0,
        public ?float $audioTempo = 1.0,
        public ?string $audioFormat = 'wav',
        public ?float $targetLufs = null,
    ) {
        if ($this->audioPitch !== null && ($this->audioPitch < -12 || $this->audioPitch > 12)) {
            throw new \InvalidArgumentException('audioPitch must be between -12 and 12');
        }
        if ($this->audioTempo !== null && ($this->audioTempo < 0.5 || $this->audioTempo > 2.0)) {
            throw new \InvalidArgumentException('audioTempo must be between 0.5 and 2.0');
        }
        if ($this->audioFormat !== null && !in_array($this->audioFormat, ['wav', 'mp3'], true)) {
            throw new \InvalidArgumentException("audioFormat must be 'wav' or 'mp3'");
        }
        if ($this->targetLufs !== null && ($this->targetLufs < -70.0 || $this->targetLufs > 0.0)) {
            throw new \InvalidArgumentException('targetLufs must be between -70 and 0');
        }
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
        if ($this->targetLufs !== null) {
            $data['target_lufs'] = $this->targetLufs;
        }

        return $data;
    }
}
