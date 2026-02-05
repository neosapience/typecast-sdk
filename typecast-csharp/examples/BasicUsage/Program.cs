using DotNetEnv;
using Typecast;
using Typecast.Exceptions;
using Typecast.Models;

// Load environment variables from .env file
Env.Load();

Console.WriteLine("Typecast C# SDK - Basic Usage Example");
Console.WriteLine("=====================================\n");

// Create client using environment variable
using var client = new TypecastClient();

try
{
    // Get available voices
    Console.WriteLine("Fetching available voices...\n");
    var voices = await client.GetVoicesV2Async(new VoicesV2Filter
    {
        Model = TTSModel.SsfmV30
    });

    Console.WriteLine($"Found {voices.Count} voices supporting ssfm-v30\n");

    if (voices.Count == 0)
    {
        Console.WriteLine("No voices available. Exiting.");
        return;
    }

    // Display first 5 voices
    Console.WriteLine("First 5 voices:");
    foreach (var voice in voices.Take(5))
    {
        Console.WriteLine($"  - {voice.VoiceId}: {voice.VoiceName} ({voice.Gender?.ToString() ?? "N/A"})");
    }
    Console.WriteLine();

    // Select first voice for synthesis
    var selectedVoice = voices.First();
    Console.WriteLine($"Using voice: {selectedVoice.VoiceName}");

    // Example 1: Basic synthesis
    Console.WriteLine("\n--- Example 1: Basic Synthesis ---");
    var basicRequest = new TTSRequest(
        text: "Hello! This is a test of the Typecast text-to-speech SDK.",
        voiceId: selectedVoice.VoiceId,
        model: TTSModel.SsfmV30
    )
    {
        Language = LanguageCode.English
    };

    var basicResponse = await client.TextToSpeechAsync(basicRequest);
    Console.WriteLine($"Audio generated: {basicResponse.AudioData.Length} bytes, {basicResponse.Duration:F2} seconds");
    
    await basicResponse.SaveToFileAsync("output_basic.wav");
    Console.WriteLine("Saved to: output_basic.wav");

    // Example 2: With emotion (preset mode)
    Console.WriteLine("\n--- Example 2: Preset Emotion ---");
    var emotionRequest = new TTSRequest(
        text: "I am so excited about this new technology!",
        voiceId: selectedVoice.VoiceId,
        model: TTSModel.SsfmV30
    )
    {
        Language = LanguageCode.English,
        Prompt = new PresetPrompt(EmotionPreset.Happy, emotionIntensity: 1.5)
    };

    var emotionResponse = await client.TextToSpeechAsync(emotionRequest);
    Console.WriteLine($"Audio generated: {emotionResponse.AudioData.Length} bytes, {emotionResponse.Duration:F2} seconds");
    
    await emotionResponse.SaveToFileAsync("output_emotion.wav");
    Console.WriteLine("Saved to: output_emotion.wav");

    // Example 3: With smart emotion
    Console.WriteLine("\n--- Example 3: Smart Emotion ---");
    var smartRequest = new TTSRequest(
        text: "I can't believe this happened.",
        voiceId: selectedVoice.VoiceId,
        model: TTSModel.SsfmV30
    )
    {
        Language = LanguageCode.English,
        Prompt = new SmartPrompt(
            previousText: "I was waiting for months, and finally...",
            nextText: "This is the best day ever!"
        )
    };

    var smartResponse = await client.TextToSpeechAsync(smartRequest);
    Console.WriteLine($"Audio generated: {smartResponse.AudioData.Length} bytes, {smartResponse.Duration:F2} seconds");
    
    await smartResponse.SaveToFileAsync("output_smart.wav");
    Console.WriteLine("Saved to: output_smart.wav");

    // Example 4: MP3 format with adjustments
    Console.WriteLine("\n--- Example 4: MP3 with Audio Adjustments ---");
    var mp3Request = new TTSRequest(
        text: "This audio has been adjusted with custom settings.",
        voiceId: selectedVoice.VoiceId,
        model: TTSModel.SsfmV30
    )
    {
        Language = LanguageCode.English,
        Output = new Output(
            volume: 120,
            audioPitch: 2,
            audioTempo: 1.1,
            audioFormat: AudioFormat.Mp3
        )
    };

    var mp3Response = await client.TextToSpeechAsync(mp3Request);
    Console.WriteLine($"Audio generated: {mp3Response.AudioData.Length} bytes, {mp3Response.Duration:F2} seconds");
    Console.WriteLine($"Format: {mp3Response.Format}");
    
    await mp3Response.SaveToFileAsync("output_adjusted.mp3");
    Console.WriteLine("Saved to: output_adjusted.mp3");

    Console.WriteLine("\n=====================================");
    Console.WriteLine("All examples completed successfully!");
}
catch (UnauthorizedException)
{
    Console.WriteLine("Error: Invalid API key. Please check your TYPECAST_API_KEY environment variable.");
}
catch (PaymentRequiredException)
{
    Console.WriteLine("Error: Insufficient credits. Please check your Typecast account.");
}
catch (TypecastException ex)
{
    Console.WriteLine($"API Error ({ex.StatusCode}): {ex.Message}");
}
catch (Exception ex)
{
    Console.WriteLine($"Error: {ex.Message}");
}
