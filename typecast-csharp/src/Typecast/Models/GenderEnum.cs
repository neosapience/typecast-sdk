using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Gender classification for voice filtering.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum GenderEnum
{
    /// <summary>Male voice</summary>
    [JsonPropertyName("male")] Male,
    
    /// <summary>Female voice</summary>
    [JsonPropertyName("female")] Female
}

/// <summary>
/// Extension methods for GenderEnum.
/// </summary>
public static class GenderEnumExtensions
{
    /// <summary>
    /// Converts the GenderEnum to its API string representation.
    /// </summary>
    public static string ToApiString(this GenderEnum gender) => gender switch
    {
        GenderEnum.Male => "male",
        GenderEnum.Female => "female",
        _ => throw new ArgumentOutOfRangeException(nameof(gender))
    };

    /// <summary>
    /// Parses a string to GenderEnum.
    /// </summary>
    public static GenderEnum ParseGender(string value) => value.ToLowerInvariant() switch
    {
        "male" => GenderEnum.Male,
        "female" => GenderEnum.Female,
        _ => throw new ArgumentException($"Unknown gender: {value}", nameof(value))
    };
}
