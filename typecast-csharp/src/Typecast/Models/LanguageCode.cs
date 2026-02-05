using System.Text.Json.Serialization;

namespace Typecast.Models;

/// <summary>
/// Supported language codes (ISO 639-3 format).
/// </summary>
[JsonConverter(typeof(JsonStringEnumMemberConverter))]
public enum LanguageCode
{
    /// <summary>Korean</summary>
    [JsonPropertyName("kor")] Korean,
    
    /// <summary>English</summary>
    [JsonPropertyName("eng")] English,
    
    /// <summary>Japanese</summary>
    [JsonPropertyName("jpn")] Japanese,
    
    /// <summary>Chinese (Mandarin)</summary>
    [JsonPropertyName("cmn")] Chinese,
    
    /// <summary>Spanish</summary>
    [JsonPropertyName("spa")] Spanish,
    
    /// <summary>French</summary>
    [JsonPropertyName("fra")] French,
    
    /// <summary>German</summary>
    [JsonPropertyName("deu")] German,
    
    /// <summary>Italian</summary>
    [JsonPropertyName("ita")] Italian,
    
    /// <summary>Portuguese</summary>
    [JsonPropertyName("por")] Portuguese,
    
    /// <summary>Russian</summary>
    [JsonPropertyName("rus")] Russian,
    
    /// <summary>Hindi</summary>
    [JsonPropertyName("hin")] Hindi,
    
    /// <summary>Vietnamese</summary>
    [JsonPropertyName("vie")] Vietnamese,
    
    /// <summary>Thai</summary>
    [JsonPropertyName("tha")] Thai,
    
    /// <summary>Indonesian</summary>
    [JsonPropertyName("ind")] Indonesian,
    
    /// <summary>Arabic</summary>
    [JsonPropertyName("ara")] Arabic,
    
    /// <summary>Dutch</summary>
    [JsonPropertyName("nld")] Dutch,
    
    /// <summary>Polish</summary>
    [JsonPropertyName("pol")] Polish,
    
    /// <summary>Swedish</summary>
    [JsonPropertyName("swe")] Swedish,
    
    /// <summary>Turkish</summary>
    [JsonPropertyName("tur")] Turkish,
    
    /// <summary>Ukrainian</summary>
    [JsonPropertyName("ukr")] Ukrainian,
    
    /// <summary>Czech</summary>
    [JsonPropertyName("ces")] Czech,
    
    /// <summary>Danish</summary>
    [JsonPropertyName("dan")] Danish,
    
    /// <summary>Finnish</summary>
    [JsonPropertyName("fin")] Finnish,
    
    /// <summary>Greek</summary>
    [JsonPropertyName("ell")] Greek,
    
    /// <summary>Hebrew</summary>
    [JsonPropertyName("heb")] Hebrew,
    
    /// <summary>Hungarian</summary>
    [JsonPropertyName("hun")] Hungarian,
    
    /// <summary>Norwegian</summary>
    [JsonPropertyName("nor")] Norwegian,
    
    /// <summary>Romanian</summary>
    [JsonPropertyName("ron")] Romanian,
    
    /// <summary>Slovak</summary>
    [JsonPropertyName("slk")] Slovak,
    
    /// <summary>Malay</summary>
    [JsonPropertyName("msa")] Malay,
    
    /// <summary>Filipino/Tagalog</summary>
    [JsonPropertyName("fil")] Filipino,
    
    /// <summary>Bengali</summary>
    [JsonPropertyName("ben")] Bengali,
    
    /// <summary>Tamil</summary>
    [JsonPropertyName("tam")] Tamil,
    
    /// <summary>Telugu</summary>
    [JsonPropertyName("tel")] Telugu,
    
    /// <summary>Cantonese</summary>
    [JsonPropertyName("yue")] Cantonese,
    
    /// <summary>Catalan</summary>
    [JsonPropertyName("cat")] Catalan,
    
    /// <summary>Croatian</summary>
    [JsonPropertyName("hrv")] Croatian
}

/// <summary>
/// Extension methods for LanguageCode enum.
/// </summary>
public static class LanguageCodeExtensions
{
    private static readonly Dictionary<LanguageCode, string> LanguageCodeMap = new()
    {
        { LanguageCode.Korean, "kor" },
        { LanguageCode.English, "eng" },
        { LanguageCode.Japanese, "jpn" },
        { LanguageCode.Chinese, "cmn" },
        { LanguageCode.Spanish, "spa" },
        { LanguageCode.French, "fra" },
        { LanguageCode.German, "deu" },
        { LanguageCode.Italian, "ita" },
        { LanguageCode.Portuguese, "por" },
        { LanguageCode.Russian, "rus" },
        { LanguageCode.Hindi, "hin" },
        { LanguageCode.Vietnamese, "vie" },
        { LanguageCode.Thai, "tha" },
        { LanguageCode.Indonesian, "ind" },
        { LanguageCode.Arabic, "ara" },
        { LanguageCode.Dutch, "nld" },
        { LanguageCode.Polish, "pol" },
        { LanguageCode.Swedish, "swe" },
        { LanguageCode.Turkish, "tur" },
        { LanguageCode.Ukrainian, "ukr" },
        { LanguageCode.Czech, "ces" },
        { LanguageCode.Danish, "dan" },
        { LanguageCode.Finnish, "fin" },
        { LanguageCode.Greek, "ell" },
        { LanguageCode.Hebrew, "heb" },
        { LanguageCode.Hungarian, "hun" },
        { LanguageCode.Norwegian, "nor" },
        { LanguageCode.Romanian, "ron" },
        { LanguageCode.Slovak, "slk" },
        { LanguageCode.Malay, "msa" },
        { LanguageCode.Filipino, "fil" },
        { LanguageCode.Bengali, "ben" },
        { LanguageCode.Tamil, "tam" },
        { LanguageCode.Telugu, "tel" },
        { LanguageCode.Cantonese, "yue" },
        { LanguageCode.Catalan, "cat" },
        { LanguageCode.Croatian, "hrv" }
    };

    private static readonly Dictionary<string, LanguageCode> ReverseLanguageCodeMap =
        LanguageCodeMap.ToDictionary(kvp => kvp.Value, kvp => kvp.Key);

    /// <summary>
    /// Converts the LanguageCode to its ISO 639-3 string representation.
    /// </summary>
    public static string ToApiString(this LanguageCode code) =>
        LanguageCodeMap.TryGetValue(code, out var value) ? value : throw new ArgumentOutOfRangeException(nameof(code));

    /// <summary>
    /// Parses an ISO 639-3 string to LanguageCode enum.
    /// </summary>
    public static LanguageCode ParseLanguageCode(string value) =>
        ReverseLanguageCodeMap.TryGetValue(value, out var code) ? code : throw new ArgumentException($"Unknown language code: {value}", nameof(value));
}
