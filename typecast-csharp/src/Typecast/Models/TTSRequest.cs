using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Request model for Text-to-Speech synthesis.
/// </summary>
public class TTSRequest
{
    private const int MaxTextLength = 5000;

    /// <summary>
    /// The text to synthesize (max 5000 characters).
    /// </summary>
    [JsonPropertyName("text")]
    public string Text { get; set; } = string.Empty;

    /// <summary>
    /// The voice ID to use for synthesis.
    /// </summary>
    [JsonPropertyName("voice_id")]
    public string VoiceId { get; set; } = string.Empty;

    /// <summary>
    /// The TTS model to use.
    /// </summary>
    [JsonPropertyName("model")]
    public TTSModel Model { get; set; }

    /// <summary>
    /// The language code for synthesis (optional, auto-detected if not specified).
    /// </summary>
    [JsonPropertyName("language")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public LanguageCode? Language { get; set; }

    /// <summary>
    /// Emotion/prompt configuration (optional).
    /// Use <see cref="Prompt"/> for ssfm-v21, or <see cref="PresetPrompt"/>/<see cref="SmartPrompt"/> for ssfm-v30.
    /// </summary>
    [JsonPropertyName("prompt")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public ITTSPrompt? Prompt { get; set; }

    /// <summary>
    /// Audio output configuration (optional).
    /// </summary>
    [JsonPropertyName("output")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public Output? Output { get; set; }

    /// <summary>
    /// Random seed for reproducible synthesis (optional).
    /// </summary>
    [JsonPropertyName("seed")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public int? Seed { get; set; }

    /// <summary>
    /// Creates a new empty TTSRequest.
    /// </summary>
    public TTSRequest() { }

    /// <summary>
    /// Creates a new TTSRequest with required parameters.
    /// </summary>
    /// <param name="text">The text to synthesize</param>
    /// <param name="voiceId">The voice ID to use</param>
    /// <param name="model">The TTS model to use</param>
    public TTSRequest(string text, string voiceId, TTSModel model)
    {
        Text = text;
        VoiceId = voiceId;
        Model = model;
    }

    /// <summary>
    /// Validates the request parameters.
    /// </summary>
    /// <exception cref="ArgumentException">Thrown when required fields are missing or invalid.</exception>
    /// <exception cref="ArgumentOutOfRangeException">Thrown when values are out of valid range.</exception>
    public void Validate()
    {
        if (string.IsNullOrWhiteSpace(Text))
        {
            throw new ArgumentException("Text is required.", nameof(Text));
        }

        if (Text.Length > MaxTextLength)
        {
            throw new ArgumentException($"Text must not exceed {MaxTextLength} characters.", nameof(Text));
        }

        if (string.IsNullOrWhiteSpace(VoiceId))
        {
            throw new ArgumentException("VoiceId is required.", nameof(VoiceId));
        }

        Prompt?.Validate();
        Output?.Validate();
    }
}
