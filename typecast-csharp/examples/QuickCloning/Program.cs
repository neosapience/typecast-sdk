// Example: Quick Voice Cloning — clone a voice and synthesize speech with it.
//
// Prerequisites:
//   TYPECAST_API_KEY  — your Typecast API key
//   AUDIO_FILE        — path to a WAV or MP3 audio sample (max 25 MB)
//
// Usage:
//   dotnet run
using Typecast;
using Typecast.Exceptions;
using Typecast.Models;

var apiKey = Environment.GetEnvironmentVariable("TYPECAST_API_KEY")
    ?? throw new InvalidOperationException("TYPECAST_API_KEY environment variable is not set.");

var audioFile = Environment.GetEnvironmentVariable("AUDIO_FILE") ?? "sample.wav";

if (!File.Exists(audioFile))
{
    Console.Error.WriteLine($"Audio file not found: {audioFile}");
    Console.Error.WriteLine("Set the AUDIO_FILE environment variable to point to a WAV or MP3 file.");
    return 1;
}

using var client = new TypecastClient(new TypecastClientConfig { ApiKey = apiKey });

// ------------------------------------------------------------------
// 1. Clone the voice
// ------------------------------------------------------------------
Console.WriteLine($"Cloning voice from: {audioFile}");

CustomVoice cloned;
try
{
    cloned = await client.CloneVoiceAsync(
        audioFile: audioFile,
        name: "My Cloned Voice",  // 1–30 characters
        model: "ssfm-v30"
    );
}
catch (ArgumentException ex)
{
    Console.Error.WriteLine($"Validation error: {ex.Message}");
    return 1;
}
catch (TypecastException ex)
{
    Console.Error.WriteLine($"API error ({ex.StatusCode}): {ex.Message}");
    return 1;
}

Console.WriteLine($"Voice cloned successfully!");
Console.WriteLine($"  VoiceId : {cloned.VoiceId}");
Console.WriteLine($"  Name    : {cloned.Name}");
Console.WriteLine($"  Model   : {cloned.Model}");

// ------------------------------------------------------------------
// 2. Synthesize speech with the cloned voice
// ------------------------------------------------------------------
Console.WriteLine("\nSynthesizing speech with the cloned voice...");

var request = new TTSRequest(
    text: "Hello! This is my cloned voice speaking through the Typecast API.",
    voiceId: cloned.VoiceId,
    model: TTSModel.SsfmV30
)
{
    Language = LanguageCode.English
};

TTSResponse tts;
try
{
    tts = await client.TextToSpeechAsync(request);
}
catch (TypecastException ex)
{
    Console.Error.WriteLine($"TTS error ({ex.StatusCode}): {ex.Message}");
    // Clean up the cloned voice before exiting
    await client.DeleteVoiceAsync(cloned.VoiceId);
    return 1;
}

var outputPath = "/tmp/quick_cloning_csharp.wav";
await tts.SaveToFileAsync(outputPath);
Console.WriteLine($"Audio saved to: {outputPath} ({tts.Duration:F2}s, format={tts.Format})");

// ------------------------------------------------------------------
// 3. Delete the cloned voice
// ------------------------------------------------------------------
Console.WriteLine("\nDeleting the cloned voice...");
await client.DeleteVoiceAsync(cloned.VoiceId);
Console.WriteLine("Voice deleted.");

return 0;
