using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class TimestampTTSTests : IDisposable
{
    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    private static string FixtureDir
    {
        get
        {
            var dir = AppContext.BaseDirectory;
            for (int i = 0; i < 10; i++)
            {
                var candidate = Path.Combine(dir, "test-fixtures", "with-timestamps");
                if (Directory.Exists(candidate)) return candidate;
                var parent = Directory.GetParent(dir)?.FullName;
                if (parent == null) break;
                dir = parent;
            }
            throw new InvalidOperationException("test-fixtures/with-timestamps not found");
        }
    }

    private static string LoadFixture(string name) =>
        File.ReadAllText(Path.Combine(FixtureDir, name));

    private static string LoadExpected(string name) =>
        File.ReadAllText(Path.Combine(FixtureDir, "expected", name));

    private static TTSWithTimestampsResponse Parse(string fixtureName) =>
        JsonSerializer.Deserialize<TTSWithTimestampsResponse>(LoadFixture(fixtureName))
            ?? throw new InvalidOperationException("parse failed");

    // -----------------------------------------------------------------------
    // Client mock setup (mirrors TypecastClientTests.cs pattern)
    // -----------------------------------------------------------------------

    private readonly Mock<HttpMessageHandler> _mockHandler;
    private readonly HttpClient _httpClient;
    private readonly TypecastClient _client;

    public TimestampTTSTests()
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

    // -----------------------------------------------------------------------
    // ToSrt — 4 shared fixtures
    // -----------------------------------------------------------------------

    [Theory]
    [InlineData("both")]
    [InlineData("word_only")]
    [InlineData("char_only")]
    [InlineData("jpn_char")]
    public void ToSrt_MatchesExpected(string name)
    {
        var resp = Parse($"{name}.json");
        Assert.Equal(LoadExpected($"{name}.srt"), resp.ToSrt());
    }

    // -----------------------------------------------------------------------
    // ToVtt — 4 shared fixtures
    // -----------------------------------------------------------------------

    [Theory]
    [InlineData("both")]
    [InlineData("word_only")]
    [InlineData("char_only")]
    [InlineData("jpn_char")]
    public void ToVtt_MatchesExpected(string name)
    {
        var resp = Parse($"{name}.json");
        Assert.Equal(LoadExpected($"{name}.vtt"), resp.ToVtt());
    }

    // -----------------------------------------------------------------------
    // AudioBytes / SaveAudio
    // -----------------------------------------------------------------------

    [Fact]
    public void AudioBytes_DecodesBase64()
    {
        var resp = Parse("both.json");
        var b = resp.AudioBytes();
        Assert.NotEmpty(b);
    }

    [Fact]
    public async Task SaveAudio_WritesFile()
    {
        var resp = Parse("both.json");
        var path = Path.GetTempFileName();
        try
        {
            await resp.SaveAudioAsync(path);
            Assert.True(new FileInfo(path).Length > 0);
        }
        finally
        {
            File.Delete(path);
        }
    }

    // -----------------------------------------------------------------------
    // Error: no segments
    // -----------------------------------------------------------------------

    [Fact]
    public void ToSrt_NoSegments_Throws()
    {
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 0,
            words: null,
            characters: null);
        Assert.Throws<InvalidOperationException>(() => resp.ToSrt());
    }

    [Fact]
    public void ToVtt_NoSegments_Throws()
    {
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 0,
            words: null,
            characters: null);
        Assert.Throws<InvalidOperationException>(() => resp.ToVtt());
    }

    // -----------------------------------------------------------------------
    // TextToSpeechWithTimestampsAsync — HTTP client mocking tests
    // -----------------------------------------------------------------------

    private static string BuildFakeResponseJson()
    {
        // Minimal valid response JSON
        return @"{
            ""audio"": ""UklGRgAAAA=="",
            ""audio_format"": ""wav"",
            ""audio_duration"": 1.5,
            ""words"": [{ ""text"": ""Hello."", ""start"": 0.1, ""end"": 0.5 }],
            ""characters"": null
        }";
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_NoGranularity_PostsToCorrectPath()
    {
        var responseJson = BuildFakeResponseJson();
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(responseJson, Encoding.UTF8, "application/json")
        };

        HttpRequestMessage? captured = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) => captured = req)
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hello", "v1", TTSModel.SsfmV30);
        var result = await _client.TextToSpeechWithTimestampsAsync(request);

        result.Should().NotBeNull();
        result.Words.Should().HaveCount(1);
        captured.Should().NotBeNull();
        captured!.Method.Should().Be(HttpMethod.Post);
        captured.RequestUri!.AbsolutePath.Should().Be("/v1/text-to-speech/with-timestamps");
        captured.RequestUri.Query.Should().BeEmpty();
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_WordGranularity_AppendsQuery()
    {
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };

        HttpRequestMessage? captured = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) => captured = req)
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hello", "v1", TTSModel.SsfmV30);
        await _client.TextToSpeechWithTimestampsAsync(request, granularity: "word");

        captured.Should().NotBeNull();
        captured!.RequestUri!.Query.Should().Contain("granularity=word");
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_CharGranularity_AppendsQuery()
    {
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };

        HttpRequestMessage? captured = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) => captured = req)
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hello", "v1", TTSModel.SsfmV30);
        await _client.TextToSpeechWithTimestampsAsync(request, granularity: "char");

        captured.Should().NotBeNull();
        captured!.RequestUri!.Query.Should().Contain("granularity=char");
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_BothGranularity_AppendsQuery()
    {
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hello", "v1", TTSModel.SsfmV30);
        var result = await _client.TextToSpeechWithTimestampsAsync(request, granularity: "both");
        result.Should().NotBeNull();
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_InvalidGranularity_Throws()
    {
        var request = new TTSRequestWithTimestamps("Hello", "v1", TTSModel.SsfmV30);
        await Assert.ThrowsAsync<ArgumentException>(
            () => _client.TextToSpeechWithTimestampsAsync(request, granularity: "sentence"));
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_NullRequest_Throws()
    {
        await Assert.ThrowsAsync<ArgumentNullException>(
            () => _client.TextToSpeechWithTimestampsAsync(null!));
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_402_ThrowsPaymentRequiredException()
    {
        var httpResponse = new HttpResponseMessage(HttpStatusCode.PaymentRequired)
        {
            Content = new StringContent("Payment required")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hello", "v1", TTSModel.SsfmV30);
        await Assert.ThrowsAsync<PaymentRequiredException>(
            () => _client.TextToSpeechWithTimestampsAsync(request));
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_SerializesRequestBody()
    {
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };

        string? capturedBody = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) =>
            {
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            })
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hello world", "voice_123", TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Seed = 42
        };
        await _client.TextToSpeechWithTimestampsAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"text\":\"Hello world\"");
        capturedBody.Should().Contain("\"voice_id\":\"voice_123\"");
        capturedBody.Should().Contain("\"seed\":42");
    }
}
