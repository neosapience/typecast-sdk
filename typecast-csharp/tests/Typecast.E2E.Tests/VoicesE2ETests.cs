using FluentAssertions;
using Typecast.Exceptions;
using Typecast.Models;
using Xunit;

namespace Typecast.E2E.Tests;

/// <summary>
/// End-to-end tests for the Voices API.
/// These tests require a valid TYPECAST_API_KEY environment variable.
/// </summary>
public class VoicesE2ETests : E2ETestBase
{
    [Fact]
    public async Task GetVoicesV2Async_ShouldReturnVoices()
    {
        // Act
        var voices = await Client.GetVoicesV2Async();

        // Assert
        voices.Should().NotBeNull();
        voices.Should().NotBeEmpty("API should return available voices");
        
        var firstVoice = voices.First();
        firstVoice.VoiceId.Should().NotBeNullOrWhiteSpace();
        firstVoice.VoiceName.Should().NotBeNullOrWhiteSpace();
        firstVoice.Models.Should().NotBeEmpty("Voice should have at least one model");
    }

    [Fact]
    public async Task GetVoicesV2Async_WithModelFilter_ShouldReturnFilteredVoices()
    {
        // Arrange
        var filter = new VoicesV2Filter { Model = TTSModel.SsfmV30 };

        // Act
        var voices = await Client.GetVoicesV2Async(filter);

        // Assert
        voices.Should().NotBeNull();
        voices.Should().NotBeEmpty("API should return voices for ssfm-v30 model");
        
        foreach (var voice in voices)
        {
            voice.Models.Should().Contain(m => m.Version == "ssfm-v30",
                "All voices should support the ssfm-v30 model");
        }
    }

    [Fact]
    public async Task GetVoicesV2Async_WithGenderFilter_ShouldReturnFilteredVoices()
    {
        // Arrange
        var filter = new VoicesV2Filter { Gender = GenderEnum.Female };

        // Act
        var voices = await Client.GetVoicesV2Async(filter);

        // Assert
        voices.Should().NotBeNull();
        if (voices.Count > 0)
        {
            foreach (var voice in voices.Where(v => v.Gender.HasValue))
            {
                voice.Gender.Should().Be(GenderEnum.Female,
                    "All voices with gender should be female");
            }
        }
    }

    [Fact]
    public async Task GetVoiceV2Async_WithValidVoiceId_ShouldReturnVoice()
    {
        // Arrange - First get a list of voices to get a valid voice ID
        var voices = await Client.GetVoicesV2Async();
        voices.Should().NotBeEmpty();
        var voiceId = voices.First().VoiceId;

        // Act
        var voice = await Client.GetVoiceV2Async(voiceId);

        // Assert
        voice.Should().NotBeNull();
        voice.VoiceId.Should().Be(voiceId);
        voice.VoiceName.Should().NotBeNullOrWhiteSpace();
    }

    [Fact]
    public async Task GetVoiceV2Async_WithInvalidVoiceId_ShouldThrowException()
    {
        // Arrange
        var invalidVoiceId = "invalid_voice_id_12345";

        // Act & Assert
        // API returns 400 (BadRequest) for invalid voice IDs, not 404
        var exception = await Assert.ThrowsAnyAsync<TypecastException>(
            () => Client.GetVoiceV2Async(invalidVoiceId));
        
        exception.Should().Match<TypecastException>(e => 
            e is BadRequestException || e is NotFoundException,
            "Should throw BadRequestException or NotFoundException for invalid voice ID");
    }

    [Fact]
    public void GetVoicesV2_Sync_ShouldReturnVoices()
    {
        // Act
        var voices = Client.GetVoicesV2();

        // Assert
        voices.Should().NotBeNull();
        voices.Should().NotBeEmpty("API should return available voices");
    }
}
