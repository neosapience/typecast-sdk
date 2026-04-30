<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

use Neosapience\Typecast\Internal\CaptioningHelpers;

/**
 * Response from POST /v1/text-to-speech/with-timestamps.
 *
 * Provides helpers to decode the audio payload and generate SRT/VTT captions
 * from the embedded word- or character-level alignment segments.
 */
final class TTSWithTimestampsResponse
{
    /**
     * @param list<AlignmentSegmentWord>|null      $words
     * @param list<AlignmentSegmentCharacter>|null $characters
     */
    public function __construct(
        public readonly string $audio,
        public readonly string $audioFormat,
        public readonly float $audioDuration,
        public readonly ?array $words,
        public readonly ?array $characters,
    ) {
    }

    /**
     * Build a response object from a decoded JSON array.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        foreach (['audio', 'audio_format', 'audio_duration'] as $required) {
            if (!array_key_exists($required, $data)) {
                throw new \InvalidArgumentException("Missing required field: {$required}");
            }
        }

        $words = null;
        if (isset($data['words']) && is_array($data['words'])) {
            $words = array_map(
                static fn(array $w) => new AlignmentSegmentWord(
                    (string) $w['text'],
                    (float) $w['start'],
                    (float) $w['end'],
                ),
                $data['words'],
            );
        }

        $chars = null;
        if (isset($data['characters']) && is_array($data['characters'])) {
            $chars = array_map(
                static fn(array $c) => new AlignmentSegmentCharacter(
                    (string) $c['text'],
                    (float) $c['start'],
                    (float) $c['end'],
                ),
                $data['characters'],
            );
        }

        return new self(
            audio: (string) $data['audio'],
            audioFormat: (string) $data['audio_format'],
            audioDuration: (float) $data['audio_duration'],
            words: $words,
            characters: $chars,
        );
    }

    /**
     * Decode the base64-encoded audio payload and return raw bytes.
     *
     * @throws \RuntimeException on invalid base64 data
     */
    public function audioBytes(): string
    {
        $decoded = base64_decode($this->audio, true);
        if ($decoded === false) {
            throw new \RuntimeException('Invalid base64 audio data');
        }
        return $decoded;
    }

    /**
     * Decode and write the audio bytes to a file.
     *
     * @throws \RuntimeException if the write fails
     */
    public function saveAudio(string $path): void
    {
        if (file_put_contents($path, $this->audioBytes()) === false) {
            throw new \RuntimeException("Failed to write audio to {$path}");
        }
    }

    /**
     * Generate an SRT subtitle string from the alignment segments.
     *
     * @throws \RuntimeException when no alignment segments are available
     */
    public function toSrt(): string
    {
        return $this->formatCaptions(srt: true);
    }

    /**
     * Generate a WebVTT subtitle string from the alignment segments.
     *
     * @throws \RuntimeException when no alignment segments are available
     */
    public function toVtt(): string
    {
        return $this->formatCaptions(srt: false);
    }

    /**
     * Build the SRT or VTT text from cues produced by the captioning helpers.
     */
    private function formatCaptions(bool $srt): string
    {
        $picked = $this->pickSegments();
        $cues = CaptioningHelpers::groupIntoCues($picked['segments'], $picked['wordMode']);

        if (count($cues) === 0) {
            throw new \RuntimeException('no alignment segments to caption from');
        }

        $out = '';
        if (!$srt) {
            $out .= "WEBVTT\n\n";
        }

        foreach ($cues as $i => $c) {
            if ($srt) {
                $out .= ($i + 1) . "\n";
            }
            $tStart = $srt
                ? CaptioningHelpers::formatSrtTime($c['start'])
                : CaptioningHelpers::formatVttTime($c['start']);
            $tEnd = $srt
                ? CaptioningHelpers::formatSrtTime($c['end'])
                : CaptioningHelpers::formatVttTime($c['end']);
            $out .= "{$tStart} --> {$tEnd}\n";
            $out .= $c['text'] . "\n\n";
        }

        return $out;
    }

    /**
     * Pick the best available segment list and return it together with a flag
     * indicating whether it is word-mode (true) or character-mode (false).
     *
     * Priority: words (≥2) > characters (≥1) > words (=1)
     *
     * @return array{segments: list<array{text: string, start: float, end: float}>, wordMode: bool}
     * @throws \RuntimeException when neither words nor characters are present
     */
    private function pickSegments(): array
    {
        $toMap = static fn(array $segs): array => array_map(
            static fn($s) => ['text' => $s->text, 'start' => $s->start, 'end' => $s->end],
            $segs,
        );

        $w = $this->words;
        $c = $this->characters;

        if ($w !== null && count($w) >= 2) {
            return ['segments' => $toMap($w), 'wordMode' => true];
        }
        if ($c !== null && count($c) >= 1) {
            return ['segments' => $toMap($c), 'wordMode' => false];
        }
        if ($w !== null && count($w) === 1) {
            return ['segments' => $toMap($w), 'wordMode' => true];
        }

        throw new \RuntimeException('no alignment segments to caption from');
    }
}
