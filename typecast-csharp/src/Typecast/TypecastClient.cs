using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Typecast.Exceptions;
using Typecast.Models;

namespace Typecast;

/// <summary>
/// Client for interacting with the Typecast Text-to-Speech API.
/// </summary>
public class TypecastClient : IDisposable
{
    private readonly HttpClient _httpClient;
    private readonly string _apiHost;
    private readonly bool _ownsHttpClient;
    private bool _disposed;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Converters = { new JsonStringEnumMemberConverter() }
    };

    /// <summary>
    /// Creates a new TypecastClient using environment variables for configuration.
    /// </summary>
    public TypecastClient() : this(new TypecastClientConfig())
    {
    }

    /// <summary>
    /// Creates a new TypecastClient with the specified API key.
    /// </summary>
    /// <param name="apiKey">The Typecast API key</param>
    public TypecastClient(string apiKey) : this(new TypecastClientConfig { ApiKey = apiKey })
    {
    }

    /// <summary>
    /// Creates a new TypecastClient with the specified configuration.
    /// </summary>
    /// <param name="config">The client configuration</param>
    public TypecastClient(TypecastClientConfig config)
    {
        if (config == null) throw new ArgumentNullException(nameof(config));

        var apiKey = config.GetEffectiveApiKey();
        _apiHost = config.GetEffectiveApiHost().TrimEnd('/');

        if (config.HttpClient != null)
        {
            _httpClient = config.HttpClient;
            _ownsHttpClient = false;
        }
        else
        {
            _httpClient = new HttpClient
            {
                Timeout = TimeSpan.FromSeconds(config.TimeoutSeconds)
            };
            _ownsHttpClient = true;
        }

        _httpClient.DefaultRequestHeaders.Clear();
        _httpClient.DefaultRequestHeaders.Add("X-API-KEY", apiKey);
        _httpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    }

    #region Text-to-Speech

    /// <summary>
    /// Synthesizes text to speech.
    /// </summary>
    /// <param name="request">The TTS request</param>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>The TTS response containing audio data</returns>
    /// <exception cref="TypecastException">Thrown when the API returns an error</exception>
    public async Task<TTSResponse> TextToSpeechAsync(TTSRequest request, CancellationToken cancellationToken = default)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));
        request.Validate();

        var url = $"{_apiHost}/v1/text-to-speech";
        var json = SerializeRequest(request);
        
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var response = await _httpClient.PostAsync(url, content, cancellationToken).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
        {
            var errorBody = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
            throw TypecastException.FromStatusCode((int)response.StatusCode, errorBody);
        }

        var audioData = await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
        
        var duration = 0.0;
        if (response.Headers.TryGetValues("x-audio-duration", out var durationValues))
        {
            var durationStr = durationValues.FirstOrDefault();
            if (!string.IsNullOrEmpty(durationStr))
            {
                double.TryParse(durationStr, out duration);
            }
        }

        var format = AudioFormat.Wav;
        var contentType = response.Content.Headers.ContentType?.MediaType;
        if (!string.IsNullOrEmpty(contentType))
        {
            try
            {
                format = AudioFormatExtensions.ParseFromContentType(contentType);
            }
            catch
            {
                // Default to wav if parsing fails
            }
        }

        return new TTSResponse(audioData, duration, format);
    }

    /// <summary>
    /// Synthesizes text to speech synchronously.
    /// </summary>
    /// <param name="request">The TTS request</param>
    /// <returns>The TTS response containing audio data</returns>
    /// <exception cref="TypecastException">Thrown when the API returns an error</exception>
    public TTSResponse TextToSpeech(TTSRequest request)
    {
        return TextToSpeechAsync(request).GetAwaiter().GetResult();
    }

    #endregion

    #region Voices V1 (Deprecated)

    /// <summary>
    /// Gets available voices (V1 API - deprecated, use GetVoicesV2Async instead).
    /// </summary>
    /// <param name="model">Optional model filter</param>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>List of available voices</returns>
    [Obsolete("Use GetVoicesV2Async instead for enhanced voice metadata")]
    public async Task<List<VoicesResponse>> GetVoicesAsync(string? model = null, CancellationToken cancellationToken = default)
    {
        var url = $"{_apiHost}/v1/voices";
        if (!string.IsNullOrEmpty(model))
        {
            url += $"?model={Uri.EscapeDataString(model)}";
        }

        using var response = await _httpClient.GetAsync(url, cancellationToken).ConfigureAwait(false);
        return await HandleJsonResponseAsync<List<VoicesResponse>>(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Gets a voice by ID (V1 API - deprecated, use GetVoiceV2Async instead).
    /// </summary>
    /// <param name="voiceId">The voice ID</param>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>The voice information</returns>
    [Obsolete("Use GetVoiceV2Async instead for enhanced voice metadata")]
    public async Task<VoicesResponse> GetVoiceAsync(string voiceId, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(voiceId)) throw new ArgumentException("Voice ID is required", nameof(voiceId));

        var url = $"{_apiHost}/v1/voices/{Uri.EscapeDataString(voiceId)}";

        using var response = await _httpClient.GetAsync(url, cancellationToken).ConfigureAwait(false);
        return await HandleJsonResponseAsync<VoicesResponse>(response, cancellationToken).ConfigureAwait(false);
    }

    #endregion

    #region Voices V2

    /// <summary>
    /// Gets available voices with enhanced metadata (V2 API).
    /// </summary>
    /// <param name="filter">Optional filter options</param>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>List of available voices with metadata</returns>
    public async Task<List<VoiceV2Response>> GetVoicesV2Async(VoicesV2Filter? filter = null, CancellationToken cancellationToken = default)
    {
        var url = $"{_apiHost}/v2/voices";
        
        if (filter != null)
        {
            var queryParams = filter.ToQueryParameters();
            if (queryParams.Count > 0)
            {
                var queryString = string.Join("&", queryParams.Select(kvp => 
                    $"{Uri.EscapeDataString(kvp.Key)}={Uri.EscapeDataString(kvp.Value)}"));
                url += $"?{queryString}";
            }
        }

        using var response = await _httpClient.GetAsync(url, cancellationToken).ConfigureAwait(false);
        return await HandleJsonResponseAsync<List<VoiceV2Response>>(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Gets available voices with enhanced metadata synchronously (V2 API).
    /// </summary>
    /// <param name="filter">Optional filter options</param>
    /// <returns>List of available voices with metadata</returns>
    public List<VoiceV2Response> GetVoicesV2(VoicesV2Filter? filter = null)
    {
        return GetVoicesV2Async(filter).GetAwaiter().GetResult();
    }

    /// <summary>
    /// Gets a voice by ID with enhanced metadata (V2 API).
    /// </summary>
    /// <param name="voiceId">The voice ID</param>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>The voice information with metadata</returns>
    public async Task<VoiceV2Response> GetVoiceV2Async(string voiceId, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(voiceId)) throw new ArgumentException("Voice ID is required", nameof(voiceId));

        var url = $"{_apiHost}/v2/voices/{Uri.EscapeDataString(voiceId)}";

        using var response = await _httpClient.GetAsync(url, cancellationToken).ConfigureAwait(false);
        return await HandleJsonResponseAsync<VoiceV2Response>(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Gets a voice by ID with enhanced metadata synchronously (V2 API).
    /// </summary>
    /// <param name="voiceId">The voice ID</param>
    /// <returns>The voice information with metadata</returns>
    public VoiceV2Response GetVoiceV2(string voiceId)
    {
        return GetVoiceV2Async(voiceId).GetAwaiter().GetResult();
    }

    #endregion

    #region Helper Methods

    private string SerializeRequest(TTSRequest request)
    {
        // Build the request object manually to handle the prompt polymorphism
        var dict = new Dictionary<string, object>
        {
            ["text"] = request.Text,
            ["voice_id"] = request.VoiceId,
            ["model"] = request.Model.ToApiString()
        };

        if (request.Language.HasValue)
        {
            dict["language"] = request.Language.Value.ToApiString();
        }

        if (request.Prompt != null)
        {
            dict["prompt"] = SerializePrompt(request.Prompt);
        }

        if (request.Output != null)
        {
            var outputDict = new Dictionary<string, object>();
            if (request.Output.Volume.HasValue)
                outputDict["volume"] = request.Output.Volume.Value;
            if (request.Output.AudioPitch.HasValue)
                outputDict["audio_pitch"] = request.Output.AudioPitch.Value;
            if (request.Output.AudioTempo.HasValue)
                outputDict["audio_tempo"] = request.Output.AudioTempo.Value;
            if (request.Output.AudioFormat.HasValue)
                outputDict["audio_format"] = request.Output.AudioFormat.Value.ToApiString();
            
            if (outputDict.Count > 0)
                dict["output"] = outputDict;
        }

        if (request.Seed.HasValue)
        {
            dict["seed"] = request.Seed.Value;
        }

        return JsonSerializer.Serialize(dict, JsonOptions);
    }

    private static Dictionary<string, object> SerializePrompt(ITTSPrompt prompt)
    {
        var result = new Dictionary<string, object>();

        switch (prompt)
        {
            case SmartPrompt smart:
                result["emotion_type"] = "smart";
                if (!string.IsNullOrEmpty(smart.PreviousText))
                    result["previous_text"] = smart.PreviousText;
                if (!string.IsNullOrEmpty(smart.NextText))
                    result["next_text"] = smart.NextText;
                break;

            case PresetPrompt preset:
                result["emotion_type"] = "preset";
                if (preset.EmotionPreset.HasValue)
                    result["emotion_preset"] = preset.EmotionPreset.Value.ToApiString();
                if (preset.EmotionIntensity.HasValue)
                    result["emotion_intensity"] = preset.EmotionIntensity.Value;
                break;

            case Prompt basic:
                if (basic.EmotionPreset.HasValue)
                    result["emotion_preset"] = basic.EmotionPreset.Value.ToApiString();
                if (basic.EmotionIntensity.HasValue)
                    result["emotion_intensity"] = basic.EmotionIntensity.Value;
                break;
        }

        return result;
    }

    private async Task<T> HandleJsonResponseAsync<T>(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        var content = await response.Content.ReadAsStringAsync().ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
        {
            throw TypecastException.FromStatusCode((int)response.StatusCode, content);
        }

        try
        {
            var result = JsonSerializer.Deserialize<T>(content, JsonOptions);
            if (result == null)
            {
                throw new TypecastException("Failed to deserialize response", (int)response.StatusCode, content);
            }
            return result;
        }
        catch (JsonException ex)
        {
            throw new TypecastException($"Failed to parse response: {ex.Message}", (int)response.StatusCode, content, ex);
        }
    }

    #endregion

    #region IDisposable

    /// <summary>
    /// Disposes the client and releases resources.
    /// </summary>
    public void Dispose()
    {
        Dispose(true);
        GC.SuppressFinalize(this);
    }

    /// <summary>
    /// Disposes the client and releases resources.
    /// </summary>
    /// <param name="disposing">Whether to dispose managed resources</param>
    protected virtual void Dispose(bool disposing)
    {
        if (!_disposed)
        {
            if (disposing && _ownsHttpClient)
            {
                _httpClient.Dispose();
            }
            _disposed = true;
        }
    }

    #endregion
}
