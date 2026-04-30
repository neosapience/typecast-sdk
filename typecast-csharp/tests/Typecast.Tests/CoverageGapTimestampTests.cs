using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading.Tasks;
using FluentAssertions;
using Moq;
using Moq.Protected;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

/// <summary>
/// Targeted tests that close coverage gaps introduced by the timestamp TTS feature.
/// Each test exists specifically to cover a previously-unhit line or branch.
/// </summary>
public class CoverageGapTimestampTests : IDisposable
{
    // -----------------------------------------------------------------------
    // Client mock setup
    // -----------------------------------------------------------------------

    private readonly Mock<HttpMessageHandler> _mockHandler;
    private readonly HttpClient _httpClient;
    private readonly TypecastClient _client;

    public CoverageGapTimestampTests()
    {
        _mockHandler = new Mock<HttpMessageHandler>();
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

    // -----------------------------------------------------------------------
    // AlignmentSegments: parameterized constructors (lines 29-34, 61-66)
    // -----------------------------------------------------------------------

    [Fact]
    public void WordSegment_ParameterizedConstructor_SetsValues()
    {
        var seg = new WordSegment("hello", 0.1, 0.5);
        seg.Text.Should().Be("hello");
        seg.Start.Should().Be(0.1);
        seg.End.Should().Be(0.5);
    }

    [Fact]
    public void CharacterSegment_ParameterizedConstructor_SetsValues()
    {
        var seg = new CharacterSegment("H", 0.1, 0.2);
        seg.Text.Should().Be("H");
        seg.Start.Should().Be(0.1);
        seg.End.Should().Be(0.2);
    }

    // -----------------------------------------------------------------------
    // TTSRequestWithTimestamps.Validate: uncovered branches (lines 61, 84, 87, 90)
    // -----------------------------------------------------------------------

    [Fact]
    public void TTSRequestWithTimestamps_DefaultCtor_SetsEmptyDefaults()
    {
        // Hits the parameterless ctor (line 61)
        var req = new TTSRequestWithTimestamps();
        req.Text.Should().BeEmpty();
        req.VoiceId.Should().BeEmpty();
    }

    [Fact]
    public void TTSRequestWithTimestamps_Validate_EmptyText_Throws()
    {
        // Hits the empty-text branch (line 84)
        var req = new TTSRequestWithTimestamps { Text = "", VoiceId = "v1", Model = TTSModel.SsfmV30 };
        Action act = () => req.Validate();
        act.Should().Throw<ArgumentException>().WithMessage("*Text*");
    }

    [Fact]
    public void TTSRequestWithTimestamps_Validate_TextTooLong_Throws()
    {
        // Hits the max-length branch (line 87)
        var req = new TTSRequestWithTimestamps
        {
            Text = new string('a', 5001),
            VoiceId = "v1",
            Model = TTSModel.SsfmV30
        };
        Action act = () => req.Validate();
        act.Should().Throw<ArgumentException>().WithMessage("*5000*");
    }

    [Fact]
    public void TTSRequestWithTimestamps_Validate_EmptyVoiceId_Throws()
    {
        // Hits the empty-VoiceId branch (line 90)
        var req = new TTSRequestWithTimestamps { Text = "hi", VoiceId = "", Model = TTSModel.SsfmV30 };
        Action act = () => req.Validate();
        act.Should().Throw<ArgumentException>().WithMessage("*VoiceId*");
    }

    [Fact]
    public void TTSRequestWithTimestamps_Validate_WithPromptAndOutput_CallsValidateOnBoth()
    {
        // Hits Prompt?.Validate() and Output?.Validate() branches (lines 92-93)
        var req = new TTSRequestWithTimestamps("hello", "v1", TTSModel.SsfmV30)
        {
            Prompt = new Prompt(EmotionPreset.Happy, 1.0),
            Output = new Output { Volume = 80 }
        };
        Action act = () => req.Validate();
        act.Should().NotThrow();
    }

    // -----------------------------------------------------------------------
    // CaptioningHelpers: hard-cap seconds split — exercised via ToSrt()
    // -----------------------------------------------------------------------

    [Fact]
    public void ToSrt_HardCapSeconds_SplitsBeforeAppend()
    {
        // Build segments where the second segment would push the cue past 7s
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 10.0,
            words: new List<WordSegment>
            {
                new WordSegment("First", 0.0, 4.0),
                // (8.5 - 0.0) = 8.5 > 7.0 → forces split before appending Second
                new WordSegment("Second.", 6.9, 8.5),
            },
            characters: null);

        var srt = resp.ToSrt();
        // Should produce two cues
        srt.Should().Contain("First");
        srt.Should().Contain("Second.");
        srt.Should().Contain("2\n");  // second cue number
    }

