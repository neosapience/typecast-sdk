package com.neosapience;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neosapience.models.AudioFormat;
import com.neosapience.models.Output;
import com.neosapience.models.TTSModel;
import com.neosapience.models.TTSResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

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
        List<SpeechPart> parts = SpeechComposer.parsePauseMarkup("a<|0.3s|>b<|abc|>c<|3s|>");
        assertEquals(5, parts.size());
        assertEquals("a", parts.get(0).getText());
        assertTrue(parts.get(1).isPause());
        assertEquals("b<|abc|>c", parts.get(2).getText());
        assertTrue(parts.get(3).isPause());
    }
}
