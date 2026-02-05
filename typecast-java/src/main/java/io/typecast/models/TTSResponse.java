package io.typecast.models;

/**
 * Response object from Text-to-Speech synthesis.
 * 
 * <p>Contains the generated audio data along with metadata.</p>
 */
public class TTSResponse {
    private final byte[] audioData;
    private final double duration;
    private final String format;

    /**
     * Creates a new TTSResponse.
     * 
     * @param audioData the generated audio data as bytes
     * @param duration  the audio duration in seconds
     * @param format    the audio format (e.g., "wav", "mp3")
     */
    public TTSResponse(byte[] audioData, double duration, String format) {
        this.audioData = audioData;
        this.duration = duration;
        this.format = format;
    }

    /**
     * Gets the audio data.
     * 
     * @return the audio data as a byte array
     */
    public byte[] getAudioData() {
        return audioData;
    }

    /**
     * Gets the audio duration.
     * 
     * @return the duration in seconds
     */
    public double getDuration() {
        return duration;
    }

    /**
     * Gets the audio format.
     * 
     * @return the format string (e.g., "wav", "mp3")
     */
    public String getFormat() {
        return format;
    }

    /**
     * Gets the size of the audio data.
     * 
     * @return the size in bytes
     */
    public int getSize() {
        return audioData != null ? audioData.length : 0;
    }

    @Override
    public String toString() {
        return "TTSResponse{" +
                "size=" + getSize() + " bytes" +
                ", duration=" + duration + "s" +
                ", format='" + format + '\'' +
                '}';
    }
}
