using FluentAssertions;
using Xunit;

namespace Typecast.Tests;

public class TypecastClientConfigTests
{
    [Fact]
    public void GetEffectiveApiKey_WithApiKeySet_ShouldReturnApiKey()
    {
        // Arrange
        var config = new TypecastClientConfig { ApiKey = "test-api-key" };

        // Act
        var apiKey = config.GetEffectiveApiKey();

        // Assert
        apiKey.Should().Be("test-api-key");
    }

    [Fact]
    public void GetEffectiveApiKey_WithEnvironmentVariable_ShouldReturnEnvVar()
    {
        // Arrange
        var originalValue = Environment.GetEnvironmentVariable(TypecastClientConfig.ApiKeyEnvVar);
        try
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiKeyEnvVar, "env-api-key");
            var config = new TypecastClientConfig();

            // Act
            var apiKey = config.GetEffectiveApiKey();

            // Assert
            apiKey.Should().Be("env-api-key");
        }
        finally
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiKeyEnvVar, originalValue);
        }
    }

    [Fact]
    public void GetEffectiveApiKey_WithNoApiKey_ShouldThrow()
    {
        // Arrange
        var originalValue = Environment.GetEnvironmentVariable(TypecastClientConfig.ApiKeyEnvVar);
        try
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiKeyEnvVar, null);
            var config = new TypecastClientConfig();

            // Act & Assert
            config.Invoking(c => c.GetEffectiveApiKey())
                .Should().Throw<InvalidOperationException>()
                .WithMessage("*API key*");
        }
        finally
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiKeyEnvVar, originalValue);
        }
    }

    [Fact]
    public void GetEffectiveApiHost_WithApiHostSet_ShouldReturnApiHost()
    {
        // Arrange
        var config = new TypecastClientConfig { ApiHost = "https://custom.api.com" };

        // Act
        var apiHost = config.GetEffectiveApiHost();

        // Assert
        apiHost.Should().Be("https://custom.api.com");
    }

    [Fact]
    public void GetEffectiveApiHost_WithEnvironmentVariable_ShouldReturnEnvVar()
    {
        // Arrange
        var originalValue = Environment.GetEnvironmentVariable(TypecastClientConfig.ApiHostEnvVar);
        try
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiHostEnvVar, "https://env.api.com");
            var config = new TypecastClientConfig();

            // Act
            var apiHost = config.GetEffectiveApiHost();

            // Assert
            apiHost.Should().Be("https://env.api.com");
        }
        finally
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiHostEnvVar, originalValue);
        }
    }

    [Fact]
    public void GetEffectiveApiHost_WithNoApiHost_ShouldReturnDefault()
    {
        // Arrange
        var originalValue = Environment.GetEnvironmentVariable(TypecastClientConfig.ApiHostEnvVar);
        try
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiHostEnvVar, null);
            var config = new TypecastClientConfig();

            // Act
            var apiHost = config.GetEffectiveApiHost();

            // Assert
            apiHost.Should().Be(TypecastClientConfig.DefaultApiHost);
        }
        finally
        {
            Environment.SetEnvironmentVariable(TypecastClientConfig.ApiHostEnvVar, originalValue);
        }
    }

    [Fact]
    public void DefaultTimeout_ShouldBe30Seconds()
    {
        // Arrange
        var config = new TypecastClientConfig();

        // Assert
        config.TimeoutSeconds.Should().Be(30);
    }

    [Fact]
    public void Constants_ShouldHaveCorrectValues()
    {
        // Assert
        TypecastClientConfig.DefaultApiHost.Should().Be("https://api.typecast.ai");
        TypecastClientConfig.ApiKeyEnvVar.Should().Be("TYPECAST_API_KEY");
        TypecastClientConfig.ApiHostEnvVar.Should().Be("TYPECAST_API_HOST");
    }
}
