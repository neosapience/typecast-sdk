using System.Net;
using System.Text;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class TypecastClientTests : IDisposable
{
    private readonly Mock<HttpMessageHandler> _mockHandler;
    private readonly HttpClient _httpClient;
    private readonly TypecastClient _client;

    public TypecastClientTests()
    {
        _mockHandler = new Mock<HttpMessageHandler>();
        _httpClient = new HttpClient(_mockHandler.Object);
        
        var config = new TypecastClientConfig
        {
            ApiKey = "test-api-key",
            HttpClient = _httpClient
        };
        
        _client = new TypecastClient(config);
    }

    public void Dispose()
    {
        _client.Dispose();
        _httpClient.Dispose();
    }

    [Fact]
    public void Constructor_WithApiKey_ShouldCreateClient()
    {
        // Arrange & Act
        using var client = new TypecastClient("test-key");

        // Assert - no exception thrown
        client.Should().NotBeNull();
    }

    [Fact]
    public void Constructor_WithNullConfig_ShouldThrow()
    {
        // Act & Assert
        Action act = () => new TypecastClient((TypecastClientConfig)null!);
        act.Should().Throw<ArgumentNullException>();
    }

    [Fact]
    public async Task TextToSpeechAsync_WithNullRequest_ShouldThrow()
    {
        // Act & Assert
        await Assert.ThrowsAsync<ArgumentNullException>(() => _client.TextToSpeechAsync(null!));
    }

    [Fact]
    public async Task TextToSpeechAsync_WithValidRequest_ShouldReturnResponse()
    {
        // Arrange
        var audioData = new byte[] { 1, 2, 3, 4, 5 };
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(audioData)
        };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        response.Headers.Add("x-audio-duration", "2.5");

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.Is<HttpRequestMessage>(req => req.RequestUri!.ToString().Contains("/v1/text-to-speech")),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30);

        // Act
        var result = await _client.TextToSpeechAsync(request);

        // Assert
        result.Should().NotBeNull();
        result.AudioData.Should().BeEquivalentTo(audioData);
        result.Duration.Should().Be(2.5);
        result.Format.Should().Be(AudioFormat.Wav);
    }

    [Fact]
    public async Task TextToSpeechAsync_WithMp3Response_ShouldParseFormatCorrectly()
    {
        // Arrange
        var audioData = new byte[] { 1, 2, 3 };
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(audioData)
        };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/mp3");
        response.Headers.Add("x-audio-duration", "1.0");

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var request = new TTSRequest("Test", "voice_id", TTSModel.SsfmV30);

        // Act
        var result = await _client.TextToSpeechAsync(request);

        // Assert
        result.Format.Should().Be(AudioFormat.Mp3);
    }

    [Fact]
    public async Task TextToSpeechAsync_WithError_ShouldThrowCorrectException()
    {
        // Arrange
        var response = new HttpResponseMessage(HttpStatusCode.Unauthorized)
        {
            Content = new StringContent("Invalid API key")
        };

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var request = new TTSRequest("Hello", "voice_id", TTSModel.SsfmV30);

        // Act & Assert
        await Assert.ThrowsAsync<UnauthorizedException>(() => _client.TextToSpeechAsync(request));
    }

    [Fact]
    public async Task GetVoicesV2Async_ShouldReturnVoices()
    {
        // Arrange
        var jsonResponse = @"[
            {
                ""voice_id"": ""voice_1"",
                ""voice_name"": ""Test Voice"",
                ""models"": [{ ""version"": ""ssfm-v30"", ""emotions"": [""normal"", ""happy""] }],
                ""gender"": ""female"",
                ""age"": ""young_adult""
            }
        ]";

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(jsonResponse, Encoding.UTF8, "application/json")
        };

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.Is<HttpRequestMessage>(req => req.RequestUri!.ToString().Contains("/v2/voices")),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        // Act
        var result = await _client.GetVoicesV2Async();

        // Assert
        result.Should().HaveCount(1);
        result[0].VoiceId.Should().Be("voice_1");
        result[0].VoiceName.Should().Be("Test Voice");
        result[0].Gender.Should().Be(GenderEnum.Female);
        result[0].Age.Should().Be(AgeEnum.YoungAdult);
    }

    [Fact]
    public async Task GetVoicesV2Async_WithFilter_ShouldIncludeQueryParams()
    {
        // Arrange
        var jsonResponse = "[]";
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(jsonResponse, Encoding.UTF8, "application/json")
        };

        HttpRequestMessage? capturedRequest = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) => capturedRequest = req)
            .ReturnsAsync(response);

        var filter = new VoicesV2Filter
        {
            Model = TTSModel.SsfmV30,
            Gender = GenderEnum.Male
        };

        // Act
        await _client.GetVoicesV2Async(filter);

        // Assert
        capturedRequest.Should().NotBeNull();
        capturedRequest!.RequestUri!.Query.Should().Contain("model=ssfm-v30");
        capturedRequest.RequestUri.Query.Should().Contain("gender=male");
    }

    [Fact]
    public async Task GetVoiceV2Async_ShouldReturnVoice()
    {
        // Arrange
        var jsonResponse = @"{
            ""voice_id"": ""voice_123"",
            ""voice_name"": ""Test Voice"",
            ""models"": [{ ""version"": ""ssfm-v30"", ""emotions"": [""normal""] }]
        }";

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(jsonResponse, Encoding.UTF8, "application/json")
        };

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.Is<HttpRequestMessage>(req => req.RequestUri!.ToString().Contains("/v2/voices/voice_123")),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        // Act
        var result = await _client.GetVoiceV2Async("voice_123");

        // Assert
        result.Should().NotBeNull();
        result.VoiceId.Should().Be("voice_123");
    }

    [Fact]
    public async Task GetVoiceV2Async_WithEmptyVoiceId_ShouldThrow()
    {
        // Act & Assert
        await Assert.ThrowsAsync<ArgumentException>(() => _client.GetVoiceV2Async(""));
    }

    [Fact]
    public async Task Request_ShouldIncludeApiKeyHeader()
    {
        // Arrange
        var jsonResponse = "[]";
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(jsonResponse, Encoding.UTF8, "application/json")
        };

        HttpRequestMessage? capturedRequest = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) => capturedRequest = req)
            .ReturnsAsync(response);

        // Act
        await _client.GetVoicesV2Async();

        // Assert
        capturedRequest.Should().NotBeNull();
        capturedRequest!.Headers.Should().Contain(h => h.Key == "X-API-KEY" && h.Value.Contains("test-api-key"));
    }

    [Theory]
    [InlineData(400, typeof(BadRequestException))]
    [InlineData(401, typeof(UnauthorizedException))]
    [InlineData(402, typeof(PaymentRequiredException))]
    [InlineData(404, typeof(NotFoundException))]
    [InlineData(422, typeof(UnprocessableEntityException))]
    [InlineData(429, typeof(RateLimitException))]
    [InlineData(500, typeof(InternalServerException))]
    public async Task Request_WithErrorStatus_ShouldThrowCorrectException(int statusCode, Type expectedExceptionType)
    {
        // Arrange
        var response = new HttpResponseMessage((HttpStatusCode)statusCode)
        {
            Content = new StringContent("Error message")
        };

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        // Act & Assert
        try
        {
            await _client.GetVoicesV2Async();
            Assert.Fail("Expected exception was not thrown");
        }
        catch (TypecastException ex)
        {
            ex.Should().BeOfType(expectedExceptionType);
            ex.StatusCode.Should().Be(statusCode);
        }
    }

    // ----- V1 voice methods (deprecated, but still under coverage) -----

