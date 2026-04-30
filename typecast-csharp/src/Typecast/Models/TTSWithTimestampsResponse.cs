using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;
using Typecast.Internal;

namespace Typecast.Models;

/// <summary>
/// Response model for Text-to-Speech synthesis with word/character timestamp alignment.
/// </summary>
public class TTSWithTimestampsResponse
{
    /// <summary>Base64-encoded audio data.</summary>
    [JsonPropertyName("audio")]
    public string Audio { get; set; } = string.Empty;

    /// <summary>Audio format string (e.g. "wav", "mp3").</summary>
    [JsonPropertyName("audio_format")]
    public string AudioFormat { get; set; } = string.Empty;

    /// <summary>Duration of the audio in seconds.</summary>
    [JsonPropertyName("audio_duration")]
    public double AudioDuration { get; set; }

    /// <summary>Word-level alignment segments, or null if not requested.</summary>
    [JsonPropertyName("words")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public IReadOnlyList<WordSegment>? Words { get; set; }

    /// <summary>Character-level alignment segments, or null if not requested.</summary>
    [JsonPropertyName("characters")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public IReadOnlyList<CharacterSegment>? Characters { get; set; }

    /// <summary>Creates a new empty TTSWithTimestampsResponse.</summary>
    public TTSWithTimestampsResponse() { }

    /// <summary>Creates a new TTSWithTimestampsResponse with the given values.</summary>
    /// <param name="audio">Base64-encoded audio data.</param>
    /// <param name="audioFormat">Audio format string.</param>
    /// <param name="audioDuration">Duration in seconds.</param>
    /// <param name="words">Word-level segments (optional).</param>
    /// <param name="characters">Character-level segments (optional).</param>
    public TTSWithTimestampsResponse(
        string audio,
        string audioFormat,
        double audioDuration,
        IReadOnlyList<WordSegment>? words,
        IReadOnlyList<CharacterSegment>? characters)
    {
        Audio = audio;
        AudioFormat = audioFormat;
        AudioDuration = audioDuration;
        Words = words;
        Characters = characters;
    }

    /// <summary>
    /// Decodes the base64-encoded audio data into a byte array.
    /// </summary>
    /// <returns>The raw audio bytes.</returns>
    public byte[] AudioBytes() => Convert.FromBase64String(Audio);

    /// <summary>
    /// Writes the decoded audio bytes to a file asynchronously.
    /// </summary>
    /// <param name="path">Destination file path.</param>
    /// <param name="ct">Cancellation token.</param>
    public async Task SaveAudioAsync(string path, CancellationToken ct = default)
    {
#if NETSTANDARD2_0 || NETSTANDARD2_1
        await Task.Run(() => File.WriteAllBytes(path, AudioBytes()), ct).ConfigureAwait(false);
#else
        await File.WriteAllBytesAsync(path, AudioBytes(), ct).ConfigureAwait(false);
#endif
    }

    /// <summary>
    /// Generates an SRT subtitle string from the alignment segments.
    /// Uses word-level segments if available; falls back to character-level segments.
    /// </summary>
    /// <returns>SRT-formatted string with LF line endings.</returns>
    /// <exception cref="InvalidOperationException">Thrown when no alignment segments are present.</exception>
    public string ToSrt()
    {
        var (segments, wordMode) = ResolveSegments();
        var cues = CaptioningHelpers.GroupIntoCues(segments, wordMode);
        return CaptioningHelpers.BuildSrt(cues);
    }

    /// <summary>
    /// Generates a WebVTT subtitle string from the alignment segments.
    /// Uses word-level segments if available; falls back to character-level segments.
    /// </summary>
    /// <returns>VTT-formatted string with LF line endings.</returns>
    /// <exception cref="InvalidOperationException">Thrown when no alignment segments are present.</exception>
    public string ToVtt()
    {
        var (segments, wordMode) = ResolveSegments();
        var cues = CaptioningHelpers.GroupIntoCues(segments, wordMode);
        return CaptioningHelpers.BuildVtt(cues);
    }

    // Picks the best available segment list and returns it alongside the join mode.
    // Priority mirrors Python _segments_for_captioning:
    //   1. words >= 2 -> words (word_mode=true)
    //   2. characters >= 1 -> characters (word_mode=false)
    //   3. words == 1 (no characters) -> words (word_mode=true)
    //   4. neither -> error
    private ((string Text, double Start, double End)[] Segments, bool WordMode) ResolveSegments()
    {
        if (Words != null && Words.Count >= 2)
        {
            var segs = Words.Select(w => (w.Text, w.Start, w.End)).ToArray();
            return (segs, true);
        }

        if (Characters != null && Characters.Count > 0)
        {
            var segs = Characters.Select(c => (c.Text, c.Start, c.End)).ToArray();
            return (segs, false);
        }

        if (Words != null && Words.Count == 1)
        {
            var segs = Words.Select(w => (w.Text, w.Start, w.End)).ToArray();
            return (segs, true);
        }

        throw new InvalidOperationException(
            "No alignment segments available. Request the TTS endpoint with a granularity parameter.");
    }
}
