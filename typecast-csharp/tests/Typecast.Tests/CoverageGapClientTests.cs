using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

/// <summary>
/// Targeted tests that close coverage gaps in TypecastClient and its
/// HTTP/serialization paths. Each test exists specifically to cover a
/// previously-unhit line or branch reported by coverlet.
/// </summary>
public class CoverageGapClientTests
{
    // ----- TypecastClient.SerializeRequest: target_lufs branch -----

    [Fact]
    public async Task TextToSpeechAsync_WithTargetLufsOutput_ShouldSerializeTargetLufs()
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        using var http = new HttpClient(mockHandler.Object);
        using var client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "k",
            HttpClient = http,
        });

        string? capturedBody = null;
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(new byte[] { 1 })
        };
        response.Content.Headers.ContentType = new MediaTypeHeaderValue("audio/wav");

        mockHandler
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
            // Use the constructor that auto-clears Volume so the
            // serializer also walks the target_lufs branch.
            Output = new Output(targetLufs: -14.0, audioPitch: 0, audioTempo: 1.0, audioFormat: AudioFormat.Wav),
        };

        await client.TextToSpeechAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"target_lufs\":-14");
    }

    // ----- TypecastClient.HandleJsonResponseAsync: null result + JsonException -----

    [Fact]
    public async Task GetVoicesV2Async_WhenResponseIsJsonNull_ShouldThrowTypecastException()
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        using var http = new HttpClient(mockHandler.Object);
        using var client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "k",
            HttpClient = http,
        });

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent("null", Encoding.UTF8, "application/json"),
        };

        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var ex = await Assert.ThrowsAsync<TypecastException>(() => client.GetVoicesV2Async());
        ex.Message.Should().Contain("deserialize");
    }

    [Fact]
    public async Task GetVoicesV2Async_WhenResponseIsInvalidJson_ShouldThrowTypecastException()
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        using var http = new HttpClient(mockHandler.Object);
        using var client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "k",
            HttpClient = http,
        });

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent("not-json{", Encoding.UTF8, "application/json"),
        };

        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var ex = await Assert.ThrowsAsync<TypecastException>(() => client.GetVoicesV2Async());
        ex.Message.Should().Contain("parse response");
        ex.InnerException.Should().BeOfType<JsonException>();
    }

    // ----- TypecastClient.TextToSpeechAsync: ParseFromContentType fallback -----
    // The catch-all in TextToSpeechAsync wraps a call to
    // AudioFormatExtensions.ParseFromContentType. ParseFromContentType
    // does not throw for any unknown media type today (it returns wav),
    // so the catch path is unreachable from any practical input. We
    // still cover the happy path for an unknown content type so the try
    // branch is exercised.

    [Fact]
    public async Task TextToSpeechAsync_WithUnknownContentType_ShouldFallBackToWav()
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        using var http = new HttpClient(mockHandler.Object);
        using var client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "k",
            HttpClient = http,
        });

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(new byte[] { 1 })
        };
        response.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");

        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var result = await client.TextToSpeechAsync(new TTSRequest("Hi", "v1", TTSModel.SsfmV21));
        // ParseFromContentType returns Wav for unknown values.
        result.Format.Should().Be(AudioFormat.Wav);
    }

    // ----- TypecastClient.SerializePrompt: SmartPrompt with empty/null context fields -----

    [Fact]
    public async Task TextToSpeechAsync_WithSmartPromptEmptyContext_ShouldSerialize()
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        using var http = new HttpClient(mockHandler.Object);
        using var client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "k",
            HttpClient = http,
        });

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(new byte[] { 1 })
        };
        response.Content.Headers.ContentType = new MediaTypeHeaderValue("audio/wav");

        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        // PreviousText and NextText null -> the `is { Length: > 0 }`
        // patterns in SerializePrompt take their false branch.
        var request = new TTSRequest
        {
            Text = "Hi",
            VoiceId = "v1",
            Model = TTSModel.SsfmV30,
            Prompt = new SmartPrompt(),
        };

        var result = await client.TextToSpeechAsync(request);
        result.Should().NotBeNull();
    }

    // ----- TypecastClient.TextToSpeechAsync: response with no Content-Type header -----

    [Fact]
    public async Task TextToSpeechAsync_WithoutContentType_ShouldDefaultToWav()
    {
        var mockHandler = new Mock<HttpMessageHandler>();
        using var http = new HttpClient(mockHandler.Object);
        using var client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "k",
            HttpClient = http,
        });

        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(new byte[] { 1 })
        };
        // Explicitly clear the content type so the null branch in
        // TextToSpeechAsync is taken.
        response.Content.Headers.ContentType = null;

        mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var result = await client.TextToSpeechAsync(new TTSRequest("Hi", "v1", TTSModel.SsfmV21));
        result.Format.Should().Be(AudioFormat.Wav);
    }

    // ----- TypecastClient.Dispose: branch where _ownsHttpClient is true -----

    [Fact]
    public void Dispose_WhenOwningHttpClient_ShouldDisposeIt()
    {
        // Constructing without an injected HttpClient causes the
        // client to own its HttpClient instance, so Dispose takes
        // the `_ownsHttpClient = true` branch.
        var prevKey = Environment.GetEnvironmentVariable("TYPECAST_API_KEY");
        try
        {
            Environment.SetEnvironmentVariable("TYPECAST_API_KEY", "env-key");
            var client = new TypecastClient();
            client.Dispose();
            // Calling Dispose a second time should be a no-op.
            client.Dispose();
        }
        finally
        {
            Environment.SetEnvironmentVariable("TYPECAST_API_KEY", prevKey);
        }
    }
}
