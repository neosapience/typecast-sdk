using System.Net;
using System.Text;
using System.Text.Json;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class SpeechComposerTests : IDisposable
{
    private readonly Mock<HttpMessageHandler> _handler = new();
    private readonly HttpClient _httpClient;
    private readonly TypecastClient _client;

    public SpeechComposerTests()
    {
        _httpClient = new HttpClient(_handler.Object);
        _client = new TypecastClient(new TypecastClientConfig { ApiKey = "test-key", HttpClient = _httpClient });
    }

    [Fact]
    public async Task GenerateAsync_UsesComposeApiAndMergesOverrides()
    {
        HttpRequestMessage? captured = null;
        string? requestBody = null;
        _handler.Protected().Setup<Task<HttpResponseMessage>>(
                "SendAsync", ItExpr.IsAny<HttpRequestMessage>(), ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync((HttpRequestMessage request, CancellationToken _) =>
            {
                captured = request;
                requestBody = request.Content!.ReadAsStringAsync().GetAwaiter().GetResult();
                var response = new HttpResponseMessage(HttpStatusCode.OK)
                {
                    Content = new ByteArrayContent(Encoding.UTF8.GetBytes("composed-audio"))
                };
                response.Content.Headers.ContentType = new("audio/mpeg");
                response.Headers.Add("X-Audio-Duration", "1.25");
                return response;
            });

        var response = await _client.ComposeSpeech()
            .Defaults(new ComposerSettings
            {
                VoiceId = "voice-a", Model = TTSModel.SsfmV30,
                Output = new Output(volume: null, audioPitch: 1, audioFormat: AudioFormat.Mp3)
            })
            .Say("Hello<|0.3s|>world", new ComposerSettings
            {
                VoiceId = "voice-b",
                Output = new Output(volume: null, audioPitch: null, audioTempo: 1.1, audioFormat: null)
            })
            .GenerateAsync(AudioFormat.Mp3);

        captured!.RequestUri!.AbsolutePath.Should().Be("/v1/text-to-speech/compose");
        using var document = JsonDocument.Parse(requestBody!);
        var segments = document.RootElement.GetProperty("segments");
        segments.GetArrayLength().Should().Be(3);
        segments[0].GetProperty("type").GetString().Should().Be("tts");
        segments[0].GetProperty("text").GetString().Should().Be("Hello");
        segments[0].GetProperty("voice_id").GetString().Should().Be("voice-b");
        segments[0].GetProperty("output").GetProperty("audio_format").GetString().Should().Be("mp3");
        segments[0].GetProperty("output").GetProperty("audio_pitch").GetInt32().Should().Be(1);
        segments[0].GetProperty("output").GetProperty("audio_tempo").GetDouble().Should().Be(1.1);
        segments[1].GetProperty("type").GetString().Should().Be("pause");
        segments[1].GetProperty("duration_seconds").GetDouble().Should().Be(0.3);
        segments[2].GetProperty("text").GetString().Should().Be("world");
        Encoding.UTF8.GetString(response.AudioData).Should().Be("composed-audio");
        response.Format.Should().Be(AudioFormat.Mp3);
        response.Duration.Should().Be(1.25);
        _handler.Protected().Verify("SendAsync", Times.Once(), ItExpr.IsAny<HttpRequestMessage>(), ItExpr.IsAny<CancellationToken>());
    }

    [Fact]
    public void Generate_ValidatesBeforeNetwork()
    {
        FluentActions.Invoking(() => _client.ComposeSpeech().Say("Hello").Generate())
            .Should().Throw<InvalidOperationException>().WithMessage("*VoiceId is required*");
        FluentActions.Invoking(() => _client.ComposeSpeech().Generate())
            .Should().Throw<InvalidOperationException>().WithMessage("*At least one speech segment*");
    }

    [Fact]
    public void ParsePauseMarkup_PreservesInvalidTokens()
    {
        var parts = SpeechComposer.ParsePauseMarkup("a<|0.3s|>b<|abc|>c<|3s|>");
        parts.Should().HaveCount(5);
        parts[0].Text.Should().Be("a");
        parts[1].IsPause.Should().BeTrue();
        parts[2].Text.Should().Be("b<|abc|>c");
        parts[3].IsPause.Should().BeTrue();
    }

    public void Dispose()
    {
        _client.Dispose();
        _httpClient.Dispose();
    }
}
