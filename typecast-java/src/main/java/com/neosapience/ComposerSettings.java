package com.neosapience;

import com.neosapience.models.*;

/**
 * Defaults or per-segment overrides for {@link SpeechComposer}.
 *
 * <p>Use this to set voice, model, pitch, tempo, prompt, seed, and output options
 * globally with {@link SpeechComposer#defaults(ComposerSettings)} or for an
 * individual {@link SpeechComposer#say(String, ComposerSettings)} segment.</p>
 */
public class ComposerSettings {
    private String voiceId;
    private TTSModel model;
    private LanguageCode language;
    private Object prompt;
    private Output output;
    private Integer seed;

    public String getVoiceId() { return voiceId; }
    public ComposerSettings setVoiceId(String voiceId) { this.voiceId = voiceId; return this; }
    public TTSModel getModel() { return model; }
    public ComposerSettings setModel(TTSModel model) { this.model = model; return this; }
    public LanguageCode getLanguage() { return language; }
    public ComposerSettings setLanguage(LanguageCode language) { this.language = language; return this; }
    public Object getPrompt() { return prompt; }
    public ComposerSettings setPrompt(Prompt prompt) { this.prompt = prompt; return this; }
    public ComposerSettings setPrompt(PresetPrompt prompt) { this.prompt = prompt; return this; }
    public ComposerSettings setPrompt(SmartPrompt prompt) { this.prompt = prompt; return this; }
    public Output getOutput() { return output; }
    public ComposerSettings setOutput(Output output) { this.output = output; return this; }
    public Integer getSeed() { return seed; }
    public ComposerSettings setSeed(Integer seed) { this.seed = seed; return this; }
}
