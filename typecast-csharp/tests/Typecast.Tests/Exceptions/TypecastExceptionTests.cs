using FluentAssertions;
using Typecast.Exceptions;
using Xunit;

namespace Typecast.Tests.Exceptions;

public class TypecastExceptionTests
{
    [Fact]
    public void TypecastException_ShouldSetProperties()
    {
        // Arrange & Act
        var exception = new TypecastException("Test error", 500, "Response body");

        // Assert
        exception.Message.Should().Be("Test error");
        exception.StatusCode.Should().Be(500);
        exception.ResponseBody.Should().Be("Response body");
    }

    [Theory]
    [InlineData(400, typeof(BadRequestException))]
    [InlineData(401, typeof(UnauthorizedException))]
    [InlineData(402, typeof(PaymentRequiredException))]
    [InlineData(404, typeof(NotFoundException))]
    [InlineData(422, typeof(UnprocessableEntityException))]
    [InlineData(429, typeof(RateLimitException))]
    [InlineData(500, typeof(InternalServerException))]
    [InlineData(502, typeof(InternalServerException))]
    [InlineData(503, typeof(InternalServerException))]
    public void FromStatusCode_ShouldReturnCorrectExceptionType(int statusCode, Type expectedType)
    {
        // Act
        var exception = TypecastException.FromStatusCode(statusCode, null);

        // Assert
        exception.Should().BeOfType(expectedType);
        exception.StatusCode.Should().Be(statusCode);
    }

    [Fact]
    public void FromStatusCode_WithUnknownStatusCode_ShouldReturnBaseException()
    {
        // Act
        var exception = TypecastException.FromStatusCode(418, null);

        // Assert
        exception.Should().BeOfType<TypecastException>();
        exception.StatusCode.Should().Be(418);
    }

    [Fact]
    public void FromStatusCode_WithResponseBody_ShouldIncludeInMessage()
    {
        // Act
        var exception = TypecastException.FromStatusCode(400, "Invalid parameter");

        // Assert
        exception.Message.Should().Contain("Invalid parameter");
        exception.ResponseBody.Should().Be("Invalid parameter");
    }

    [Fact]
    public void BadRequestException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new BadRequestException("Bad request");

        // Assert
        exception.StatusCode.Should().Be(400);
    }

    [Fact]
    public void UnauthorizedException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new UnauthorizedException("Unauthorized");

        // Assert
        exception.StatusCode.Should().Be(401);
    }

    [Fact]
    public void PaymentRequiredException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new PaymentRequiredException("Payment required");

        // Assert
        exception.StatusCode.Should().Be(402);
    }

    [Fact]
    public void NotFoundException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new NotFoundException("Not found");

        // Assert
        exception.StatusCode.Should().Be(404);
    }

    [Fact]
    public void UnprocessableEntityException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new UnprocessableEntityException("Validation error");

        // Assert
        exception.StatusCode.Should().Be(422);
    }

    [Fact]
    public void RateLimitException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new RateLimitException("Rate limit exceeded");

        // Assert
        exception.StatusCode.Should().Be(429);
    }

    [Fact]
    public void InternalServerException_ShouldHaveCorrectStatusCode()
    {
        // Act
        var exception = new InternalServerException("Internal server error");

        // Assert
        exception.StatusCode.Should().Be(500);
    }

    [Fact]
    public void InternalServerException_WithCustomStatusCode_ShouldUseProvidedStatusCode()
    {
        // Act
        var exception = new InternalServerException("Bad gateway", 502);

        // Assert
        exception.StatusCode.Should().Be(502);
    }

    [Fact]
    public void Exception_WithInnerException_ShouldPreserveInnerException()
    {
        // Arrange
        var innerException = new InvalidOperationException("Inner error");

        // Act
        var exception = new TypecastException("Outer error", 500, null, innerException);

        // Assert
        exception.InnerException.Should().Be(innerException);
    }
}
