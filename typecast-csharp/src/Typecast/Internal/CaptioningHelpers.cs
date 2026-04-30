using System.Text;

namespace Typecast.Internal;

/// <summary>
/// Internal utilities for building SRT/VTT caption cues from TTS alignment segments.
///
/// Rules (must match Python/JS/Go/Java/Kotlin reference implementations byte-for-byte):
///   - Hard cap: split BEFORE appending if cue would exceed 7.0 s OR 42 codepoints,
///     AND the cue already has segments.
///   - Sentence-terminator split: flush AFTER appending a segment whose text ends
///     with one of: . ? ! 。 ？ ！
///   - Word mode: parts joined with a single space; Char mode: parts joined with "".
///   - Joined text is trimmed (leading/trailing whitespace stripped).
/// </summary>
internal static class CaptioningHelpers
{
    /// <summary>Maximum cue duration in seconds before a hard split is forced.</summary>
    internal const double MaxCaptionSeconds = 7.0;

    /// <summary>Maximum cue length in Unicode codepoints before a hard split is forced.</summary>
    internal const int MaxCaptionChars = 42;

    private static readonly string[] SentenceTerminators = { ".", "?", "!", "。", "？", "！" };

    // -----------------------------------------------------------------------
    // Cue grouping
    // -----------------------------------------------------------------------

    /// <summary>
    /// Groups alignment segments into caption cues.
    /// </summary>
    /// <param name="segments">Ordered sequence of (text, start, end) tuples.</param>
    /// <param name="wordMode">True to join with spaces; false to join without separator.</param>
    /// <returns>List of finished cues (may be empty).</returns>
    /// TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
    /// TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
    internal static List<(string Text, double Start, double End)> GroupIntoCues(
        IEnumerable<(string Text, double Start, double End)> segments,
        bool wordMode)
    {
        var cues = new List<(string, double, double)>();
        var parts = new List<string>();
        double? curStart = null;
        double lastEnd = 0.0;

        foreach (var (text, start, end) in segments)
        {
            // Hard-cap pre-check: only when cue already has content.
            if (parts.Count > 0 && curStart.HasValue)
            {
                var tentative = new List<string>(parts) { text };
                string wouldBe = JoinParts(tentative, wordMode);
                bool wouldExceedSeconds = (end - curStart.Value) > MaxCaptionSeconds;
                bool wouldExceedChars = CodepointCount(wouldBe) > MaxCaptionChars;
                if (wouldExceedSeconds || wouldExceedChars)
                {
                    string cueText = JoinParts(parts, wordMode);
                    if (cueText.Length > 0)
                        cues.Add((cueText, curStart.Value, lastEnd));
                    parts.Clear();
                    curStart = null;
                }
            }

            if (!curStart.HasValue)
                curStart = start;

            parts.Add(text);
            lastEnd = end;

            // Sentence-terminator flush: after appending.
            if (EndsInSentence(text))
            {
                string cueText = JoinParts(parts, wordMode);
                if (cueText.Length > 0)
                    cues.Add((cueText, curStart.Value, end));
                parts.Clear();
                curStart = null;
            }
        }

        // Flush remaining parts.
        if (parts.Count > 0 && curStart.HasValue)
        {
            string cueText = JoinParts(parts, wordMode);
            if (cueText.Length > 0)
                cues.Add((cueText, curStart.Value, lastEnd));
        }

        return cues;
    }

    // -----------------------------------------------------------------------
    // SRT / VTT builders
    // -----------------------------------------------------------------------

    /// <summary>Renders cues as an SRT document with LF line endings.</summary>
    internal static string BuildSrt(List<(string Text, double Start, double End)> cues)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < cues.Count; i++)
        {
            var (text, start, end) = cues[i];
            sb.Append(i + 1);
            sb.Append('\n');
            sb.Append(FormatSrtTime(start));
            sb.Append(" --> ");
            sb.Append(FormatSrtTime(end));
            sb.Append('\n');
            sb.Append(text);
            sb.Append('\n');
            sb.Append('\n');
        }
        return sb.ToString();
    }

    /// <summary>Renders cues as a WebVTT document with LF line endings.</summary>
    internal static string BuildVtt(List<(string Text, double Start, double End)> cues)
    {
        var sb = new StringBuilder();
        sb.Append("WEBVTT");
        sb.Append('\n');
        sb.Append('\n');
        for (int i = 0; i < cues.Count; i++)
        {
            var (text, start, end) = cues[i];
            sb.Append(FormatVttTime(start));
            sb.Append(" --> ");
            sb.Append(FormatVttTime(end));
            sb.Append('\n');
            sb.Append(text);
            sb.Append('\n');
            sb.Append('\n');
        }
        return sb.ToString();
    }

    // -----------------------------------------------------------------------
    // Timestamp formatting
    // -----------------------------------------------------------------------

    /// <summary>Formats a timestamp as HH:MM:SS,mmm (SRT format).</summary>
    internal static string FormatSrtTime(double seconds)
    {
        long totalMs = (long)Math.Round(seconds * 1000.0);
        long ms = totalMs % 1000;
        long totalSec = totalMs / 1000;
        long ss = totalSec % 60;
        long totalMin = totalSec / 60;
        long mm = totalMin % 60;
        long hh = totalMin / 60;
        return $"{hh:D2}:{mm:D2}:{ss:D2},{ms:D3}";
    }

    /// <summary>Formats a timestamp as HH:MM:SS.mmm (VTT format).</summary>
    internal static string FormatVttTime(double seconds) =>
        FormatSrtTime(seconds).Replace(',', '.');

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static string JoinParts(IList<string> parts, bool wordMode)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < parts.Count; i++)
        {
            if (i > 0 && wordMode)
                sb.Append(' ');
            sb.Append(parts[i]);
        }
        // Trim leading/trailing whitespace.
        int start = 0, end = sb.Length;
        while (start < end && sb[start] <= ' ') start++;
        while (end > start && sb[end - 1] <= ' ') end--;
        return sb.ToString(start, end - start);
    }

    private static bool EndsInSentence(string text)
    {
        // Trim trailing whitespace before checking.
        int end = text.Length;
        while (end > 0 && text[end - 1] <= ' ') end--;
        if (end == 0) return false;
        string trimmed = end == text.Length ? text : text.Substring(0, end);
        foreach (var t in SentenceTerminators)
        {
            if (trimmed.EndsWith(t, StringComparison.Ordinal))
                return true;
        }
        return false;
    }

    // Counts Unicode codepoints (not UTF-16 code units; handles surrogate pairs).
    private static int CodepointCount(string s)
    {
        int count = 0;
        for (int i = 0; i < s.Length; i++)
        {
            if (char.IsHighSurrogate(s[i]) && i + 1 < s.Length && char.IsLowSurrogate(s[i + 1]))
            {
                count++;
                i++;
            }
            else
            {
                count++;
            }
        }
        return count;
    }
}
