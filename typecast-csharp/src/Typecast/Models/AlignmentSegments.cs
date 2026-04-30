using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// A word-level alignment segment with timing information.
/// </summary>
public class WordSegment
{
    /// <summary>The text of this segment.</summary>
    [JsonPropertyName("text")]
    public string Text { get; set; } = string.Empty;

    /// <summary>Start time in seconds.</summary>
    [JsonPropertyName("start")]
    public double Start { get; set; }

    /// <summary>End time in seconds.</summary>
    [JsonPropertyName("end")]
    public double End { get; set; }

    /// <summary>Creates a new empty WordSegment.</summary>
    public WordSegment() { }

    /// <summary>Creates a new WordSegment with the given values.</summary>
    /// <param name="text">The segment text.</param>
    /// <param name="start">Start time in seconds.</param>
    /// <param name="end">End time in seconds.</param>
    public WordSegment(string text, double start, double end)
    {
        Text = text;
        Start = start;
        End = end;
    }
}

/// <summary>
/// A character-level alignment segment with timing information.
/// </summary>
public class CharacterSegment
{
    /// <summary>The text of this segment.</summary>
    [JsonPropertyName("text")]
    public string Text { get; set; } = string.Empty;

    /// <summary>Start time in seconds.</summary>
    [JsonPropertyName("start")]
    public double Start { get; set; }

    /// <summary>End time in seconds.</summary>
    [JsonPropertyName("end")]
    public double End { get; set; }

    /// <summary>Creates a new empty CharacterSegment.</summary>
    public CharacterSegment() { }

    /// <summary>Creates a new CharacterSegment with the given values.</summary>
    /// <param name="text">The segment text.</param>
    /// <param name="start">Start time in seconds.</param>
    /// <param name="end">End time in seconds.</param>
    public CharacterSegment(string text, double start, double end)
    {
        Text = text;
        Start = start;
        End = end;
    }
}
