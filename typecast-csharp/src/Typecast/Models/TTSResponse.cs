namespace Typecast.Models;

/// <summary>
/// Response model for Text-to-Speech synthesis.
/// </summary>
public class TTSResponse
{
    /// <summary>
    /// The synthesized audio data as a byte array.
    /// </summary>
    public byte[] AudioData { get; }

    /// <summary>
    /// The duration of the audio in seconds.
    /// </summary>
    public double Duration { get; }

    /// <summary>
    /// The format of the audio data.
    /// </summary>
    public AudioFormat Format { get; }

    /// <summary>
    /// Creates a new TTSResponse.
    /// </summary>
    /// <param name="audioData">The audio data bytes</param>
    /// <param name="duration">The duration in seconds</param>
    /// <param name="format">The audio format</param>
    public TTSResponse(byte[] audioData, double duration, AudioFormat format)
    {
        AudioData = audioData ?? throw new ArgumentNullException(nameof(audioData));
        Duration = duration;
        Format = format;
    }

    /// <summary>
    /// Saves the audio data to a file.
    /// </summary>
    /// <param name="filePath">The path to save the audio file</param>
    /// <param name="cancellationToken">Cancellation token</param>
    public async Task SaveToFileAsync(string filePath, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(filePath))
        {
            throw new ArgumentException("File path is required.", nameof(filePath));
        }

#if NETSTANDARD2_0 || NETSTANDARD2_1
        await Task.Run(() => File.WriteAllBytes(filePath, AudioData), cancellationToken).ConfigureAwait(false);
#else
        await File.WriteAllBytesAsync(filePath, AudioData, cancellationToken).ConfigureAwait(false);
#endif
    }

    /// <summary>
    /// Saves the audio data to a file synchronously.
    /// </summary>
    /// <param name="filePath">The path to save the audio file</param>
    public void SaveToFile(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath))
        {
            throw new ArgumentException("File path is required.", nameof(filePath));
        }

        File.WriteAllBytes(filePath, AudioData);
    }

    /// <summary>
    /// Gets the audio data as a memory stream.
    /// </summary>
    /// <returns>A MemoryStream containing the audio data</returns>
    public MemoryStream ToStream()
    {
        return new MemoryStream(AudioData, writable: false);
    }

    /// <summary>
    /// Gets the suggested file extension for the audio format.
    /// </summary>
    public string FileExtension => Format switch
    {
        AudioFormat.Wav => ".wav",
        AudioFormat.Mp3 => ".mp3",
        _ => ".bin"
    };
}
