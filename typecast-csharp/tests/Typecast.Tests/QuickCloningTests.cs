using System.Net;
using System.Text;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class QuickCloningTests : IDisposable
{
    private readonly Mock<HttpMessageHandler> _mockHandler;
    private readonly HttpClient _httpClient;
    private readonly TypecastClient _client;

    private const string CloneVoiceJson = """
        {
            "voice_id": "uc_abc123",
            "name": "My Voice",
            "model": "ssfm-v30"
        }
        """;

    public QuickCloningTests()
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

    // ──────────────────────────────────────────────────────────────────────────
    // 1. CloneVoiceAsync returns a populated CustomVoice
    // ──────────────────────────────────────────────────────────────────────────

    [Fact]
    public async Task CloneVoiceAsync_Returns_CustomVoice()
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(CloneVoiceJson, Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        var audio = new byte[1024]; // 1 KB — well below the 25 MB limit
        var result = await _client.CloneVoiceAsync(audio, "sample.wav", "My Voice", "ssfm-v30");

        result.Should().NotBeNull();
        result.VoiceId.Should().Be("uc_abc123");
        result.Name.Should().Be("My Voice");
        result.Model.Should().Be("ssfm-v30");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. CloneVoiceAsync sends a correct multipart/form-data request
    // ──────────────────────────────────────────────────────────────────────────

    [Fact]
    public async Task CloneVoiceAsync_Sends_Multipart_Body()
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(CloneVoiceJson, Encoding.UTF8, "application/json")
        };

        HttpMethod? capturedMethod = null;
        Uri? capturedUri = null;
        string? capturedContentType = null;
        string? capturedBody = null;
        bool capturedIsMultipart = false;

        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) =>
            {
                capturedMethod = req.Method;
                capturedUri = req.RequestUri;
                capturedIsMultipart = req.Content is MultipartFormDataContent;
                capturedContentType = req.Content?.Headers.ContentType?.ToString();
                // Read the body synchronously inside the callback, before HttpClient disposes it.
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult();
            })
            .ReturnsAsync(response);

        var audio = new byte[512];
        await _client.CloneVoiceAsync(audio, "test.wav", "My Voice", "ssfm-v30");

        capturedMethod.Should().Be(HttpMethod.Post);
        capturedUri!.ToString().Should().EndWith("/v1/voices/clone");
        capturedIsMultipart.Should().BeTrue();
        capturedContentType.Should().StartWith("multipart/form-data; boundary=");
        // .NET's MultipartFormDataContent writes: name=name (no quotes in some runtimes)
        // or name="name" — match both styles by checking the field name text is present.
        capturedBody.Should().MatchRegex(@"name=.?name.?");
        capturedBody.Should().MatchRegex(@"name=.?model.?");
        capturedBody.Should().MatchRegex(@"name=.?file.?");
    }

    [Theory]
    [InlineData("test.mp3", "audio/mpeg")]
    [InlineData("test.bin", "application/octet-stream")]
    public async Task CloneVoiceAsync_Sets_File_Content_Type_From_Extension(string filename, string expectedContentType)
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(CloneVoiceJson, Encoding.UTF8, "application/json")
        };

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

        await _client.CloneVoiceAsync(new byte[512], filename, "My Voice", "ssfm-v30");

        capturedBody.Should().Contain($"Content-Type: {expectedContentType}");
    }

    [Fact]
    public async Task CloneVoiceAsync_FilePath_Overload_Reads_File_And_Uses_Basename()
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(CloneVoiceJson, Encoding.UTF8, "application/json")
        };

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

        var path = Path.Combine(Path.GetTempPath(), $"typecast-{Guid.NewGuid():N}.mp3");
        await File.WriteAllBytesAsync(path, new byte[] { 1, 2, 3, 4 });
        try
        {
            var result = await _client.CloneVoiceAsync(path, "My Voice", "ssfm-v30");

            result.VoiceId.Should().Be("uc_abc123");
            capturedBody.Should().Contain(Path.GetFileName(path));
            capturedBody.Should().Contain("Content-Type: audio/mpeg");
        }
        finally
        {
            File.Delete(path);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. CloneVoiceAsync rejects audio exceeding 25 MB (no HTTP call)
    // ──────────────────────────────────────────────────────────────────────────

    [Fact]
    public async Task CloneVoiceAsync_Rejects_Oversized_Audio()
    {
        // 26 MB — one byte over the limit
        var oversized = new byte[26L * 1024 * 1024];

        Func<Task> act = () => _client.CloneVoiceAsync(oversized, "big.wav", "My Voice", "ssfm-v30");

        await act.Should().ThrowAsync<ArgumentException>()
            .WithParameterName("audio");

        // Verify no HTTP call was made
        _mockHandler
            .Protected()
            .Verify<Task<HttpResponseMessage>>(
                "SendAsync",
                Times.Never(),
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. CloneVoiceAsync rejects invalid name lengths
    // ──────────────────────────────────────────────────────────────────────────

    [Theory]
    [InlineData("")]                                  // empty — below NameMinLength
    [InlineData("1234567890123456789012345678901")]   // 31 chars — above NameMaxLength
    public async Task CloneVoiceAsync_Rejects_Bad_Name_Length(string badName)
    {
        var audio = new byte[512];

        Func<Task> act = () => _client.CloneVoiceAsync(audio, "sample.wav", badName, "ssfm-v30");

        await act.Should().ThrowAsync<ArgumentException>()
            .WithParameterName("name");

        _mockHandler
            .Protected()
            .Verify<Task<HttpResponseMessage>>(
                "SendAsync",
                Times.Never(),
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. DeleteVoiceAsync succeeds on 204 No Content
    // ──────────────────────────────────────────────────────────────────────────

    [Fact]
    public async Task DeleteVoiceAsync_Succeeds_On_204()
    {
        var response = new HttpResponseMessage(HttpStatusCode.NoContent);

        HttpRequestMessage? captured = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .Callback<HttpRequestMessage, CancellationToken>((req, _) => captured = req)
            .ReturnsAsync(response);

        await _client.Invoking(c => c.DeleteVoiceAsync("uc_abc123"))
            .Should().NotThrowAsync();

        captured.Should().NotBeNull();
        captured!.Method.Should().Be(HttpMethod.Delete);
        captured.RequestUri!.ToString().Should().EndWith("/v1/voices/uc_abc123");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. DeleteVoiceAsync throws NotFoundException on 404
    // ──────────────────────────────────────────────────────────────────────────

    [Fact]
    public async Task DeleteVoiceAsync_Throws_On_404()
    {
        var response = new HttpResponseMessage(HttpStatusCode.NotFound)
        {
            Content = new StringContent("{\"error\":\"voice not found\"}", Encoding.UTF8, "application/json")
        };
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(response);

        await Assert.ThrowsAsync<NotFoundException>(() => _client.DeleteVoiceAsync("uc_nonexistent"));
    }
}
