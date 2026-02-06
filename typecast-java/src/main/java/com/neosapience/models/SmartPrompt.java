package com.neosapience.models;

/**
 * Smart context-aware emotion prompt for SSFM v3.0 model.
 * 
 * <p>This prompt type automatically determines the appropriate emotion
 * based on surrounding text context.</p>
 */
public class SmartPrompt {
    private static final String EMOTION_TYPE = "smart";
    private static final int MAX_TEXT_LENGTH = 2000;
    
    private String previousText;
    private String nextText;

    /**
     * Creates a new SmartPrompt with no context.
     */
    public SmartPrompt() {
    }

    /**
     * Creates a new SmartPrompt with specified context.
     * 
     * @param previousText text appearing before the main text (max 2000 chars)
     * @param nextText     text appearing after the main text (max 2000 chars)
     */
    public SmartPrompt(String previousText, String nextText) {
        setPreviousText(previousText);
        setNextText(nextText);
    }

    /**
     * Gets the emotion type (always "smart" for SmartPrompt).
     * 
     * @return "smart"
     */
    public String getEmotionType() {
        return EMOTION_TYPE;
    }

    /**
     * Gets the previous context text.
     * 
     * @return the text appearing before the main text
     */
    public String getPreviousText() {
        return previousText;
    }

    /**
     * Sets the previous context text.
     * 
     * @param previousText text appearing before the main text (max 2000 chars)
     * @return this SmartPrompt for chaining
     * @throws IllegalArgumentException if text exceeds 2000 characters
     */
    public SmartPrompt setPreviousText(String previousText) {
        if (previousText != null && previousText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Previous text must not exceed " + MAX_TEXT_LENGTH + " characters");
        }
        this.previousText = previousText;
        return this;
    }

    /**
     * Gets the next context text.
     * 
     * @return the text appearing after the main text
     */
    public String getNextText() {
        return nextText;
    }

    /**
     * Sets the next context text.
     * 
     * @param nextText text appearing after the main text (max 2000 chars)
     * @return this SmartPrompt for chaining
     * @throws IllegalArgumentException if text exceeds 2000 characters
     */
    public SmartPrompt setNextText(String nextText) {
        if (nextText != null && nextText.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Next text must not exceed " + MAX_TEXT_LENGTH + " characters");
        }
        this.nextText = nextText;
        return this;
    }

    /**
     * Creates a builder for SmartPrompt.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for SmartPrompt.
     */
    public static class Builder {
        private final SmartPrompt prompt = new SmartPrompt();

        /**
         * Sets the previous context text.
         * 
         * @param previousText text appearing before the main text
         * @return this Builder for chaining
         */
        public Builder previousText(String previousText) {
            prompt.setPreviousText(previousText);
            return this;
        }

        /**
         * Sets the next context text.
         * 
         * @param nextText text appearing after the main text
         * @return this Builder for chaining
         */
        public Builder nextText(String nextText) {
            prompt.setNextText(nextText);
            return this;
        }

        /**
         * Builds the SmartPrompt instance.
         * 
         * @return the configured SmartPrompt
         */
        public SmartPrompt build() {
            return prompt;
        }
    }

    @Override
    public String toString() {
        return "SmartPrompt{" +
                "emotionType='" + EMOTION_TYPE + '\'' +
                ", previousText='" + (previousText != null ? previousText.substring(0, Math.min(50, previousText.length())) + "..." : null) + '\'' +
                ", nextText='" + (nextText != null ? nextText.substring(0, Math.min(50, nextText.length())) + "..." : null) + '\'' +
                '}';
    }
}
