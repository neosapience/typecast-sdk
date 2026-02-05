using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class TTSRequestTests
{
    [Fact]
    public void Constructor_WithRequiredParameters_ShouldSetProperties()
    {
        // Arrange & Act
        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30);

        // Assert
        request.Text.Should().Be("Hello world");
        request.VoiceId.Should().Be("voice_id_123");
        request.Model.Should().Be(TTSModel.SsfmV30);
    }

    [Fact]
    public void Validate_WithValidRequest_ShouldNotThrow()
    {
        // Arrange
        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30);

        // Act & Assert
        request.Invoking(r => r.Validate()).Should().NotThrow();
    }

    [Fact]
    public void Validate_WithEmptyText_ShouldThrowArgumentException()
    {
        // Arrange
        var request = new TTSRequest("", "voice_id_123", TTSModel.SsfmV30);

        // Act & Assert
        request.Invoking(r => r.Validate())
            .Should().Throw<ArgumentException>()
            .WithMessage("*Text*");
    }

    [Fact]
    public void Validate_WithTextExceedingMaxLength_ShouldThrowArgumentException()
    {
        // Arrange
        var longText = new string('a', 5001);
        var request = new TTSRequest(longText, "voice_id_123", TTSModel.SsfmV30);

        // Act & Assert
        request.Invoking(r => r.Validate())
            .Should().Throw<ArgumentException>()
            .WithMessage("*5000 characters*");
    }

    [Fact]
    public void Validate_WithEmptyVoiceId_ShouldThrowArgumentException()
    {
        // Arrange
        var request = new TTSRequest("Hello world", "", TTSModel.SsfmV30);

        // Act & Assert
        request.Invoking(r => r.Validate())
            .Should().Throw<ArgumentException>()
            .WithMessage("*VoiceId*");
    }

    [Fact]
    public void Validate_WithInvalidOutputVolume_ShouldThrowArgumentOutOfRangeException()
    {
        // Arrange
        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30)
        {
            Output = new Output(volume: 250)
        };

        // Act & Assert
        request.Invoking(r => r.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*Volume*");
    }

    [Fact]
    public void Validate_WithInvalidOutputPitch_ShouldThrowArgumentOutOfRangeException()
    {
        // Arrange
        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30)
        {
            Output = new Output(audioPitch: 15)
        };

        // Act & Assert
        request.Invoking(r => r.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*pitch*");
    }

    [Fact]
    public void Validate_WithInvalidOutputTempo_ShouldThrowArgumentOutOfRangeException()
    {
        // Arrange
        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30)
        {
            Output = new Output(audioTempo: 3.0)
        };

        // Act & Assert
        request.Invoking(r => r.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*tempo*");
    }

    [Fact]
    public void Request_WithAllOptionalParameters_ShouldSetProperties()
    {
        // Arrange & Act
        var request = new TTSRequest("Hello world", "voice_id_123", TTSModel.SsfmV30)
        {
            Language = LanguageCode.English,
            Prompt = new PresetPrompt(EmotionPreset.Happy, 1.5),
            Output = new Output(100, 0, 1.0, AudioFormat.Mp3),
            Seed = 42
        };

        // Assert
        request.Language.Should().Be(LanguageCode.English);
        request.Prompt.Should().BeOfType<PresetPrompt>();
        request.Output.Should().NotBeNull();
        request.Output!.Volume.Should().Be(100);
        request.Seed.Should().Be(42);
    }
}
