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

        $outputFormat = $this->defaults->output?->audioFormat ?? 'wav';
        $wavSpec = null;
        $outputSamples = [];

        foreach ($plan as $part) {
            if ($part['kind'] === 'pause') {
                $seconds = $part['seconds'];
                if (!self::isValidPause($seconds)) {
                    throw new \InvalidArgumentException('pause seconds must be greater than 0');
                }
                if ($wavSpec === null) {
                    throw new \InvalidArgumentException('pause cannot be the first composed part');
                }
                array_push($outputSamples, ...array_fill(0, self::secondsToSamples($seconds, $wavSpec['sample_rate']), 0));
                continue;
            }

            $response = $this->client->textToSpeech(self::requestFromPart($part));
            $wav = self::parseWav($response->audioData);
            if ($wavSpec !== null && $wav['spec'] !== $wavSpec) {
                throw new \InvalidArgumentException('all composed WAV segments must use the same PCM format');
            }
            $wavSpec = $wav['spec'];
            array_push($outputSamples, ...self::trimSilence($wav['samples']));
        }

        if ($wavSpec === null) {
            throw new \InvalidArgumentException('at least one speech segment is required');
        }
        if ($outputFormat === 'mp3') {
            throw new \InvalidArgumentException('MP3 conversion is app-level responsibility for composed speech');
        }

        return new TTSResponse(
            audioData: self::encodeWav($outputSamples, $wavSpec),
            duration: count($outputSamples) / $wavSpec['sample_rate'],
            format: 'wav',
        );
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
    private static function requestFromPart(array $part): TTSRequest
    {
        /** @var ComposerSettings $settings */
        $settings = $part['settings'];
        return new TTSRequest(
            voiceId: (string) $settings->voiceId,
            text: (string) $part['text'],
            model: (string) $settings->model,
            language: $settings->language,
            prompt: $settings->prompt,
            output: self::mergeOutput($settings->output, new Output(volume: null, audioPitch: null, audioTempo: null, audioFormat: 'wav')),
            seed: $settings->seed,
        );
    }

    /**
     * @return array{spec: array<string, int>, samples: array<int, int>}
     */
    private static function parseWav(string $data): array
    {
        if (strlen($data) < 12 || substr($data, 0, 4) !== 'RIFF' || substr($data, 8, 4) !== 'WAVE') {
            throw new \InvalidArgumentException('unsupported WAV data');
        }

        $offset = 12;
        $spec = null;
        $samples = null;
        while ($offset + 8 <= strlen($data)) {
            $chunkId = substr($data, $offset, 4);
            $chunkSize = unpack('V', substr($data, $offset + 4, 4))[1];
            $chunkDataOffset = $offset + 8;
            $chunkEnd = $chunkDataOffset + $chunkSize;
            if ($chunkEnd > strlen($data)) {
                throw new \InvalidArgumentException('unsupported WAV data');
            }

            if ($chunkId === 'fmt ') {
                if ($chunkSize < 16) {
                    throw new \InvalidArgumentException('unsupported WAV data');
                }
                $fmt = unpack('vaudio_format/vchannels/Vsample_rate/Vbyte_rate/vblock_align/vbits_per_sample', substr($data, $chunkDataOffset, 16));
                if ($fmt['audio_format'] !== 1 || $fmt['channels'] !== 1 || $fmt['bits_per_sample'] !== 16) {
                    throw new \InvalidArgumentException('only mono 16-bit PCM WAV is supported for composed speech');
                }
                $spec = [
                    'sample_rate' => $fmt['sample_rate'],
                    'channels' => $fmt['channels'],
                    'bits_per_sample' => $fmt['bits_per_sample'],
                ];
            } elseif ($chunkId === 'data') {
                $samples = [];
                for ($i = $chunkDataOffset; $i + 1 < $chunkEnd; $i += 2) {
                    $value = unpack('v', substr($data, $i, 2))[1];
                    $samples[] = $value >= 0x8000 ? $value - 0x10000 : $value;
                }
            }
            $offset = $chunkEnd + ($chunkSize % 2);
        }

        if ($spec === null || $samples === null) {
            throw new \InvalidArgumentException('unsupported WAV data');
        }

        return ['spec' => $spec, 'samples' => $samples];
    }

    /**
     * @param array<int, int> $samples
     * @param array<string, int> $spec
     */
    private static function encodeWav(array $samples, array $spec): string
    {
        $payload = '';
        foreach ($samples as $sample) {
            $payload .= pack('v', $sample & 0xffff);
        }

        return 'RIFF'
            . pack('V', 36 + strlen($payload))
            . 'WAVEfmt '
            . pack('VvvVVvv', 16, 1, $spec['channels'], $spec['sample_rate'], $spec['sample_rate'] * $spec['channels'] * 2, $spec['channels'] * 2, $spec['bits_per_sample'])
            . 'data'
            . pack('V', strlen($payload))
            . $payload;
    }

    /**
     * @param array<int, int> $samples
     * @return array<int, int>
     */
    private static function trimSilence(array $samples): array
    {
        $start = 0;
        $end = count($samples);
        while ($start < $end && abs($samples[$start]) <= 0) {
            $start++;
        }
        while ($end > $start && abs($samples[$end - 1]) <= 0) {
            $end--;
        }
        return array_values(array_slice($samples, $start, $end - $start));
    }

    private static function secondsToSamples(float $seconds, int $sampleRate): int
    {
        return (int) round($seconds * $sampleRate);
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
