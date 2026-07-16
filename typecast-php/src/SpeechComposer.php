<?php

declare(strict_types=1);

namespace Neosapience\Typecast;

use Neosapience\Typecast\Models\Output;
use Neosapience\Typecast\Models\PresetPrompt;
use Neosapience\Typecast\Models\Prompt;
use Neosapience\Typecast\Models\SmartPrompt;
use Neosapience\Typecast\Models\TTSRequest;
use Neosapience\Typecast\Models\TTSResponse;

class SpeechPart
{
    public function __construct(
        public string $kind,
        public ?string $text = null,
        public ?float $seconds = null,
    ) {
    }
}

class ComposerSettings
{
    public function __construct(
        public ?string $voiceId = null,
        public ?string $model = null,
        public ?string $language = null,
        public Prompt|PresetPrompt|SmartPrompt|null $prompt = null,
        public ?Output $output = null,
        public ?int $seed = null,
    ) {
    }
}

class SpeechComposer
{
    private ComposerSettings $defaults;

    /** @var array<int, array<string, mixed>> */
    private array $parts = [];

    public function __construct(private readonly TypecastClient $client)
    {
        $this->defaults = new ComposerSettings();
    }

    public function defaults(ComposerSettings $settings): self
    {
        $this->defaults = self::mergeSettings($this->defaults, $settings);
        return $this;
    }

    public function say(string $text, ?ComposerSettings $overrides = null): self
    {
        $this->parts[] = [
            'kind' => 'speech',
            'text' => $text,
            'settings' => self::mergeSettings($this->defaults, $overrides ?? new ComposerSettings()),
        ];
        return $this;
    }

    /**
     * Inserts silence between speech segments.
     *
     * $seconds is a duration in seconds. Use 0.3 for 300 ms, 3 for three seconds.
     */
    public function pause(float $seconds): self
    {
        $this->parts[] = ['kind' => 'pause', 'seconds' => $seconds];
        return $this;
    }

    public function generate(): TTSResponse
    {
        $plan = $this->buildPlan();
        $hasSpeech = false;
        foreach ($plan as $part) {
            if ($part['kind'] === 'speech') {
                $hasSpeech = true;
                break;
            }
        }
        if (!$hasSpeech) {
            throw new \InvalidArgumentException('at least one speech segment is required');
        }

        $formats = [];
        foreach ($plan as $part) {
            if ($part['kind'] === 'speech' && $part['settings']->output?->audioFormat !== null) {
                $formats[$part['settings']->output->audioFormat] = true;
            }
        }
        if (count($formats) > 1) {
            throw new \InvalidArgumentException('composed speech segments must use one audio format');
        }
        $outputFormat = array_key_first($formats) ?? 'wav';
        $segments = [];
        foreach ($plan as $part) {
            if ($part['kind'] === 'pause') {
                $seconds = $part['seconds'];
                if (!self::isValidPause($seconds)) {
                    throw new \InvalidArgumentException('pause seconds must be greater than 0');
                }
                $segments[] = ['type' => 'pause', 'duration_seconds' => $seconds];
                continue;
            }
            $segments[] = ['type' => 'tts'] + self::requestFromPart($part, $outputFormat)->toArray();
        }
        return $this->client->composeTextToSpeech($segments);
    }

    /**
     * @return array<int, array<string, mixed>>
     */
    private function buildPlan(): array
    {
        $plan = [];
        foreach ($this->parts as $part) {
            if ($part['kind'] === 'pause') {
                if (!self::isValidPause($part['seconds'])) {
                    throw new \InvalidArgumentException('pause seconds must be greater than 0');
                }
                $plan[] = $part;
                continue;
            }

            foreach (parsePauseMarkup($part['text']) as $parsed) {
                if ($parsed->kind === 'pause') {
                    $plan[] = ['kind' => 'pause', 'seconds' => $parsed->seconds];
                    continue;
                }
                if (trim((string) $parsed->text) === '') {
                    continue;
                }
                /** @var ComposerSettings $settings */
                $settings = $part['settings'];
                if (trim((string) $settings->voiceId) === '') {
                    throw new \InvalidArgumentException('voiceId is required for composed speech segments');
                }
                if (trim((string) $settings->model) === '') {
                    throw new \InvalidArgumentException('model is required for composed speech segments');
                }
                $plan[] = [
                    'kind' => 'speech',
                    'text' => $parsed->text,
                    'settings' => $settings,
                ];
            }
        }
        return $plan;
    }

