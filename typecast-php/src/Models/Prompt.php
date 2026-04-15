<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Emotion and style settings for ssfm-v21 model.
 */
class Prompt
{
    public function __construct(
        public ?string $emotionPreset = 'normal',
        public ?float $emotionIntensity = 1.0,
    ) {
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        $data = [];

        if ($this->emotionPreset !== null) {
            $data['emotion_preset'] = $this->emotionPreset;
        }
        if ($this->emotionIntensity !== null) {
            $data['emotion_intensity'] = $this->emotionIntensity;
        }

        return $data;
    }
}
