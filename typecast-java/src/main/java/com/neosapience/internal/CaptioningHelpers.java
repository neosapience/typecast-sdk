package com.neosapience.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Package-private utilities for building SRT/VTT caption cues from TTS alignment segments.
 *
 * <p>Rules (must match Python/JS/Go reference implementations byte-for-byte):</p>
 * <ul>
 *   <li>Hard cap: split BEFORE appending if cue would exceed 7.0 s OR 42 codepoints,
 *       AND the cue already has segments.</li>
 *   <li>Sentence-terminator split: flush AFTER appending a segment whose text ends
 *       with one of {@code . ? ! 。 ？ ！}.</li>
 *   <li>Word mode: parts joined with a single space; Char mode: parts joined with "".</li>
 *   <li>Joined text is trimmed (leading/trailing whitespace stripped).</li>
 * </ul>
 */
public final class CaptioningHelpers {

    private CaptioningHelpers() {}

    /** Maximum cue duration in seconds before a hard split is forced. */
    public static final double MAX_CAPTION_SECONDS = 7.0;

    /** Maximum cue length in Unicode codepoints before a hard split is forced. */
    public static final int MAX_CAPTION_CHARS = 42;

    /** Characters that trigger a cue flush after the current segment is appended. */
    public static final List<String> SENTENCE_TERMINATORS =
            Arrays.asList(".", "?", "!", "。", "？", "！");

    // -----------------------------------------------------------------------
    // Public data types
    // -----------------------------------------------------------------------

    /** An alignment segment (word or character) with timing information. */
    public static class Segment {
        /** The text of this segment. */
        public final String text;
        /** Start time in seconds. */
        public final double start;
        /** End time in seconds. */
        public final double end;

        /**
         * Creates a Segment.
         *
         * @param text  the segment text
         * @param start start time in seconds
         * @param end   end time in seconds
         */
        public Segment(String text, double start, double end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    /** A finished caption cue with start/end time and joined text. */
    public static class Cue {
        /** The cue text (segments joined per word/char mode, trimmed). */
        public final String text;
        /** Start time of the first segment in this cue (seconds). */
        public final double start;
        /** End time of the last segment in this cue (seconds). */
        public final double end;

        /**
         * Creates a Cue.
         *
         * @param text  the joined, trimmed cue text
         * @param start start time in seconds
         * @param end   end time in seconds
         */
        public Cue(String text, double start, double end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    // -----------------------------------------------------------------------
    // Algorithm
    // -----------------------------------------------------------------------

    /**
     * Groups alignment segments into caption cues.
     *
     * @param segs     the ordered list of alignment segments
     * @param wordMode {@code true} to join with spaces, {@code false} to join with ""
     * @return list of finished cues (may be empty if all segments produce empty text)
     *
     * TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
     * TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
     */
    /**
     * Appends a cue to {@code cues} when {@code text} is non-empty (guards against
     * all-whitespace / all-empty alignment segments producing blank cues).
     */
    private static void emitCue(List<Cue> cues, String text, double start, double end) {
        if (!text.isEmpty()) {
            cues.add(new Cue(text, start, end));
        }
    }

    public static List<Cue> groupIntoCues(List<Segment> segs, boolean wordMode) {
        List<Cue> cues = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        // Invariant: curStart and lastEnd are always in sync with parts —
        // they are set whenever a segment is appended and cleared with parts.
        // Using 0.0 as the sentinel default; they are only read after parts is non-empty.
        double curStart = 0.0;
        double lastEnd = 0.0;

        for (Segment seg : segs) {
            // Hard-cap pre-check: only when cue already has content.
            if (!parts.isEmpty()) {
                List<String> tentative = new ArrayList<>(parts);
                tentative.add(seg.text);
                String wouldBeText = joinParts(tentative, wordMode);
                boolean wouldExceedSeconds = (seg.end - curStart) > MAX_CAPTION_SECONDS;
                int cpCount = wouldBeText.codePointCount(0, wouldBeText.length());
                boolean wouldExceedChars = cpCount > MAX_CAPTION_CHARS;
                if (wouldExceedSeconds || wouldExceedChars) {
                    emitCue(cues, joinParts(parts, wordMode), curStart, lastEnd);
                    parts.clear();
                }
            }

            if (parts.isEmpty()) {
                curStart = seg.start;
            }
            parts.add(seg.text);
            lastEnd = seg.end;

            // Sentence-terminator flush: after appending.
            if (endsInSentence(seg.text)) {
                emitCue(cues, joinParts(parts, wordMode), curStart, seg.end);
                parts.clear();
            }
        }

        // Flush remaining parts.
        if (!parts.isEmpty()) {
            emitCue(cues, joinParts(parts, wordMode), curStart, lastEnd);
        }

        return cues;
    }

    /**
     * Joins parts with a space (word mode) or empty string (char mode), then trims.
     *
     * @param parts    the text parts to join
     * @param wordMode if true, join with a single space; otherwise join with ""
     * @return the joined and trimmed string
     */
    public static String joinParts(List<String> parts, boolean wordMode) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (!first && wordMode) {
                sb.append(' ');
            }
            sb.append(p);
            first = false;
        }
        return sb.toString().trim();
    }

    /**
     * Returns {@code true} if the (right-trimmed) text ends with a sentence terminator.
     *
     * @param text the text to check
     * @return true if it ends with a sentence-terminal character
     */
    public static boolean endsInSentence(String text) {
        String trimmed = trimTrailingWhitespace(text);
        for (String t : SENTENCE_TERMINATORS) {
            if (trimmed.endsWith(t)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Timestamp formatting
    // -----------------------------------------------------------------------

    /**
     * Formats a timestamp in SRT format: {@code HH:MM:SS,mmm}.
     *
     * @param seconds the time in seconds
     * @return the formatted SRT timestamp string
     */
    public static String formatSrtTime(double seconds) {
        long totalMs = Math.round(seconds * 1000);
        long ms = totalMs % 1000;
        long totalSec = totalMs / 1000;
        long ss = totalSec % 60;
        long totalMin = totalSec / 60;
        long mm = totalMin % 60;
        long hh = totalMin / 60;
        return String.format("%02d:%02d:%02d,%03d", hh, mm, ss, ms);
    }

    /**
     * Formats a timestamp in WebVTT format: {@code HH:MM:SS.mmm}.
     *
     * @param seconds the time in seconds
     * @return the formatted VTT timestamp string
     */
    public static String formatVttTime(double seconds) {
        return formatSrtTime(seconds).replace(',', '.');
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String trimTrailingWhitespace(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
