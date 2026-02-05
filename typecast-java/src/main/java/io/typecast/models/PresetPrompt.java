package io.typecast.models;

/**
 * Preset emotion prompt for SSFM v3.0 model.
 * 
 * <p>This prompt type allows explicit emotion presets with intensity control.</p>
 */
public class PresetPrompt {
    private static final String EMOTION_TYPE = "preset";
    
    private EmotionPreset emotionPreset;
    private Double emotionIntensity;

    /**
     * Creates a new PresetPrompt with default values (normal emotion, intensity 1.0).
     */
    public PresetPrompt() {
        this.emotionPreset = EmotionPreset.NORMAL;
        this.emotionIntensity = 1.0;
    }

    /**
     * Creates a new PresetPrompt with specified values.
     * 
     * @param emotionPreset    the emotion preset to use
     * @param emotionIntensity the emotion intensity (0.0 to 2.0)
     */
    public PresetPrompt(EmotionPreset emotionPreset, Double emotionIntensity) {
        this.emotionPreset = emotionPreset;
        this.emotionIntensity = emotionIntensity;
    }

    /**
     * Gets the emotion type (always "preset" for PresetPrompt).
     * 
     * @return "preset"
     */
    public String getEmotionType() {
        return EMOTION_TYPE;
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
     * @return this PresetPrompt for chaining
     */
    public PresetPrompt setEmotionPreset(EmotionPreset emotionPreset) {
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
     * @return this PresetPrompt for chaining
     * @throws IllegalArgumentException if intensity is out of range
     */
    public PresetPrompt setEmotionIntensity(Double emotionIntensity) {
        if (emotionIntensity != null && (emotionIntensity < 0.0 || emotionIntensity > 2.0)) {
            throw new IllegalArgumentException("Emotion intensity must be between 0.0 and 2.0");
        }
        this.emotionIntensity = emotionIntensity;
        return this;
    }

    /**
     * Creates a builder for PresetPrompt.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for PresetPrompt.
     */
    public static class Builder {
        private final PresetPrompt prompt = new PresetPrompt();

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
         * Builds the PresetPrompt instance.
         * 
         * @return the configured PresetPrompt
         */
        public PresetPrompt build() {
            return prompt;
        }
    }

    @Override
    public String toString() {
        return "PresetPrompt{" +
                "emotionType='" + EMOTION_TYPE + '\'' +
                ", emotionPreset=" + emotionPreset +
                ", emotionIntensity=" + emotionIntensity +
                '}';
    }
}
