// Example: text-to-speech with word/character timestamps, SRT and VTT export.
using Typecast;
using Typecast.Models;

var apiKey = Environment.GetEnvironmentVariable("TYPECAST_API_KEY")
    ?? throw new InvalidOperationException("TYPECAST_API_KEY not set");

using var client = new TypecastClient(new TypecastClientConfig { ApiKey = apiKey });

var request = new TTSRequestWithTimestamps(
    voiceId: "tc_60e5426de8b95f1d3000d7b5",
    text: "Hello. How are you?",
    model: TTSModel.SsfmV30
)
{
    Language = LanguageCode.English
};

var resp = await client.TextToSpeechWithTimestampsAsync(request);

await resp.SaveAudioAsync("/tmp/with_timestamps_csharp.wav");
await File.WriteAllTextAsync("/tmp/with_timestamps_csharp.srt", resp.ToSrt());
await File.WriteAllTextAsync("/tmp/with_timestamps_csharp.vtt", resp.ToVtt());

Console.WriteLine($"audio: /tmp/with_timestamps_csharp.wav ({resp.AudioDuration:F2}s, format={resp.AudioFormat})");
int wordCount = resp.Words?.Count ?? 0;
int charCount = resp.Characters?.Count ?? 0;
Console.WriteLine($"words: {wordCount}, characters: {charCount}");
var firstCue = string.Join("\n", resp.ToSrt().Split('\n').Take(4));
Console.WriteLine($"SRT first cue:\n{firstCue}");
