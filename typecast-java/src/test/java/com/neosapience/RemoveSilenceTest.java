package com.neosapience;

import com.neosapience.models.Output;
import com.neosapience.models.OutputStream;
import com.neosapience.models.TTSRequest;
import com.neosapience.models.TTSRequestStream;
import com.neosapience.models.TTSRequestWithTimestamps;
import com.neosapience.models.TTSModel;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoveSilenceTest {
    @Test void forwardsZeroToAllEndpointsAndCompose() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            TypecastClient client = new TypecastClient("test", server.url("/").toString());
            try {
                Output output = Output.builder().removeSilenceMs(0).build();
                server.enqueue(new MockResponse().setHeader("Content-Type", "audio/wav").setBody("audio"));
                client.textToSpeech(TTSRequest.builder().voiceId("voice").text("Hello")
                        .model(TTSModel.SSFM_V30).output(output).build());
                assertEquals(0, JsonParser.parseString(server.takeRequest().getBody().readUtf8())
                        .getAsJsonObject().getAsJsonObject("output").get("remove_silence_ms").getAsInt());
                server.enqueue(new MockResponse().setBody("audio"));
                client.textToSpeechStream(TTSRequestStream.builder().voiceId("voice").text("Hello")
                        .model(TTSModel.SSFM_V30).output(OutputStream.builder().removeSilenceMs(0).build()).build()).close();
                assertEquals(0, JsonParser.parseString(server.takeRequest().getBody().readUtf8())
                        .getAsJsonObject().getAsJsonObject("output").get("remove_silence_ms").getAsInt());
                server.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                        .setBody("{\"audio\":\"\",\"audio_format\":\"wav\"}"));
                client.textToSpeechWithTimestamps(TTSRequestWithTimestamps.builder().voiceId("voice").text("Hello")
                        .model(TTSModel.SSFM_V30).output(output).build(), null);
                assertEquals(0, JsonParser.parseString(server.takeRequest().getBody().readUtf8())
                        .getAsJsonObject().getAsJsonObject("output").get("remove_silence_ms").getAsInt());
                server.enqueue(new MockResponse().setHeader("Content-Type", "audio/wav").setBody("audio"));
                client.composeSpeech().defaults(new ComposerSettings().setVoiceId("voice")
                        .setOutput(Output.builder().removeSilenceMs(300).build()))
                        .say("Hello", new ComposerSettings().setOutput(output)).pause(1).generate();
                JsonArray segments = JsonParser.parseString(server.takeRequest().getBody().readUtf8())
                        .getAsJsonObject().getAsJsonArray("segments");
                assertEquals(0, segments.get(0).getAsJsonObject().getAsJsonObject("output").get("remove_silence_ms").getAsInt());
                assertEquals(1, segments.get(1).getAsJsonObject().get("duration_seconds").getAsInt());
            } finally {
                client.close();
            }
        }
    }
    @Test void preservesOptionalZeroAndValidatesRange() {
        assertNull(new Output().getRemoveSilenceMs());
        assertNull(new OutputStream().getRemoveSilenceMs());
        for (int value : new int[]{0, 300, 1000}) {
            assertEquals(value, Output.builder().removeSilenceMs(value).build().getRemoveSilenceMs());
            assertEquals(value, OutputStream.builder().removeSilenceMs(value).build().getRemoveSilenceMs());
        }
        for (int value : new int[]{-1, 1001}) {
            assertThrows(IllegalArgumentException.class, () -> Output.builder().removeSilenceMs(value).build());
            assertThrows(IllegalArgumentException.class, () -> OutputStream.builder().removeSilenceMs(value).build());
        }
    }
}
