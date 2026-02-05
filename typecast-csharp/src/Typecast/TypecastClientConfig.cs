namespace Typecast;

/// <summary>
/// Configuration options for the Typecast client.
/// </summary>
public class TypecastClientConfig
{
    /// <summary>
    /// Default API host URL.
    /// </summary>
    public const string DefaultApiHost = "https://api.typecast.ai";

    /// <summary>
    /// Environment variable name for API key.
    /// </summary>
    public const string ApiKeyEnvVar = "TYPECAST_API_KEY";

    /// <summary>
    /// Environment variable name for API host.
    /// </summary>
    public const string ApiHostEnvVar = "TYPECAST_API_HOST";

    /// <summary>
    /// The Typecast API key.
    /// If not provided, will be read from TYPECAST_API_KEY environment variable.
    /// </summary>
    public string? ApiKey { get; set; }

    /// <summary>
    /// The Typecast API host URL.
    /// If not provided, will be read from TYPECAST_API_HOST environment variable or use the default.
    /// </summary>
    public string? ApiHost { get; set; }

    /// <summary>
    /// HTTP timeout in seconds. Default is 30 seconds.
    /// </summary>
    public int TimeoutSeconds { get; set; } = 30;

    /// <summary>
    /// Custom HttpClient to use for requests.
    /// If not provided, a new HttpClient will be created.
    /// </summary>
    public HttpClient? HttpClient { get; set; }

    /// <summary>
    /// Gets the effective API key, falling back to environment variable if not set.
    /// </summary>
    /// <returns>The API key</returns>
    /// <exception cref="InvalidOperationException">Thrown when no API key is configured</exception>
    public string GetEffectiveApiKey()
    {
        var apiKey = ApiKey ?? Environment.GetEnvironmentVariable(ApiKeyEnvVar);
        
        if (string.IsNullOrWhiteSpace(apiKey))
        {
            throw new InvalidOperationException(
                $"API key is required. Set the '{ApiKeyEnvVar}' environment variable or provide an API key in the configuration.");
        }

        return apiKey;
    }

    /// <summary>
    /// Gets the effective API host, falling back to environment variable or default if not set.
    /// </summary>
    /// <returns>The API host URL</returns>
    public string GetEffectiveApiHost()
    {
        return ApiHost 
               ?? Environment.GetEnvironmentVariable(ApiHostEnvVar) 
               ?? DefaultApiHost;
    }
}
