using Typecast.Models;

namespace Typecast;

/// <summary>
/// Optional defaults or per-segment overrides for composed speech.
/// </summary>
public class ComposerSettings
{
    /// <summary>Voice ID. Browse available API voices at https://typecast.ai/developers/api/voices.</summary>
    public string? VoiceId { get; set; }

    /// <summary>Typecast TTS model to use for this segment. Defaults to <see cref="TTSModel.SsfmV30"/> when omitted.</summary>
    public TTSModel? Model { get; set; }

    /// <summary>Optional ISO 639-3 language code, such as <see cref="LanguageCode.Korean"/> or <see cref="LanguageCode.English"/>.</summary>
    public LanguageCode? Language { get; set; }

    /// <summary>Optional emotion/style prompt for this segment.</summary>
    public ITTSPrompt? Prompt { get; set; }

    /// <summary>Optional output controls such as pitch, tempo, volume, target LUFS, and requested format.</summary>
    public Output? Output { get; set; }

    /// <summary>Optional deterministic generation seed passed through to the Typecast API.</summary>
    public int? Seed { get; set; }
}

/// <summary>
/// A parsed text or pause part returned by <see cref="SpeechComposer.ParsePauseMarkup"/>.
/// </summary>
/// <param name="Text">Text content for speech parts, or an empty string for pause parts.</param>
/// <param name="PauseSeconds">Pause duration in seconds for pause parts.</param>
/// <param name="IsPause">Whether this part represents a pause token.</param>
public sealed record SpeechPart(string Text, double PauseSeconds, bool IsPause);

internal sealed record ComposerSpeechPart(string Text, ComposerSettings Settings);
internal sealed record ComposePart(TTSRequest? Request, double? PauseSeconds);

/// <summary>
/// Builder for composing multi-speaker speech and explicit pauses.
/// </summary>
public class SpeechComposer
{
    private readonly TypecastClient _client;
    private readonly List<object> _parts = new();
    private ComposerSettings _defaults = new();

    internal SpeechComposer(TypecastClient client)
    {
        _client = client;
    }

    /// <summary>Set defaults shared by following speech segments.</summary>
    /// <param name="settings">Defaults to apply to subsequent <see cref="Say(string, ComposerSettings?)"/> calls.</param>
    /// <returns>This composer so calls can be chained.</returns>
    public SpeechComposer Defaults(ComposerSettings settings)
    {
        _defaults = Merge(_defaults, settings);
        return this;
    }

    /// <summary>Add one speech segment. Overrides apply only to this segment.</summary>
    /// <param name="text">Text to synthesize for this segment.</param>
    /// <param name="overrides">Optional per-segment settings such as voice, pitch, prompt, tempo, and seed.</param>
    /// <returns>This composer so calls can be chained.</returns>
    public SpeechComposer Say(string text, ComposerSettings? overrides = null)
    {
        _parts.Add(new ComposerSpeechPart(text, overrides ?? new ComposerSettings()));
        return this;
    }

    /// <summary>Add an explicit silent pause in seconds.</summary>
    /// <param name="seconds">Pause duration in seconds. Use <c>0.3</c> for 300 ms or <c>3</c> for 3 seconds.</param>
    /// <returns>This composer so calls can be chained.</returns>
    /// <exception cref="ArgumentOutOfRangeException">Thrown when <paramref name="seconds"/> is negative.</exception>
    public SpeechComposer Pause(double seconds)
    {
        if (seconds < 0) throw new ArgumentOutOfRangeException(nameof(seconds), "Pause must be non-negative.");
        _parts.Add(seconds);
        return this;
    }

    /// <summary>
    /// Builds the individual Typecast TTS requests that will be sent for speech segments.
    /// </summary>
    /// <remarks>
    /// Each request uses WAV output for backward compatibility. This method is useful for testing, logging, and previewing
    /// the merged per-segment settings before generation.
    /// </remarks>
    /// <returns>The planned TTS requests, excluding pause-only parts.</returns>
    public IReadOnlyList<TTSRequest> SegmentRequests()
    {
        return _parts
            .OfType<ComposerSpeechPart>()
            .Select(speech => BuildRequest(speech))
            .ToList();
    }

