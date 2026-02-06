package com.neosapience.models;

/**
 * Audio output format enumeration.
 */
public enum AudioFormat {
    /** WAV format (default) */
    WAV("wav"),
    /** MP3 format */
    MP3("mp3");

    private final String value;

    AudioFormat(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the audio format.
     * 
     * @return the audio format string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates an AudioFormat from a string value.
     * 
     * @param value the audio format string
     * @return the corresponding AudioFormat
     * @throws IllegalArgumentException if the value doesn't match any format
     */
    public static AudioFormat fromValue(String value) {
        for (AudioFormat format : values()) {
            if (format.value.equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown audio format: " + value);
    }

    /**
     * Returns the MIME type for this audio format.
     * 
     * @return the MIME type string
     */
    public String getMimeType() {
        return "audio/" + value;
    }

    @Override
    public String toString() {
        return value;
    }
}
