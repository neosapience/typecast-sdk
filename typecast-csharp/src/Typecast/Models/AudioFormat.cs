using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Available audio output formats.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum AudioFormat
{
    /// <summary>WAV format (uncompressed, higher quality)</summary>
    [JsonPropertyName("wav")] Wav,
    
    /// <summary>MP3 format (compressed, smaller file size)</summary>
    [JsonPropertyName("mp3")] Mp3
}

/// <summary>
/// Extension methods for AudioFormat enum.
/// </summary>
public static class AudioFormatExtensions
{
    /// <summary>
    /// Converts the AudioFormat to its API string representation.
    /// </summary>
    public static string ToApiString(this AudioFormat format) => format switch
    {
        AudioFormat.Wav => "wav",
        AudioFormat.Mp3 => "mp3",
        _ => throw new ArgumentOutOfRangeException(nameof(format))
    };

    /// <summary>
    /// Parses a string to AudioFormat enum.
    /// </summary>
    public static AudioFormat ParseAudioFormat(string value) => value.ToLowerInvariant() switch
    {
        "wav" => AudioFormat.Wav,
        "mp3" => AudioFormat.Mp3,
        _ => throw new ArgumentException($"Unknown audio format: {value}", nameof(value))
    };

    /// <summary>
    /// Gets the MIME content type for the audio format.
    /// </summary>
    public static string ToContentType(this AudioFormat format) => format switch
    {
        AudioFormat.Wav => "audio/wav",
        AudioFormat.Mp3 => "audio/mp3",
        _ => throw new ArgumentOutOfRangeException(nameof(format))
    };

    /// <summary>
    /// Parses a MIME content type to AudioFormat enum.
    /// </summary>
    public static AudioFormat ParseFromContentType(string contentType)
    {
        if (contentType.IndexOf("wav", StringComparison.OrdinalIgnoreCase) >= 0)
            return AudioFormat.Wav;
        if (contentType.IndexOf("mp3", StringComparison.OrdinalIgnoreCase) >= 0 ||
            contentType.IndexOf("mpeg", StringComparison.OrdinalIgnoreCase) >= 0)
            return AudioFormat.Mp3;
        
        throw new ArgumentException($"Unknown content type: {contentType}", nameof(contentType));
    }
}
