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
}
