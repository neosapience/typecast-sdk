package com.neosapience;

import com.google.gson.Gson;
import com.neosapience.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for timestamp TTS wrapping (textToSpeechWithTimestamps,
 * TTSWithTimestampsResponse.toSrt(), toVtt()).
 *
 * <p>SRT/VTT output is verified byte-for-byte against shared fixtures under
 * {@code test-fixtures/with-timestamps/expected/}.</p>
 */
class TimestampTTSTest {

    private static final Path FIX = findFixtureDir();

    private MockWebServer server;
    private TypecastClient client;
    private final Gson gson = new Gson();

    private static Path findFixtureDir() {
        Path dir = Paths.get(System.getProperty("user.dir"));
        for (int i = 0; i < 6; i++) {
            Path candidate = dir.resolve("test-fixtures/with-timestamps");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException("test-fixtures/with-timestamps not found");
    }

    private String loadFixture(String name) throws IOException {
        return new String(Files.readAllBytes(FIX.resolve(name)), StandardCharsets.UTF_8);
    }

    private String loadExpected(String name) throws IOException {
        return new String(Files.readAllBytes(FIX.resolve("expected").resolve(name)), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String url = server.url("/").toString();
        client = new TypecastClient("k", url.substring(0, url.length() - 1));
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    // -----------------------------------------------------------------------
    // Caption output matches expected files byte-for-byte
    // -----------------------------------------------------------------------

    @Test
    void toSrt_matches_all_fixtures() throws Exception {
        for (String name : new String[]{"both", "word_only", "char_only", "jpn_char"}) {
            TTSWithTimestampsResponse resp =
                    gson.fromJson(loadFixture(name + ".json"), TTSWithTimestampsResponse.class);
            assertEquals(loadExpected(name + ".srt"), resp.toSrt(),
                    "SRT mismatch for " + name);
        }
    }

    @Test
    void toVtt_matches_all_fixtures() throws Exception {
        for (String name : new String[]{"both", "word_only", "char_only", "jpn_char"}) {
            TTSWithTimestampsResponse resp =
                    gson.fromJson(loadFixture(name + ".json"), TTSWithTimestampsResponse.class);
            assertEquals(loadExpected(name + ".vtt"), resp.toVtt(),
                    "VTT mismatch for " + name);
        }
    }

    // -----------------------------------------------------------------------
    // Audio helpers
    // -----------------------------------------------------------------------

    @Test
    void audioBytes_decodes_base64() throws Exception {
        TTSWithTimestampsResponse resp =
                gson.fromJson(loadFixture("both.json"), TTSWithTimestampsResponse.class);
        byte[] b = resp.audioBytes();
        assertTrue(b.length > 0);
    }

    @Test
    void saveAudio_writes_file(@TempDir Path tmp) throws Exception {
        TTSWithTimestampsResponse resp =
                gson.fromJson(loadFixture("both.json"), TTSWithTimestampsResponse.class);
        Path out = tmp.resolve("out.wav");
        resp.saveAudio(out);
        assertTrue(Files.size(out) > 0);
    }

    // -----------------------------------------------------------------------
    // TypecastClient integration with MockWebServer
    // -----------------------------------------------------------------------

    @Test
    void textToSpeechWithTimestamps_no_granularity() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_x").text("Hi").model(TTSModel.SSFM_V30).build();
        TTSWithTimestampsResponse out = client.textToSpeechWithTimestamps(req, null);
        assertNotNull(out);
        RecordedRequest recorded = server.takeRequest();
        assertEquals("/v1/text-to-speech/with-timestamps",
                recorded.getRequestUrl().encodedPath());
        assertNull(recorded.getRequestUrl().queryParameter("granularity"));
    }

    @Test
    void textToSpeechWithTimestamps_word_granularity() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("word_only.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_x").text("Hi").model(TTSModel.SSFM_V30).build();
        client.textToSpeechWithTimestamps(req, "word");
        RecordedRequest recorded = server.takeRequest();
        assertEquals("word", recorded.getRequestUrl().queryParameter("granularity"));
    }

    @Test
    void textToSpeechWithTimestamps_char_granularity() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("char_only.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_x").text("Hi").model(TTSModel.SSFM_V30).build();
        client.textToSpeechWithTimestamps(req, "char");
        RecordedRequest recorded = server.takeRequest();
        assertEquals("char", recorded.getRequestUrl().queryParameter("granularity"));
    }

    @Test
    void textToSpeechWithTimestamps_invalid_granularity() {
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_x").text("Hi").model(TTSModel.SSFM_V30).build();
        assertThrows(IllegalArgumentException.class,
                () -> client.textToSpeechWithTimestamps(req, "words"));
    }

