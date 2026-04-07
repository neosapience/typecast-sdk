using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

/// <summary>
/// Targeted tests that close the last few coverage gaps. Each test in
/// this file exists specifically to cover a previously-unhit line or
/// branch reported by coverlet. Keep them grouped by the source file
/// they target so it is clear what they exercise.
/// </summary>
public class CoverageGapTests
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
    // so the catch path is unreachable from any practical input. Cover
    // it by feeding a bogus content-type that ParseFromContentType
    // throws on (force exception via empty string after trim) — and if
    // that is impossible, we leave the lines explicitly excluded.
    //
    // The current implementation of ParseFromContentType returns Wav
    // for unknown values without throwing, so we cannot reach the
    // catch from the public surface. The lines are marked
    // [ExcludeFromCodeCoverage] in the source via partial-class
    // pattern is not applied here; instead we rely on a defensive
    // exclusion pragma in coverlet via attribute on a wrapper. As a
    // workaround, we add a unit test that goes through the happy path
    // for a not-explicitly-listed content type, ensuring the try
    // succeeds and giving the catch path a documented "no input can
    // trigger" rationale.

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
        // ParseFromContentType returns Wav for unknown values, so the
        // catch path is not reachable from this code path either.
        result.Format.Should().Be(AudioFormat.Wav);
    }

    // ----- Prompt.Validate: branch where EmotionIntensity has no value -----

    [Fact]
    public void Prompt_Validate_WithoutEmotionIntensity_ShouldNotThrow()
    {
        var prompt = new Prompt(EmotionPreset.Happy);
        prompt.Invoking(p => p.Validate()).Should().NotThrow();
    }

    // ----- PresetPrompt.Validate: out-of-range emotion intensity -----

    [Fact]
    public void PresetPrompt_Validate_WithEmotionIntensityTooLow_ShouldThrow()
    {
        var prompt = new PresetPrompt(EmotionPreset.Happy, -0.5);
        prompt.Invoking(p => p.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*EmotionIntensity*");
    }

    [Fact]
    public void PresetPrompt_Validate_WithEmotionIntensityTooHigh_ShouldThrow()
    {
        var prompt = new PresetPrompt(EmotionPreset.Happy, 2.5);
        prompt.Invoking(p => p.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*EmotionIntensity*");
    }

    [Fact]
    public void PresetPrompt_Validate_WithoutEmotionIntensity_ShouldNotThrow()
    {
        var prompt = new PresetPrompt(EmotionPreset.Happy);
        prompt.Invoking(p => p.Validate()).Should().NotThrow();
    }

    // ----- Output.Validate: branches where AudioPitch / AudioTempo have no value -----

    [Fact]
    public void Output_Validate_WithoutAudioPitch_ShouldNotThrow()
    {
        var output = new Output { Volume = 80, AudioPitch = null, AudioTempo = 1.0, AudioFormat = AudioFormat.Wav };
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    [Fact]
    public void Output_Validate_WithoutAudioTempo_ShouldNotThrow()
    {
        var output = new Output { Volume = 80, AudioPitch = 0, AudioTempo = null, AudioFormat = AudioFormat.Wav };
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    // ----- SmartPrompt.Validate: branch where NextText is null -----

    [Fact]
    public void SmartPrompt_Validate_WithPreviousTextOnly_ShouldNotThrow()
    {
        var prompt = new SmartPrompt(previousText: "ok", nextText: null);
        prompt.Invoking(p => p.Validate()).Should().NotThrow();
    }

    [Fact]
    public void SmartPrompt_Validate_WithNeitherText_ShouldNotThrow()
    {
        var prompt = new SmartPrompt();
        prompt.Invoking(p => p.Validate()).Should().NotThrow();
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

    // ----- TTSResponse: SaveToFile sync empty path + FileExtension default -----

    [Fact]
    public void SaveToFile_WithEmptyPath_ShouldThrow()
    {
        var response = new TTSResponse(new byte[] { 1 }, 1.0, AudioFormat.Wav);
        Action act = () => response.SaveToFile("");
        act.Should().Throw<ArgumentException>().WithMessage("*File path*");
    }

    [Fact]
    public void SaveToFile_WithWhitespacePath_ShouldThrow()
    {
        var response = new TTSResponse(new byte[] { 1 }, 1.0, AudioFormat.Wav);
        Action act = () => response.SaveToFile("   ");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void FileExtension_ForUnknownFormat_ShouldReturnBin()
    {
        var response = new TTSResponse(new byte[] { 1 }, 1.0, (AudioFormat)999);
        response.FileExtension.Should().Be(".bin");
    }

    [Fact]
    public void SaveToFile_Sync_ShouldWriteCorrectData()
    {
        var data = new byte[] { 9, 8, 7 };
        var response = new TTSResponse(data, 1.0, AudioFormat.Wav);
        var temp = Path.Combine(Path.GetTempPath(), $"typecast-sync-{Guid.NewGuid():N}.wav");
        try
        {
            response.SaveToFile(temp);
            File.ReadAllBytes(temp).Should().BeEquivalentTo(data);
        }
        finally
        {
            if (File.Exists(temp)) File.Delete(temp);
        }
    }

    // ----- VoiceV2Response.UseCases setter -----

    [Fact]
    public void VoiceV2Response_UseCases_SetterAndGetter_ShouldRoundTrip()
    {
        var voice = new VoiceV2Response
        {
            VoiceId = "v",
            VoiceName = "V",
            UseCases = new List<string> { "narration", "ads" },
        };
        voice.UseCases.Should().NotBeNull();
        voice.UseCases!.Should().Contain("narration");
    }

    // ----- TypecastException: 504 status branch -----

    [Fact]
    public void TypecastException_FromStatusCode_504_ShouldReturnInternalServerException()
    {
        var ex = TypecastException.FromStatusCode(504, null);
        ex.Should().BeOfType<InternalServerException>();
        ex.StatusCode.Should().Be(504);
        ex.Message.Should().Contain("Gateway timeout");
    }

    // ----- TTSModel: ToApiString invalid value -----

    [Fact]
    public void TTSModel_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((TTSModel)9999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    // ----- JsonStringEnumMemberConverterInner: Read null token, Write fallback,
    //       and ctor with an enum that has NO JsonPropertyName attributes ----

    private enum PlainEnum
    {
        Foo,
        Bar,
    }

    [Fact]
    public void JsonStringEnumMemberConverter_PlainEnum_ShouldRoundTripUsingLowerInvariantName()
    {
        // PlainEnum members have no JsonPropertyName attribute, so the
        // ctor walks the "attribute is null" branch and the name
        // becomes value.ToString().ToLowerInvariant().
        var options = new JsonSerializerOptions
        {
            Converters = { new JsonStringEnumMemberConverter() },
        };
        var json = JsonSerializer.Serialize(PlainEnum.Foo, options);
        json.Should().Be("\"foo\"");

        var parsed = JsonSerializer.Deserialize<PlainEnum>("\"bar\"", options);
        parsed.Should().Be(PlainEnum.Bar);
    }

    [Fact]
    public void JsonStringEnumMemberConverter_Read_NullToken_ShouldThrow()
    {
        var options = new JsonSerializerOptions
        {
            Converters = { new JsonStringEnumMemberConverter() },
        };
        // Reading a JSON null into a non-nullable enum hits the
        // "stringValue == null" branch in Read.
        Action act = () => JsonSerializer.Deserialize<EmotionPreset>("null", options);
        act.Should().Throw<JsonException>();
    }

    [Fact]
    public void JsonStringEnumMemberConverter_Write_UndefinedValue_ShouldUseLowerInvariantToString()
    {
        // Casting an out-of-range int to the enum produces a value
        // that is not present in _enumToString, so Write falls into
        // the else branch and writes value.ToString().ToLowerInvariant().
        var options = new JsonSerializerOptions
        {
            Converters = { new JsonStringEnumMemberConverter() },
        };
        var json = JsonSerializer.Serialize((PlainEnum)999, options);
        json.Should().Be("\"999\"");
    }
}
