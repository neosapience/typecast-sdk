package com.neosapience;

import com.neosapience.exceptions.TypecastException;
import com.neosapience.models.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
 * tempo, prompt, seed, and output settings. Internal segment requests force WAV
 * so the SDK can trim leading/trailing silence and concatenate PCM.</p>
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
        if (outputFormat == AudioFormat.MP3) {
            throw new TypecastException("MP3 conversion is not available for composed speech in the Java SDK.");
        }
        List<byte[]> wavs = new ArrayList<>();
        List<Double> pauses = new ArrayList<>();
        double pendingPause = 0.0;
        boolean hasAudio = false;
        for (Object part : parts) {
            if (part instanceof Double) {
                pendingPause += (Double) part;
                continue;
            }
            if (!(part instanceof ComposerSpeechPart)) continue;
            if (hasAudio) pauses.add(pendingPause);
            pendingPause = 0.0;
            wavs.add(client.textToSpeech(buildRequest((ComposerSpeechPart) part)).getAudioData());
            hasAudio = true;
        }
        if (wavs.isEmpty()) throw new IllegalStateException("At least one speech segment is required");
        byte[] audio = composeWav(wavs, pauses);
        WavInfo info = parseWav(audio);
        return new TTSResponse(audio, (info.pcmLen / 2.0) / info.sampleRate, "wav");
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
        ComposerSettings settings = merge(defaults, speech.settings);
        if (settings.getVoiceId() == null || settings.getVoiceId().trim().isEmpty()) {
            throw new IllegalStateException("voiceId is required for composed speech segments");
        }
        Output output = copyOutput(settings.getOutput());
        if (output == null) output = Output.builder().volume(null).audioPitch(null).audioTempo(null).audioFormat(null).build();
        output.setAudioFormat(AudioFormat.WAV);
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

    private static byte[] composeWav(List<byte[]> wavs, List<Double> pauses) {
        if (pauses.size() + 1 != wavs.size()) throw new IllegalStateException("Invalid composed speech parts");
        List<WavInfo> infos = new ArrayList<>();
        for (byte[] wav : wavs) infos.add(parseWav(wav));
        int sampleRate = infos.get(0).sampleRate;
        int totalSamples = 0;
        List<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < wavs.size(); i++) {
            WavInfo info = infos.get(i);
            if (info.sampleRate != sampleRate) throw new IllegalStateException("WAV segment sample rates must match");
            int start = 0;
            int end = info.pcmLen / 2;
            while (start < end && readShort(wavs.get(i), info.pcmStart + start * 2) == 0) start++;
            while (end > start && readShort(wavs.get(i), info.pcmStart + (end - 1) * 2) == 0) end--;
            ranges.add(new int[]{start, end});
            totalSamples += end - start;
            if (i < pauses.size()) totalSamples += (int) Math.round(pauses.get(i) * sampleRate);
        }
        ByteBuffer out = ByteBuffer.allocate(44 + totalSamples * 2).order(ByteOrder.LITTLE_ENDIAN);
        writeWavHeader(out, sampleRate, totalSamples * 2);
        for (int i = 0; i < wavs.size(); i++) {
            WavInfo info = infos.get(i);
            int[] range = ranges.get(i);
            out.put(wavs.get(i), info.pcmStart + range[0] * 2, (range[1] - range[0]) * 2);
            if (i < pauses.size()) {
                int pauseBytes = (int) Math.round(pauses.get(i) * sampleRate) * 2;
                out.put(new byte[pauseBytes]);
            }
        }
        return out.array();
    }

    private static WavInfo parseWav(byte[] wav) {
        if (wav.length < 44 || !ascii(wav, 0, 4).equals("RIFF") || !ascii(wav, 8, 4).equals("WAVE")) {
            throw new IllegalStateException("Composed speech requires WAV audio");
        }
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getShort(20) != 1 || b.getShort(22) != 1 || b.getShort(34) != 16) {
            throw new IllegalStateException("Composed speech requires mono 16-bit PCM WAV segments");
        }
        int cursor = 36;
        while (cursor + 8 <= wav.length) {
            String id = ascii(wav, cursor, 4);
            int size = b.getInt(cursor + 4);
            if (cursor + 8 + size > wav.length) throw new IllegalStateException("Invalid WAV data");
            if (id.equals("data")) return new WavInfo(b.getInt(24), cursor + 8, size);
            cursor += 8 + size;
        }
        throw new IllegalStateException("WAV data chunk is missing");
    }

    private static short readShort(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static void writeWavHeader(ByteBuffer out, int sampleRate, int dataLength) {
        out.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.putInt(36 + dataLength);
        out.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        out.putInt(16);
        out.putShort((short) 1);
        out.putShort((short) 1);
        out.putInt(sampleRate);
        out.putInt(sampleRate * 2);
        out.putShort((short) 2);
        out.putShort((short) 16);
        out.put("data".getBytes(StandardCharsets.US_ASCII));
        out.putInt(dataLength);
    }

    private static class ComposerSpeechPart {
        final String text;
        final ComposerSettings settings;
        ComposerSpeechPart(String text, ComposerSettings settings) {
            this.text = text;
            this.settings = settings;
        }
    }

    private static class WavInfo {
        final int sampleRate;
        final int pcmStart;
        final int pcmLen;
        WavInfo(int sampleRate, int pcmStart, int pcmLen) {
            this.sampleRate = sampleRate;
            this.pcmStart = pcmStart;
            this.pcmLen = pcmLen;
        }
    }
}
