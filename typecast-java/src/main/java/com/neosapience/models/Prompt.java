package com.neosapience.models;

/**
 * Base emotion prompt for TTS synthesis (compatible with SSFM v2.1).
 * 
 * <p>For SSFM v3.0, use {@link PresetPrompt} or {@link SmartPrompt} instead.</p>
 */
public class Prompt {
    private EmotionPreset emotionPreset;
    private Double emotionIntensity;

    /**
     * Creates a new Prompt with default values (normal emotion, intensity 1.0).
     */
    public Prompt() {
        this.emotionPreset = EmotionPreset.NORMAL;
        this.emotionIntensity = 1.0;
    }

    /**
     * Creates a new Prompt with specified values.
     * 
     * @param emotionPreset    the emotion preset to use
     * @param emotionIntensity the emotion intensity (0.0 to 2.0)
     */
    public Prompt(EmotionPreset emotionPreset, Double emotionIntensity) {
        this.emotionPreset = emotionPreset;
        this.emotionIntensity = emotionIntensity;
    }

    /**
     * Gets the emotion preset.
     * 
     * @return the emotion preset
     */
    public EmotionPreset getEmotionPreset() {
        return emotionPreset;
    }

    /**
     * Sets the emotion preset.
     * 
     * @param emotionPreset the emotion preset
     * @return this Prompt for chaining
     */
    public Prompt setEmotionPreset(EmotionPreset emotionPreset) {
        this.emotionPreset = emotionPreset;
        return this;
    }

    /**
     * Gets the emotion intensity.
     * 
     * @return the emotion intensity (0.0 to 2.0)
     */
    public Double getEmotionIntensity() {
        return emotionIntensity;
    }

    /**
     * Sets the emotion intensity.
     * 
     * @param emotionIntensity the intensity (0.0 to 2.0, default 1.0)
     * @return this Prompt for chaining
     * @throws IllegalArgumentException if intensity is out of range
     */
    public Prompt setEmotionIntensity(Double emotionIntensity) {
        if (emotionIntensity != null && (emotionIntensity < 0.0 || emotionIntensity > 2.0)) {
            throw new IllegalArgumentException("Emotion intensity must be between 0.0 and 2.0");
        }
        this.emotionIntensity = emotionIntensity;
        return this;
    }

    /**
     * Creates a builder for Prompt.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for Prompt.
     */
    public static class Builder {
        private final Prompt prompt = new Prompt();

        /**
         * Sets the emotion preset.
         * 
         * @param emotionPreset the emotion preset
         * @return this Builder for chaining
         */
        public Builder emotionPreset(EmotionPreset emotionPreset) {
            prompt.setEmotionPreset(emotionPreset);
            return this;
        }

        /**
         * Sets the emotion intensity.
         * 
         * @param emotionIntensity the intensity (0.0 to 2.0)
         * @return this Builder for chaining
         */
        public Builder emotionIntensity(Double emotionIntensity) {
            prompt.setEmotionIntensity(emotionIntensity);
            return this;
        }

        /**
         * Builds the Prompt instance.
         * 
         * @return the configured Prompt
         */
        public Prompt build() {
            return prompt;
        }
    }

    @Override
    public String toString() {
        return "Prompt{" +
                "emotionPreset=" + emotionPreset +
                ", emotionIntensity=" + emotionIntensity +
                '}';
    }
}
