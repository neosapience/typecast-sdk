using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class OutputTests
{
    [Fact]
    public void Output_WithDefaultValues_ShouldValidate()
    {
        // Arrange
        var output = new Output();

        // Act & Assert
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    [Fact]
    public void Output_WithValidValues_ShouldValidate()
    {
        // Arrange
        var output = new Output(
            volume: 100,
            audioPitch: 0,
            audioTempo: 1.0,
            audioFormat: AudioFormat.Wav
        );

        // Act & Assert
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    [Theory]
    [InlineData(-1)]
    [InlineData(201)]
    public void Output_WithInvalidVolume_ShouldThrow(int volume)
    {
        // Arrange
        var output = new Output(volume: volume);

        // Act & Assert
        output.Invoking(o => o.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*Volume*");
    }

    [Theory]
    [InlineData(0)]
    [InlineData(100)]
    [InlineData(200)]
    public void Output_WithValidVolume_ShouldNotThrow(int volume)
    {
        // Arrange
        var output = new Output(volume: volume);

        // Act & Assert
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    [Theory]
    [InlineData(-13)]
    [InlineData(13)]
    public void Output_WithInvalidPitch_ShouldThrow(int pitch)
    {
        // Arrange
        var output = new Output(audioPitch: pitch);

        // Act & Assert
        output.Invoking(o => o.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*pitch*");
    }

    [Theory]
    [InlineData(-12)]
    [InlineData(0)]
    [InlineData(12)]
    public void Output_WithValidPitch_ShouldNotThrow(int pitch)
    {
        // Arrange
        var output = new Output(audioPitch: pitch);

        // Act & Assert
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    [Theory]
    [InlineData(0.4)]
    [InlineData(2.1)]
    public void Output_WithInvalidTempo_ShouldThrow(double tempo)
    {
        // Arrange
        var output = new Output(audioTempo: tempo);

        // Act & Assert
        output.Invoking(o => o.Validate())
            .Should().Throw<ArgumentOutOfRangeException>()
            .WithMessage("*tempo*");
    }

    [Theory]
    [InlineData(0.5)]
    [InlineData(1.0)]
    [InlineData(2.0)]
    public void Output_WithValidTempo_ShouldNotThrow(double tempo)
    {
        // Arrange
        var output = new Output(audioTempo: tempo);

        // Act & Assert
        output.Invoking(o => o.Validate()).Should().NotThrow();
    }

    [Fact]
    public void Output_WithWavFormat_ShouldSetCorrectFormat()
    {
        // Arrange
        var output = new Output(audioFormat: AudioFormat.Wav);

        // Assert
        output.AudioFormat.Should().Be(AudioFormat.Wav);
    }

    [Fact]
    public void Output_WithMp3Format_ShouldSetCorrectFormat()
    {
        // Arrange
        var output = new Output(audioFormat: AudioFormat.Mp3);

        // Assert
        output.AudioFormat.Should().Be(AudioFormat.Mp3);
    }
}