    /// <summary>
    /// Generates composed speech with one Typecast Compose API request.
    /// </summary>
    /// <param name="outputFormat">
    /// Final output format.
    /// </param>
    /// <param name="cancellationToken">Cancellation token for the underlying Typecast API calls.</param>
    /// <returns>A <see cref="TTSResponse"/> containing the composed audio.</returns>
    /// <exception cref="InvalidOperationException">Thrown when no speech segment is present.</exception>
    public async Task<TTSResponse> GenerateAsync(AudioFormat outputFormat = AudioFormat.Wav, CancellationToken cancellationToken = default)
    {
        var segments = new List<ComposePart>();
        foreach (var part in _parts)
        {
            if (part is double pause)
            {
                segments.Add(new ComposePart(null, pause));
                continue;
            }

            if (part is not ComposerSpeechPart speech) continue;
            foreach (var parsed in ParsePauseMarkup(speech.Text))
            {
                if (parsed.IsPause)
                {
                    segments.Add(new ComposePart(null, parsed.PauseSeconds));
                }
                else if (!string.IsNullOrWhiteSpace(parsed.Text))
                {
                    segments.Add(new ComposePart(BuildRequest(new ComposerSpeechPart(parsed.Text, speech.Settings), outputFormat), null));
                }
            }
        }

        if (!segments.Any(segment => segment.Request is not null))
        {
            throw new InvalidOperationException("At least one speech segment is required.");
        }
        return await _client.ComposeTextToSpeechAsync(segments, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Synchronously generates composed speech.
    /// </summary>
    /// <param name="outputFormat">
    /// Final output format.
    /// </param>
    /// <returns>A <see cref="TTSResponse"/> containing the composed audio.</returns>
    /// <exception cref="InvalidOperationException">Thrown when no speech segment is present.</exception>
    public TTSResponse Generate(AudioFormat outputFormat = AudioFormat.Wav)
    {
        return GenerateAsync(outputFormat).GetAwaiter().GetResult();
    }

    /// <summary>
    /// Parses clear pause markup tokens such as <c>&lt;|3s|&gt;</c>,
    /// <c>&lt;|0.3s|&gt;</c>, or <c>&lt;|0.34413s|&gt;</c>.
    /// </summary>
    /// <param name="text">Input text that may contain pause markup.</param>
    /// <returns>Text and pause parts in source order. Invalid tokens remain in text parts.</returns>
    public static IReadOnlyList<SpeechPart> ParsePauseMarkup(string text)
    {
        var parts = new List<SpeechPart>();
        var textStart = 0;
        var searchStart = 0;
        while (searchStart < text.Length)
        {
            var start = text.IndexOf("<|", searchStart, StringComparison.Ordinal);
            if (start < 0) break;
            var valueStart = start + 2;
            var end = text.IndexOf("|>", valueStart, StringComparison.Ordinal);
            if (end < 0) break;
            var token = text.Substring(valueStart, end - valueStart);
            if (TryParsePauseToken(token, out var seconds))
            {
                parts.Add(new SpeechPart(text.Substring(textStart, start - textStart), 0, false));
                parts.Add(new SpeechPart("", seconds, true));
                textStart = end + 2;
                searchStart = textStart;
            }
            else
            {
                searchStart = valueStart;
            }
        }
        parts.Add(new SpeechPart(text.Substring(textStart), 0, false));
        return parts;
    }

    private TTSRequest BuildRequest(ComposerSpeechPart speech, AudioFormat outputFormat = AudioFormat.Wav)
    {
        var settings = Merge(_defaults, speech.Settings);
        if (string.IsNullOrWhiteSpace(settings.VoiceId))
        {
            throw new InvalidOperationException("VoiceId is required for composed speech segments.");
        }

        var output = CopyOutput(settings.Output) ?? new Output(volume: null, audioPitch: null, audioTempo: null, audioFormat: null);
        output.AudioFormat = outputFormat;
        return new TTSRequest(speech.Text, settings.VoiceId!, settings.Model ?? TTSModel.SsfmV30)
        {
            Language = settings.Language,
            Prompt = settings.Prompt,
            Output = output,
            Seed = settings.Seed
        };
    }

    private static ComposerSettings Merge(ComposerSettings baseSettings, ComposerSettings overrides)
    {
        return new ComposerSettings
        {
            VoiceId = overrides.VoiceId ?? baseSettings.VoiceId,
            Model = overrides.Model ?? baseSettings.Model,
            Language = overrides.Language ?? baseSettings.Language,
            Prompt = overrides.Prompt ?? baseSettings.Prompt,
            Output = MergeOutput(baseSettings.Output, overrides.Output),
            Seed = overrides.Seed ?? baseSettings.Seed
        };
    }

    private static Output? MergeOutput(Output? baseOutput, Output? overrideOutput)
    {
        if (baseOutput is null && overrideOutput is null) return null;
        var merged = CopyOutput(baseOutput) ?? new Output
        {
            Volume = null,
            AudioPitch = null,
            AudioTempo = null,
            AudioFormat = null,
            TargetLufs = null
        };
        if (overrideOutput is null) return merged;
        if (overrideOutput.Volume.HasValue) merged.Volume = overrideOutput.Volume;
        if (overrideOutput.AudioPitch.HasValue) merged.AudioPitch = overrideOutput.AudioPitch;
        if (overrideOutput.AudioTempo.HasValue) merged.AudioTempo = overrideOutput.AudioTempo;
        if (overrideOutput.AudioFormat.HasValue) merged.AudioFormat = overrideOutput.AudioFormat;
        if (overrideOutput.TargetLufs.HasValue) merged.TargetLufs = overrideOutput.TargetLufs;
        return merged;
    }

    private static Output? CopyOutput(Output? output)
    {
        if (output is null) return null;
        return new Output(output.Volume, output.AudioPitch, output.AudioTempo, output.AudioFormat, output.TargetLufs);
    }

    private static bool TryParsePauseToken(string token, out double seconds)
    {
        seconds = 0;
        if (!token.EndsWith("s", StringComparison.Ordinal) || token.Length < 2) return false;
        var number = token.Substring(0, token.Length - 1);
        if (number.Any(c => !char.IsDigit(c) && c != '.')) return false;
        return double.TryParse(number, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out seconds);
    }

}
