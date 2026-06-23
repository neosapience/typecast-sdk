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

        _apiHost = config.GetEffectiveApiHost().TrimEnd('/');
        var apiKey = config.GetEffectiveApiKey(_apiHost);

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
        if (!string.IsNullOrWhiteSpace(apiKey))
        {
            _httpClient.DefaultRequestHeaders.Add("X-API-KEY", apiKey);
        }
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd(BuildUserAgent(config));
        _httpClient.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
    }

    private string BuildUserAgent(TypecastClientConfig config)
    {
        var version = typeof(TypecastClient).Assembly.GetName().Version!.ToString(3);
        var baseKind = string.Equals(
            _apiHost.TrimEnd('/'),
            TypecastClientConfig.DefaultApiHost,
            StringComparison.OrdinalIgnoreCase)
            ? "default"
            : "custom";
        var timeout = config.TimeoutSeconds == 30 ? "default" : $"{config.TimeoutSeconds}s";
        return $"typecast-csharp/{version} dotnet/{Environment.Version.Major}.{Environment.Version.Minor} HttpClient (tfm={TargetFramework}; base={baseKind}; timeout={timeout})";
    }

    private static string TargetFramework
    {
        get
        {
#if NET10_0
            return "net10.0";
#elif NET8_0
            return "net8.0";
#elif NET6_0
            return "net6.0";
#elif NETSTANDARD2_1
            return "netstandard2.1";
#else
            return "netstandard2.0";
#endif
        }
    }

    #region Text-to-Speech with Timestamps

    /// <summary>
    /// Synthesizes text to speech and returns audio together with word/character
    /// alignment timestamps, enabling subtitle generation.
    /// </summary>
    /// <param name="request">The TTS request.</param>
    /// <param name="granularity">
    /// Alignment granularity: <c>"word"</c>, <c>"char"</c>, or <c>"both"</c>.
    /// When <see langword="null"/> the API returns its default (typically both).
    /// </param>
    /// <param name="ct">Cancellation token.</param>
    /// <returns>
    /// A <see cref="TTSWithTimestampsResponse"/> containing base64-encoded audio
    /// and alignment segment lists.
    /// </returns>
    /// <exception cref="ArgumentNullException">Thrown when <paramref name="request"/> is null.</exception>
    /// <exception cref="ArgumentException">Thrown when <paramref name="granularity"/> is not one of the accepted values.</exception>
    /// <exception cref="TypecastException">Thrown when the API returns an error.</exception>
    public async Task<TTSWithTimestampsResponse> TextToSpeechWithTimestampsAsync(
        TTSRequestWithTimestamps request,
        string? granularity = null,
        CancellationToken ct = default)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));
        request.Validate();

        if (granularity != null)
        {
            var allowed = new[] { "word", "char", "both" };
            if (!Array.Exists(allowed, g => g == granularity))
            {
                throw new ArgumentException(
                    $"Invalid granularity '{granularity}'. Must be one of: word, char, both.",
                    nameof(granularity));
            }
        }

        var url = $"{_apiHost}/v1/text-to-speech/with-timestamps";
        if (granularity != null)
            url += $"?granularity={Uri.EscapeDataString(granularity)}";

        var json = SerializeTimestampRequest(request);
        using var content = new StringContent(json, Encoding.UTF8, "application/json");
        using var response = await _httpClient.PostAsync(url, content, ct).ConfigureAwait(false);

        return await HandleJsonResponseAsync<TTSWithTimestampsResponse>(response, ct).ConfigureAwait(false);
    }

    #endregion

    #region Text-to-Speech

    /// <summary>
    /// Creates a composed speech builder for multi-speaker audio and explicit pauses.
    /// Use <see cref="SpeechComposer.Defaults"/> for shared options, then chain
    /// <see cref="SpeechComposer.Say"/> and <see cref="SpeechComposer.Pause"/>.
    /// Each <c>Say</c> call may override voice, pitch, tempo, prompt, seed, and other
    /// TTS options for that segment. Internal segment requests are generated as WAV
    /// so the SDK can trim leading/trailing silence and concatenate PCM safely.
    /// </summary>
    public SpeechComposer ComposeSpeech()
    {
        return new SpeechComposer(this);
    }

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
        if (contentType is not null && contentType.Length > 0)
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

    /// <summary>
    /// Synthesizes text to speech and saves the audio to a file.
    /// </summary>
    public async Task<TTSResponse> GenerateToFileAsync(
        string filePath,
        GenerateToFileRequest request,
        CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(filePath))
        {
            throw new ArgumentException("File path is required.", nameof(filePath));
        }
        if (request == null) throw new ArgumentNullException(nameof(request));

        var response = await TextToSpeechAsync(request.ToTTSRequest(filePath), cancellationToken).ConfigureAwait(false);
        await response.SaveToFileAsync(filePath, cancellationToken).ConfigureAwait(false);
        return response;
    }

    /// <summary>
    /// Synthesizes text to speech and saves the audio to a file synchronously.
    /// </summary>
    public TTSResponse GenerateToFile(string filePath, GenerateToFileRequest request)
    {
        return GenerateToFileAsync(filePath, request).GetAwaiter().GetResult();
    }

    /// <summary>
    /// Synthesizes text to speech and returns the response body as a readable
    /// stream from <c>POST /v1/text-to-speech/stream</c>.
    /// </summary>
    /// <param name="request">The streaming TTS request. Uses <see cref="OutputStream"/>
    /// which omits <c>volume</c>.</param>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>
    /// A <see cref="Stream"/> containing the audio bytes as they arrive from the
    /// server. The caller is responsible for disposing the returned stream, which
    /// will release the underlying HTTP response.
    /// </returns>
    /// <exception cref="TypecastException">Thrown when the API returns an error</exception>
    public async Task<Stream> TextToSpeechStreamAsync(TTSRequestStream request, CancellationToken cancellationToken = default)
    {
        if (request == null) throw new ArgumentNullException(nameof(request));
        request.Validate();

        var url = $"{_apiHost}/v1/text-to-speech/stream";
        var json = SerializeStreamRequest(request);

        var httpRequest = new HttpRequestMessage(HttpMethod.Post, url)
        {
            Content = new StringContent(json, Encoding.UTF8, "application/json")
        };

        var response = await _httpClient
            .SendAsync(httpRequest, HttpCompletionOption.ResponseHeadersRead, cancellationToken)
            .ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
        {
            string errorBody;
            try
            {
                errorBody = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
            }
            finally
            {
                response.Dispose();
                httpRequest.Dispose();
            }
            throw TypecastException.FromStatusCode((int)response.StatusCode, errorBody);
        }

        // Caller owns the returned Stream; disposing it will dispose the
        // underlying HttpResponseMessage / connection.
        return await response.Content.ReadAsStreamAsync().ConfigureAwait(false);
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

    #region Subscription

    /// <summary>
    /// Gets the authenticated user's current subscription.
    /// </summary>
    /// <param name="cancellationToken">Cancellation token</param>
    /// <returns>The subscription response containing plan, credits, and limits</returns>
    /// <exception cref="TypecastException">Thrown when the API returns an error</exception>
    public async Task<SubscriptionResponse> GetMySubscriptionAsync(CancellationToken cancellationToken = default)
    {
        var url = $"{_apiHost}/v1/users/me/subscription";

        using var response = await _httpClient.GetAsync(url, cancellationToken).ConfigureAwait(false);
        return await HandleJsonResponseAsync<SubscriptionResponse>(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Gets the authenticated user's current subscription synchronously.
    /// </summary>
    /// <returns>The subscription response containing plan, credits, and limits</returns>
    /// <exception cref="TypecastException">Thrown when the API returns an error</exception>
    public SubscriptionResponse GetMySubscription()
    {
        return GetMySubscriptionAsync().GetAwaiter().GetResult();
    }

    #endregion

    #region Instant cloning

    /// <summary>
    /// Clones a voice from an audio sample via POST /v1/voices/clone.
    /// The returned <see cref="CustomVoice.VoiceId"/> has the "uc_" prefix and
    /// can be passed directly as <c>voice_id</c> in <see cref="TextToSpeechAsync"/>.
    /// </summary>
    /// <param name="audio">Raw audio bytes (WAV or MP3). Must not exceed 25 MB.</param>
    /// <param name="filename">
    /// File name including extension (e.g., "sample.wav"). Used to set the
    /// Content-Type of the file part in the multipart body.
    /// </param>
    /// <param name="name">Display name for the cloned voice (1–30 characters).</param>
    /// <param name="model">TTS model string, e.g., "ssfm-v30".</param>
    /// <param name="cancellationToken">Cancellation token.</param>
    /// <returns>Metadata of the newly created custom voice.</returns>
    /// <exception cref="ArgumentException">
    /// Thrown when <paramref name="audio"/> exceeds 25 MB or <paramref name="name"/>
    /// is outside the 1–30 character range.
    /// </exception>
    /// <exception cref="TypecastException">Thrown when the API returns an error.</exception>
    public async Task<CustomVoice> CloneVoiceAsync(
        byte[] audio,
        string filename,
        string name,
        string model,
        CancellationToken cancellationToken = default)
    {
        if (name.Length < QuickCloningLimits.NameMinLength || name.Length > QuickCloningLimits.NameMaxLength)
            throw new ArgumentException(
                $"name must be {QuickCloningLimits.NameMinLength}–{QuickCloningLimits.NameMaxLength} characters; got {name.Length}.",
                nameof(name));

        if (audio.LongLength > QuickCloningLimits.CloningMaxFileSize)
            throw new ArgumentException(
                $"audio file exceeds the 25 MB limit; got {audio.LongLength} bytes.",
                nameof(audio));

        var content = new MultipartFormDataContent();
        content.Add(new StringContent(name), "name");
        content.Add(new StringContent(model), "model");

        var fileContent = new ByteArrayContent(audio);
        fileContent.Headers.ContentType = new MediaTypeHeaderValue(GuessAudioMime(filename));
        content.Add(fileContent, "file", filename);

        var url = $"{_apiHost}/v1/voices/clone";
        // Note: do not dispose content before the response is read; let the
        // HttpResponseMessage lifetime manage it (HttpClient disposes the
        // request content after sending).
        using var response = await _httpClient.PostAsync(url, content, cancellationToken).ConfigureAwait(false);
        return await HandleJsonResponseAsync<CustomVoice>(response, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Clones a voice from a local audio file via POST /v1/voices/clone.
    /// </summary>
    /// <param name="audioFile">Path to the audio file (WAV or MP3). Must not exceed 25 MB.</param>
    /// <param name="name">Display name for the cloned voice (1–30 characters).</param>
    /// <param name="model">TTS model string, e.g., "ssfm-v30".</param>
    /// <param name="cancellationToken">Cancellation token.</param>
    /// <returns>Metadata of the newly created custom voice.</returns>
    public async Task<CustomVoice> CloneVoiceAsync(
        string audioFile,
        string name,
        string model,
        CancellationToken cancellationToken = default)
    {
#if NETSTANDARD2_0
        cancellationToken.ThrowIfCancellationRequested();
        var audio = File.ReadAllBytes(audioFile);
#else
        var audio = await File.ReadAllBytesAsync(audioFile, cancellationToken).ConfigureAwait(false);
#endif
        return await CloneVoiceAsync(audio, Path.GetFileName(audioFile), name, model, cancellationToken)
            .ConfigureAwait(false);
    }

    /// <summary>
    /// Deletes a custom voice via DELETE /v1/voices/{voiceId}.
    /// </summary>
    /// <param name="voiceId">The custom voice ID to delete (e.g., "uc_abc123").</param>
    /// <param name="cancellationToken">Cancellation token.</param>
    /// <exception cref="TypecastException">Thrown when the API returns an error.</exception>
    public async Task DeleteVoiceAsync(string voiceId, CancellationToken cancellationToken = default)
    {
        var url = $"{_apiHost}/v1/voices/{Uri.EscapeDataString(voiceId)}";
        using var response = await _httpClient.DeleteAsync(url, cancellationToken).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
        {
            var errorBody = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
            throw TypecastException.FromStatusCode((int)response.StatusCode, errorBody);
        }
    }

    private static string GuessAudioMime(string filename)
    {
        var lower = filename.ToLowerInvariant();
        if (lower.EndsWith(".wav", StringComparison.Ordinal)) return "audio/wav";
        if (lower.EndsWith(".mp3", StringComparison.Ordinal)) return "audio/mpeg";
        return "application/octet-stream";
    }

    #endregion

    #region Helper Methods

    private string SerializeTimestampRequest(TTSRequestWithTimestamps request)
    {
        var dict = new Dictionary<string, object>
        {
            ["text"] = request.Text,
            ["voice_id"] = request.VoiceId,
            ["model"] = request.Model.ToApiString()
        };

        if (request.Language.HasValue)
            dict["language"] = request.Language.Value.ToApiString();

        if (request.Prompt != null)
            dict["prompt"] = SerializePrompt(request.Prompt);

        if (request.Output != null)
        {
            var outputDict = new Dictionary<string, object>();
            if (request.Output.Volume.HasValue)
                outputDict["volume"] = request.Output.Volume.Value;
            if (request.Output.TargetLufs.HasValue)
                outputDict["target_lufs"] = request.Output.TargetLufs.Value;
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
            dict["seed"] = request.Seed.Value;

        return JsonSerializer.Serialize(dict, JsonOptions);
    }

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
            if (request.Output.TargetLufs.HasValue)
                outputDict["target_lufs"] = request.Output.TargetLufs.Value;
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

    private string SerializeStreamRequest(TTSRequestStream request)
    {
        // Build the request object manually to handle prompt polymorphism.
        // OutputStream omits volume by design.
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
            if (request.Output.TargetLufs.HasValue)
                outputDict["target_lufs"] = request.Output.TargetLufs.Value;
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
                if (smart.PreviousText is { Length: > 0 } previousText)
                    result["previous_text"] = previousText;
                if (smart.NextText is { Length: > 0 } nextText)
                    result["next_text"] = nextText;
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
    /// <param name="disposing">Whether to dispose managed resources.
    /// This class has no finalizer so <paramref name="disposing"/> is
    /// always <c>true</c> in practice; the parameter is kept for the
    /// standard dispose pattern in case derived classes add one.</param>
    [System.Diagnostics.CodeAnalysis.SuppressMessage("Usage", "CA1816", Justification = "Standard dispose pattern.")]
    protected virtual void Dispose(bool disposing)
    {
        if (_disposed) return;

        if (_ownsHttpClient)
        {
            _httpClient.Dispose();
        }
        _disposed = true;
    }

    #endregion
}
