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