#pragma warning disable CS0618 // suppress obsolete warnings on deprecated GetVoices/GetVoice

    [Fact]
    public async Task GetVoicesAsync_V1_ShouldReturnVoices()
    {
        var voicesJson = "[{\"voice_id\":\"v1\",\"voice_name\":\"V1\",\"model\":\"ssfm-v21\",\"emotions\":[\"normal\"]}]";
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(voicesJson, Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.Is<HttpRequestMessage>(req => req.RequestUri!.ToString().Contains("/v1/voices")),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var voices = await _client.GetVoicesAsync();

        voices.Should().HaveCount(1);
        voices[0].VoiceId.Should().Be("v1");
    }

    [Fact]
    public async Task GetVoicesAsync_V1_WithModelFilter_ShouldIncludeQuery()
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent("[]", Encoding.UTF8, "application/json")
        };
        Uri? capturedUri = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) => capturedUri = req.RequestUri)
            .ReturnsAsync(response);

        await _client.GetVoicesAsync("ssfm-v21");

        capturedUri.Should().NotBeNull();
        capturedUri!.ToString().Should().Contain("model=ssfm-v21");
    }

    [Fact]
    public async Task GetVoicesAsync_V1_OnError_ShouldThrow()
    {
        var response = new HttpResponseMessage(HttpStatusCode.Unauthorized)
        {
            Content = new StringContent("{\"error\":\"unauthorized\"}", Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        await Assert.ThrowsAsync<UnauthorizedException>(() => _client.GetVoicesAsync());
    }

    [Fact]
    public async Task GetVoiceAsync_V1_ShouldReturnVoice()
    {
        var voiceJson = "{\"voice_id\":\"v1\",\"voice_name\":\"V1\",\"model\":\"ssfm-v21\",\"emotions\":[\"normal\"]}";
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(voiceJson, Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.Is<HttpRequestMessage>(req => req.RequestUri!.ToString().Contains("/v1/voices/v1")),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var voice = await _client.GetVoiceAsync("v1");

        voice.VoiceId.Should().Be("v1");
        voice.VoiceName.Should().Be("V1");
    }

    [Fact]
    public async Task GetVoiceAsync_V1_WithEmptyVoiceId_ShouldThrow()
    {
        await Assert.ThrowsAsync<ArgumentException>(() => _client.GetVoiceAsync(""));
        await Assert.ThrowsAsync<ArgumentException>(() => _client.GetVoiceAsync("   "));
    }

    [Fact]
    public async Task GetVoiceAsync_V1_OnNotFound_ShouldThrow()
    {
        var response = new HttpResponseMessage(HttpStatusCode.NotFound)
        {
            Content = new StringContent("{\"error\":\"not found\"}", Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        await Assert.ThrowsAsync<NotFoundException>(() => _client.GetVoiceAsync("missing"));
    }

#pragma warning restore CS0618

    // ----- Sync wrapper methods + parameterless ctor + private helpers -----

    [Fact]
    public void Constructor_Parameterless_ShouldUseEnvDefaults()
    {
        // Set the API key via env so the parameterless ctor's effective config
        // doesn't fail validation.
        var prevKey = Environment.GetEnvironmentVariable("TYPECAST_API_KEY");
        try
        {
            Environment.SetEnvironmentVariable("TYPECAST_API_KEY", "env-key");
            using var client = new TypecastClient();
            client.Should().NotBeNull();
        }
        finally
        {
            Environment.SetEnvironmentVariable("TYPECAST_API_KEY", prevKey);
        }
    }

    [Fact]
    public void TextToSpeech_Sync_ShouldReturnResponse()
    {
        var audio = new byte[] { 1, 2, 3 };
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(audio)
        };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        response.Headers.Add("x-audio-duration", "1.0");

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var request = new TTSRequest
        {
            Text = "Hello",
            VoiceId = "v1",
            Model = TTSModel.SsfmV21,
        };

        var result = _client.TextToSpeech(request);
        result.AudioData.Should().HaveCount(3);
    }

    [Fact]
    public void GetVoicesV2_Sync_ShouldReturnList()
    {
        var json = "[{\"voice_id\":\"v1\",\"voice_name\":\"V1\",\"models\":[{\"version\":\"ssfm-v30\",\"emotions\":[\"normal\"]}]}]";
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(json, Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var voices = _client.GetVoicesV2();
        voices.Should().HaveCount(1);
    }

    [Fact]
    public void GetVoiceV2_Sync_ShouldReturnVoice()
    {
        var json = "{\"voice_id\":\"v1\",\"voice_name\":\"V1\",\"models\":[{\"version\":\"ssfm-v30\",\"emotions\":[\"normal\"]}]}";
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(json, Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var voice = _client.GetVoiceV2("v1");
        voice.VoiceId.Should().Be("v1");
    }

    [Fact]
    public async Task TextToSpeechAsync_WithBasicPrompt_ShouldSerializeRequest()
    {
        // Cover SerializeRequest + SerializePrompt for the Prompt branch
        string? capturedBody = null;
        var audio = new byte[] { 1 };
        var response = new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(audio) };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) =>
            {
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            })
            .ReturnsAsync(response);

        var request = new TTSRequest
        {
            Text = "Hello",
            VoiceId = "v1",
            Model = TTSModel.SsfmV21,
            Language = LanguageCode.English,
            Prompt = new Prompt { EmotionPreset = EmotionPreset.Happy, EmotionIntensity = 1.5 },
            Output = new Output(volume: 80, audioPitch: 0, audioTempo: 1.0, audioFormat: AudioFormat.Wav),
            Seed = 42,
        };

        await _client.TextToSpeechAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"text\":\"Hello\"");
        capturedBody.Should().Contain("\"voice_id\":\"v1\"");
        capturedBody.Should().Contain("\"emotion_preset\":\"happy\"");
    }

    [Fact]
    public async Task TextToSpeechAsync_WithPresetPrompt_ShouldSerializeRequest()
    {
        var audio = new byte[] { 1 };
        var response = new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(audio) };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        string? capturedBody = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) =>
            {
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            })
            .ReturnsAsync(response);

        var request = new TTSRequest
        {
            Text = "Hi",
            VoiceId = "v1",
            Model = TTSModel.SsfmV30,
            Prompt = new PresetPrompt { EmotionPreset = EmotionPreset.Sad, EmotionIntensity = 0.8 },
        };

        await _client.TextToSpeechAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"emotion_type\":\"preset\"");
    }

    [Fact]
    public async Task TextToSpeechAsync_WithSmartPrompt_ShouldSerializeRequest()
    {
        var audio = new byte[] { 1 };
        var response = new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(audio) };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        string? capturedBody = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) =>
            {
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            })
            .ReturnsAsync(response);

        var request = new TTSRequest
        {
            Text = "Hi",
            VoiceId = "v1",
            Model = TTSModel.SsfmV30,
            Prompt = new SmartPrompt { PreviousText = "Earlier", NextText = "Later" },
        };

        await _client.TextToSpeechAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"emotion_type\":\"smart\"");
        capturedBody.Should().Contain("\"previous_text\":\"Earlier\"");
        capturedBody.Should().Contain("\"next_text\":\"Later\"");
    }

    [Fact]
    public async Task TextToSpeechAsync_WithMinimalRequest_NoPromptNoOutput()
    {
        var audio = new byte[] { 1 };
        var response = new HttpResponseMessage(HttpStatusCode.OK) { Content = new ByteArrayContent(audio) };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var request = new TTSRequest
        {
            Text = "Hi",
            VoiceId = "v1",
            Model = TTSModel.SsfmV21,
        };

        var result = await _client.TextToSpeechAsync(request);
        result.AudioData.Should().HaveCount(1);
    }
}
