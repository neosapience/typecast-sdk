using System.Net;
using System.Reflection;
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
        var parts = SpeechComposer.ParsePauseMarkup("Hello <|0.3s|>world <|bad|> <|s|> <|3|> <||> <|xs|> <|1.2.3s|> <|3000s|>");

        parts.Should().HaveCount(5);
        parts[0].Text.Should().Be("Hello ");
        parts[1].PauseSeconds.Should().BeApproximately(0.3, 0.0001);
        parts[2].Text.Should().Be("world <|bad|> <|s|> <|3|> <||> <|xs|> <|1.2.3s|> ");
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
                Prompt = new PresetPrompt(EmotionPreset.Happy, 1.0),
                Seed = 77,
                Output = new Output(volume: 80, audioPitch: -2, audioTempo: 1.1, audioFormat: AudioFormat.Mp3, targetLufs: -18.0)
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
        requests[1].Output!.Volume.Should().Be(80);
        requests[1].Output!.AudioPitch.Should().Be(-2);
        requests[1].Output!.AudioTempo.Should().Be(1.1);
        requests[1].Output!.TargetLufs.Should().Be(-18.0);
        requests[1].Prompt.Should().BeOfType<PresetPrompt>();
        requests[1].Seed.Should().Be(77);
    }

    [Fact]
    public void Pause_ShouldRejectNegativeDurations()
    {
        Action act = () => _client.ComposeSpeech().Pause(-0.1);

        act.Should().Throw<ArgumentOutOfRangeException>()
            .WithParameterName("seconds");
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
    public async Task GenerateAsync_ShouldTrimAllZeroSegmentsToEmptyAudio()
    {
        _mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(WavResponse(new short[] { 0, 0 }, 10));

        var response = await _client.ComposeSpeech()
            .Defaults(new ComposerSettings { VoiceId = "voice-a", Model = TTSModel.SsfmV30 })
            .Say("Silence")
            .GenerateAsync();

        ReadPcm(response.AudioData).Should().BeEmpty();
        response.Duration.Should().Be(0);
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

    [Fact]
    public async Task GenerateAsync_ShouldRejectEmptyComposer()
    {
        Func<Task> act = () => _client.ComposeSpeech()
            .Pause(0.1)
            .GenerateAsync();

        await act.Should().ThrowAsync<InvalidOperationException>()
            .WithMessage("*At least one speech segment*");
    }

    [Fact]
    public void Generate_ShouldSynchronouslyComposeSpeech()
    {
        _mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(WavResponse(new short[] { 0, 321, 0 }, 10));

        var response = _client.ComposeSpeech()
            .Defaults(new ComposerSettings { VoiceId = "voice-a", Model = TTSModel.SsfmV30 })
            .Say("Hello")
            .Generate();

        ReadPcm(response.AudioData).Should().Equal(new short[] { 321 });
    }

    [Fact]
    public void SegmentRequests_ShouldRequireVoiceId()
    {
        Action act = () => _client.ComposeSpeech()
            .Say("Hello")
            .SegmentRequests();

        act.Should().Throw<InvalidOperationException>()
            .WithMessage("*VoiceId is required*");
    }

    [Fact]
    public async Task GenerateAsync_ShouldRejectInvalidWavResponses()
    {
        var cases = new[]
        {
            new byte[] { 1, 2, 3 },
            TestWav(new short[] { 100 }, 10, audioFormat: 2),
            TestWavWithInvalidChunkSize(),
            TestWavWithoutData(extraChunk: true),
            TestWavWithoutData(extraChunk: false),
        };

        foreach (var wav in cases)
        {
            _mockHandler.Reset();
            _mockHandler.Protected()
                .Setup<Task<HttpResponseMessage>>(
                    "SendAsync",
                    ItExpr.IsAny<HttpRequestMessage>(),
                    ItExpr.IsAny<CancellationToken>())
                .ReturnsAsync(WavResponse(wav));

            Func<Task> act = () => _client.ComposeSpeech()
                .Defaults(new ComposerSettings { VoiceId = "voice-a", Model = TTSModel.SsfmV30 })
                .Say("Hello")
                .GenerateAsync();

            await act.Should().ThrowAsync<InvalidOperationException>();
        }
    }

    [Fact]
    public async Task GenerateAsync_ShouldRejectMismatchedSampleRates()
    {
        var responses = new Queue<HttpResponseMessage>(new[]
        {
            WavResponse(new short[] { 100 }, 10),
            WavResponse(new short[] { 200 }, 20),
        });

        _mockHandler.Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<CancellationToken>())
            .ReturnsAsync(() => responses.Dequeue());

        Func<Task> act = () => _client.ComposeSpeech()
            .Defaults(new ComposerSettings { VoiceId = "voice-a", Model = TTSModel.SsfmV30 })
            .Say("First")
            .Say("Second")
            .GenerateAsync();

        await act.Should().ThrowAsync<InvalidOperationException>()
            .WithMessage("*sample rates must match*");
    }

    [Fact]
    public void ComposeWav_ShouldRejectInvalidInternalPartShape()
    {
        var method = typeof(SpeechComposer).GetMethod("ComposeWav", BindingFlags.NonPublic | BindingFlags.Static)!;
        Action act = () => method.Invoke(null, new object[] { Array.Empty<byte[]>(), Array.Empty<double>() });

        act.Should().Throw<TargetInvocationException>()
            .WithInnerException<InvalidOperationException>()
            .WithMessage("*Invalid composed speech parts*");
    }

    [Fact]
    public void MergeOutput_ShouldPreferOverridesAndFallbackToDefaults()
    {
        var method = typeof(SpeechComposer).GetMethod("MergeOutput", BindingFlags.NonPublic | BindingFlags.Static)!;
        var baseOutput = new Output(volume: 10, audioPitch: 1, audioTempo: 0.9, audioFormat: AudioFormat.Mp3, targetLufs: -20.0);
        var emptyOverride = new Output
        {
            Volume = null,
            AudioPitch = null,
            AudioTempo = null,
            AudioFormat = null,
            TargetLufs = null
        };
        var fallback = (Output)method.Invoke(null, new object?[] { baseOutput, emptyOverride })!;

        fallback.Volume.Should().Be(10);
        fallback.AudioPitch.Should().Be(1);
        fallback.AudioTempo.Should().Be(0.9);
        fallback.AudioFormat.Should().Be(AudioFormat.Mp3);
        fallback.TargetLufs.Should().Be(-20.0);

        var overrideOutput = new Output(volume: 20, audioPitch: -1, audioTempo: 1.2, audioFormat: AudioFormat.Wav, targetLufs: -18.0);
        var overridden = (Output)method.Invoke(null, new object?[] { baseOutput, overrideOutput })!;

        overridden.Volume.Should().Be(20);
        overridden.AudioPitch.Should().Be(-1);
        overridden.AudioTempo.Should().Be(1.2);
        overridden.AudioFormat.Should().Be(AudioFormat.Wav);
        overridden.TargetLufs.Should().Be(-18.0);
    }

    private static HttpResponseMessage WavResponse(short[] samples, int sampleRate)
    {
        return WavResponse(TestWav(samples, sampleRate));
    }

    private static HttpResponseMessage WavResponse(byte[] wav)
    {
        var response = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(wav)
        };
        response.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("audio/wav");
        return response;
    }

    private static byte[] TestWav(short[] samples, int sampleRate, short audioFormat = 1)
    {
        var dataLength = samples.Length * 2;
        var bytes = new byte[44 + dataLength];
        System.Text.Encoding.ASCII.GetBytes("RIFF").CopyTo(bytes, 0);
        BitConverter.GetBytes(36 + dataLength).CopyTo(bytes, 4);
        System.Text.Encoding.ASCII.GetBytes("WAVEfmt ").CopyTo(bytes, 8);
        BitConverter.GetBytes(16).CopyTo(bytes, 16);
        BitConverter.GetBytes(audioFormat).CopyTo(bytes, 20);
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

    private static byte[] TestWavWithoutData(bool extraChunk)
    {
        var payloadLength = extraChunk ? 12 : 8;
        var bytes = new byte[36 + payloadLength];
        System.Text.Encoding.ASCII.GetBytes("RIFF").CopyTo(bytes, 0);
        BitConverter.GetBytes(28 + payloadLength).CopyTo(bytes, 4);
        System.Text.Encoding.ASCII.GetBytes("WAVEfmt ").CopyTo(bytes, 8);
        BitConverter.GetBytes(16).CopyTo(bytes, 16);
        BitConverter.GetBytes((short)1).CopyTo(bytes, 20);
        BitConverter.GetBytes((short)1).CopyTo(bytes, 22);
        BitConverter.GetBytes(10).CopyTo(bytes, 24);
        BitConverter.GetBytes(20).CopyTo(bytes, 28);
        BitConverter.GetBytes((short)2).CopyTo(bytes, 32);
        BitConverter.GetBytes((short)16).CopyTo(bytes, 34);
        if (extraChunk)
        {
            System.Text.Encoding.ASCII.GetBytes("JUNK").CopyTo(bytes, 36);
            BitConverter.GetBytes(4).CopyTo(bytes, 40);
            BitConverter.GetBytes(123).CopyTo(bytes, 44);
        }
        return bytes;
    }

    private static byte[] TestWavWithInvalidChunkSize()
    {
        var bytes = TestWav(Array.Empty<short>(), 10);
        System.Text.Encoding.ASCII.GetBytes("JUNK").CopyTo(bytes, 36);
        BitConverter.GetBytes(1000).CopyTo(bytes, 40);
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