    private static function mergeSettings(ComposerSettings $base, ComposerSettings $override): ComposerSettings
    {
        return new ComposerSettings(
            voiceId: $override->voiceId ?? $base->voiceId,
            model: $override->model ?? $base->model,
            language: $override->language ?? $base->language,
            prompt: $override->prompt ?? $base->prompt,
            output: self::mergeOutput($base->output, $override->output),
            seed: $override->seed ?? $base->seed,
        );
    }

    private static function mergeOutput(?Output $base, ?Output $override): ?Output
    {
        if ($base === null && $override === null) {
            return null;
        }
        return new Output(
            volume: $override?->volume ?? $base?->volume,
            targetLufs: $override?->targetLufs ?? $base?->targetLufs,
            audioPitch: $override?->audioPitch ?? $base?->audioPitch,
            audioTempo: $override?->audioTempo ?? $base?->audioTempo,
            audioFormat: $override?->audioFormat ?? $base?->audioFormat,
        );
    }

    /**
     * @param array<string, mixed> $part
     */
    private static function requestFromPart(array $part, string $outputFormat = 'wav'): TTSRequest
    {
        /** @var ComposerSettings $settings */
        $settings = $part['settings'];
        return new TTSRequest(
            voiceId: (string) $settings->voiceId,
            text: (string) $part['text'],
            model: (string) $settings->model,
            language: $settings->language,
            prompt: $settings->prompt,
            output: self::mergeOutput($settings->output, new Output(volume: null, audioPitch: null, audioTempo: null, audioFormat: $outputFormat)),
            seed: $settings->seed,
        );
    }

    private static function isValidPause(float $seconds): bool
    {
        return is_finite($seconds) && $seconds > 0;
    }
}

/**
 * @return SpeechPart[]
 */
function parsePauseMarkup(string $text): array
{
    $parts = [];
    $lastEmit = 0;
    $searchFrom = 0;
    while (($tokenStart = strpos($text, '<|', $searchFrom)) !== false) {
        $bodyStart = $tokenStart + 2;
        $bodyEnd = strpos($text, '|>', $bodyStart);
        if ($bodyEnd === false) {
            break;
        }
        $tokenEnd = $bodyEnd + 2;
        $tokenBody = substr($text, $bodyStart, $bodyEnd - $bodyStart);
        if (str_ends_with($tokenBody, 's')) {
            $secondsText = substr($tokenBody, 0, -1);
            if (validSecondsLiteral($secondsText)) {
                $seconds = (float) $secondsText;
                if ($tokenStart > $lastEmit) {
                    $parts[] = new SpeechPart('text', substr($text, $lastEmit, $tokenStart - $lastEmit));
                }
                $parts[] = new SpeechPart('pause', seconds: $seconds);
                $lastEmit = $tokenEnd;
                $searchFrom = $tokenEnd;
                continue;
            }
        }
        $searchFrom = $bodyStart;
    }
    if ($lastEmit < strlen($text)) {
        $parts[] = new SpeechPart('text', substr($text, $lastEmit));
    }
    return $parts;
}

function validSecondsLiteral(string $value): bool
{
    if ($value === '') {
        return false;
    }
    $parts = explode('.', $value);
    if (count($parts) > 2) {
        return false;
    }
    foreach ($parts as $part) {
        if ($part === '' || preg_match('/^\d+$/', $part) !== 1) {
            return false;
        }
    }
    return true;
}
