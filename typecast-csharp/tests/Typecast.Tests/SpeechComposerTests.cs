using System.Net;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class SpeechComposerTests : IDisposable
{
    private readonly Mock<HttpMessageHandler> _mockHandler = new();
    private readonly HttpClient _httpClient;
    private readonly TypecastClient _client;

    public SpeechComposerTests()
    {
        _httpClient = new HttpClient(_mockHandler.Object);
        _client = new TypecastClient(new TypecastClientConfig
        {
            ApiKey = "test-key",
            HttpClient = _httpClient
        });
    }

    public void Dispose()
    {
        _client.Dispose();
        _httpClient.Dispose();
    }

    [Fact]
    public void ParsePauseMarkup_ShouldPreserveInvalidTokens()
    {
        var parts = SpeechComposer.ParsePauseMarkup("Hello <|0.3s|>world <|bad|> <|3000s|>");

        parts.Should().HaveCount(5);
        parts[0].Text.Should().Be("Hello ");
        parts[1].PauseSeconds.Should().BeApproximately(0.3, 0.0001);
        parts[2].Text.Should().Be("world <|bad|> ");
        parts[3].PauseSeconds.Should().Be(3000);
        parts[4].Text.Should().Be("");
    }

    [Fact]
    public void SegmentRequests_ShouldMergeDefaultsAndForceWav()
    {
        var requests = _client.ComposeSpeech()
            .Defaults(new ComposerSettings
            {
                VoiceId = "voice-a",
                Model = TTSModel.SsfmV30,
                Language = LanguageCode.English,
                Output = new Output(audioPitch: 1, audioTempo: 0.9, audioFormat: AudioFormat.Mp3)
            })
            .Say("First")
            .Pause(0.25)
            .Say("Second", new ComposerSettings
            {
                VoiceId = "voice-b",
                Output = new Output(volume: null, audioPitch: -2, audioTempo: 1.1, audioFormat: null)
            })
            .SegmentRequests();

        requests.Should().HaveCount(2);
        requests[0].VoiceId.Should().Be("voice-a");
        requests[0].Text.Should().Be("First");
        requests[0].Output!.AudioFormat.Should().Be(AudioFormat.Wav);
        requests[0].Output!.AudioPitch.Should().Be(1);
        requests[0].Output!.AudioTempo.Should().Be(0.9);
        requests[1].VoiceId.Should().Be("voice-b");
        requests[1].Text.Should().Be("Second");
        requests[1].Output!.AudioFormat.Should().Be(AudioFormat.Wav);
        requests[1].Output!.AudioPitch.Should().Be(-2);
        requests[1].Output!.AudioTempo.Should().Be(1.1);
    }

    [Fact]
    public async Task GenerateAsync_ShouldTrimSegmentsAndInsertSilence()
    {
        var responses = new Queue<HttpResponseMessage>(new[]
        {
            WavResponse(new short[] { 0, 100, 0 }, 10),
            WavResponse(new short[] { 0, -200, 0 }, 10),
        });

        _mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(() => responses.Dequeue());

        var response = await _client.ComposeSpeech()
            .Defaults(new ComposerSettings { VoiceId = "voice-a", Model = TTSModel.SsfmV30 })
            .Say("First")
            .Pause(0.2)
            .Say("Second")
            .GenerateAsync();

        ReadPcm(response.AudioData).Should().Equal(new short[] { 100, 0, 0, -200 });
        response.Duration.Should().BeApproximately(0.4, 0.0001);
    }

    [Fact]
    public async Task GenerateAsync_ShouldRejectMp3()
    {
        Func<Task> act = () => _client.ComposeSpeech()
            .Defaults(new ComposerSettings { VoiceId = "voice-a" })
            .Say("Hello")
            .GenerateAsync(AudioFormat.Mp3);

        await act.Should().ThrowAsync<TypecastException>()
            .WithMessage("*MP3 conversion*");
    }

    private static HttpResponseMessage WavResponse(short[] samples, int sampleRate)
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(TestWav(samples, sampleRate))
        };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        return response;
    }

    private static byte[] TestWav(short[] samples, int sampleRate)
    {
        var dataLength = samples.Length * 2;
        var bytes = new byte[44 + dataLength];
        System.Text.Encoding.ASCII.GetBytes("RIFF").CopyTo(bytes, 0);
        BitConverter.GetBytes(36 + dataLength).CopyTo(bytes, 4);
        System.Text.Encoding.ASCII.GetBytes("WAVEfmt ").CopyTo(bytes, 8);
        BitConverter.GetBytes(16).CopyTo(bytes, 16);
        BitConverter.GetBytes((short)1).CopyTo(bytes, 20);
        BitConverter.GetBytes((short)1).CopyTo(bytes, 22);
        BitConverter.GetBytes(sampleRate).CopyTo(bytes, 24);
        BitConverter.GetBytes(sampleRate * 2).CopyTo(bytes, 28);
        BitConverter.GetBytes((short)2).CopyTo(bytes, 32);
        BitConverter.GetBytes((short)16).CopyTo(bytes, 34);
        System.Text.Encoding.ASCII.GetBytes("data").CopyTo(bytes, 36);
        BitConverter.GetBytes(dataLength).CopyTo(bytes, 40);
        for (var i = 0; i < samples.Length; i++)
        {
            BitConverter.GetBytes(samples[i]).CopyTo(bytes, 44 + i * 2);
        }
        return bytes;
    }

    private static short[] ReadPcm(byte[] wav)
    {
        var dataLength = BitConverter.ToInt32(wav, 40);
        var samples = new short[dataLength / 2];
        for (var i = 0; i < samples.Length; i++)
        {
            samples[i] = BitConverter.ToInt16(wav, 44 + i * 2);
        }
        return samples;
    }
}
