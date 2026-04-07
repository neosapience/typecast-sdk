using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class EnumTests
{
    #region TTSModel

    [Theory]
    [InlineData(TTSModel.SsfmV21, "ssfm-v21")]
    [InlineData(TTSModel.SsfmV30, "ssfm-v30")]
    public void TTSModel_ToApiString_ShouldReturnCorrectValue(TTSModel model, string expected)
    {
        model.ToApiString().Should().Be(expected);
    }

    [Theory]
    [InlineData("ssfm-v21", TTSModel.SsfmV21)]
    [InlineData("ssfm-v30", TTSModel.SsfmV30)]
    public void TTSModel_Parse_ShouldReturnCorrectEnum(string value, TTSModel expected)
    {
        TTSModelExtensions.ParseTTSModel(value).Should().Be(expected);
    }

    [Fact]
    public void TTSModel_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => TTSModelExtensions.ParseTTSModel("invalid");
        act.Should().Throw<ArgumentException>();
    }

    #endregion

    #region EmotionPreset

    [Theory]
    [InlineData(EmotionPreset.Normal, "normal")]
    [InlineData(EmotionPreset.Happy, "happy")]
    [InlineData(EmotionPreset.Sad, "sad")]
    [InlineData(EmotionPreset.Angry, "angry")]
    [InlineData(EmotionPreset.Whisper, "whisper")]
    [InlineData(EmotionPreset.ToneUp, "toneup")]
    [InlineData(EmotionPreset.ToneDown, "tonedown")]
    public void EmotionPreset_ToApiString_ShouldReturnCorrectValue(EmotionPreset preset, string expected)
    {
        preset.ToApiString().Should().Be(expected);
    }

    [Theory]
    [InlineData("normal", EmotionPreset.Normal)]
    [InlineData("happy", EmotionPreset.Happy)]
    [InlineData("sad", EmotionPreset.Sad)]
    public void EmotionPreset_Parse_ShouldReturnCorrectEnum(string value, EmotionPreset expected)
    {
        EmotionPresetExtensions.ParseEmotionPreset(value).Should().Be(expected);
    }

    #endregion

    #region AudioFormat

    [Theory]
    [InlineData(AudioFormat.Wav, "wav")]
    [InlineData(AudioFormat.Mp3, "mp3")]
    public void AudioFormat_ToApiString_ShouldReturnCorrectValue(AudioFormat format, string expected)
    {
        format.ToApiString().Should().Be(expected);
    }

    [Theory]
    [InlineData(AudioFormat.Wav, "audio/wav")]
    [InlineData(AudioFormat.Mp3, "audio/mp3")]
    public void AudioFormat_ToContentType_ShouldReturnCorrectValue(AudioFormat format, string expected)
    {
        format.ToContentType().Should().Be(expected);
    }

    [Theory]
    [InlineData("audio/wav", AudioFormat.Wav)]
    [InlineData("audio/mp3", AudioFormat.Mp3)]
    [InlineData("audio/mpeg", AudioFormat.Mp3)]
    public void AudioFormat_ParseFromContentType_ShouldReturnCorrectValue(string contentType, AudioFormat expected)
    {
        AudioFormatExtensions.ParseFromContentType(contentType).Should().Be(expected);
    }

    #endregion

    #region LanguageCode

    [Theory]
    [InlineData(LanguageCode.Korean, "kor")]
    [InlineData(LanguageCode.English, "eng")]
    [InlineData(LanguageCode.Japanese, "jpn")]
    [InlineData(LanguageCode.Chinese, "cmn")]
    public void LanguageCode_ToApiString_ShouldReturnCorrectValue(LanguageCode code, string expected)
    {
        code.ToApiString().Should().Be(expected);
    }

    [Theory]
    [InlineData("kor", LanguageCode.Korean)]
    [InlineData("eng", LanguageCode.English)]
    [InlineData("jpn", LanguageCode.Japanese)]
    public void LanguageCode_Parse_ShouldReturnCorrectEnum(string value, LanguageCode expected)
    {
        LanguageCodeExtensions.ParseLanguageCode(value).Should().Be(expected);
    }

    #endregion

    #region GenderEnum

    [Theory]
    [InlineData(GenderEnum.Male, "male")]
    [InlineData(GenderEnum.Female, "female")]
    public void GenderEnum_ToApiString_ShouldReturnCorrectValue(GenderEnum gender, string expected)
    {
        gender.ToApiString().Should().Be(expected);
    }

    #endregion

    #region AgeEnum

    [Theory]
    [InlineData(AgeEnum.Child, "child")]
    [InlineData(AgeEnum.Teenager, "teenager")]
    [InlineData(AgeEnum.YoungAdult, "young_adult")]
    [InlineData(AgeEnum.MiddleAge, "middle_age")]
    [InlineData(AgeEnum.Elder, "elder")]
    public void AgeEnum_ToApiString_ShouldReturnCorrectValue(AgeEnum age, string expected)
    {
        age.ToApiString().Should().Be(expected);
    }

    #endregion

    #region UseCaseEnum

    [Theory]
    [InlineData(UseCaseEnum.Education, "education")]
    [InlineData(UseCaseEnum.Game, "game")]
    [InlineData(UseCaseEnum.General, "general")]
    [InlineData(UseCaseEnum.News, "news")]
    [InlineData(UseCaseEnum.Documentary, "documentary")]
    [InlineData(UseCaseEnum.Audiobook, "audiobook")]
    [InlineData(UseCaseEnum.Conversational, "conversational")]
    public void UseCaseEnum_ToApiString_ShouldReturnCorrectValue(UseCaseEnum useCase, string expected)
    {
        useCase.ToApiString().Should().Be(expected);
    }

    [Theory]
    [InlineData("education", UseCaseEnum.Education)]
    [InlineData("game", UseCaseEnum.Game)]
    [InlineData("general", UseCaseEnum.General)]
    [InlineData("news", UseCaseEnum.News)]
    [InlineData("documentary", UseCaseEnum.Documentary)]
    [InlineData("audiobook", UseCaseEnum.Audiobook)]
    [InlineData("conversational", UseCaseEnum.Conversational)]
    public void UseCaseEnum_Parse_ShouldReturnCorrectEnum(string value, UseCaseEnum expected)
    {
        UseCaseEnumExtensions.ParseUseCase(value).Should().Be(expected);
    }

    [Fact]
    public void UseCaseEnum_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => UseCaseEnumExtensions.ParseUseCase("invalid");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void UseCaseEnum_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((UseCaseEnum)999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    #endregion

    #region EmotionPreset (additional)

    [Theory]
    [InlineData("angry", EmotionPreset.Angry)]
    [InlineData("whisper", EmotionPreset.Whisper)]
    [InlineData("toneup", EmotionPreset.ToneUp)]
    [InlineData("tonedown", EmotionPreset.ToneDown)]
    public void EmotionPreset_Parse_AdditionalValues(string value, EmotionPreset expected)
    {
        EmotionPresetExtensions.ParseEmotionPreset(value).Should().Be(expected);
    }

    [Fact]
    public void EmotionPreset_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => EmotionPresetExtensions.ParseEmotionPreset("invalid");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void EmotionPreset_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((EmotionPreset)999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    #endregion

    #region GenderEnum (additional)

    [Theory]
    [InlineData("male", GenderEnum.Male)]
    [InlineData("female", GenderEnum.Female)]
    public void GenderEnum_Parse_ShouldReturnCorrectEnum(string value, GenderEnum expected)
    {
        GenderEnumExtensions.ParseGender(value).Should().Be(expected);
    }

    [Fact]
    public void GenderEnum_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => GenderEnumExtensions.ParseGender("invalid");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void GenderEnum_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((GenderEnum)999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    #endregion

    #region AgeEnum (additional)

    [Theory]
    [InlineData("child", AgeEnum.Child)]
    [InlineData("teenager", AgeEnum.Teenager)]
    [InlineData("young_adult", AgeEnum.YoungAdult)]
    [InlineData("middle_age", AgeEnum.MiddleAge)]
    [InlineData("elder", AgeEnum.Elder)]
    public void AgeEnum_Parse_ShouldReturnCorrectEnum(string value, AgeEnum expected)
    {
        AgeEnumExtensions.ParseAge(value).Should().Be(expected);
    }

    [Fact]
    public void AgeEnum_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => AgeEnumExtensions.ParseAge("invalid");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void AgeEnum_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((AgeEnum)999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    #endregion

    #region AudioFormat (additional)

    [Fact]
    public void AudioFormat_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((AudioFormat)999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Fact]
    public void AudioFormat_ToContentType_InvalidValue_ShouldThrow()
    {
        Action act = () => ((AudioFormat)999).ToContentType();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Fact]
    public void AudioFormat_ParseFromContentType_InvalidValue_ShouldThrow()
    {
        Action act = () => AudioFormatExtensions.ParseFromContentType("application/json");
        act.Should().Throw<ArgumentException>();
    }

    #endregion

    #region AudioFormat ParseAudioFormat

    [Theory]
    [InlineData("wav", AudioFormat.Wav)]
    [InlineData("mp3", AudioFormat.Mp3)]
    [InlineData("WAV", AudioFormat.Wav)]
    public void AudioFormat_ParseAudioFormat_ShouldReturnCorrectValue(string value, AudioFormat expected)
    {
        AudioFormatExtensions.ParseAudioFormat(value).Should().Be(expected);
    }

    [Fact]
    public void AudioFormat_ParseAudioFormat_InvalidValue_ShouldThrow()
    {
        Action act = () => AudioFormatExtensions.ParseAudioFormat("ogg");
        act.Should().Throw<ArgumentException>();
    }

    #endregion

    #region JsonStringEnumMemberConverter

    [Fact]
    public void JsonStringEnumMemberConverter_Write_ShouldUseJsonPropertyNameAttribute()
    {
        var options = new System.Text.Json.JsonSerializerOptions
        {
            Converters = { new JsonStringEnumMemberConverter() }
        };
        var json = System.Text.Json.JsonSerializer.Serialize(EmotionPreset.Happy, options);
        json.Should().Be("\"happy\"");
    }

    [Fact]
    public void JsonStringEnumMemberConverter_Read_ShouldUseJsonPropertyNameAttribute()
    {
        var options = new System.Text.Json.JsonSerializerOptions
        {
            Converters = { new JsonStringEnumMemberConverter() }
        };
        var value = System.Text.Json.JsonSerializer.Deserialize<EmotionPreset>("\"happy\"", options);
        value.Should().Be(EmotionPreset.Happy);
    }

    [Fact]
    public void JsonStringEnumMemberConverter_Read_InvalidValue_ShouldThrow()
    {
        var options = new System.Text.Json.JsonSerializerOptions
        {
            Converters = { new JsonStringEnumMemberConverter() }
        };
        Action act = () => System.Text.Json.JsonSerializer.Deserialize<EmotionPreset>("\"invalid\"", options);
        act.Should().Throw<System.Text.Json.JsonException>();
    }

    [Fact]
    public void JsonStringEnumMemberConverter_CanConvert_OnlyEnums()
    {
        var converter = new JsonStringEnumMemberConverter();
        converter.CanConvert(typeof(EmotionPreset)).Should().BeTrue();
        converter.CanConvert(typeof(string)).Should().BeFalse();
        converter.CanConvert(typeof(int)).Should().BeFalse();
    }

    #endregion

    #region LanguageCode (full enumeration)

    [Theory]
    [InlineData(LanguageCode.Korean, "kor")]
    [InlineData(LanguageCode.English, "eng")]
    [InlineData(LanguageCode.Japanese, "jpn")]
    [InlineData(LanguageCode.Chinese, "cmn")]
    public void LanguageCode_ToApiString_DuplicateCheck(LanguageCode code, string expected)
    {
        // Existing test already covers these — this is here so the
        // additional Parse / invalid tests below have a complete
        // describe block of context.
        code.ToApiString().Should().Be(expected);
    }

    [Fact]
    public void LanguageCode_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => LanguageCodeExtensions.ParseLanguageCode("xyz");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void LanguageCode_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((LanguageCode)9999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    #endregion
}
