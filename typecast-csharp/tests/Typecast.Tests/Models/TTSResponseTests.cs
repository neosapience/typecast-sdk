using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class TTSResponseTests
{
    [Fact]
    public void Constructor_ShouldSetProperties()
    {
        // Arrange
        var audioData = new byte[] { 1, 2, 3, 4, 5 };
        var duration = 2.5;
        var format = AudioFormat.Wav;

        // Act
        var response = new TTSResponse(audioData, duration, format);

        // Assert
        response.AudioData.Should().BeEquivalentTo(audioData);
        response.Duration.Should().Be(duration);
        response.Format.Should().Be(format);
    }

    [Fact]
    public void Constructor_WithNullAudioData_ShouldThrow()
    {
        // Act & Assert
        Action act = () => new TTSResponse(null!, 1.0, AudioFormat.Wav);
        act.Should().Throw<ArgumentNullException>();
    }

    [Fact]
    public void FileExtension_ForWav_ShouldReturnCorrectExtension()
    {
        // Arrange
        var response = new TTSResponse(new byte[] { 1 }, 1.0, AudioFormat.Wav);

        // Assert
        response.FileExtension.Should().Be(".wav");
    }

    [Fact]
    public void FileExtension_ForMp3_ShouldReturnCorrectExtension()
    {
        // Arrange
        var response = new TTSResponse(new byte[] { 1 }, 1.0, AudioFormat.Mp3);

        // Assert
        response.FileExtension.Should().Be(".mp3");
    }

    [Fact]
    public void ToStream_ShouldReturnReadableStream()
    {
        // Arrange
        var audioData = new byte[] { 1, 2, 3, 4, 5 };
        var response = new TTSResponse(audioData, 1.0, AudioFormat.Wav);

        // Act
        using var stream = response.ToStream();
        var buffer = new byte[5];
        var bytesRead = stream.Read(buffer, 0, 5);

        // Assert
        bytesRead.Should().Be(5);
        buffer.Should().BeEquivalentTo(audioData);
    }

    [Fact]
    public async Task SaveToFileAsync_ShouldWriteCorrectData()
    {
        // Arrange
        var audioData = new byte[] { 1, 2, 3, 4, 5 };
        var response = new TTSResponse(audioData, 1.0, AudioFormat.Wav);
        var tempFile = Path.GetTempFileName();

        try
        {
            // Act
            await response.SaveToFileAsync(tempFile);
            var savedData = await File.ReadAllBytesAsync(tempFile);

            // Assert
            savedData.Should().BeEquivalentTo(audioData);
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
    public void SaveToFile_ShouldWriteCorrectData()
    {
        // Arrange
        var audioData = new byte[] { 1, 2, 3, 4, 5 };
        var response = new TTSResponse(audioData, 1.0, AudioFormat.Mp3);
        var tempFile = Path.GetTempFileName();

        try
        {
            // Act
            response.SaveToFile(tempFile);
            var savedData = File.ReadAllBytes(tempFile);

            // Assert
            savedData.Should().BeEquivalentTo(audioData);
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
    public async Task SaveToFileAsync_WithEmptyPath_ShouldThrow()
    {
        // Arrange
        var response = new TTSResponse(new byte[] { 1 }, 1.0, AudioFormat.Wav);

        // Act & Assert
        await Assert.ThrowsAsync<ArgumentException>(() => response.SaveToFileAsync(""));
    }
}
