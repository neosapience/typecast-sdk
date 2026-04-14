// Streaming TTS integration test for typecast-csharp SDK.
using Typecast;
using Typecast.Models;

const string ApiKey = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3";
const string Host = "https://api.icepeak.in";
const string VoiceId = "tc_68d259f809700d8ac76e8567";
const string OutputFile = "/tmp/streaming_test_csharp.wav";

var config = new TypecastClientConfig
{
    ApiKey = ApiKey,
    ApiHost = Host,
};
var client = new TypecastClient(config);

var request = new TTSRequestStream
{
    VoiceId = VoiceId,
    Text = "Hello, this is a streaming integration test from the C# SDK.",
    Model = "ssfm-v30",
    Language = "eng",
    Output = new Typecast.Models.OutputStream
    {
        AudioFormat = AudioFormat.Wav,
    },
};

Console.WriteLine("[C#] Calling TextToSpeechStreamAsync...");
using var stream = await client.TextToSpeechStreamAsync(request);
using var fileStream = File.Create(OutputFile);

var buffer = new byte[8192];
int totalBytes = 0;
int chunkCount = 0;
int bytesRead;

while ((bytesRead = await stream.ReadAsync(buffer)) > 0)
{
    await fileStream.WriteAsync(buffer.AsMemory(0, bytesRead));
    totalBytes += bytesRead;
    chunkCount++;
}

Console.WriteLine($"[C#] SUCCESS - {chunkCount} chunks, {totalBytes} bytes -> {OutputFile}");
if (totalBytes == 0) throw new Exception("No audio data received");
