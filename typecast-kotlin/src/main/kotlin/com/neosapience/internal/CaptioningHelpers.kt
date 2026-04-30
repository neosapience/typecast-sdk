package com.neosapience.internal

/**
 * Internal utilities for building SRT/VTT caption cues from TTS alignment segments.
 *
 * Rules (must match Python/JS/Go/Java reference implementations byte-for-byte):
 * - Hard cap: split BEFORE appending if cue would exceed 7.0 s OR 42 codepoints,
 *   AND the cue already has segments.
 * - Sentence-terminator split: flush AFTER appending a segment whose text ends
 *   with one of . ? ! 。 ？ ！
 * - Word mode: parts joined with a single space; Char mode: parts joined with "".
 * - Joined text is trimmed (leading/trailing whitespace stripped).
 */
internal object CaptioningHelpers {

    /** Maximum cue duration in seconds before a hard split is forced. */
    const val MAX_CAPTION_SECONDS: Double = 7.0

    /** Maximum cue length in Unicode codepoints before a hard split is forced. */
    const val MAX_CAPTION_CHARS: Int = 42

    /** Characters that trigger a cue flush after the current segment is appended. */
    val SENTENCE_TERMINATORS: List<String> = listOf(".", "?", "!", "。", "？", "！")

    /** An alignment segment (word or character) with timing information. */
    data class Segment(val text: String, val start: Double, val end: Double)

    /** A finished caption cue with start/end time and joined text. */
    data class Cue(val text: String, val start: Double, val end: Double)

    /**
     * Joins parts with a space (word mode) or empty string (char mode), then trims.
     */
    fun joinParts(parts: List<String>, wordMode: Boolean): String {
        val sep = if (wordMode) " " else ""
        return parts.joinToString(sep).trim()
    }

    /**
     * Returns true if the (right-trimmed) text ends with a sentence terminator.
     */
    fun endsInSentence(text: String): Boolean {
        val trimmed = text.trimEnd()
        return SENTENCE_TERMINATORS.any { trimmed.endsWith(it) }
    }

    /**
     * Emits a cue into [cues] when [text] is non-empty.
     */
    private fun emitCue(cues: MutableList<Cue>, text: String, start: Double, end: Double) {
        if (text.isNotEmpty()) cues.add(Cue(text, start, end))
    }

    /**
     * Groups alignment segments into caption cues.
     *
     * @param segs     the ordered list of alignment segments
     * @param wordMode true to join with spaces, false to join with ""
     * @return list of finished cues (may be empty if all segments produce empty text)
     *
     * TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
     * TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
     */
    fun groupIntoCues(segs: List<Segment>, wordMode: Boolean): List<Cue> {
        val cues = mutableListOf<Cue>()
        val parts = mutableListOf<String>()
        var cueStart = 0.0
        var lastEnd = 0.0
        var hasParts = false

        for (seg in segs) {
            // Hard-cap pre-check: only when cue already has content.
            if (hasParts) {
                val tentative = parts + seg.text
                val wouldBeText = joinParts(tentative, wordMode)
                val wouldExceedSeconds = (seg.end - cueStart) > MAX_CAPTION_SECONDS
                val codepointCount = wouldBeText.codePointCount(0, wouldBeText.length)
                val wouldExceedChars = codepointCount > MAX_CAPTION_CHARS
                if (wouldExceedSeconds || wouldExceedChars) {
                    emitCue(cues, joinParts(parts, wordMode), cueStart, lastEnd)
                    parts.clear()
                    hasParts = false
                }
            }

            if (!hasParts) cueStart = seg.start
            parts.add(seg.text)
            lastEnd = seg.end
            hasParts = true

            // Sentence-terminator flush: after appending.
            if (endsInSentence(seg.text)) {
                emitCue(cues, joinParts(parts, wordMode), cueStart, seg.end)
                parts.clear()
                hasParts = false
            }
        }

        // Flush remaining parts.
        if (hasParts) {
            emitCue(cues, joinParts(parts, wordMode), cueStart, lastEnd)
        }

        return cues
    }

    /**
     * Formats a timestamp in SRT format: HH:MM:SS,mmm.
     *
     * @param seconds the time in seconds
     * @return the formatted SRT timestamp string
     */
    fun formatSrtTime(seconds: Double): String {
        val totalMs = Math.round(seconds * 1000)
        val ms = totalMs % 1000
        val totalSec = totalMs / 1000
        val ss = totalSec % 60
        val totalMin = totalSec / 60
        val mm = totalMin % 60
        val hh = totalMin / 60
        return "%02d:%02d:%02d,%03d".format(hh, mm, ss, ms)
    }

    /**
     * Formats a timestamp in WebVTT format: HH:MM:SS.mmm.
     *
     * @param seconds the time in seconds
     * @return the formatted VTT timestamp string
     */
    fun formatVttTime(seconds: Double): String = formatSrtTime(seconds).replace(',', '.')
}
