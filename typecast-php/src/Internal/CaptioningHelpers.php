<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Internal;

/**
 * Static helpers for grouping alignment segments into subtitle cues
 * and formatting timestamps in SRT/VTT notation.
 */
final class CaptioningHelpers
{
    public const MAX_CAPTION_SECONDS = 7.0;
    public const MAX_CAPTION_CHARS = 42;
    public const SENTENCE_TERMINATORS = ['.', '?', '!', "\xe3\x80\x82", "\xef\xbc\x9f", "\xef\xbc\x81"];

    /**
     * Group a flat list of segments into caption cues.
     *
     * @param list<array{text: string, start: float, end: float}> $segments
     * @return list<array{text: string, start: float, end: float}>
     *
     * TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
     * TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
     */
    public static function groupIntoCues(array $segments, bool $wordMode): array
    {
        $cues = [];
        $parts = [];
        $curStart = null;
        $lastEnd = null;

        foreach ($segments as $seg) {
            if (count($parts) > 0 && $curStart !== null && $lastEnd !== null) {
                $tentative = array_merge($parts, [$seg['text']]);
                $wouldBeText = self::joinParts($tentative, $wordMode);
                $wouldExceedSec = ($seg['end'] - $curStart) > self::MAX_CAPTION_SECONDS;
                $wouldExceedChars = mb_strlen($wouldBeText, 'UTF-8') > self::MAX_CAPTION_CHARS;
                if ($wouldExceedSec || $wouldExceedChars) {
                    $text = self::joinParts($parts, $wordMode);
                    if ($text !== '') {
                        $cues[] = ['text' => $text, 'start' => $curStart, 'end' => $lastEnd];
                    }
                    $parts = [];
                    $curStart = null;
                }
            }

            if ($curStart === null) {
                $curStart = $seg['start'];
            }
            $parts[] = $seg['text'];
            $lastEnd = $seg['end'];

            if (self::endsInSentence($seg['text'])) {
                $text = self::joinParts($parts, $wordMode);
                if ($text !== '') {
                    $cues[] = ['text' => $text, 'start' => $curStart, 'end' => $seg['end']];
                }
                $parts = [];
                $curStart = null;
            }
        }
        if (count($parts) > 0 && $curStart !== null && $lastEnd !== null) {
            $text = self::joinParts($parts, $wordMode);
            if ($text !== '') {
                $cues[] = ['text' => $text, 'start' => $curStart, 'end' => $lastEnd];
            }
        }
        return $cues;
    }

    /**
     * Join parts with a space (word mode) or empty string (char mode), then trim.
     *
     * @param list<string> $parts
     */
    public static function joinParts(array $parts, bool $wordMode): string
    {
        $sep = $wordMode ? ' ' : '';
        return trim(implode($sep, $parts));
    }

    /**
     * Check whether the text ends with a sentence terminator.
     */
    public static function endsInSentence(string $text): bool
    {
        $trimmed = rtrim($text);
        foreach (self::SENTENCE_TERMINATORS as $t) {
            if (str_ends_with($trimmed, $t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Format seconds as SRT timestamp: HH:MM:SS,mmm
     */
    public static function formatSrtTime(float $seconds): string
    {
        $totalMs = (int) round($seconds * 1000);
        $ms = $totalMs % 1000;
        $totalSec = intdiv($totalMs, 1000);
        $ss = $totalSec % 60;
        $totalMin = intdiv($totalSec, 60);
        $mm = $totalMin % 60;
        $hh = intdiv($totalMin, 60);
        return sprintf('%02d:%02d:%02d,%03d', $hh, $mm, $ss, $ms);
    }

    /**
     * Format seconds as VTT timestamp: HH:MM:SS.mmm
     */
    public static function formatVttTime(float $seconds): string
    {
        return str_replace(',', '.', self::formatSrtTime($seconds));
    }
}
