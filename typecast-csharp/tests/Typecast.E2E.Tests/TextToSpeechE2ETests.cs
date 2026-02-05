using FluentAssertions;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.E2E.Tests;

/// <summary>
/// End-to-end tests for the Text-to-Speech API.
/// These tests require a valid TYPECAST_API_KEY environment variable.
/// </summary>
public class TextToSpeechE2ETests : E2ETestBase
{
    private async Task<string> GetFirstAvailableVoiceIdAsync()
    {
        var voices = await Client.GetVoicesV2Async(new VoicesV2Filter { Model = TTSModel.SsfmV30 });
        voices.Should().NotBeEmpty("Need at least one voice for TTS tests");
        return voices.First().VoiceId;
    }

    [Fact]
    public async Task TextToSpeechAsync_WithBasicRequest_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("Hello, this is a test.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty("Audio data should be returned");
        // Duration may be 0 if the header is not returned by the API
        response.Format.Should().Be(AudioFormat.Wav, "Default format should be WAV");
    }

    [Fact]
    public async Task TextToSpeechAsync_WithMp3Format_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("Hello, this is an MP3 test.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Output = new Output(audioFormat: AudioFormat.Mp3)
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
        // Note: Format detection depends on Content-Type header from API
        // The important thing is that we got audio data back
    }

    [Fact]
    public async Task TextToSpeechAsync_WithPresetEmotion_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("I am so happy today!", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Prompt = new PresetPrompt(EmotionPreset.Happy, 1.5)
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task TextToSpeechAsync_WithSmartEmotion_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("This is amazing!", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Prompt = new SmartPrompt(
                previousText: "I just heard some great news.",
                nextText: "I can't believe it happened!"
            )
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task TextToSpeechAsync_WithAudioAdjustments_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("Testing audio adjustments.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Output = new Output(
                volume: 80,
                audioPitch: 2,
                audioTempo: 1.2
            )
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task TextToSpeechAsync_WithSeed_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var seed = 42;
        
        var request = new TTSRequest("Testing with seed.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Seed = seed
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
        // Note: Reproducibility depends on API implementation
    }

    [Fact]
    public async Task TextToSpeechAsync_WithKoreanText_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("안녕하세요, 테스트입니다.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.Korean
        };

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task TextToSpeechAsync_SaveToFile_ShouldCreateFile()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("File save test.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English
        };
        var tempFile = Path.GetTempFileName() + ".wav";

        try
        {
            // Act
            var response = await Client.TextToSpeechAsync(request);
            await response.SaveToFileAsync(tempFile);

            // Assert
            File.Exists(tempFile).Should().BeTrue();
            var fileBytes = await File.ReadAllBytesAsync(tempFile);
            fileBytes.Should().BeEquivalentTo(response.AudioData);
        }
        finally
        {
            // Cleanup
            if (File.Exists(tempFile))
            {
                File.Delete(tempFile);
            }
        }
    }

    [Fact]
    public void TextToSpeech_Sync_ShouldReturnAudio()
    {
        // Arrange
        var voices = Client.GetVoicesV2(new VoicesV2Filter { Model = TTSModel.SsfmV30 });
        voices.Should().NotBeEmpty();
        var voiceId = voices.First().VoiceId;
        
        var request = new TTSRequest("Synchronous test.", voiceId, TTSModel.SsfmV30)
        {
            Language = LanguageCode.English
        };

        // Act
        var response = Client.TextToSpeech(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
    }

    [Fact]
    public async Task TextToSpeechAsync_WithInvalidApiKey_ShouldThrowUnauthorized()
    {
        // Arrange
        using var client = new TypecastClient("invalid-api-key");
        var request = new TTSRequest("Test", "voice_id", TTSModel.SsfmV30);

        // Act & Assert
        await Assert.ThrowsAsync<UnauthorizedException>(
            () => client.TextToSpeechAsync(request));
    }

    [Fact]
    public async Task TextToSpeechAsync_WithAutoLanguageDetection_ShouldReturnAudio()
    {
        // Arrange
        var voiceId = await GetFirstAvailableVoiceIdAsync();
        var request = new TTSRequest("Hello, world!", voiceId, TTSModel.SsfmV30);
        // Note: Language is not specified, should be auto-detected

        // Act
        var response = await Client.TextToSpeechAsync(request);

        // Assert
        response.Should().NotBeNull();
        response.AudioData.Should().NotBeNullOrEmpty();
    }
}
