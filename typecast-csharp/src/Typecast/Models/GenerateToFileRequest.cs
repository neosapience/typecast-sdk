namespace Typecast.Models;

/// <summary>
/// Convenience request for generating speech directly to a file.
/// </summary>
public class GenerateToFileRequest
{
    /// <summary>
    /// The text to synthesize.
    /// </summary>
    public string Text { get; set; } = string.Empty;

    /// <summary>
    /// The voice ID to use for synthesis.
    /// Browse available API voices at https://typecast.ai/developers/api/voices.
    /// </summary>
    public string VoiceId { get; set; } = string.Empty;

    /// <summary>
    /// The TTS model to use. Defaults to SSFM v3.0.
    /// </summary>
    public TTSModel Model { get; set; } = TTSModel.SsfmV30;

    /// <summary>
    /// Optional language code for synthesis.
    /// </summary>
    public LanguageCode? Language { get; set; }

    /// <summary>
    /// Optional emotion/prompt configuration.
    /// </summary>
    public ITTSPrompt? Prompt { get; set; }

    /// <summary>
    /// Optional audio output configuration.
    /// </summary>
    public Output? Output { get; set; }

    /// <summary>
    /// Optional random seed for reproducible synthesis.
    /// </summary>
    public int? Seed { get; set; }

    /// <summary>
    /// Converts this convenience request to a TTS request.
    /// </summary>
    public TTSRequest ToTTSRequest(string filePath)
    {
        var inferred = InferOutput(filePath);
        var output = Output;
        if (output is null)
        {
            output = inferred;
        }
        else if (output.AudioFormat is null && inferred?.AudioFormat is not null)
        {
            output.AudioFormat = inferred.AudioFormat;
        }
        return new TTSRequest(Text, VoiceId, Model)
        {
            Language = Language,
            Prompt = Prompt,
            Output = output,
            Seed = Seed
        };
    }

    private static Output? InferOutput(string filePath)
    {
        return Path.GetExtension(filePath).ToLowerInvariant() switch
        {
            ".mp3" => new Output(audioFormat: AudioFormat.Mp3),
            ".wav" => new Output(audioFormat: AudioFormat.Wav),
            _ => null
        };
    }
}
