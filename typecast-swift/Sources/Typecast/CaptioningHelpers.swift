import Foundation

/// Internal utilities for building SRT/VTT caption cues from TTS alignment segments.
///
/// Rules (must match Python/JS/Go/Java/Kotlin/C# reference implementations byte-for-byte):
///   - Hard cap: split BEFORE appending if cue would exceed 7.0 s OR 42 codepoints,
///     AND the cue already has segments.
///   - Sentence-terminator split: flush AFTER appending a segment whose text ends
///     with one of: . ? ! 。 ？ ！
///   - Word mode: parts joined with a single space; Char mode: parts joined with "".
///   - Joined text is trimmed (leading/trailing whitespace stripped).
internal enum CaptioningHelpers {

    /// Maximum cue duration in seconds before a hard split is forced.
    static let maxCaptionSeconds: Double = 7.0

    /// Maximum cue length in Unicode codepoints before a hard split is forced.
    static let maxCaptionChars: Int = 42

    private static let sentenceTerminators: [Character] = [".", "?", "!", "。", "？", "！"]

    // MARK: - Public types

    /// A generic (text, start, end) tuple used internally across the captioning pipeline.
    struct Segment {
        let text: String
        let start: Double
        let end: Double
    }

    /// A finished caption cue ready for SRT/VTT serialisation.
    struct Cue {
        let text: String
        let start: Double
        let end: Double
    }

    // MARK: - Cue grouping

    /// Groups alignment segments into caption cues.
    /// - Parameters:
    ///   - segments: Ordered sequence of segments.
    ///   - wordMode: `true` to join parts with a space; `false` to concatenate.
    /// - Returns: Array of finished cues (may be empty).
    ///
    /// TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
    /// TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
    static func groupIntoCues(segments: [Segment], wordMode: Bool) -> [Cue] {
        var cues: [Cue] = []
        var parts: [String] = []
        var curStart: Double? = nil
        var lastEnd: Double = 0.0

        for seg in segments {
            // Hard-cap pre-check: only when the current cue already has content.
            if !parts.isEmpty, let cs = curStart {
                let tentative = parts + [seg.text]
                let wouldBeText = joinParts(tentative, wordMode: wordMode)
                let wouldExceedSeconds = (seg.end - cs) > maxCaptionSeconds
                let wouldExceedChars = codepointCount(wouldBeText) > maxCaptionChars
                if wouldExceedSeconds || wouldExceedChars {
                    let cueText = joinParts(parts, wordMode: wordMode)
                    if !cueText.isEmpty {
                        cues.append(Cue(text: cueText, start: cs, end: lastEnd))
                    }
                    parts.removeAll()
                    curStart = nil
                }
            }

            if curStart == nil {
                curStart = seg.start
            }
            parts.append(seg.text)
            lastEnd = seg.end

            // Sentence-terminator flush: after appending.
            if endsInSentence(seg.text) {
                let cueText = joinParts(parts, wordMode: wordMode)
                if !cueText.isEmpty, let cs = curStart {
                    cues.append(Cue(text: cueText, start: cs, end: seg.end))
                }
                parts.removeAll()
                curStart = nil
            }
        }

        // Flush any remaining parts.
        if !parts.isEmpty, let cs = curStart {
            let cueText = joinParts(parts, wordMode: wordMode)
            if !cueText.isEmpty {
                cues.append(Cue(text: cueText, start: cs, end: lastEnd))
            }
        }

        return cues
    }

    // MARK: - SRT / VTT builders

    /// Renders cues as an SRT document with LF line endings.
    static func buildSrt(cues: [Cue]) -> String {
        var out = ""
        for (i, cue) in cues.enumerated() {
            out += "\(i + 1)\n"
            out += "\(formatSrtTime(cue.start)) --> \(formatSrtTime(cue.end))\n"
            out += "\(cue.text)\n\n"
        }
        return out
    }

    /// Renders cues as a WebVTT document with LF line endings.
    static func buildVtt(cues: [Cue]) -> String {
        var out = "WEBVTT\n\n"
        for cue in cues {
            out += "\(formatVttTime(cue.start)) --> \(formatVttTime(cue.end))\n"
            out += "\(cue.text)\n\n"
        }
        return out
    }

    // MARK: - Timestamp formatting

    /// Formats a timestamp as `HH:MM:SS,mmm` (SRT format).
    static func formatSrtTime(_ seconds: Double) -> String {
        let totalMs = Int64((seconds * 1000.0).rounded())
        let ms = totalMs % 1000
        let totalSec = totalMs / 1000
        let ss = totalSec % 60
        let totalMin = totalSec / 60
        let mm = totalMin % 60
        let hh = totalMin / 60
        return String(format: "%02d:%02d:%02d,%03d", hh, mm, ss, ms)
    }

    /// Formats a timestamp as `HH:MM:SS.mmm` (VTT format).
    static func formatVttTime(_ seconds: Double) -> String {
        formatSrtTime(seconds).replacingOccurrences(of: ",", with: ".")
    }

    // MARK: - Private helpers

    private static func joinParts(_ parts: [String], wordMode: Bool) -> String {
        let separator = wordMode ? " " : ""
        return parts.joined(separator: separator)
            .trimmingCharacters(in: .whitespaces)
    }

    private static func endsInSentence(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespaces)
        guard let last = trimmed.last else { return false }
        return sentenceTerminators.contains(last)
    }

    /// Returns the number of Unicode scalar values (codepoints) in `text`.
    /// This matches Python's `len()` for BMP text and correctly handles
    /// supplementary codepoints that are two UTF-16 code units.
    static func codepointCount(_ text: String) -> Int {
        text.unicodeScalars.count
    }
}
