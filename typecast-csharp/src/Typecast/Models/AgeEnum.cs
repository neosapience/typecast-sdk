using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Age classification for voice filtering.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum AgeEnum
{
    /// <summary>Child voice (approximately 0-12 years)</summary>
    [JsonPropertyName("child")] Child,
    
    /// <summary>Teenager voice (approximately 13-19 years)</summary>
    [JsonPropertyName("teenager")] Teenager,
    
    /// <summary>Young adult voice (approximately 20-35 years)</summary>
    [JsonPropertyName("young_adult")] YoungAdult,
    
    /// <summary>Middle-aged voice (approximately 36-55 years)</summary>
    [JsonPropertyName("middle_age")] MiddleAge,
    
    /// <summary>Elder voice (approximately 56+ years)</summary>
    [JsonPropertyName("elder")] Elder
}

/// <summary>
/// Extension methods for AgeEnum.
/// </summary>
public static class AgeEnumExtensions
{
    /// <summary>
    /// Converts the AgeEnum to its API string representation.
    /// </summary>
    public static string ToApiString(this AgeEnum age) => age switch
    {
        AgeEnum.Child => "child",
        AgeEnum.Teenager => "teenager",
        AgeEnum.YoungAdult => "young_adult",
        AgeEnum.MiddleAge => "middle_age",
        AgeEnum.Elder => "elder",
        _ => throw new ArgumentOutOfRangeException(nameof(age))
    };

    /// <summary>
    /// Parses a string to AgeEnum.
    /// </summary>
    public static AgeEnum ParseAge(string value) => value.ToLowerInvariant() switch
    {
        "child" => AgeEnum.Child,
        "teenager" => AgeEnum.Teenager,
        "young_adult" => AgeEnum.YoungAdult,
        "middle_age" => AgeEnum.MiddleAge,
        "elder" => AgeEnum.Elder,
        _ => throw new ArgumentException($"Unknown age: {value}", nameof(value))
    };
}
