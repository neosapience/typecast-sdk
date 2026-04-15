<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Preset-based emotion control for ssfm-v30 model.
 */
class PresetPrompt
{
    private const VALID_EMOTION_PRESETS = [
        'normal', 'happy', 'sad', 'angry', 'fearful', 'disgusted',
        'surprised', 'whisper', 'shouting',
    ];

    public function __construct(
        public ?string $emotionPreset = 'normal',
        public ?float $emotionIntensity = 1.0,
    ) {
        if ($this->emotionPreset !== null && !in_array($this->emotionPreset, self::VALID_EMOTION_PRESETS, true)) {
            throw new \InvalidArgumentException(
                "emotionPreset must be one of: " . implode(', ', self::VALID_EMOTION_PRESETS)
            );
        }
        if ($this->emotionIntensity !== null && ($this->emotionIntensity < 0.0 || $this->emotionIntensity > 2.0)) {
            throw new \InvalidArgumentException('emotionIntensity must be between 0.0 and 2.0');
        }
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
