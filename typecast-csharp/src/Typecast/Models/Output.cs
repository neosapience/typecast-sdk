using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Audio output configuration for TTS synthesis.
/// </summary>
public class Output
{
    /// <summary>
    /// Volume level (0-200, default 100).
    /// </summary>
    [JsonPropertyName("volume")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? Volume { get; set; } = 100;

    /// <summary>
    /// Audio pitch adjustment in semitones (-12 to 12, default 0).
    /// </summary>
    [JsonPropertyName("audio_pitch")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? AudioPitch { get; set; } = 0;

    /// <summary>
    /// Audio tempo multiplier (0.5-2.0, default 1.0).
    /// </summary>
    [JsonPropertyName("audio_tempo")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public double? AudioTempo { get; set; } = 1.0;

    /// <summary>
    /// Audio output format (default "wav").
    /// </summary>
    [JsonPropertyName("audio_format")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? AudioFormat { get; set; } = "wav";

    /// <summary>
    /// Creates a new Output with default values.
    /// </summary>
    public Output() { }

    /// <summary>
    /// Creates a new Output with specified values.
    /// </summary>
    /// <param name="volume">Volume level (0-200)</param>
    /// <param name="audioPitch">Pitch adjustment in semitones (-12 to 12)</param>
    /// <param name="audioTempo">Tempo multiplier (0.5-2.0)</param>
    /// <param name="audioFormat">Audio format ("wav" or "mp3")</param>
    public Output(int? volume = 100, int? audioPitch = 0, double? audioTempo = 1.0, string? audioFormat = "wav")
    {
        Volume = volume;
        AudioPitch = audioPitch;
        AudioTempo = audioTempo;
        AudioFormat = audioFormat;
    }
}
