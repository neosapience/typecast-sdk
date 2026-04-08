using FluentAssertions;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests.Models;

public class SubscriptionTests
{
    [Theory]
    [InlineData(PlanTier.Free, "free")]
    [InlineData(PlanTier.Lite, "lite")]
    [InlineData(PlanTier.Plus, "plus")]
    [InlineData(PlanTier.Custom, "custom")]
    public void PlanTier_ToApiString_ShouldReturnCorrectValue(PlanTier tier, string expected)
    {
        tier.ToApiString().Should().Be(expected);
    }

    [Fact]
    public void PlanTier_ToApiString_InvalidValue_ShouldThrow()
    {
        Action act = () => ((PlanTier)999).ToApiString();
        act.Should().Throw<ArgumentOutOfRangeException>();
    }

    [Theory]
    [InlineData("free", PlanTier.Free)]
    [InlineData("LITE", PlanTier.Lite)]
    [InlineData("plus", PlanTier.Plus)]
    [InlineData("custom", PlanTier.Custom)]
    public void PlanTier_Parse_ShouldReturnCorrectEnum(string value, PlanTier expected)
    {
        PlanTierExtensions.ParsePlanTier(value).Should().Be(expected);
    }

    [Fact]
    public void PlanTier_Parse_InvalidValue_ShouldThrow()
    {
        Action act = () => PlanTierExtensions.ParsePlanTier("enterprise");
        act.Should().Throw<ArgumentException>();
    }

    [Fact]
    public void Credits_Properties_ShouldRoundTrip()
    {
        var credits = new Credits { PlanCredits = 1000L, UsedCredits = 250L };
        credits.PlanCredits.Should().Be(1000L);
        credits.UsedCredits.Should().Be(250L);
    }

    [Fact]
    public void Limits_Properties_ShouldRoundTrip()
    {
        var limits = new Limits { ConcurrencyLimit = 5 };
        limits.ConcurrencyLimit.Should().Be(5);
    }

    [Fact]
    public void SubscriptionResponse_Defaults_ShouldNotBeNull()
    {
        var response = new SubscriptionResponse();
        response.Plan.Should().Be(PlanTier.Free);
        response.Credits.Should().NotBeNull();
        response.Limits.Should().NotBeNull();
    }

    [Fact]
    public void SubscriptionResponse_Properties_ShouldRoundTrip()
    {
        var response = new SubscriptionResponse
        {
            Plan = PlanTier.Plus,
            Credits = new Credits { PlanCredits = 10000L, UsedCredits = 1234L },
            Limits = new Limits { ConcurrencyLimit = 10 }
        };

        response.Plan.Should().Be(PlanTier.Plus);
        response.Credits.PlanCredits.Should().Be(10000L);
        response.Credits.UsedCredits.Should().Be(1234L);
        response.Limits.ConcurrencyLimit.Should().Be(10);
    }
}
