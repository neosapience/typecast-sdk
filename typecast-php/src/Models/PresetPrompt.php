<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Preset-based emotion control for ssfm-v30 model.
 */
class PresetPrompt
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
        $data = ['emotion_type' => 'preset'];

        if ($this->emotionPreset !== null) {
            $data['emotion_preset'] = $this->emotionPreset;
        }
        if ($this->emotionIntensity !== null) {
            $data['emotion_intensity'] = $this->emotionIntensity;
        }

        return $data;
    }
}
