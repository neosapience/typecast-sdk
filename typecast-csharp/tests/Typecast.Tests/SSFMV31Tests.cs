using System.Text.Json;
using Typecast.Models;
using Xunit;

namespace Typecast.Tests;

public class SSFMV31Tests
{
    [Fact]
    public void ModelRoundTripPreservesExistingValues()
    {
        Assert.Equal("ssfm-v31", TTSModel.SsfmV31.ToApiString());
        Assert.Equal(TTSModel.SsfmV31, TTSModelExtensions.ParseTTSModel("ssfm-v31"));
        Assert.Equal("\"ssfm-v31\"", JsonSerializer.Serialize(TTSModel.SsfmV31));
        Assert.Equal(TTSModel.SsfmV31, JsonSerializer.Deserialize<TTSModel>("\"ssfm-v31\""));
        Assert.Equal(0, (int)TTSModel.SsfmV21);
        Assert.Equal(1, (int)TTSModel.SsfmV30);
    }
}
