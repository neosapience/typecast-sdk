package com.neosapience;

import com.neosapience.models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for composing multi-speaker speech and explicit pauses.
 *
 * <p>Pause markup, when parsed manually, uses clear delimiters such as
 * {@code <|3s|>}, {@code <|0.3s|>}, and {@code <|0.34413s|>}. Invalid tokens are
 * preserved as text. Multi-speaker composition is exposed through chaining only:
 * call {@link #defaults(ComposerSettings)}, {@link #say(String, ComposerSettings)},
 * and {@link #pause(double)}. Each speech segment may override voice, pitch,
 * tempo, prompt, seed, and output settings. Generation is performed by the
 * Typecast Compose API in one request.</p>
 */
public class SpeechComposer {
    private final TypecastClient client;
    private final List<Object> parts = new ArrayList<>();
    private ComposerSettings defaults = new ComposerSettings();

    SpeechComposer(TypecastClient client) {
        this.client = client;
    }

    public SpeechComposer defaults(ComposerSettings settings) {
        this.defaults = merge(this.defaults, settings);
        return this;
    }

    public SpeechComposer say(String text) {
        return say(text, new ComposerSettings());
    }

    public SpeechComposer say(String text, ComposerSettings overrides) {
        this.parts.add(new ComposerSpeechPart(text, overrides == null ? new ComposerSettings() : overrides));
        return this;
    }

    public SpeechComposer pause(double seconds) {
        if (seconds < 0) throw new IllegalArgumentException("Pause must be non-negative");
        this.parts.add(seconds);
        return this;
    }

    public List<TTSRequest> segmentRequests() {
        List<TTSRequest> requests = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof ComposerSpeechPart) {
                requests.add(buildRequest((ComposerSpeechPart) part));
            }
        }
        return requests;
    }

    public TTSResponse generate() {
        return generate(AudioFormat.WAV);
    }

    public TTSResponse generate(AudioFormat outputFormat) {
        List<Object> segments = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof Double) {
                segments.add(part);
                continue;
            }
            if (!(part instanceof ComposerSpeechPart)) continue;
            ComposerSpeechPart speech = (ComposerSpeechPart) part;
            for (SpeechPart parsed : parsePauseMarkup(speech.text)) {
                if (parsed.isPause()) {
                    segments.add(parsed.getPauseSeconds());
                } else if (!parsed.getText().trim().isEmpty()) {
                    segments.add(buildRequest(new ComposerSpeechPart(parsed.getText(), speech.settings), outputFormat));
                }
            }
        }
        if (segments.stream().noneMatch(TTSRequest.class::isInstance)) throw new IllegalStateException("At least one speech segment is required");
        return client.composeTextToSpeech(segments);
    }

    public static List<SpeechPart> parsePauseMarkup(String text) {
        List<SpeechPart> parts = new ArrayList<>();
        int textStart = 0;
        int searchStart = 0;
        while (searchStart < text.length()) {
            int start = text.indexOf("<|", searchStart);
            if (start < 0) break;
            int valueStart = start + 2;
            int end = text.indexOf("|>", valueStart);
            if (end < 0) break;
            String token = text.substring(valueStart, end);
            Double seconds = parsePauseToken(token);
            if (seconds != null) {
                parts.add(new SpeechPart(text.substring(textStart, start), 0.0, false));
                parts.add(new SpeechPart("", seconds, true));
                textStart = end + 2;
                searchStart = textStart;
            } else {
                searchStart = valueStart;
            }
        }
        parts.add(new SpeechPart(text.substring(textStart), 0.0, false));
        return parts;
    }

    private TTSRequest buildRequest(ComposerSpeechPart speech) {
        return buildRequest(speech, AudioFormat.WAV);
    }

    private TTSRequest buildRequest(ComposerSpeechPart speech, AudioFormat outputFormat) {
        ComposerSettings settings = merge(defaults, speech.settings);
        if (settings.getVoiceId() == null || settings.getVoiceId().trim().isEmpty()) {
            throw new IllegalStateException("voiceId is required for composed speech segments");
        }
        Output output = copyOutput(settings.getOutput());
        if (output == null) output = Output.builder().volume(null).audioPitch(null).audioTempo(null).audioFormat(null).build();
        output.setAudioFormat(outputFormat);
        TTSRequest request = new TTSRequest(settings.getVoiceId(), speech.text, settings.getModel() == null ? TTSModel.SSFM_V30 : settings.getModel());
        request.setLanguage(settings.getLanguage());
        if (settings.getPrompt() instanceof Prompt) request.setPrompt((Prompt) settings.getPrompt());
        if (settings.getPrompt() instanceof PresetPrompt) request.setPrompt((PresetPrompt) settings.getPrompt());
        if (settings.getPrompt() instanceof SmartPrompt) request.setPrompt((SmartPrompt) settings.getPrompt());
        request.setOutput(output);
        request.setSeed(settings.getSeed());
        return request;
    }

    private static ComposerSettings merge(ComposerSettings base, ComposerSettings overrides) {
        ComposerSettings out = new ComposerSettings();
        out.setVoiceId(overrides.getVoiceId() != null ? overrides.getVoiceId() : base.getVoiceId());
        out.setModel(overrides.getModel() != null ? overrides.getModel() : base.getModel());
        out.setLanguage(overrides.getLanguage() != null ? overrides.getLanguage() : base.getLanguage());
        if (overrides.getPrompt() instanceof Prompt) out.setPrompt((Prompt) overrides.getPrompt());
        else if (overrides.getPrompt() instanceof PresetPrompt) out.setPrompt((PresetPrompt) overrides.getPrompt());
        else if (overrides.getPrompt() instanceof SmartPrompt) out.setPrompt((SmartPrompt) overrides.getPrompt());
        else if (base.getPrompt() instanceof Prompt) out.setPrompt((Prompt) base.getPrompt());
        else if (base.getPrompt() instanceof PresetPrompt) out.setPrompt((PresetPrompt) base.getPrompt());
        else if (base.getPrompt() instanceof SmartPrompt) out.setPrompt((SmartPrompt) base.getPrompt());
        out.setOutput(mergeOutput(base.getOutput(), overrides.getOutput()));
        out.setSeed(overrides.getSeed() != null ? overrides.getSeed() : base.getSeed());
        return out;
    }

    private static Output mergeOutput(Output base, Output overrides) {
        if (base == null && overrides == null) return null;
        Output out = copyOutput(base);
        if (out == null) {
            out = Output.builder().volume(null).targetLufs(null).audioPitch(null).audioTempo(null).audioFormat(null).build();
        }
        if (overrides == null) return out;
        if (overrides.getVolume() != null) out.setVolume(overrides.getVolume());
        if (overrides.getTargetLufs() != null) out.setTargetLufs(overrides.getTargetLufs());
        if (overrides.getAudioPitch() != null) out.setAudioPitch(overrides.getAudioPitch());
        if (overrides.getAudioTempo() != null) out.setAudioTempo(overrides.getAudioTempo());
        if (overrides.getAudioFormat() != null) out.setAudioFormat(overrides.getAudioFormat());
        return out;
    }

    private static Output copyOutput(Output output) {
        if (output == null) return null;
        return Output.builder()
                .volume(output.getVolume())
                .targetLufs(output.getTargetLufs())
                .audioPitch(output.getAudioPitch())
                .audioTempo(output.getAudioTempo())
                .audioFormat(output.getAudioFormat())
                .build();
    }

    private static Double parsePauseToken(String token) {
        if (!token.endsWith("s") || token.length() < 2) return null;
        String number = token.substring(0, token.length() - 1);
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (!Character.isDigit(c) && c != '.') return null;
        }
        try {
            return Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class ComposerSpeechPart {
        final String text;
        final ComposerSettings settings;
        ComposerSpeechPart(String text, ComposerSettings settings) {
            this.text = text;
            this.settings = settings;
        }
    }

}
