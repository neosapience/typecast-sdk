using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Response of POST /v1/voices/clone — quick-cloned custom voice metadata.
/// VoiceId has the "uc_" prefix and can be used directly as voice_id in TextToSpeechAsync.
/// </summary>
public record CustomVoice(
    [property: JsonPropertyName("voice_id")] string VoiceId,
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("model")] string Model
);

/// <summary>
/// Constants that govern the Quick Voice Cloning endpoint constraints.
/// </summary>
public static class QuickCloningLimits
{
    /// <summary>Maximum audio file size accepted by the clone endpoint (25 MB).</summary>
    public const long CloningMaxFileSize = 25L * 1024 * 1024;

    /// <summary>Minimum character length of the custom voice display name.</summary>
    public const int NameMinLength = 1;

    /// <summary>Maximum character length of the custom voice display name.</summary>
    public const int NameMaxLength = 30;
}
