using System;
using System.Text.Json;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class RemoveSilenceTests
{
    [Fact]
    public void PreservesZeroAndValidatesRange()
    {
        Assert.DoesNotContain("remove_silence_ms", JsonSerializer.Serialize(new Output()));
        Assert.DoesNotContain("remove_silence_ms", JsonSerializer.Serialize(new OutputStream()));
        foreach (var value in new[] { 0, 300, 1000 })
        {
            var output = new Output { RemoveSilenceMs = value };
            var stream = new OutputStream { RemoveSilenceMs = value };
            output.Validate();
            stream.Validate();
            Assert.Contains("\"remove_silence_ms\":" + value, JsonSerializer.Serialize(output));
            Assert.Contains("\"remove_silence_ms\":" + value, JsonSerializer.Serialize(stream));
        }
        foreach (var value in new[] { -1, 1001 })
        {
            Assert.Throws<ArgumentOutOfRangeException>(() => new Output { RemoveSilenceMs = value }.Validate());
            Assert.Throws<ArgumentOutOfRangeException>(() => new OutputStream { RemoveSilenceMs = value }.Validate());
        }
    }
}
