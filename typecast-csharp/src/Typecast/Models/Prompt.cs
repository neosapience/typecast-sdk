using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Base interface for all prompt types.
/// </summary>
public interface ITTSPrompt
{
    /// <summary>
    /// Validates the prompt configuration.
    /// </summary>
    void Validate();
}

/// <summary>
/// Basic emotion prompt for ssfm-v21 model.
/// </summary>
public class Prompt : ITTSPrompt
{
    /// <summary>
    /// The emotion preset to apply.
    /// </summary>
    [JsonPropertyName("emotion_preset")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public EmotionPreset? EmotionPreset { get; set; }

    /// <summary>
    /// Emotion intensity (0.0 to 2.0, default: 1.0).
    /// </summary>
    [JsonPropertyName("emotion_intensity")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public double? EmotionIntensity { get; set; }

    /// <summary>
    /// Creates a new Prompt with default values.
    /// </summary>
    public Prompt() { }

    /// <summary>
    /// Creates a new Prompt with specified values.
    /// </summary>
    /// <param name="emotionPreset">The emotion preset to apply</param>
    /// <param name="emotionIntensity">Emotion intensity (0.0 to 2.0)</param>
    public Prompt(EmotionPreset? emotionPreset = null, double? emotionIntensity = null)
    {
        EmotionPreset = emotionPreset;
        EmotionIntensity = emotionIntensity;
    }

    /// <inheritdoc />
    public void Validate()
    {
        if (EmotionIntensity.HasValue && (EmotionIntensity.Value < 0.0 || EmotionIntensity.Value > 2.0))
        {
            throw new ArgumentOutOfRangeException(nameof(EmotionIntensity), EmotionIntensity.Value, 
                "Emotion intensity must be between 0.0 and 2.0.");
        }
    }
}

/// <summary>
/// Preset-based emotion prompt for ssfm-v30 model.
/// </summary>
public class PresetPrompt : ITTSPrompt
{
    /// <summary>
    /// The emotion type (always "preset" for this prompt type).
    /// </summary>
    [JsonPropertyName("emotion_type")]
    public string EmotionType => "preset";

    /// <summary>
    /// The emotion preset to apply.
    /// </summary>
    [JsonPropertyName("emotion_preset")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public EmotionPreset? EmotionPreset { get; set; }

    /// <summary>
    /// Emotion intensity (0.0 to 2.0, default: 1.0).
    /// </summary>
    [JsonPropertyName("emotion_intensity")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public double? EmotionIntensity { get; set; }

    /// <summary>
    /// Creates a new PresetPrompt with default values.
    /// </summary>
    public PresetPrompt() { }

    /// <summary>
    /// Creates a new PresetPrompt with specified values.
    /// </summary>
    /// <param name="emotionPreset">The emotion preset to apply</param>
    /// <param name="emotionIntensity">Emotion intensity (0.0 to 2.0)</param>
    public PresetPrompt(EmotionPreset? emotionPreset = null, double? emotionIntensity = null)
    {
        EmotionPreset = emotionPreset;
        EmotionIntensity = emotionIntensity;
    }

    /// <inheritdoc />
    public void Validate()
    {
        if (EmotionIntensity.HasValue && (EmotionIntensity.Value < 0.0 || EmotionIntensity.Value > 2.0))
        {
            throw new ArgumentOutOfRangeException(nameof(EmotionIntensity), EmotionIntensity.Value, 
                "Emotion intensity must be between 0.0 and 2.0.");
        }
    }
}

/// <summary>
/// Smart context-aware emotion prompt for ssfm-v30 model.
/// Uses surrounding text context to infer appropriate emotion.
/// </summary>
public class SmartPrompt : ITTSPrompt
{
    private const int MaxContextLength = 2000;

    /// <summary>
    /// The emotion type (always "smart" for this prompt type).
    /// </summary>
    [JsonPropertyName("emotion_type")]
    public string EmotionType => "smart";

    /// <summary>
    /// Previous text context for emotion inference (max 2000 characters).
    /// </summary>
    [JsonPropertyName("previous_text")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? PreviousText { get; set; }

    /// <summary>
    /// Next text context for emotion inference (max 2000 characters).
    /// </summary>
    [JsonPropertyName("next_text")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? NextText { get; set; }

    /// <summary>
    /// Creates a new SmartPrompt with default values.
    /// </summary>
    public SmartPrompt() { }

    /// <summary>
    /// Creates a new SmartPrompt with specified context.
    /// </summary>
    /// <param name="previousText">Text that comes before the current text</param>
    /// <param name="nextText">Text that comes after the current text</param>
    public SmartPrompt(string? previousText = null, string? nextText = null)
    {
        PreviousText = previousText;
        NextText = nextText;
    }

    /// <inheritdoc />
    public void Validate()
    {
        if (PreviousText != null && PreviousText.Length > MaxContextLength)
        {
            throw new ArgumentException($"Previous text must not exceed {MaxContextLength} characters.", nameof(PreviousText));
        }

        if (NextText != null && NextText.Length > MaxContextLength)
        {
            throw new ArgumentException($"Next text must not exceed {MaxContextLength} characters.", nameof(NextText));
        }
    }
}
