using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Subscription plan tier.
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum PlanTier
{
    /// <summary>Free plan.</summary>
    [JsonPropertyName("free")] Free,

    /// <summary>Lite plan.</summary>
    [JsonPropertyName("lite")] Lite,

    /// <summary>Plus plan.</summary>
    [JsonPropertyName("plus")] Plus,

    /// <summary>Custom plan.</summary>
    [JsonPropertyName("custom")] Custom
}

/// <summary>
/// Extension methods for <see cref="PlanTier"/>.
/// </summary>
public static class PlanTierExtensions
{
    /// <summary>
    /// Converts the <see cref="PlanTier"/> to its API string representation.
    /// </summary>
    public static string ToApiString(this PlanTier tier) => tier switch
    {
        PlanTier.Free => "free",
        PlanTier.Lite => "lite",
        PlanTier.Plus => "plus",
        PlanTier.Custom => "custom",
        _ => throw new ArgumentOutOfRangeException(nameof(tier))
    };

    /// <summary>
    /// Parses a string to <see cref="PlanTier"/>.
    /// </summary>
    public static PlanTier ParsePlanTier(string value) => value.ToLowerInvariant() switch
    {
        "free" => PlanTier.Free,
        "lite" => PlanTier.Lite,
        "plus" => PlanTier.Plus,
        "custom" => PlanTier.Custom,
        _ => throw new ArgumentException($"Unknown plan tier: {value}", nameof(value))
    };
}

/// <summary>
/// Credit usage information for the current subscription.
/// </summary>
public class Credits
{
    /// <summary>
    /// Total credits provided by the plan.
    /// </summary>
    [JsonPropertyName("plan_credits")]
    public long PlanCredits { get; set; }

    /// <summary>
    /// Number of credits used.
    /// </summary>
    [JsonPropertyName("used_credits")]
    public long UsedCredits { get; set; }
}

/// <summary>
/// Usage limit information for the current subscription.
/// </summary>
public class Limits
{
    /// <summary>
    /// Maximum number of concurrent requests allowed.
    /// </summary>
    [JsonPropertyName("concurrency_limit")]
    public int ConcurrencyLimit { get; set; }
}

/// <summary>
/// Response from <c>GET /v1/users/me/subscription</c>.
/// </summary>
public class SubscriptionResponse
{
    /// <summary>
    /// Current subscription plan tier.
    /// </summary>
    [JsonPropertyName("plan")]
    public PlanTier Plan { get; set; }

    /// <summary>
    /// Credit usage information.
    /// </summary>
    [JsonPropertyName("credits")]
    public Credits Credits { get; set; } = new();

    /// <summary>
    /// Usage limit information.
    /// </summary>
    [JsonPropertyName("limits")]
    public Limits Limits { get; set; } = new();
}