    @Test
    void textToSpeechWithTimestamps_null_request_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> client.textToSpeechWithTimestamps(null, null));
    }

    @Test
    void textToSpeechWithTimestamps_ioException_wrapped() throws IOException {
        server.shutdown();
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_x").text("Hi").model(TTSModel.SSFM_V30).build();
        assertThrows(com.neosapience.exceptions.TypecastException.class,
                () -> client.textToSpeechWithTimestamps(req, null));
    }

    @Test
    void textToSpeechWithTimestamps_errorResponse_throws() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"detail\":\"unauthorized\"}"));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_x").text("Hi").model(TTSModel.SSFM_V30).build();
        assertThrows(com.neosapience.exceptions.UnauthorizedException.class,
                () -> client.textToSpeechWithTimestamps(req, null));
    }

    @Test
    void textToSpeechWithTimestamps_serializesBody() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("tc_voice")
                .text("Hello world")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .seed(99)
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"voice_id\":\"tc_voice\""));
        assertTrue(body.contains("\"text\":\"Hello world\""));
        assertTrue(body.contains("\"model\":\"ssfm-v30\""));
        assertTrue(body.contains("\"language\":\"eng\""));
        assertTrue(body.contains("\"seed\":99"));
    }

    // -----------------------------------------------------------------------
    // TTSRequestWithTimestamps model
    // -----------------------------------------------------------------------

    @Test
    void ttsRequestWithTimestamps_requiredFieldValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> TTSRequestWithTimestamps.builder().text("t").model(TTSModel.SSFM_V30).build());
        assertThrows(IllegalArgumentException.class,
                () -> TTSRequestWithTimestamps.builder().voiceId("v").model(TTSModel.SSFM_V30).build());
        assertThrows(IllegalArgumentException.class,
                () -> TTSRequestWithTimestamps.builder().voiceId("v").text("t").build());
    }

    @Test
    void ttsRequestWithTimestamps_settersAndGetters() {
        TTSRequestWithTimestamps r = new TTSRequestWithTimestamps("v", "t", TTSModel.SSFM_V21);
        assertEquals("v", r.getVoiceId());
        assertEquals("t", r.getText());
        assertEquals(TTSModel.SSFM_V21, r.getModel());
        assertSame(r, r.setLanguage(LanguageCode.KOR));
        assertSame(r, r.setSeed(42));
        assertSame(r, r.setOutput(new Output()));
        assertSame(r, r.setPrompt(new Prompt()));
        assertSame(r, r.setPrompt(new PresetPrompt()));
        assertSame(r, r.setPrompt(new SmartPrompt()));
        assertEquals(LanguageCode.KOR, r.getLanguage());
        assertEquals(Integer.valueOf(42), r.getSeed());
        assertNotNull(r.getOutput());
        assertNotNull(r.getPrompt());
        assertNotNull(r.toString());
    }

    @Test
    void ttsRequestWithTimestamps_toString_showsTextLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("x");
        }
        TTSRequestWithTimestamps r = new TTSRequestWithTimestamps("v", sb.toString(), TTSModel.SSFM_V30);
        assertTrue(r.toString().contains("text.length=100"));
    }

    @Test
    void ttsRequestWithTimestamps_textTooLong_throws() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5001; i++) {
            sb.append("a");
        }
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestWithTimestamps("v", sb.toString(), TTSModel.SSFM_V30));
    }

    // -----------------------------------------------------------------------
    // AlignmentSegment models
    // -----------------------------------------------------------------------

    @Test
    void alignmentSegmentWord_gettersAndToString() {
        AlignmentSegmentWord w = new AlignmentSegmentWord("hello", 0.1, 0.5);
        assertEquals("hello", w.getText());
        assertEquals(0.1, w.getStart());
        assertEquals(0.5, w.getEnd());
        assertNotNull(w.toString());

        AlignmentSegmentWord w2 = new AlignmentSegmentWord();
        assertNull(w2.getText());
    }

    @Test
    void alignmentSegmentCharacter_gettersAndToString() {
        AlignmentSegmentCharacter c = new AlignmentSegmentCharacter("a", 0.1, 0.2);
        assertEquals("a", c.getText());
        assertEquals(0.1, c.getStart());
        assertEquals(0.2, c.getEnd());
        assertNotNull(c.toString());

        AlignmentSegmentCharacter c2 = new AlignmentSegmentCharacter();
        assertNull(c2.getText());
    }

    // -----------------------------------------------------------------------
    // TTSWithTimestampsResponse getters
    // -----------------------------------------------------------------------

    @Test
    void ttsWithTimestampsResponse_getters() throws Exception {
        TTSWithTimestampsResponse resp =
                gson.fromJson(loadFixture("both.json"), TTSWithTimestampsResponse.class);
        assertNotNull(resp.getAudio());
        assertNotNull(resp.getAudioFormat());
        assertTrue(resp.getAudioDuration() > 0);
        assertNotNull(resp.getWords());
        assertFalse(resp.getWords().isEmpty());
        assertNotNull(resp.getCharacters());
        assertFalse(resp.getCharacters().isEmpty());
    }

    // -----------------------------------------------------------------------
    // Error path: no segments
    // -----------------------------------------------------------------------

    @Test
    void toSrt_noSegments_throws() {
        TTSWithTimestampsResponse resp = new TTSWithTimestampsResponse(
                "dGVzdA==", "wav", 1.0, null, null);
        assertThrows(IllegalStateException.class, resp::toSrt);
    }

    @Test
    void toVtt_noSegments_throws() {
        TTSWithTimestampsResponse resp = new TTSWithTimestampsResponse(
                "dGVzdA==", "wav", 1.0, null, null);
        assertThrows(IllegalStateException.class, resp::toVtt);
    }

    // -----------------------------------------------------------------------
    // buildTTSRequestWithTimestampsJson — prompt/output serialization branches
    // -----------------------------------------------------------------------

    @Test
    void textToSpeechWithTimestamps_withPresetPrompt() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30)
                .prompt(PresetPrompt.builder()
                        .emotionPreset(EmotionPreset.HAPPY)
                        .emotionIntensity(1.5)
                        .build())
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"preset\""));
        assertTrue(body.contains("\"emotion_preset\":\"happy\""));
        assertTrue(body.contains("\"emotion_intensity\":1.5"));
    }

    @Test
    void textToSpeechWithTimestamps_withSmartPrompt() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().previousText("prev").nextText("next").build())
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"smart\""));
        assertTrue(body.contains("\"previous_text\":\"prev\""));
        assertTrue(body.contains("\"next_text\":\"next\""));
    }

    @Test
    void textToSpeechWithTimestamps_withPrompt() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V21)
                .prompt(new Prompt(EmotionPreset.SAD, 0.8))
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_preset\":\"sad\""));
        assertTrue(body.contains("\"emotion_intensity\":0.8"));
    }

    @Test
    void textToSpeechWithTimestamps_withOutput() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30)
                .output(Output.builder()
                        .volume(80)
                        .audioPitch(2)
                        .audioTempo(1.2)
                        .audioFormat(AudioFormat.MP3)
                        .build())
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"volume\":80"));
        assertTrue(body.contains("\"audio_pitch\":2"));
        assertTrue(body.contains("\"audio_tempo\":1.2"));
        assertTrue(body.contains("\"audio_format\":\"mp3\""));
    }

    @Test
    void textToSpeechWithTimestamps_withTargetLufs() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30)
                .output(Output.builder().volume(null).targetLufs(-14.0).build())
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"target_lufs\":-14.0"));
    }

    @Test
    void textToSpeechWithTimestamps_promptNullFields() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        Prompt p = new Prompt();
        p.setEmotionPreset(null);
        p.setEmotionIntensity(null);
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V21).prompt(p).build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"prompt\":{}"));
    }

    @Test
    void textToSpeechWithTimestamps_presetPromptNullFields() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        PresetPrompt p = new PresetPrompt();
        p.setEmotionPreset(null);
        p.setEmotionIntensity(null);
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30).prompt(p).build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"preset\""));
        assertFalse(body.contains("emotion_preset"));
        assertFalse(body.contains("emotion_intensity"));
    }

    @Test
    void textToSpeechWithTimestamps_smartPromptNoPrevious() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().nextText("only next").build())
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"next_text\":\"only next\""));
        assertFalse(body.contains("previous_text"));
    }

    @Test
    void textToSpeechWithTimestamps_smartPromptNoNext() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().previousText("only prev").build())
                .build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"previous_text\":\"only prev\""));
        assertFalse(body.contains("next_text"));
    }

    @Test
    void textToSpeechWithTimestamps_outputAllNulls() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("both.json")));
        Output o = new Output();
        o.setVolume(null);
        o.setAudioPitch(null);
        o.setAudioTempo(null);
        o.setAudioFormat(null);
        TTSRequestWithTimestamps req = TTSRequestWithTimestamps.builder()
                .voiceId("v").text("t").model(TTSModel.SSFM_V30).output(o).build();
        client.textToSpeechWithTimestamps(req, null);
        RecordedRequest recorded = server.takeRequest();
        String body = recorded.getBody().readUtf8();
        assertTrue(body.contains("\"output\":{}"));
    }
}
