using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.E2E.Tests;

/// <summary>
/// Real-API E2E tests for text-to-speech with timestamps.
/// Requires TYPECAST_API_KEY environment variable (skipped when absent).
/// </summary>
public class TimestampTTSE2ETests : E2ETestBase
{
    private const string Voice = "tc_60e5426de8b95f1d3000d7b5";

    private static TTSRequestWithTimestamps BuildRequest(string text, LanguageCode language) =>
        new(text, Voice, TTSModel.SsfmV30)
        {
            Language = language,
            Prompt = new PresetPrompt(EmotionPreset.Normal, 1.0),
            Seed = 42,
        };

    [Fact]
    public async Task WithTimestamps_NoGranularity_WordsAndCharactersReturned()
    {
        var req = BuildRequest("Hello.", LanguageCode.English);

        var resp = await Client.TextToSpeechWithTimestampsAsync(req, null);

        resp.Should().NotBeNull();
        resp.AudioDuration.Should().BeGreaterThan(0, "audio_duration should be > 0");
        resp.Words.Should().NotBeNullOrEmpty("words should be non-null and non-empty");
        resp.Characters.Should().NotBeNullOrEmpty("characters should be non-null and non-empty");
    }

    [Fact]
    public async Task WithTimestamps_WordGranularity_WordsOnlyCharactersNull()
    {
        var req = BuildRequest("Hello.", LanguageCode.English);

        var resp = await Client.TextToSpeechWithTimestampsAsync(req, "word");

        resp.Should().NotBeNull();
        resp.Words.Should().NotBeNullOrEmpty("words should be non-empty for word granularity");
        resp.Characters.Should().BeNullOrEmpty("characters should be null for word granularity");
    }

    [Fact]
    public async Task WithTimestamps_CharGranularity_CharactersOnlyWordsNull()
    {
        var req = BuildRequest("Hello.", LanguageCode.English);

        var resp = await Client.TextToSpeechWithTimestampsAsync(req, "char");

        resp.Should().NotBeNull();
        resp.Characters.Should().NotBeNullOrEmpty("characters should be non-empty for char granularity");
        resp.Words.Should().BeNullOrEmpty("words should be null for char granularity");
    }

    [Fact]
    public async Task WithTimestamps_JpnChar_AtLeastFiveSegments()
    {
        var req = BuildRequest("こんにちは。お元気ですか?", LanguageCode.Japanese);

        var resp = await Client.TextToSpeechWithTimestampsAsync(req, "char");

        resp.Should().NotBeNull();
        resp.Characters.Should().NotBeNull("characters should not be null for jpn+char");
        resp.Characters!.Count.Should().BeGreaterThanOrEqualTo(5,
            "Expected >= 5 character segments for Japanese");
    }
}
