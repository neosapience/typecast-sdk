using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Available TTS (Text-to-Speech) model versions.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum TTSModel
{
    /// <summary>
    /// SSFM version 2.1 - supports basic emotion presets.
    /// </summary>
    [JsonPropertyName("ssfm-v21")]
    SsfmV21,

    /// <summary>
    /// SSFM version 3.0 - supports advanced emotion control including smart mode.
    /// </summary>
    [JsonPropertyName("ssfm-v30")]
    SsfmV30
}

/// <summary>
/// Extension methods for TTSModel enum.
/// </summary>
public static class TTSModelExtensions
{
    /// <summary>
    /// Converts the TTSModel to its API string representation.
    /// </summary>
    public static string ToApiString(this TTSModel model) => model switch
    {
        TTSModel.SsfmV21 => "ssfm-v21",
        TTSModel.SsfmV30 => "ssfm-v30",
        _ => throw new ArgumentOutOfRangeException(nameof(model))
    };

    /// <summary>
    /// Parses a string to TTSModel enum.
    /// </summary>
    public static TTSModel ParseTTSModel(string value) => value switch
    {
        "ssfm-v21" => TTSModel.SsfmV21,
        "ssfm-v30" => TTSModel.SsfmV30,
        _ => throw new ArgumentException($"Unknown TTS model: {value}", nameof(value))
    };
}
