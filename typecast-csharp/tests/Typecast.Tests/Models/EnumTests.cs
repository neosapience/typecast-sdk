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
    public void UseCaseEnum_ToApiString_ShouldReturnCorrectValue(UseCaseEnum useCase, string expected)
    {
        useCase.ToApiString().Should().Be(expected);
    }

    #endregion
}
