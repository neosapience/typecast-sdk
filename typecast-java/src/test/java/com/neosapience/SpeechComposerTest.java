package com.neosapience;

import com.neosapience.exceptions.TypecastException;
import com.neosapience.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpeechComposerTest {
    @Test
    void parsePauseMarkupPreservesInvalidTokens() {
        List<SpeechPart> parts = SpeechComposer.parsePauseMarkup("Hello <|0.3s|>world <|bad|> <|3000s|>");

        assertEquals(5, parts.size());
        assertEquals("Hello ", parts.get(0).getText());
        assertEquals(0.3, parts.get(1).getPauseSeconds(), 0.0001);
        assertEquals("world <|bad|> ", parts.get(2).getText());
        assertEquals(3000.0, parts.get(3).getPauseSeconds(), 0.0001);
        assertEquals("", parts.get(4).getText());
    }

    @Test
    void segmentRequestsMergeDefaultsAndForceWav() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");

        List<TTSRequest> requests = client.composeSpeech()
                .defaults(new ComposerSettings()
                        .setVoiceId("voice-a")
                        .setModel(TTSModel.SSFM_V30)
                        .setLanguage(LanguageCode.ENG)
                        .setOutput(Output.builder().audioPitch(1).audioTempo(0.9).audioFormat(AudioFormat.MP3).build()))
                .say("First")
                .pause(0.25)
                .say("Second", new ComposerSettings()
                        .setVoiceId("voice-b")
                        .setOutput(Output.builder().volume(null).audioPitch(-2).audioTempo(1.1).audioFormat(null).build()))
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
        assertEquals(-2, requests.get(1).getOutput().getAudioPitch());
        assertEquals(1.1, requests.get(1).getOutput().getAudioTempo(), 0.0001);
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
    void generateRejectsMp3() {
        TypecastClient client = new TypecastClient("test-key", "http://localhost:1");
        TypecastException error = assertThrows(TypecastException.class, () -> client.composeSpeech()
                .defaults(new ComposerSettings().setVoiceId("voice-a"))
                .say("Hello")
                .generate(AudioFormat.MP3));
        assertTrue(error.getMessage().contains("MP3 conversion"));
    }

    private static byte[] testWav(short[] samples, int sampleRate) {
        ByteBuffer buffer = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(36 + samples.length * 2);
        buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(16);
        buffer.putShort((short) 1);
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

    private static short[] readPcm(byte[] wav) {
        ByteBuffer buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int dataLength = buffer.getInt(40);
        short[] samples = new short[dataLength / 2];
        buffer.position(44);
        for (int i = 0; i < samples.length; i++) samples[i] = buffer.getShort();
        return samples;
    }
}
