using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Available emotion presets for TTS synthesis.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum EmotionPreset
{
    /// <summary>Neutral/normal tone</summary>
    [JsonPropertyName("normal")] Normal,
    
    /// <summary>Happy/cheerful tone</summary>
    [JsonPropertyName("happy")] Happy,
    
    /// <summary>Sad/melancholic tone</summary>
    [JsonPropertyName("sad")] Sad,
    
    /// <summary>Angry/frustrated tone</summary>
    [JsonPropertyName("angry")] Angry,
    
    /// <summary>Whispered/quiet tone</summary>
    [JsonPropertyName("whisper")] Whisper,
    
    /// <summary>Higher pitched/excited tone</summary>
    [JsonPropertyName("toneup")] ToneUp,
    
    /// <summary>Lower pitched/calm tone</summary>
    [JsonPropertyName("tonedown")] ToneDown
}

/// <summary>
/// Extension methods for EmotionPreset enum.
/// </summary>
public static class EmotionPresetExtensions
{
    /// <summary>
    /// Converts the EmotionPreset to its API string representation.
    /// </summary>
    public static string ToApiString(this EmotionPreset preset) => preset switch
    {
        EmotionPreset.Normal => "normal",
        EmotionPreset.Happy => "happy",
        EmotionPreset.Sad => "sad",
        EmotionPreset.Angry => "angry",
        EmotionPreset.Whisper => "whisper",
        EmotionPreset.ToneUp => "toneup",
        EmotionPreset.ToneDown => "tonedown",
        _ => throw new ArgumentOutOfRangeException(nameof(preset))
    };

    /// <summary>
    /// Parses a string to EmotionPreset enum.
    /// </summary>
    public static EmotionPreset ParseEmotionPreset(string value) => value.ToLowerInvariant() switch
    {
        "normal" => EmotionPreset.Normal,
        "happy" => EmotionPreset.Happy,
        "sad" => EmotionPreset.Sad,
        "angry" => EmotionPreset.Angry,
        "whisper" => EmotionPreset.Whisper,
        "toneup" => EmotionPreset.ToneUp,
        "tonedown" => EmotionPreset.ToneDown,
        _ => throw new ArgumentException($"Unknown emotion preset: {value}", nameof(value))
    };
}
