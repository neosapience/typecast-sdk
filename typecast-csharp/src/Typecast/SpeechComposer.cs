using Typecast.Exceptions;
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
    /// Each request is forced to WAV output so generated segments can be trimmed
    /// and concatenated. This method is useful for testing, logging, and previewing
    /// the merged per-segment settings before generation.
    /// </remarks>
    /// <returns>The planned TTS requests, excluding pause-only parts.</returns>
    public IReadOnlyList<TTSRequest> SegmentRequests()
    {
        return _parts
            .OfType<ComposerSpeechPart>()
            .Select(BuildRequest)
            .ToList();
    }

    /// <summary>
    /// Generates composed speech by synthesizing each segment, trimming leading and trailing
    /// silent PCM samples, and inserting explicit pauses between segments.
    /// </summary>
    /// <param name="outputFormat">
    /// Final output format. Only <see cref="AudioFormat.Wav"/> is supported by the C# SDK composer.
    /// </param>
    /// <param name="cancellationToken">Cancellation token for the underlying Typecast API calls.</param>
    /// <returns>A WAV <see cref="TTSResponse"/> containing the composed audio.</returns>
    /// <exception cref="TypecastException">Thrown when MP3 output is requested.</exception>
    /// <exception cref="InvalidOperationException">Thrown when no speech segment is present or WAV segments are incompatible.</exception>
    public async Task<TTSResponse> GenerateAsync(AudioFormat outputFormat = AudioFormat.Wav, CancellationToken cancellationToken = default)
    {
        if (outputFormat == AudioFormat.Mp3)
        {
            throw new TypecastException("MP3 conversion is not available for composed speech in the C# SDK.");
        }

        var segments = new List<byte[]>();
        var pauses = new List<double>();
        var pendingPause = 0.0;
        var hasAudio = false;
        foreach (var part in _parts)
        {
            if (part is double pause)
            {
                pendingPause += pause;
                continue;
            }

            if (part is not ComposerSpeechPart speech) continue;
            if (hasAudio) pauses.Add(pendingPause);
            pendingPause = 0.0;
            var response = await _client.TextToSpeechAsync(BuildRequest(speech), cancellationToken).ConfigureAwait(false);
            segments.Add(response.AudioData);
            hasAudio = true;
        }

        if (segments.Count == 0)
        {
            throw new InvalidOperationException("At least one speech segment is required.");
        }

        var audio = ComposeWav(segments, pauses);
        var info = ParseWav(audio);
        return new TTSResponse(audio, info.PcmLength / 2.0 / info.SampleRate, AudioFormat.Wav);
    }

    /// <summary>
    /// Synchronously generates composed speech.
    /// </summary>
    /// <param name="outputFormat">
    /// Final output format. Only <see cref="AudioFormat.Wav"/> is supported by the C# SDK composer.
    /// </param>
    /// <returns>A WAV <see cref="TTSResponse"/> containing the composed audio.</returns>
    /// <exception cref="TypecastException">Thrown when MP3 output is requested.</exception>
    /// <exception cref="InvalidOperationException">Thrown when no speech segment is present or WAV segments are incompatible.</exception>
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

    private TTSRequest BuildRequest(ComposerSpeechPart speech)
    {
        var settings = Merge(_defaults, speech.Settings);
        if (string.IsNullOrWhiteSpace(settings.VoiceId))
        {
            throw new InvalidOperationException("VoiceId is required for composed speech segments.");
        }

        var output = CopyOutput(settings.Output) ?? new Output(volume: null, audioPitch: null, audioTempo: null, audioFormat: null);
        output.AudioFormat = AudioFormat.Wav;
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

    private readonly record struct WavInfo(int SampleRate, int PcmStart, int PcmLength);

    private static byte[] ComposeWav(IReadOnlyList<byte[]> wavs, IReadOnlyList<double> pauses)
    {
        if (wavs.Count == 0 || pauses.Count + 1 != wavs.Count) throw new InvalidOperationException("Invalid composed speech parts.");
        var infos = wavs.Select(ParseWav).ToList();
        var sampleRate = infos[0].SampleRate;
        if (infos.Any(i => i.SampleRate != sampleRate)) throw new InvalidOperationException("WAV segment sample rates must match.");

        var ranges = new List<(int Start, int End)>();
        var totalSamples = 0;
        for (var i = 0; i < wavs.Count; i++)
        {
            var info = infos[i];
            var sampleCount = info.PcmLength / 2;
            var start = 0;
            var end = sampleCount;
            while (start < end && ReadInt16(wavs[i], info.PcmStart + start * 2) == 0) start++;
            while (end > start && ReadInt16(wavs[i], info.PcmStart + (end - 1) * 2) == 0) end--;
            ranges.Add((start, end));
            totalSamples += end - start;
            if (i < pauses.Count) totalSamples += (int)Math.Round(pauses[i] * sampleRate);
        }

        var output = new byte[44 + totalSamples * 2];
        WriteWavHeader(output, sampleRate, totalSamples * 2);
        var cursor = 44;
        for (var i = 0; i < wavs.Count; i++)
        {
            var info = infos[i];
            var range = ranges[i];
            var bytes = (range.End - range.Start) * 2;
            Buffer.BlockCopy(wavs[i], info.PcmStart + range.Start * 2, output, cursor, bytes);
            cursor += bytes;
            if (i < pauses.Count) cursor += (int)Math.Round(pauses[i] * sampleRate) * 2;
        }
        return output;
    }

    private static WavInfo ParseWav(byte[] wav)
    {
        if (wav.Length < 44 || ReadAscii(wav, 0, 4) != "RIFF" || ReadAscii(wav, 8, 4) != "WAVE")
            throw new InvalidOperationException("Composed speech requires WAV audio.");
        if (BitConverter.ToInt16(wav, 20) != 1 || BitConverter.ToInt16(wav, 22) != 1 || BitConverter.ToInt16(wav, 34) != 16)
            throw new InvalidOperationException("Composed speech requires mono 16-bit PCM WAV segments.");
        var cursor = 36;
        while (cursor + 8 <= wav.Length)
        {
            var id = ReadAscii(wav, cursor, 4);
            var size = BitConverter.ToInt32(wav, cursor + 4);
            if (cursor + 8 + size > wav.Length) throw new InvalidOperationException("Invalid WAV data.");
            if (id == "data") return new WavInfo(BitConverter.ToInt32(wav, 24), cursor + 8, size);
            cursor += 8 + size;
        }
        throw new InvalidOperationException("WAV data chunk is missing.");
    }

    private static short ReadInt16(byte[] data, int offset) => BitConverter.ToInt16(data, offset);
    private static string ReadAscii(byte[] data, int offset, int count) => System.Text.Encoding.ASCII.GetString(data, offset, count);

    private static void WriteWavHeader(byte[] output, int sampleRate, int dataLength)
    {
        System.Text.Encoding.ASCII.GetBytes("RIFF").CopyTo(output, 0);
        BitConverter.GetBytes(36 + dataLength).CopyTo(output, 4);
        System.Text.Encoding.ASCII.GetBytes("WAVEfmt ").CopyTo(output, 8);
        BitConverter.GetBytes(16).CopyTo(output, 16);
        BitConverter.GetBytes((short)1).CopyTo(output, 20);
        BitConverter.GetBytes((short)1).CopyTo(output, 22);
        BitConverter.GetBytes(sampleRate).CopyTo(output, 24);
        BitConverter.GetBytes(sampleRate * 2).CopyTo(output, 28);
        BitConverter.GetBytes((short)2).CopyTo(output, 32);
        BitConverter.GetBytes((short)16).CopyTo(output, 34);
        System.Text.Encoding.ASCII.GetBytes("data").CopyTo(output, 36);
        BitConverter.GetBytes(dataLength).CopyTo(output, 40);
    }
}
