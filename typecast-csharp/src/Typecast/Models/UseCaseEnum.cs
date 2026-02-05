using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Use case classification for voice filtering.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum UseCaseEnum
{
    /// <summary>Education and learning content</summary>
    [JsonPropertyName("education")] Education,
    
    /// <summary>Gaming and entertainment</summary>
    [JsonPropertyName("game")] Game,
    
    /// <summary>General purpose</summary>
    [JsonPropertyName("general")] General,
    
    /// <summary>News and broadcasting</summary>
    [JsonPropertyName("news")] News,
    
    /// <summary>Documentary narration</summary>
    [JsonPropertyName("documentary")] Documentary,
    
    /// <summary>Audiobook narration</summary>
    [JsonPropertyName("audiobook")] Audiobook,
    
    /// <summary>Conversational/chatbot</summary>
    [JsonPropertyName("conversational")] Conversational
}

/// <summary>
/// Extension methods for UseCaseEnum.
/// </summary>
public static class UseCaseEnumExtensions
{
    /// <summary>
    /// Converts the UseCaseEnum to its API string representation.
    /// </summary>
    public static string ToApiString(this UseCaseEnum useCase) => useCase switch
    {
        UseCaseEnum.Education => "education",
        UseCaseEnum.Game => "game",
        UseCaseEnum.General => "general",
        UseCaseEnum.News => "news",
        UseCaseEnum.Documentary => "documentary",
        UseCaseEnum.Audiobook => "audiobook",
        UseCaseEnum.Conversational => "conversational",
        _ => throw new ArgumentOutOfRangeException(nameof(useCase))
    };

    /// <summary>
    /// Parses a string to UseCaseEnum.
    /// </summary>
    public static UseCaseEnum ParseUseCase(string value) => value.ToLowerInvariant() switch
    {
        "education" => UseCaseEnum.Education,
        "game" => UseCaseEnum.Game,
        "general" => UseCaseEnum.General,
        "news" => UseCaseEnum.News,
        "documentary" => UseCaseEnum.Documentary,
        "audiobook" => UseCaseEnum.Audiobook,
        "conversational" => UseCaseEnum.Conversational,
        _ => throw new ArgumentException($"Unknown use case: {value}", nameof(value))
    };
}
