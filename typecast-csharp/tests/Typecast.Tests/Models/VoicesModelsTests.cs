using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class VoicesModelsTests
{
    [Fact]
    public void VoicesV2Filter_ToQueryParameters_WithNoFilters_ShouldReturnEmpty()
    {
        // Arrange
        var filter = new VoicesV2Filter();

        // Act
        var parameters = filter.ToQueryParameters();

        // Assert
        parameters.Should().BeEmpty();
    }

    [Fact]
    public void VoicesV2Filter_ToQueryParameters_WithModel_ShouldIncludeModelParam()
    {
        // Arrange
        var filter = new VoicesV2Filter { Model = TTSModel.SsfmV30 };

        // Act
        var parameters = filter.ToQueryParameters();

        // Assert
        parameters.Should().ContainKey("model");
        parameters["model"].Should().Be("ssfm-v30");
    }

    [Fact]
    public void VoicesV2Filter_ToQueryParameters_WithGender_ShouldIncludeGenderParam()
    {
        // Arrange
        var filter = new VoicesV2Filter { Gender = GenderEnum.Female };

        // Act
        var parameters = filter.ToQueryParameters();

        // Assert
        parameters.Should().ContainKey("gender");
        parameters["gender"].Should().Be("female");
    }

    [Fact]
    public void VoicesV2Filter_ToQueryParameters_WithAge_ShouldIncludeAgeParam()
    {
        // Arrange
        var filter = new VoicesV2Filter { Age = AgeEnum.YoungAdult };

        // Act
        var parameters = filter.ToQueryParameters();

        // Assert
        parameters.Should().ContainKey("age");
        parameters["age"].Should().Be("young_adult");
    }

    [Fact]
    public void VoicesV2Filter_ToQueryParameters_WithUseCase_ShouldIncludeUseCaseParam()
    {
        // Arrange
        var filter = new VoicesV2Filter { UseCase = UseCaseEnum.Game };

        // Act
        var parameters = filter.ToQueryParameters();

        // Assert
        parameters.Should().ContainKey("use_cases");
        parameters["use_cases"].Should().Be("game");
    }

    [Fact]
    public void VoicesV2Filter_ToQueryParameters_WithAllFilters_ShouldIncludeAllParams()
    {
        // Arrange
        var filter = new VoicesV2Filter
        {
            Model = TTSModel.SsfmV30,
            Gender = GenderEnum.Male,
            Age = AgeEnum.MiddleAge,
            UseCase = UseCaseEnum.News
        };

        // Act
        var parameters = filter.ToQueryParameters();

        // Assert
        parameters.Should().HaveCount(4);
        parameters["model"].Should().Be("ssfm-v30");
        parameters["gender"].Should().Be("male");
        parameters["age"].Should().Be("middle_age");
        parameters["use_cases"].Should().Be("news");
    }

    [Fact]
    public void VoiceV2Response_DefaultValues_ShouldBeInitialized()
    {
        // Arrange & Act
        var response = new VoiceV2Response();

        // Assert
        response.VoiceId.Should().BeEmpty();
        response.VoiceName.Should().BeEmpty();
        response.Models.Should().NotBeNull();
        response.Models.Should().BeEmpty();
    }

    [Fact]
    public void ModelInfo_DefaultValues_ShouldBeInitialized()
    {
        // Arrange & Act
        var modelInfo = new ModelInfo();

        // Assert
        modelInfo.Version.Should().BeEmpty();
        modelInfo.Emotions.Should().NotBeNull();
        modelInfo.Emotions.Should().BeEmpty();
    }
}
