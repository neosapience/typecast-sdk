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
    /// Audio output format (default WAV).
    /// </summary>
    [JsonPropertyName("audio_format")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public AudioFormat? AudioFormat { get; set; } = Models.AudioFormat.Wav;

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
    /// <param name="audioFormat">Audio format</param>
    public Output(int? volume = 100, int? audioPitch = 0, double? audioTempo = 1.0, AudioFormat? audioFormat = null)
    {
        Volume = volume;
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
        if (Volume.HasValue && (Volume.Value < 0 || Volume.Value > 200))
        {
            throw new ArgumentOutOfRangeException(nameof(Volume), "Volume must be between 0 and 200.");
        }

        if (AudioPitch.HasValue && (AudioPitch.Value < -12 || AudioPitch.Value > 12))
        {
            throw new ArgumentOutOfRangeException(nameof(AudioPitch), "AudioPitch must be between -12 and 12.");
        }

        if (AudioTempo.HasValue && (AudioTempo.Value < 0.5 || AudioTempo.Value > 2.0))
        {
            throw new ArgumentOutOfRangeException(nameof(AudioTempo), "AudioTempo must be between 0.5 and 2.0.");
        }
    }
}
