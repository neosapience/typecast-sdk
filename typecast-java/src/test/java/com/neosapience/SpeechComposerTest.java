package com.neosapience;

import com.neosapience.exceptions.TypecastException;
import com.neosapience.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeechComposerTest {
    @Test
    void parsePauseMarkupPreservesInvalidTokens() {
        List<SpeechPart> parts = SpeechComposer.parsePauseMarkup("Hello <|0.3s|>world <|bad|> <|s|> <|3|> <||> <|xs|> <|1.2.3s|> <|3000s|>");

        assertEquals(5, parts.size());
        assertEquals("Hello ", parts.get(0).getText());
        assertTrue(parts.get(1).isPause());
        assertEquals(0.3, parts.get(1).getPauseSeconds(), 0.0001);
        assertEquals("world <|bad|> <|s|> <|3|> <||> <|xs|> <|1.2.3s|> ", parts.get(2).getText());
        assertEquals(3000.0, parts.get(3).getPauseSeconds(), 0.0001);
        assertEquals("", parts.get(4).getText());
    }

    @Test
    void parsePauseMarkupPreservesPlainAndUnclosedText() {
        assertEquals("plain text", SpeechComposer.parsePauseMarkup("plain text").get(0).getText());
        assertEquals("hello <|0.3s", SpeechComposer.parsePauseMarkup("hello <|0.3s").get(0).getText());
    }

    @Test
    void segmentRequestsMergeDefaultsAndForceWav() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        List<TTSRequest> requests = client.composeSpeech()
                .defaults(new ComposerSettings()
                        .setVoiceId("voice-a")
                        .setModel(TTSModel.SSFM_V30)
                        .setLanguage(LanguageCode.ENG)
                        .setPrompt(Prompt.builder().emotionPreset(EmotionPreset.HAPPY).emotionIntensity(1.0).build())
                        .setOutput(Output.builder().audioPitch(1).audioTempo(0.9).audioFormat(AudioFormat.MP3).build()))
                .say("First")
                .pause(0.25)
                .say("Second", new ComposerSettings()
                        .setVoiceId("voice-b")
                        .setPrompt(PresetPrompt.builder().emotionPreset(EmotionPreset.SAD).emotionIntensity(0.5).build())
                        .setSeed(77)
                        .setOutput(Output.builder().volume(80).audioPitch(-2).audioTempo(1.1).audioFormat(AudioFormat.MP3).build()))
                .segmentRequests();

        assertEquals(2, requests.size());
        assertEquals("voice-a", requests.get(0).getVoiceId());
        assertEquals("First", requests.get(0).getText());
        assertEquals(AudioFormat.WAV, requests.get(0).getOutput().getAudioFormat());
        assertEquals(1, requests.get(0).getOutput().getAudioPitch());
        assertEquals(0.9, requests.get(0).getOutput().getAudioTempo(), 0.0001);
        assertEquals("voice-b", requests.get(1).getVoiceId());
        assertEquals("Second", requests.get(1).getText());
        assertEquals(AudioFormat.WAV, requests.get(1).getOutput().getAudioFormat());
        assertEquals(80, requests.get(1).getOutput().getVolume());
        assertEquals(-2, requests.get(1).getOutput().getAudioPitch());
        assertEquals(1.1, requests.get(1).getOutput().getAudioTempo(), 0.0001);
        assertEquals(77, requests.get(1).getSeed());
    }

    @Test
    void composerSettingsPromptOverloadsAreChainable() {
        ComposerSettings settings = new ComposerSettings()
                .setPrompt(Prompt.builder().emotionPreset(EmotionPreset.HAPPY).build())
                .setPrompt(PresetPrompt.builder().emotionPreset(EmotionPreset.SAD).build())
                .setPrompt(SmartPrompt.builder().previousText("before").nextText("after").build());

        assertTrue(settings.getPrompt() instanceof SmartPrompt);
    }

    @Test
    void sayAcceptsNullOverridesAndDefaultsModelAndOutput() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        List<TTSRequest> requests = client.composeSpeech()
                .defaults(new ComposerSettings()
                        .setVoiceId("voice-a")
                        .setPrompt(SmartPrompt.builder().previousText("before").nextText("after").build()))
                .say("Hello", null)
                .segmentRequests();

        assertEquals(1, requests.size());
        assertEquals(TTSModel.SSFM_V30, requests.get(0).getModel());
        assertEquals(AudioFormat.WAV, requests.get(0).getOutput().getAudioFormat());
        assertTrue(requests.get(0).getPrompt() instanceof SmartPrompt);
    }

    @Test
    void basePresetPromptAndBaseOutputArePreserved() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        List<TTSRequest> requests = client.composeSpeech()
                .defaults(new ComposerSettings()
                        .setVoiceId("voice-a")
                        .setPrompt(PresetPrompt.builder().emotionPreset(EmotionPreset.SAD).build())
                        .setOutput(Output.builder()
                                .volume(70)
                                .audioPitch(2)
                                .audioTempo(0.95)
                                .audioFormat(AudioFormat.MP3)
                                .build()))
                .say("Hello", new ComposerSettings().setOutput(Output.builder()
                        .volume(null)
                        .targetLufs(null)
                        .audioPitch(null)
                        .audioTempo(null)
                        .audioFormat(null)
                        .build()))
                .segmentRequests();

        assertTrue(requests.get(0).getPrompt() instanceof PresetPrompt);
        assertEquals(70, requests.get(0).getOutput().getVolume());
        assertNull(requests.get(0).getOutput().getTargetLufs());
        assertEquals(2, requests.get(0).getOutput().getAudioPitch());
        assertEquals(0.95, requests.get(0).getOutput().getAudioTempo(), 0.0001);
        assertEquals(AudioFormat.WAV, requests.get(0).getOutput().getAudioFormat());
    }

    @Test
    void pauseRejectsNegativeDurations() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        assertThrows(IllegalArgumentException.class, () -> client.composeSpeech().pause(-0.1));
    }

    @Test
    void segmentRequestsRequireVoiceId() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        assertThrows(IllegalStateException.class, () -> client.composeSpeech().say("Hello").segmentRequests());
        assertThrows(IllegalStateException.class, () -> client.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("   "))
                .say("Hello")
                .segmentRequests());
    }

    @Test
    void generateRequiresAtLeastOneSpeechSegment() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        assertThrows(IllegalStateException.class, () -> client.composeSpeech().generate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSkipsUnknownInternalParts() throws Exception {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");
        SpeechComposer composer = client.composeSpeech();
        Field partsField = SpeechComposer.class.getDeclaredField("parts");
        partsField.setAccessible(true);
        ((List<Object>) partsField.get(composer)).add("ignored");

        assertThrows(IllegalStateException.class, composer::generate);
    }

    @Test
    void generateTrimsSegmentsAndInsertsSilence() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(new okio.Buffer().write(testWav(new short[]{0, 100, 0}, 10))));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(new okio.Buffer().write(testWav(new short[]{0, -200, 0}, 10))));
            server.start();

            TypecastClient client = new TypecastClient("test-key", server.url("/").toString());
            TTSResponse response = client.composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice-a").setModel(TTSModel.SSFM_V30))
                    .say("First")
                    .pause(0.2)
                    .say("Second")
                    .generate();

            assertArrayEquals(new short[]{100, 0, 0, -200}, readPcm(response.getAudioData()));
            assertEquals(0.4, response.getDuration(), 0.0001);
        }
    }

    @Test
    void generateTrimsAllZeroSegmentsToEmptyAudio() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(new okio.Buffer().write(testWav(new short[]{0, 0}, 10))));
            server.start();

            TypecastClient client = new TypecastClient("test-key", server.url("/").toString());
            TTSResponse response = client.composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice-a").setModel(TTSModel.SSFM_V30))
                    .say("Silence")
                    .generate();

            assertArrayEquals(new short[]{}, readPcm(response.getAudioData()));
            assertEquals(0.0, response.getDuration(), 0.0001);
        }
    }

    @Test
    void generateRejectsMp3() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");
        TypecastException error = assertThrows(TypecastException.class, () -> client.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("voice-a"))
                .say("Hello")
                .generate(AudioFormat.MP3));
        assertTrue(error.getMessage().contains("MP3 conversion"));
    }

    @Test
    void generateRejectsMalformedWavResponses() throws IOException {
        List<byte[]> cases = List.of(
                new byte[]{1, 2, 3},
                corruptAscii(testWav(new short[]{100}, 10), 0, "NOPE"),
                corruptAscii(testWav(new short[]{100}, 10), 8, "NOPE"),
                testWav(new short[]{100}, 10, (short) 2),
                testWavWithChannels((short) 2),
                testWavWithBitsPerSample((short) 8),
                testWavWithInvalidChunkSize(),
                testWavWithoutData(true),
                testWavWithoutData(false)
        );

        for (byte[] wav : cases) {
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "audio/wav")
                        .setBody(new okio.Buffer().write(wav)));
                server.start();

                TypecastClient client = new TypecastClient("test-key", server.url("/").toString());
                assertThrows(IllegalStateException.class, () -> client.composeSpeech()
                        .defaults(new ComposerSettings().setVoiceId("voice-a").setModel(TTSModel.SSFM_V30))
                        .say("Hello")
                        .generate());
            }
        }
    }

    @Test
    void generateRejectsMismatchedSampleRates() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(new okio.Buffer().write(testWav(new short[]{100}, 10))));
            server.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "audio/wav")
                    .setBody(new okio.Buffer().write(testWav(new short[]{200}, 20))));
            server.start();

            TypecastClient client = new TypecastClient("test-key", server.url("/").toString());
            assertThrows(IllegalStateException.class, () -> client.composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice-a").setModel(TTSModel.SSFM_V30))
                    .say("First")
                    .say("Second")
                    .generate());
        }
    }

    @Test
    void composeWavRejectsInvalidInternalPartShape() throws Exception {
        Method method = SpeechComposer.class.getDeclaredMethod("composeWav", List.class, List.class);
        method.setAccessible(true);

        InvocationTargetException error = assertThrows(InvocationTargetException.class, () ->
                method.invoke(null, Collections.emptyList(), Collections.emptyList()));
        assertTrue(error.getCause() instanceof IllegalStateException);

        List<byte[]> wavs = new ArrayList<>();
        wavs.add(testWav(new short[]{100}, 10));
        wavs.add(testWav(new short[]{200}, 10));
        InvocationTargetException sizeError = assertThrows(InvocationTargetException.class, () ->
                method.invoke(null, wavs, Collections.emptyList()));
        assertTrue(sizeError.getCause() instanceof IllegalStateException);
    }

    @Test
    void mergeOutputSupportsTargetLufsOverride() throws Exception {
        Method method = SpeechComposer.class.getDeclaredMethod("mergeOutput", Output.class, Output.class);
        method.setAccessible(true);

        Output override = Output.builder()
                .volume(null)
                .targetLufs(-18.0)
                .audioPitch(-1)
                .audioTempo(1.2)
                .audioFormat(AudioFormat.MP3)
                .build();
        Output merged = (Output) method.invoke(null, null, override);

        assertEquals(-18.0, merged.getTargetLufs(), 0.0001);
        assertEquals(-1, merged.getAudioPitch());
        assertEquals(1.2, merged.getAudioTempo(), 0.0001);
        assertEquals(AudioFormat.MP3, merged.getAudioFormat());
    }

    private static byte[] testWav(short[] samples, int sampleRate) {
        return testWav(samples, sampleRate, (short) 1);
    }

    private static byte[] testWav(short[] samples, int sampleRate, short audioFormat) {
        ByteBuffer buffer = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + samples.length * 2);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort(audioFormat);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(sampleRate * 2);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(samples.length * 2);
        for (short sample : samples) buffer.putShort(sample);
        return buffer.array();
    }

    private static byte[] testWavWithoutData(boolean extraChunk) {
        ByteBuffer buffer = ByteBuffer.allocate(36 + (extraChunk ? 12 : 8)).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(28 + (extraChunk ? 12 : 8));
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(10);
        buffer.putInt(20);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        if (extraChunk) {
            buffer.put("JUNK".getBytes(StandardCharsets.US_ASCII));
            buffer.putInt(4);
            buffer.putInt(123);
        }
        return buffer.array();
    }

    private static byte[] testWavWithInvalidChunkSize() {
        byte[] bytes = testWav(new short[]{}, 10);
        System.arraycopy("JUNK".getBytes(StandardCharsets.US_ASCII), 0, bytes, 36, 4);
        ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(1000);
        return bytes;
    }

    private static byte[] testWavWithChannels(short channels) {
        byte[] bytes = testWav(new short[]{100}, 10);
        ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(channels);
        return bytes;
    }

    private static byte[] testWavWithBitsPerSample(short bitsPerSample) {
        byte[] bytes = testWav(new short[]{100}, 10);
        ByteBuffer.wrap(bytes, 34, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(bitsPerSample);
        return bytes;
    }

    private static byte[] corruptAscii(byte[] bytes, int offset, String value) {
        System.arraycopy(value.getBytes(StandardCharsets.US_ASCII), 0, bytes, offset, value.length());
        return bytes;
    }

    private static short[] readPcm(byte[] wav) {
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int dataLength = buffer.getInt(40);
        short[] samples = new short[dataLength / 2];
        buffer.position(44);
        for (int i = 0; i < samples.length; i++) samples[i] = buffer.getShort();
        return samples;
    }
}
