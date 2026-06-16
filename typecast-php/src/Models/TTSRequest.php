<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Request body for POST /v1/text-to-speech.
 *
 * Browse available API voices at https://typecast.ai/developers/api/voices.
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
        if (trim($this->voiceId) === '') {
            throw new \InvalidArgumentException('voiceId is required and must be non-empty');
        }
        if (trim($this->model) === '') {
            throw new \InvalidArgumentException('model is required and must be non-empty');
        }
        if (mb_strlen($this->text) === 0) {
            throw new \InvalidArgumentException('text is required and must be non-empty');
        }
        if (mb_strlen($this->text) > 2000) {
            throw new \InvalidArgumentException('text must not exceed 2000 characters');
        }
        if ($this->seed !== null && !is_int($this->seed)) {
            throw new \InvalidArgumentException('seed must be an integer');
        }
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
