using System.Text.Json;
using FluentAssertions;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

/// <summary>
/// Targeted tests that close coverage gaps in model types
/// (Prompt/Output validation, TTSResponse, enum converter,
/// TypecastException, TTSModel mapping). Each test exists
/// specifically to cover a previously-unhit line or branch.
/// </summary>
public class CoverageGapModelTests
{
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
