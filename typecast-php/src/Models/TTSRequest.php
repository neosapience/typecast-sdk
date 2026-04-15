<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Request body for POST /v1/text-to-speech.
 */
class TTSRequest
{
    public function __construct(
        public string $voiceId,
        public string $text,
        public string $model,
        public ?string $language = null,
        public Prompt|PresetPrompt|SmartPrompt|null $prompt = null,
        public ?Output $output = null,
        public ?int $seed = null,
    ) {
    }

    /**
     * @return array<string, mixed>
     */
    public function toArray(): array
    {
        $data = [
            'voice_id' => $this->voiceId,
            'text' => $this->text,
            'model' => $this->model,
        ];

        if ($this->language !== null) {
            $data['language'] = $this->language;
        }
        if ($this->prompt !== null) {
            $data['prompt'] = $this->prompt->toArray();
        }
        if ($this->output !== null) {
            $data['output'] = $this->output->toArray();
        }
        if ($this->seed !== null) {
            $data['seed'] = $this->seed;
        }

        return $data;
    }
}
