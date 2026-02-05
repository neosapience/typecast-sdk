using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class PromptTests
{
    [Fact]
    public void Prompt_WithValidEmotionIntensity_ShouldNotThrow()
    {
        // Arrange
        var prompt = new Prompt(EmotionPreset.Happy, 1.5);

        // Act & Assert
        prompt.Invoking(p => p.Validate()).Should().NotThrow();
    }

    [Fact]
    public void Prompt_WithEmotionIntensityTooLow_ShouldThrow()
    {
        // Arrange
        var prompt = new Prompt(EmotionPreset.Happy, -0.5);

        // Act & Assert
        prompt.Invoking(p => p.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*EmotionIntensity*");
    }

    [Fact]
    public void Prompt_WithEmotionIntensityTooHigh_ShouldThrow()
    {
        // Arrange
        var prompt = new Prompt(EmotionPreset.Happy, 2.5);

        // Act & Assert
        prompt.Invoking(p => p.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*EmotionIntensity*");
    }

    [Fact]
    public void PresetPrompt_ShouldHaveCorrectEmotionType()
    {
        // Arrange
        var prompt = new PresetPrompt(EmotionPreset.Sad, 1.0);

        // Assert
        prompt.EmotionType.Should().Be("preset");
        prompt.EmotionPreset.Should().Be(EmotionPreset.Sad);
    }

    [Fact]
    public void SmartPrompt_ShouldHaveCorrectEmotionType()
    {
        // Arrange
        var prompt = new SmartPrompt("Previous text", "Next text");

        // Assert
        prompt.EmotionType.Should().Be("smart");
        prompt.PreviousText.Should().Be("Previous text");
        prompt.NextText.Should().Be("Next text");
    }

    [Fact]
    public void SmartPrompt_WithPreviousTextTooLong_ShouldThrow()
    {
        // Arrange
        var longText = new string('a', 2001);
        var prompt = new SmartPrompt(longText, null);

        // Act & Assert
        prompt.Invoking(p => p.Validate())
            .Should().Throw<ArgumentException>()
            .WithMessage("*Previous text*2000 characters*");
    }

    [Fact]
    public void SmartPrompt_WithNextTextTooLong_ShouldThrow()
    {
        // Arrange
        var longText = new string('a', 2001);
        var prompt = new SmartPrompt(null, longText);

        // Act & Assert
        prompt.Invoking(p => p.Validate())
            .Should().Throw<ArgumentException>()
            .WithMessage("*Next text*2000 characters*");
    }

    [Fact]
    public void SmartPrompt_WithValidContext_ShouldNotThrow()
    {
        // Arrange
        var prompt = new SmartPrompt(
            new string('a', 2000),
            new string('b', 2000)
        );

        // Act & Assert
        prompt.Invoking(p => p.Validate()).Should().NotThrow();
    }
}
