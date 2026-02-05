using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Model information for a voice.
/// </summary>
public class ModelInfo
{
    /// <summary>
    /// The model version (e.g., "ssfm-v21", "ssfm-v30").
    /// </summary>
    [JsonPropertyName("version")]
    public string Version { get; set; } = string.Empty;

    /// <summary>
    /// List of emotions supported by this voice for this model.
    /// </summary>
    [JsonPropertyName("emotions")]
    public List<string> Emotions { get; set; } = new();
}

/// <summary>
/// Voice response from V1 API (deprecated).
/// </summary>
public class VoicesResponse
{
    /// <summary>
    /// The unique voice identifier.
    /// </summary>
    [JsonPropertyName("voice_id")]
    public string VoiceId { get; set; } = string.Empty;

    /// <summary>
    /// The display name of the voice.
    /// </summary>
    [JsonPropertyName("voice_name")]
    public string VoiceName { get; set; } = string.Empty;

    /// <summary>
    /// The TTS model this voice supports.
    /// </summary>
    [JsonPropertyName("model")]
    public string Model { get; set; } = string.Empty;

    /// <summary>
    /// List of emotions supported by this voice.
    /// </summary>
    [JsonPropertyName("emotions")]
    public List<string> Emotions { get; set; } = new();
}

/// <summary>
/// Voice response from V2 API with enhanced metadata.
/// </summary>
public class VoiceV2Response
{
    /// <summary>
    /// The unique voice identifier.
    /// </summary>
    [JsonPropertyName("voice_id")]
    public string VoiceId { get; set; } = string.Empty;

    /// <summary>
    /// The display name of the voice.
    /// </summary>
    [JsonPropertyName("voice_name")]
    public string VoiceName { get; set; } = string.Empty;

    /// <summary>
    /// List of models this voice supports with their emotions.
    /// </summary>
    [JsonPropertyName("models")]
    public List<ModelInfo> Models { get; set; } = new();

    /// <summary>
    /// The gender of the voice (optional).
    /// </summary>
    [JsonPropertyName("gender")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public GenderEnum? Gender { get; set; }

    /// <summary>
    /// The age category of the voice (optional).
    /// </summary>
    [JsonPropertyName("age")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public AgeEnum? Age { get; set; }

    /// <summary>
    /// Recommended use cases for this voice (optional).
    /// </summary>
    [JsonPropertyName("use_cases")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public List<string>? UseCases { get; set; }
}

/// <summary>
/// Filter options for V2 voices API.
/// </summary>
public class VoicesV2Filter
{
    /// <summary>
    /// Filter by TTS model.
    /// </summary>
    public TTSModel? Model { get; set; }

    /// <summary>
    /// Filter by gender.
    /// </summary>
    public GenderEnum? Gender { get; set; }

    /// <summary>
    /// Filter by age category.
    /// </summary>
    public AgeEnum? Age { get; set; }

    /// <summary>
    /// Filter by use case.
    /// </summary>
    public UseCaseEnum? UseCase { get; set; }

    /// <summary>
    /// Converts the filter to query parameters.
    /// </summary>
    public Dictionary<string, string> ToQueryParameters()
    {
        var parameters = new Dictionary<string, string>();

        if (Model.HasValue)
        {
            parameters["model"] = Model.Value.ToApiString();
        }

        if (Gender.HasValue)
        {
            parameters["gender"] = Gender.Value.ToApiString();
        }

        if (Age.HasValue)
        {
            parameters["age"] = Age.Value.ToApiString();
        }

        if (UseCase.HasValue)
        {
            parameters["use_cases"] = UseCase.Value.ToApiString();
        }

        return parameters;
    }
}
