using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Audio output configuration for streaming TTS synthesis.
/// Unlike <see cref="Output"/>, the streaming endpoint does not support
/// <c>volume</c>, but it supports <c>target_lufs</c>.
/// </summary>
public class OutputStream
{
    /// <summary>
    /// Target loudness in LUFS for streaming output normalization (-70 to 0).
    /// </summary>
    [JsonPropertyName("target_lufs")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public double? TargetLufs { get; set; }

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
    /// Audio output format (default WAV).
    /// </summary>
    [JsonPropertyName("audio_format")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public AudioFormat? AudioFormat { get; set; } = Models.AudioFormat.Wav;

    /// <summary>
    /// Creates a new OutputStream with default values.
    /// </summary>
    public OutputStream() { }

    /// <summary>
    /// Creates a new OutputStream with specified values.
    /// </summary>
    /// <param name="audioPitch">Pitch adjustment in semitones (-12 to 12)</param>
    /// <param name="audioTempo">Tempo multiplier (0.5-2.0)</param>
    /// <param name="audioFormat">Audio format</param>
    /// <param name="targetLufs">Target loudness in LUFS (-70 to 0)</param>
    public OutputStream(int? audioPitch = 0, double? audioTempo = 1.0, AudioFormat? audioFormat = null, double? targetLufs = null)
    {
        TargetLufs = targetLufs;
        AudioPitch = audioPitch;
        AudioTempo = audioTempo;
        AudioFormat = audioFormat ?? Models.AudioFormat.Wav;
    }

    /// <summary>
    /// Validates the output configuration.
    /// </summary>
    /// <exception cref="ArgumentOutOfRangeException">Thrown when values are out of valid range.</exception>
    public void Validate()
    {
        if (AudioPitch.HasValue && (AudioPitch.Value < -12 || AudioPitch.Value > 12))
        {
            throw new ArgumentOutOfRangeException(nameof(AudioPitch), "AudioPitch must be between -12 and 12.");
        }

        if (AudioTempo.HasValue && (AudioTempo.Value < 0.5 || AudioTempo.Value > 2.0))
        {
            throw new ArgumentOutOfRangeException(nameof(AudioTempo), "AudioTempo must be between 0.5 and 2.0.");
        }

        if (TargetLufs.HasValue && (TargetLufs.Value < -70.0 || TargetLufs.Value > 0.0))
        {
            throw new ArgumentOutOfRangeException(nameof(TargetLufs), "TargetLufs must be between -70 and 0.");
        }
    }
}
