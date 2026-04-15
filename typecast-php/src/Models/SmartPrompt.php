<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Context-aware emotion inference for ssfm-v30 model.
 */
class SmartPrompt
{
    public function __construct(
        public ?string $previousText = null,
        public ?string $nextText = null,
    ) {
        if ($this->previousText !== null && mb_strlen($this->previousText) > 2000) {
            throw new \InvalidArgumentException('previousText must not exceed 2000 characters');
        }
        if ($this->nextText !== null && mb_strlen($this->nextText) > 2000) {
            throw new \InvalidArgumentException('nextText must not exceed 2000 characters');
        }
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        $data = ['emotion_type' => 'smart'];

        if ($this->previousText !== null) {
            $data['previous_text'] = $this->previousText;
        }
        if ($this->nextText !== null) {
            $data['next_text'] = $this->nextText;
        }

        return $data;
    }
}