    // -----------------------------------------------------------------------
    // CaptioningHelpers: hard-cap chars split — exercised via ToSrt()
    // -----------------------------------------------------------------------

    [Fact]
    public void ToSrt_HardCapChars_SplitsBeforeAppend()
    {
        // Build a cue that would exceed 42 codepoints when next segment is appended
        string longWord = new string('a', 40);
        string nextWord = "extra.";  // total joined = 40 + 1 + 6 = 47 > 42 → split
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 3.0,
            words: new List<WordSegment>
            {
                new WordSegment(longWord, 0.0, 1.0),
                new WordSegment(nextWord, 1.5, 2.0),
            },
            characters: null);

        var srt = resp.ToSrt();
        srt.Should().Contain(longWord);
        srt.Should().Contain("extra.");
        srt.Should().Contain("2\n");
    }

    // -----------------------------------------------------------------------
    // CaptioningHelpers: flush remaining without sentence terminator
    // -----------------------------------------------------------------------

    [Fact]
    public void ToSrt_FlushRemainingParts_ProducesSingleCue()
    {
        // Two word segments with no sentence terminator → flush at end
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 2.0,
            words: new List<WordSegment>
            {
                new WordSegment("Hello", 0.0, 0.5),
                new WordSegment("World", 0.6, 1.0),
            },
            characters: null);

        var srt = resp.ToSrt();
        srt.Should().Contain("Hello World");
        srt.Should().NotContain("2\n");  // only one cue
    }

    // -----------------------------------------------------------------------
    // CaptioningHelpers: surrogate-pair codepoint path — exercised via ToSrt()
    // -----------------------------------------------------------------------

    [Fact]
    public void ToSrt_SurrogatePair_CountsAsSingleCodepoint()
    {
        // U+1F600 GRINNING FACE is a surrogate pair in UTF-16: 2 char units, 1 codepoint.
        // 42 emoji = 42 codepoints (at the limit); adding one more → 43 → triggers split.
        string emoji = "\U0001F600";
        string fortyTwoEmoji = string.Concat(System.Linq.Enumerable.Repeat(emoji, 42));

        // char mode: no space separators
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 3.0,
            words: null,
            characters: new List<CharacterSegment>
            {
                new CharacterSegment(fortyTwoEmoji, 0.0, 1.0),
                // adding one more emoji → 43 codepoints → hard-cap split
                new CharacterSegment(emoji, 1.1, 1.5),
                new CharacterSegment(".", 1.6, 2.0),
            });

        var srt = resp.ToSrt();
        // First cue should be exactly the 42-emoji string
        srt.Should().Contain(fortyTwoEmoji);
        // Second cue should contain the extra emoji
        srt.Should().Contain("2\n");
    }

    // -----------------------------------------------------------------------
    // CaptioningHelpers internal branches: trailing whitespace and leading trim
    // -----------------------------------------------------------------------

    [Fact]
    public void ToSrt_SegmentWithTrailingSpace_EndsInSentenceStillDetected()
    {
        // segment text ends with sentence terminator but has trailing whitespace
        // → EndsInSentence trims trailing space (false branch on line 184: end < text.Length)
        // and also hits the "string is empty after trim" early-return guard (line 183)
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 2.0,
            words: new List<WordSegment>
            {
                // text ends with a sentence terminator followed by a space
                new WordSegment("Hello. ", 0.0, 0.5),
                new WordSegment("World", 0.6, 1.0),
            },
            characters: null);

        var srt = resp.ToSrt();
        // "Hello." flushes a cue; "World" starts a new one
        srt.Should().Contain("Hello.");
        srt.Should().Contain("World");
        srt.Should().Contain("2\n");
    }

    [Fact]
    public void ToSrt_EmptyAfterTrim_EndsInSentenceReturnsFalse()
    {
        // A segment whose text is entirely whitespace → EndsInSentence returns false
        // (hits the `if (end == 0) return false` branch on line 183)
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 2.0,
            words: new List<WordSegment>
            {
                new WordSegment("Hello", 0.0, 0.5),
                new WordSegment("   ", 0.51, 0.55),  // whitespace-only → trimmed → empty
                new WordSegment("World.", 0.6, 1.0),
            },
            characters: null);

        var srt = resp.ToSrt();
        srt.Should().Contain("World.");
    }

    [Fact]
    public void ToSrt_AllWhitespaceParts_ProducesNoCue()
    {
        // A single space-only word segment at end of iteration:
        // JoinParts([" "], wordMode=true) = " " → trim loop runs until start==end (all spaces exhausted).
        // This exercises the `start < end` false exit of while loop on line 173 (condition 84)
        // and the `end > start` false exit on line 174 (condition 117).
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 1.0,
            words: new List<WordSegment>
            {
                // No sentence terminator → goes to end-flush with parts = [" "]
                // JoinParts([" "], true) = " " → trimmed to "" → cue not added
                new WordSegment(" ", 0.0, 0.5),
            },
            characters: null);

        // Result should be empty (no cues produced)
        var srt = resp.ToSrt();
        srt.Should().Be("");
    }

    [Fact]
    public void ToSrt_LeadingWhitespaceInParts_TrimmedInJoin()
    {
        // Parts with leading whitespace; JoinParts should trim the result.
        // This exercises the `while (start < end && sb[start] <= ' ')` branch (line 173).
        var resp = new TTSWithTimestampsResponse(
            audio: "UklGRgAAAA==",
            audioFormat: "wav",
            audioDuration: 2.0,
            words: null,
            characters: new List<CharacterSegment>
            {
                // leading-space character: when joined in char mode the result starts with a space
                new CharacterSegment(" ", 0.0, 0.1),
                new CharacterSegment("H", 0.1, 0.2),
                new CharacterSegment("i", 0.2, 0.3),
                new CharacterSegment(".", 0.3, 0.4),
            });

        var srt = resp.ToSrt();
        // Leading space gets trimmed → cue text should be "Hi." not " Hi."
        srt.Should().Contain("Hi.");
        srt.Should().NotContain("\n Hi.");
    }

    // -----------------------------------------------------------------------
    // SerializeTimestampRequest: Prompt and Output branches (lines 389-406)
    // -----------------------------------------------------------------------

    private static string BuildFakeResponseJson() => @"{
        ""audio"": ""UklGRgAAAA=="",
        ""audio_format"": ""wav"",
        ""audio_duration"": 1.0,
        ""words"": [{ ""text"": ""Hi."", ""start"": 0.1, ""end"": 0.3 }],
        ""characters"": null
    }";

    [Fact]
    public async Task TextToSpeechWithTimestamps_WithPromptAndOutput_SerializesBothBranches()
    {
        // Cover SerializeTimestampRequest: Prompt (line 389) and Output block (lines 391-406)
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };
        string? capturedBody = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) =>
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult())
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hi", "v1", TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Prompt = new SmartPrompt("prev", "next"),
            // Use TargetLufs (not Volume) to cover target_lufs branch; must set Volume=null to avoid conflict
            Output = new Output { Volume = null, TargetLufs = -14.0, AudioPitch = 0, AudioTempo = 1.0, AudioFormat = AudioFormat.Wav },
            Seed = 1
        };
        await _client.TextToSpeechWithTimestampsAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"prompt\"");
        capturedBody.Should().Contain("\"output\"");
        capturedBody.Should().Contain("\"target_lufs\"");
        capturedBody.Should().Contain("\"language\"");
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_WithVolumeOutput_SerializesVolume()
    {
        // Cover the Volume branch (line 394-395) of SerializeTimestampRequest
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };
        string? capturedBody = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) =>
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult())
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hi", "v1", TTSModel.SsfmV30)
        {
            Output = new Output { Volume = 80, AudioFormat = AudioFormat.Wav }
        };
        await _client.TextToSpeechWithTimestampsAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().Contain("\"volume\"");
    }

    [Fact]
    public async Task TextToSpeechWithTimestamps_WithEmptyOutput_OmitsOutputKey()
    {
        // Cover the else path of outputDict.Count > 0 (line 404)
        var httpResponse = new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(BuildFakeResponseJson(), Encoding.UTF8, "application/json")
        };
        string? capturedBody = null;
        _mockHandler
            .Protected()
            .Setup<Task<HttpResponseMessage>>(
                "SendAsync",
                ItExpr.IsAny<HttpRequestMessage>(),
                ItExpr.IsAny<System.Threading.CancellationToken>())
            .Callback<HttpRequestMessage, System.Threading.CancellationToken>((req, _) =>
                capturedBody = req.Content?.ReadAsStringAsync().GetAwaiter().GetResult())
            .ReturnsAsync(httpResponse);

        var request = new TTSRequestWithTimestamps("Hi", "v1", TTSModel.SsfmV30)
        {
            Output = new Output { Volume = null, AudioPitch = null, AudioTempo = null, AudioFormat = null }
        };
        await _client.TextToSpeechWithTimestampsAsync(request);

        capturedBody.Should().NotBeNull();
        capturedBody!.Should().NotContain("\"output\"");
    }
}
