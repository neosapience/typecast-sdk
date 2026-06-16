package com.neosapience.models;

/**
 * Convenience request for generating speech directly to a file.
 *
 * <p>Required fields: voiceId, text</p>
 * <p>Browse available API voices at https://typecast.ai/developers/api/voices.</p>
 * <p>Model defaults to ssfm-v30.</p>
 */
public class GenerateToFileRequest {
    private final String voiceId;
    private final String text;
    private final TTSModel model;
    private LanguageCode language;
    private Object prompt;
    private Output output;
    private Integer seed;

    public GenerateToFileRequest(String voiceId, String text) {
        this(voiceId, text, TTSModel.SSFM_V30);
    }

    public GenerateToFileRequest(String voiceId, String text, TTSModel model) {
        this.voiceId = voiceId;
        this.text = text;
        this.model = model == null ? TTSModel.SSFM_V30 : model;
    }

    public TTSRequest toTTSRequest(String filePath) {
        Output inferredOutput = inferOutput(filePath);
        Output finalOutput = output != null ? output : inferredOutput;
        if (finalOutput != null && finalOutput.getAudioFormat() == null && inferredOutput != null) {
            finalOutput.setAudioFormat(inferredOutput.getAudioFormat());
        }
        TTSRequest request = new TTSRequest(voiceId, text, model);
        request.setLanguage(language);
        if (prompt instanceof Prompt) {
            request.setPrompt((Prompt) prompt);
        } else if (prompt instanceof PresetPrompt) {
            request.setPrompt((PresetPrompt) prompt);
        } else if (prompt instanceof SmartPrompt) {
            request.setPrompt((SmartPrompt) prompt);
        }
        request.setOutput(finalOutput);
        request.setSeed(seed);
        return request;
    }

    public GenerateToFileRequest setLanguage(LanguageCode language) {
        this.language = language;
        return this;
    }

    public GenerateToFileRequest setPrompt(Prompt prompt) {
        this.prompt = prompt;
        return this;
    }

    public GenerateToFileRequest setPrompt(PresetPrompt prompt) {
        this.prompt = prompt;
        return this;
    }

    public GenerateToFileRequest setPrompt(SmartPrompt prompt) {
        this.prompt = prompt;
        return this;
    }

    public GenerateToFileRequest setOutput(Output output) {
        this.output = output;
        return this;
    }

    public GenerateToFileRequest setSeed(Integer seed) {
        this.seed = seed;
        return this;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Output inferOutput(String filePath) {
        String lower = filePath == null ? "" : filePath.toLowerCase();
        if (lower.endsWith(".mp3")) {
            return Output.builder().audioFormat(AudioFormat.MP3).build();
        }
        if (lower.endsWith(".wav")) {
            return Output.builder().audioFormat(AudioFormat.WAV).build();
        }
        return null;
    }

    public static class Builder {
        private String voiceId;
        private String text;
        private TTSModel model = TTSModel.SSFM_V30;
        private LanguageCode language;
        private Object prompt;
        private Output output;
        private Integer seed;

        public Builder voiceId(String voiceId) {
            this.voiceId = voiceId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder model(TTSModel model) {
            this.model = model;
            return this;
        }

        public Builder language(LanguageCode language) {
            this.language = language;
            return this;
        }

        public Builder prompt(Prompt prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder prompt(PresetPrompt prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder prompt(SmartPrompt prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder output(Output output) {
            this.output = output;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public GenerateToFileRequest build() {
            GenerateToFileRequest request = new GenerateToFileRequest(voiceId, text, model);
            request.language = language;
            request.prompt = prompt;
            request.output = output;
            request.seed = seed;
            return request;
        }
    }
}
