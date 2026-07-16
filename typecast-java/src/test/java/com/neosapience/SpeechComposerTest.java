package com.neosapience;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neosapience.exceptions.TypecastException;
import com.neosapience.models.AudioFormat;
import com.neosapience.models.EmotionPreset;
import com.neosapience.models.LanguageCode;
import com.neosapience.models.Output;
import com.neosapience.models.PresetPrompt;
import com.neosapience.models.Prompt;
import com.neosapience.models.SmartPrompt;
import com.neosapience.models.TTSModel;
import com.neosapience.models.TTSRequest;
import com.neosapience.models.TTSResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeechComposerTest {
    @Test
    void generateUsesComposeApiAndMergesOverrides() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "audio/mpeg")
                    .setHeader("X-Audio-Duration", "1.25")
                    .setBody("composed-audio"));
            server.start();

            TTSResponse response = new TypecastClient("test-key", server.url("/").toString())
                    .composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice-a").setModel(TTSModel.SSFM_V30)
                            .setOutput(Output.builder().audioPitch(1).audioFormat(AudioFormat.MP3).build()))
                    .say("Hello<|0.3s|>world", new ComposerSettings().setVoiceId("voice-b")
                            .setOutput(Output.builder().volume(null).targetLufs(null).audioPitch(null)
                                    .audioTempo(1.1).audioFormat(null).build()))
                    .generate(AudioFormat.MP3);

            RecordedRequest request = server.takeRequest();
            assertEquals("/v1/text-to-speech/compose", request.getPath());
            JsonArray segments = JsonParser.parseString(request.getBody().readUtf8())
                    .getAsJsonObject().getAsJsonArray("segments");
            assertEquals(3, segments.size());
            assertEquals("tts", segments.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("Hello", segments.get(0).getAsJsonObject().get("text").getAsString());
            assertEquals("voice-b", segments.get(0).getAsJsonObject().get("voice_id").getAsString());
            JsonObject output = segments.get(0).getAsJsonObject().getAsJsonObject("output");
            assertEquals("mp3", output.get("audio_format").getAsString());
            assertEquals(1, output.get("audio_pitch").getAsInt());
            assertEquals(1.1, output.get("audio_tempo").getAsDouble(), 0.0001);
            assertEquals("pause", segments.get(1).getAsJsonObject().get("type").getAsString());
            assertEquals(0.3, segments.get(1).getAsJsonObject().get("duration_seconds").getAsDouble(), 0.0001);
            assertEquals("world", segments.get(2).getAsJsonObject().get("text").getAsString());
            assertArrayEquals("composed-audio".getBytes(StandardCharsets.UTF_8), response.getAudioData());
            assertEquals("mp3", response.getFormat());
            assertEquals(1.25, response.getDuration(), 0.0001);
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void validatesBeforeNetwork() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");
        assertThrows(IllegalStateException.class, () -> client.composeSpeech().say("Hello").generate());
        assertThrows(IllegalStateException.class, () -> client.composeSpeech().generate());
    }

    @Test
    void parsePauseMarkupPreservesInvalidTokens() {
        List<SpeechPart> parts = SpeechComposer.parsePauseMarkup(
                "a<|0.3s|>b<|abc|>c<|s|><|3|><||><|xs|><|.s|><|1.2.3s|><|3s|>");
        assertEquals(5, parts.size());
        assertEquals("a", parts.get(0).getText());
        assertTrue(parts.get(1).isPause());
        assertEquals("b<|abc|>c<|s|><|3|><||><|xs|><|.s|><|1.2.3s|>", parts.get(2).getText());
        assertTrue(parts.get(3).isPause());
        assertEquals("plain", SpeechComposer.parsePauseMarkup("plain").get(0).getText());
        assertEquals("a<|1s", SpeechComposer.parsePauseMarkup("a<|1s").get(0).getText());
    }

    @Test
    void segmentRequestsKeepCompatibilityAndMergeOptions() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");
        List<TTSRequest> requests = client.composeSpeech()
                .defaults(new ComposerSettings()
                        .setVoiceId("voice-a")
                        .setModel(TTSModel.SSFM_V30)
                        .setLanguage(LanguageCode.ENG)
                        .setPrompt(Prompt.builder().emotionPreset(EmotionPreset.HAPPY).build())
                        .setOutput(Output.builder().audioPitch(1).audioTempo(0.9).audioFormat(AudioFormat.MP3).build()))
                .say("First")
                .pause(0.25)
                .say("Second", new ComposerSettings()
                        .setVoiceId("voice-b")
                        .setPrompt(PresetPrompt.builder().emotionPreset(EmotionPreset.SAD).build())
                        .setSeed(77)
                        .setOutput(Output.builder().volume(80).audioPitch(-2)
                                .audioTempo(1.1).audioFormat(AudioFormat.MP3).build()))
                .segmentRequests();

        assertEquals(2, requests.size());
        assertEquals(AudioFormat.WAV, requests.get(0).getOutput().getAudioFormat());
        assertEquals("voice-b", requests.get(1).getVoiceId());
        assertEquals(80, requests.get(1).getOutput().getVolume());
        assertEquals(77, requests.get(1).getSeed());

        List<TTSRequest> defaults = client.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("voice").setPrompt(
                        SmartPrompt.builder().previousText("before").nextText("after").build()))
                .say("Hello", null)
                .segmentRequests();
        assertEquals(TTSModel.SSFM_V30, defaults.get(0).getModel());
        assertTrue(defaults.get(0).getPrompt() instanceof SmartPrompt);

        assertThrows(IllegalArgumentException.class, () -> client.composeSpeech().pause(-0.1));
        assertThrows(IllegalStateException.class, () -> client.composeSpeech().say("Hello").segmentRequests());
        assertThrows(IllegalStateException.class, () -> client.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("   ")).say("Hello").segmentRequests());

        List<TTSRequest> presetAndLufs = client.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("voice")
                        .setPrompt(PresetPrompt.builder().emotionPreset(EmotionPreset.HAPPY).build()))
                .say("Hello", new ComposerSettings().setOutput(
                        Output.builder().targetLufs(-18.0).audioPitch(null).audioTempo(null).audioFormat(null).build()))
                .segmentRequests();
        assertTrue(presetAndLufs.get(0).getPrompt() instanceof PresetPrompt);
        assertEquals(-18, presetAndLufs.get(0).getOutput().getTargetLufs());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateSkipsUnknownInternalParts() throws Exception {
        SpeechComposer composer = new TypecastClient("test-key", "http://localhost:1").composeSpeech();
        Field partsField = SpeechComposer.class.getDeclaredField("parts");
        partsField.setAccessible(true);
        ((List<Object>) partsField.get(composer)).add("ignored");
        assertThrows(IllegalStateException.class, composer::generate);
    }

    @Test
    void defaultGenerateCoversPauseBlankTextAndResponseFallbacks() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "audio/wav")
                    .setHeader("X-Audio-Duration", "invalid")
                    .setBody("audio"));
            server.start();
            TTSResponse response = new TypecastClient("test-key", server.url("/").toString())
                    .composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice"))
                    .pause(0.1)
                    .say("Hello<|0.1s|>   ")
                    .generate();
            assertEquals("wav", response.getFormat());
            assertEquals(0.0, response.getDuration());
        }

        TypecastClient unavailable = new TypecastClient("test-key", "http://localhost:1");
        assertThrows(RuntimeException.class, () -> unavailable.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("voice")).say("Hello").generate());
    }

    @Test
    void composeResponseCoversHttpErrorAndMp3Alias() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(422).setBody("{\"message\":\"invalid\"}"));
            server.enqueue(new MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "audio/mp3")
                    .setBody("audio"));
            server.start();
            TypecastClient client = new TypecastClient("test-key", server.url("/").toString());

            assertThrows(TypecastException.class, () -> client.composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice")).say("Hello").generate());
            TTSResponse response = client.composeSpeech()
                    .defaults(new ComposerSettings().setVoiceId("voice")).say("Hello").generate(AudioFormat.MP3);
            assertEquals("mp3", response.getFormat());
            assertEquals(0.0, response.getDuration());
        }
    }
}
