package com.neosapience.models;

/**
 * Request object for Text-to-Speech synthesis.
 * 
 * <p>Required fields: voiceId, text, model</p>
 * <p>Optional fields: language, prompt, output, seed</p>
 */
public class TTSRequest {
    private static final int MAX_TEXT_LENGTH = 5000;

    private final String voiceId;
    private final String text;
    private final TTSModel model;
    private LanguageCode language;
    private Object prompt; // Can be Prompt, PresetPrompt, or SmartPrompt
    private Output output;
    private Integer seed;

    /**
     * Creates a new TTSRequest with required fields.
     * 
     * @param voiceId the voice ID (format: tc_* or uc_*)
     * @param text    the text to synthesize (max 5000 characters)
     * @param model   the TTS model to use
     * @throws IllegalArgumentException if any required field is invalid
     */
    public TTSRequest(String voiceId, String text, TTSModel model) {
        if (voiceId == null || voiceId.isEmpty()) {
            throw new IllegalArgumentException("Voice ID is required");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text is required");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text must not exceed " + MAX_TEXT_LENGTH + " characters");
        }
        if (model == null) {
            throw new IllegalArgumentException("Model is required");
        }
        
        this.voiceId = voiceId;
        this.text = text;
        this.model = model;
    }

    /**
     * Gets the voice ID.
     * 
     * @return the voice ID
     */
    public String getVoiceId() {
        return voiceId;
    }

    /**
     * Gets the text to synthesize.
     * 
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Gets the TTS model.
     * 
     * @return the model
     */
    public TTSModel getModel() {
        return model;
    }

    /**
     * Gets the language code.
     * 
     * @return the language code, or null if auto-detect
     */
    public LanguageCode getLanguage() {
        return language;
    }

    /**
     * Sets the language code.
     * 
     * @param language the ISO 639-3 language code, or null for auto-detect
     * @return this TTSRequest for chaining
     */
    public TTSRequest setLanguage(LanguageCode language) {
        this.language = language;
        return this;
    }

    /**
     * Gets the emotion prompt.
     * 
     * @return the prompt (Prompt, PresetPrompt, or SmartPrompt), or null
     */
    public Object getPrompt() {
        return prompt;
    }

    /**
     * Sets the emotion prompt (for SSFM v2.1).
     * 
     * @param prompt the emotion prompt
     * @return this TTSRequest for chaining
     */
    public TTSRequest setPrompt(Prompt prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Sets the preset emotion prompt (for SSFM v3.0).
     * 
     * @param prompt the preset emotion prompt
     * @return this TTSRequest for chaining
     */
    public TTSRequest setPrompt(PresetPrompt prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Sets the smart emotion prompt (for SSFM v3.0).
     * 
     * @param prompt the smart emotion prompt
     * @return this TTSRequest for chaining
     */
    public TTSRequest setPrompt(SmartPrompt prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Gets the output configuration.
     * 
     * @return the output configuration, or null
     */
    public Output getOutput() {
        return output;
    }

    /**
     * Sets the output configuration.
     * 
     * @param output the output configuration
     * @return this TTSRequest for chaining
     */
    public TTSRequest setOutput(Output output) {
        this.output = output;
        return this;
    }

    /**
     * Gets the random seed.
     * 
     * @return the seed, or null
     */
    public Integer getSeed() {
        return seed;
    }

    /**
     * Sets the random seed for reproducibility.
     * 
     * @param seed the random seed
     * @return this TTSRequest for chaining
     */
    public TTSRequest setSeed(Integer seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Creates a builder for TTSRequest.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for TTSRequest.
     */
    public static class Builder {
        private String voiceId;
        private String text;
        private TTSModel model;
        private LanguageCode language;
        private Object prompt;
        private Output output;
        private Integer seed;

        /**
         * Sets the voice ID.
         * 
         * @param voiceId the voice ID
         * @return this Builder for chaining
         */
        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        /**
         * Sets the text to synthesize.
         * 
         * @param text the text
         * @return this Builder for chaining
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the TTS model.
         * 
         * @param model the model
         * @return this Builder for chaining
         */
        public Builder model(TTSModel model) {
            this.model = model;
            return this;
        }

        /**
         * Sets the language code.
         * 
         * @param language the language code
         * @return this Builder for chaining
         */
        public Builder language(LanguageCode language) {
            this.language = language;
            return this;
        }

        /**
         * Sets the emotion prompt.
         * 
         * @param prompt the prompt
         * @return this Builder for chaining
         */
        public Builder prompt(Prompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the preset emotion prompt.
         * 
         * @param prompt the preset prompt
         * @return this Builder for chaining
         */
        public Builder prompt(PresetPrompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the smart emotion prompt.
         * 
         * @param prompt the smart prompt
         * @return this Builder for chaining
         */
        public Builder prompt(SmartPrompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /**
         * Sets the output configuration.
         * 
         * @param output the output configuration
         * @return this Builder for chaining
         */
        public Builder output(Output output) {
            this.output = output;
            return this;
        }

        /**
         * Sets the random seed.
         * 
         * @param seed the seed
         * @return this Builder for chaining
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Builds the TTSRequest instance.
         * 
         * @return the configured TTSRequest
         * @throws IllegalArgumentException if required fields are missing
         */
        public TTSRequest build() {
            TTSRequest request = new TTSRequest(voiceId, text, model);
            request.language = this.language;
            request.prompt = this.prompt;
            request.output = this.output;
            request.seed = this.seed;
            return request;
        }
    }

    @Override
    public String toString() {
        return "TTSRequest{" +
                "voiceId='" + voiceId + '\'' +
                ", text='" + (text.length() > 50 ? text.substring(0, 50) + "..." : text) + '\'' +
                ", model=" + model +
                ", language=" + language +
                ", prompt=" + prompt +
                ", output=" + output +
                ", seed=" + seed +
                '}';
    }
}
