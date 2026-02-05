package io.typecast.models;

/**
 * Emotion presets for TTS synthesis.
 * 
 * <p>SSFM v2.1 supports: normal, happy, sad, angry</p>
 * <p>SSFM v3.0 supports all presets including: whisper, toneup, tonedown</p>
 */
public enum EmotionPreset {
    /** Normal emotion (neutral) */
    NORMAL("normal"),
    /** Happy emotion */
    HAPPY("happy"),
    /** Sad emotion */
    SAD("sad"),
    /** Angry emotion */
    ANGRY("angry"),
    /** Whisper style (SSFM v3.0 only) */
    WHISPER("whisper"),
    /** Tone up style (SSFM v3.0 only) */
    TONEUP("toneup"),
    /** Tone down style (SSFM v3.0 only) */
    TONEDOWN("tonedown");

    private final String value;

    EmotionPreset(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the emotion preset.
     * 
     * @return the emotion preset string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates an EmotionPreset from a string value.
     * 
     * @param value the emotion preset string
     * @return the corresponding EmotionPreset
     * @throws IllegalArgumentException if the value doesn't match any preset
     */
    public static EmotionPreset fromValue(String value) {
        for (EmotionPreset preset : values()) {
            if (preset.value.equalsIgnoreCase(value)) {
                return preset;
            }
        }
        throw new IllegalArgumentException("Unknown emotion preset: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
