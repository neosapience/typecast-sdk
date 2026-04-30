package com.neosapience.models;

/**
 * Request object for Text-to-Speech synthesis with word/character-level timestamps.
 *
 * <p>Required fields: voiceId, text, model</p>
 * <p>Optional fields: language, prompt, output, seed</p>
 *
 * <p>The {@code granularity} query parameter is passed as a method argument to
 * {@code TypecastClient.textToSpeechWithTimestamps}, not as a body field.</p>
 */
public class TTSRequestWithTimestamps {
    private static final int MAX_TEXT_LENGTH = 5000;

    private final String voiceId;
    private final String text;
    private final TTSModel model;
    private LanguageCode language;
    private Object prompt; // Can be Prompt, PresetPrompt, or SmartPrompt
    private Output output;
    private Integer seed;

    /**
     * Creates a new TTSRequestWithTimestamps with required fields.
     *
     * @param voiceId the voice ID (format: tc_* or uc_*)
     * @param text    the text to synthesize (max 5000 characters)
     * @param model   the TTS model to use
     * @throws IllegalArgumentException if any required field is invalid
     */
    public TTSRequestWithTimestamps(String voiceId, String text, TTSModel model) {
        if (voiceId == null || voiceId.isBlank()) {
            throw new IllegalArgumentException("Voice ID is required");
        }
        if (text == null || text.isBlank()) {
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

    /** Returns the voice ID. */
    public String getVoiceId() {
        return voiceId;
    }

    /** Returns the text to synthesize. */
    public String getText() {
        return text;
    }

    /** Returns the TTS model. */
    public TTSModel getModel() {
        return model;
    }

    /** Returns the language code, or null if auto-detect. */
    public LanguageCode getLanguage() {
        return language;
    }

    /**
     * Sets the language code.
     *
     * @param language the ISO 639-3 language code, or null for auto-detect
     * @return this for chaining
     */
    public TTSRequestWithTimestamps setLanguage(LanguageCode language) {
        this.language = language;
        return this;
    }

    /** Returns the emotion prompt (Prompt, PresetPrompt, or SmartPrompt), or null. */
    public Object getPrompt() {
        return prompt;
    }

    /**
     * Sets the emotion prompt (for SSFM v2.1).
     *
     * @param prompt the emotion prompt
     * @return this for chaining
     */
    public TTSRequestWithTimestamps setPrompt(Prompt prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Sets the preset emotion prompt (for SSFM v3.0).
     *
     * @param prompt the preset emotion prompt
     * @return this for chaining
     */
    public TTSRequestWithTimestamps setPrompt(PresetPrompt prompt) {
        this.prompt = prompt;
        return this;
    }

    /**
     * Sets the smart emotion prompt (for SSFM v3.0).
     *
     * @param prompt the smart emotion prompt
     * @return this for chaining
     */
    public TTSRequestWithTimestamps setPrompt(SmartPrompt prompt) {
        this.prompt = prompt;
        return this;
    }

    /** Returns the output configuration, or null. */
    public Output getOutput() {
        return output;
    }

    /**
     * Sets the output configuration.
     *
     * @param output the output configuration
     * @return this for chaining
     */
    public TTSRequestWithTimestamps setOutput(Output output) {
        this.output = output;
        return this;
    }

    /** Returns the random seed, or null. */
    public Integer getSeed() {
        return seed;
    }

    /**
     * Sets the random seed for reproducibility.
     *
     * @param seed the random seed
     * @return this for chaining
     */
    public TTSRequestWithTimestamps setSeed(Integer seed) {
        this.seed = seed;
        return this;
    }

    /**
     * Creates a builder for TTSRequestWithTimestamps.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for TTSRequestWithTimestamps. */
    public static class Builder {
        private String voiceId;
        private String text;
        private TTSModel model;
        private LanguageCode language;
        private Object prompt;
        private Output output;
        private Integer seed;

        /** Sets the voice ID. */
        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        /** Sets the text to synthesize. */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /** Sets the TTS model. */
        public Builder model(TTSModel model) {
            this.model = model;
            return this;
        }

        /** Sets the language code. */
        public Builder language(LanguageCode language) {
            this.language = language;
            return this;
        }

        /** Sets the emotion prompt. */
        public Builder prompt(Prompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /** Sets the preset emotion prompt. */
        public Builder prompt(PresetPrompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /** Sets the smart emotion prompt. */
        public Builder prompt(SmartPrompt prompt) {
            this.prompt = prompt;
            return this;
        }

        /** Sets the output configuration. */
        public Builder output(Output output) {
            this.output = output;
            return this;
        }

        /** Sets the random seed. */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * Builds the TTSRequestWithTimestamps instance.
         *
         * @return the configured request
         * @throws IllegalArgumentException if required fields are missing
         */
        public TTSRequestWithTimestamps build() {
            TTSRequestWithTimestamps r = new TTSRequestWithTimestamps(voiceId, text, model);
            r.language = this.language;
            r.prompt = this.prompt;
            r.output = this.output;
            r.seed = this.seed;
            return r;
        }
    }

    @Override
    public String toString() {
        return "TTSRequestWithTimestamps{voiceId='" + voiceId + '\''
                + ", text.length=" + text.length()
                + ", model=" + model
                + ", language=" + language
                + ", prompt=" + prompt
                + ", output=" + output
                + ", seed=" + seed + '}';
    }
}
