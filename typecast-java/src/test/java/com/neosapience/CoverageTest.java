package com.neosapience;

import com.neosapience.exceptions.*;
import com.neosapience.models.*;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive coverage tests for branches and methods not covered by
 * TypecastClientTest and ModelValidationTest.
 */
class CoverageTest {

    private MockWebServer mockServer;
    private TypecastClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        client = new TypecastClient("test-api-key", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        mockServer.shutdown();
    }

    // ==================== Constructors / env resolution ====================

    @Test
    void constructor_apiKeyOnly() {
        TypecastClient c = new TypecastClient("key");
        try {
            assertEquals("https://api.typecast.ai", c.getBaseUrl());
        } finally {
            c.close();
        }
    }

    @Test
    void constructor_emptyApiKeyAndNoEnv_throws() throws Exception {
        // Make sure there's no .env in the working dir for this test
        Path envFile = Paths.get(".env");
        boolean existed = Files.exists(envFile);
        Path backup = Paths.get(".env.bak.coveragetest");
        if (existed) {
            Files.move(envFile, backup);
        }
        // Remove env vars if set
        String prevKey = removeEnv("TYPECAST_API_KEY");
        String prevHost = removeEnv("TYPECAST_API_HOST");
        try {
            assertThrows(IllegalArgumentException.class, () -> new TypecastClient(""));
            assertThrows(IllegalArgumentException.class, () -> new TypecastClient(null, null));
        } finally {
            if (prevKey != null) setEnv("TYPECAST_API_KEY", prevKey);
            if (prevHost != null) setEnv("TYPECAST_API_HOST", prevHost);
            if (existed) {
                Files.move(backup, envFile);
            }
        }
    }

    @Test
    void constructor_resolvesFromSystemEnv() throws Exception {
        // Ensure no .env interferes
        Path envFile = Paths.get(".env");
        boolean existed = Files.exists(envFile);
        Path backup = Paths.get(".env.bak.coveragetest2");
        if (existed) {
            Files.move(envFile, backup);
        }
        String prevKey = setEnv("TYPECAST_API_KEY", "env-key-value");
        String prevHost = setEnv("TYPECAST_API_HOST", "https://example.test/");
        try {
            TypecastClient c = new TypecastClient();
            try {
                // baseUrl should have trailing slash trimmed
                assertEquals("https://example.test", c.getBaseUrl());
            } finally {
                c.close();
            }
        } finally {
            restoreEnv("TYPECAST_API_KEY", prevKey);
            restoreEnv("TYPECAST_API_HOST", prevHost);
            if (existed) {
                Files.move(backup, envFile);
            }
        }
    }

    @Test
    void constructor_emptyEnvVarTreatedAsMissing() throws Exception {
        Path envFile = Paths.get(".env");
        boolean existed = Files.exists(envFile);
        Path backup = Paths.get(".env.bak.coveragetest_empty");
        if (existed) {
            Files.move(envFile, backup);
        }
        String prevKey = setEnv("TYPECAST_API_KEY", "");
        String prevHost = setEnv("TYPECAST_API_HOST", "");
        try {
            // Empty values should be ignored, treated as no env present
            assertThrows(IllegalArgumentException.class, () -> new TypecastClient());
        } finally {
            restoreEnv("TYPECAST_API_KEY", prevKey);
            restoreEnv("TYPECAST_API_HOST", prevHost);
            if (existed) {
                Files.move(backup, envFile);
            }
        }
    }

    @Test
    void constructor_emptyHostEnvFallsBackToDefault() throws Exception {
        Path envFile = Paths.get(".env");
        boolean existed = Files.exists(envFile);
        Path backup = Paths.get(".env.bak.coveragetest_emptyhost");
        if (existed) {
            Files.move(envFile, backup);
        }
        String prevKey = setEnv("TYPECAST_API_KEY", "some-key");
        String prevHost = setEnv("TYPECAST_API_HOST", "");
        try {
            TypecastClient c = new TypecastClient();
            try {
                assertEquals("https://api.typecast.ai", c.getBaseUrl());
            } finally {
                c.close();
            }
        } finally {
            restoreEnv("TYPECAST_API_KEY", prevKey);
            restoreEnv("TYPECAST_API_HOST", prevHost);
            if (existed) {
                Files.move(backup, envFile);
            }
        }
    }

    @Test
    void constructor_resolvesFromDotenvFile() throws Exception {
        Path envFile = Paths.get(".env");
        boolean existed = Files.exists(envFile);
        Path backup = Paths.get(".env.bak.coveragetest3");
        if (existed) {
            Files.move(envFile, backup);
        }
        String prevKey = removeEnv("TYPECAST_API_KEY");
        String prevHost = removeEnv("TYPECAST_API_HOST");
        try {
            Files.write(envFile,
                    Arrays.asList("TYPECAST_API_KEY=dotenv-key", "TYPECAST_API_HOST=https://dotenv.test"));
            TypecastClient c = new TypecastClient();
            try {
                assertEquals("https://dotenv.test", c.getBaseUrl());
            } finally {
                c.close();
            }
        } finally {
            Files.deleteIfExists(envFile);
            if (prevKey != null) setEnv("TYPECAST_API_KEY", prevKey);
            if (prevHost != null) setEnv("TYPECAST_API_HOST", prevHost);
            if (existed) {
                Files.move(backup, envFile);
            }
        }
    }

    @Test
    void constructor_baseUrlTrailingSlashTrimmed() {
        TypecastClient c = new TypecastClient("key", "https://x.example/");
        try {
            assertEquals("https://x.example", c.getBaseUrl());
        } finally {
            c.close();
        }
    }

    @Test
    void constructor_emptyBaseUrlUsesDefault() {
        TypecastClient c = new TypecastClient("key", "");
        try {
            assertEquals("https://api.typecast.ai", c.getBaseUrl());
        } finally {
            c.close();
        }
    }

    @Test
    void constructor_customHttpClient() {
        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS).build();
        TypecastClient c = new TypecastClient("key", "https://x.example", http);
        try {
            assertEquals("https://x.example", c.getBaseUrl());
        } finally {
            c.close();
        }
    }

    // ==================== Text-to-speech edge cases ====================

    @Test
    void textToSpeech_invalidDurationHeader_defaultsZero() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setHeader("X-Audio-Duration", "not-a-number")
                .setBody(new okio.Buffer().write(new byte[]{1, 2, 3})));

        TTSResponse res = client.textToSpeech(buildBasic());
        assertEquals(0.0, res.getDuration());
        assertEquals("wav", res.getFormat());
    }

    @Test
    void textToSpeech_noDurationHeader_defaultsZero() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{1})));
        TTSResponse res = client.textToSpeech(buildBasic());
        assertEquals(0.0, res.getDuration());
    }

    @Test
    void textToSpeech_unknownContentType_defaultsWav() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(new okio.Buffer().write(new byte[]{1})));
        TTSResponse res = client.textToSpeech(buildBasic());
        assertEquals("wav", res.getFormat());
    }

    @Test
    void textToSpeech_noContentType_defaultsWav() throws Exception {
        // build response without setting Content-Type explicitly: MockResponse adds default
        // We rely on default behaviour producing non-mp3, non-wav content type
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .removeHeader("Content-Type")
                .setBody(new okio.Buffer().write(new byte[]{1})));
        TTSResponse res = client.textToSpeech(buildBasic());
        assertEquals("wav", res.getFormat());
    }

    @Test
    void textToSpeech_withFullPromptAndSeedAndLanguage() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));

        TTSRequest req = TTSRequest.builder()
                .voiceId("tc_t").text("hi").model(TTSModel.SSFM_V21)
                .language(LanguageCode.ENG)
                .seed(42)
                .prompt(Prompt.builder()
                        .emotionPreset(EmotionPreset.HAPPY)
                        .emotionIntensity(1.5)
                        .build())
                .output(Output.builder().targetLufs(-14.0).audioFormat(AudioFormat.WAV).build())
                .build();
        client.textToSpeech(req);

        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"language\":\"eng\""));
        assertTrue(body.contains("\"seed\":42"));
        assertTrue(body.contains("\"emotion_preset\":\"happy\""));
        assertTrue(body.contains("\"emotion_intensity\":1.5"));
        assertTrue(body.contains("\"target_lufs\":-14.0"));
    }

    @Test
    void textToSpeech_smartPromptWithoutNextText() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        TTSRequest req = TTSRequest.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().previousText("only previous").build())
                .build();
        TTSResponse res = client.textToSpeech(req);
        assertEquals("mp3", res.getFormat());
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"previous_text\":\"only previous\""));
        assertFalse(body.contains("next_text"));
    }

    @Test
    void textToSpeech_smartPromptWithoutPreviousText() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        TTSRequest req = TTSRequest.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().nextText("only next").build())
                .build();
        client.textToSpeech(req);
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"next_text\":\"only next\""));
        assertFalse(body.contains("previous_text"));
    }

    @Test
    void textToSpeech_promptObjectBranch() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        TTSRequest req = TTSRequest.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V21)
                .prompt(new Prompt(EmotionPreset.SAD, 0.5))
                .build();
        client.textToSpeech(req);
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_preset\":\"sad\""));
        assertTrue(body.contains("\"emotion_intensity\":0.5"));
    }

    @Test
    void textToSpeech_promptObjectWithNullsBranch() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        Prompt p = new Prompt();
        p.setEmotionPreset(null);
        p.setEmotionIntensity(null);
        TTSRequest req = TTSRequest.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V21)
                .prompt(p).build();
        client.textToSpeech(req);
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"prompt\":{}"));
    }

    @Test
    void textToSpeech_presetPromptWithNullsBranch() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        PresetPrompt p = new PresetPrompt();
        p.setEmotionPreset(null);
        p.setEmotionIntensity(null);
        TTSRequest req = TTSRequest.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(p).build();
        client.textToSpeech(req);
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"preset\""));
        assertFalse(body.contains("emotion_preset"));
        assertFalse(body.contains("emotion_intensity"));
    }

    @Test
    void textToSpeech_outputAllNullsEmptyObject() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        Output o = new Output();
        o.setVolume(null);
        o.setAudioPitch(null);
        o.setAudioTempo(null);
        o.setAudioFormat(null);
        TTSRequest req = TTSRequest.builder().voiceId("tc").text("x").model(TTSModel.SSFM_V30).output(o).build();
        client.textToSpeech(req);
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"output\":{}"));
    }

    @Test
    void textToSpeech_emptyResponseBody_returnsZeroLengthAudio() throws Exception {
        // OkHttp always provides a non-null body, so an empty 200 response
        // should succeed with zero-length audio data. This verifies the
        // happy-path branch when the server returns no payload but valid
        // headers.
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setHeader("X-Audio-Duration", "0"));

        TTSResponse res = client.textToSpeech(buildBasic());
        assertNotNull(res.getAudioData());
        assertEquals(0, res.getAudioData().length);
        assertEquals("wav", res.getFormat());
        assertEquals(0.0, res.getDuration());
    }

    @Test
    void textToSpeech_ioException_wrapped() throws IOException {
        // Stop server to force IOException
        mockServer.shutdown();
        TTSRequest req = buildBasic();
        TypecastException ex = assertThrows(TypecastException.class, () -> client.textToSpeech(req));
        assertTrue(ex.getMessage().contains("Failed to make API request"));
    }

    @Test
    void textToSpeech_unprocessableEntity_422() {
        mockServer.enqueue(new MockResponse().setResponseCode(422)
                .setBody("{\"detail\":\"bad shape\"}"));
        UnprocessableEntityException ex = assertThrows(UnprocessableEntityException.class,
                () -> client.textToSpeech(buildBasic()));
        assertEquals(422, ex.getStatusCode());
    }

    @Test
    void textToSpeech_internalServerError_500() {
        mockServer.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"detail\":\"oops\"}"));
        InternalServerException ex = assertThrows(InternalServerException.class,
                () -> client.textToSpeech(buildBasic()));
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void textToSpeech_unknownStatus_default() {
        mockServer.enqueue(new MockResponse().setResponseCode(418)
                .setBody("{\"detail\":\"teapot\"}"));
        TypecastException ex = assertThrows(TypecastException.class,
                () -> client.textToSpeech(buildBasic()));
        assertEquals(418, ex.getStatusCode());
    }

    @Test
    void textToSpeech_notFound_404() {
        mockServer.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"detail\":\"missing\"}"));
        assertThrows(NotFoundException.class, () -> client.textToSpeech(buildBasic()));
    }

    // ==================== Error message extraction ====================

    @Test
    void error_extractMessage_fromMessageField() {
        mockServer.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"message\":\"alt msg\"}"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> client.textToSpeech(buildBasic()));
        assertTrue(ex.getMessage().contains("alt msg"));
    }

    @Test
    void error_extractMessage_fromErrorField() {
        mockServer.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"err msg\"}"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> client.textToSpeech(buildBasic()));
        assertTrue(ex.getMessage().contains("err msg"));
    }

    @Test
    void error_extractMessage_emptyBody() {
        mockServer.enqueue(new MockResponse().setResponseCode(400).setBody(""));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> client.textToSpeech(buildBasic()));
        assertTrue(ex.getMessage().contains("Unknown error"));
    }

    @Test
    void error_extractMessage_invalidJson_returnsRaw() {
        mockServer.enqueue(new MockResponse().setResponseCode(400).setBody("not json"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> client.textToSpeech(buildBasic()));
        assertTrue(ex.getMessage().contains("not json"));
    }

    @Test
    void error_extractMessage_noKnownFields_returnsRaw() {
        mockServer.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"random\":\"x\"}"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> client.textToSpeech(buildBasic()));
        assertTrue(ex.getMessage().contains("random"));
    }

    // ==================== V1 voices (deprecated) ====================

    @Test
    void getVoices_v1_noFilter() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("[{\"voice_id\":\"tc1\",\"voice_name\":\"V1\",\"model\":\"ssfm-v30\",\"emotions\":[\"normal\"]}]"));
        @SuppressWarnings("deprecation")
        List<VoicesResponse> voices = client.getVoices();
        assertEquals(1, voices.size());
        assertEquals("tc1", voices.get(0).getVoiceId());
    }

    @Test
    void getVoices_v1_withFilter() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        @SuppressWarnings("deprecation")
        List<VoicesResponse> voices = client.getVoices(TTSModel.SSFM_V30);
        assertNotNull(voices);
        RecordedRequest rr = mockServer.takeRequest();
        assertTrue(rr.getPath().contains("model=ssfm-v30"));
    }

    @Test
    void getVoices_v1_error() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"detail\":\"x\"}"));
        @SuppressWarnings("deprecation")
        Executable call = () -> client.getVoices();
        assertThrows(InternalServerException.class, call);
    }

    @Test
    void getVoices_v1_ioException() throws IOException {
        mockServer.shutdown();
        @SuppressWarnings("deprecation")
        Executable call = () -> client.getVoices();
        assertThrows(TypecastException.class, call);
    }

    @Test
    void getVoice_v1_noModel() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("[{\"voice_id\":\"tc1\",\"voice_name\":\"V\"}]"));
        @SuppressWarnings("deprecation")
        VoicesResponse v = client.getVoice("tc1");
        assertEquals("tc1", v.getVoiceId());
    }

    @Test
    void getVoice_v1_withModel() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200)
                .setBody("[{\"voice_id\":\"tc1\"}]"));
        @SuppressWarnings("deprecation")
        VoicesResponse v = client.getVoice("tc1", TTSModel.SSFM_V21);
        assertEquals("tc1", v.getVoiceId());
        RecordedRequest rr = mockServer.takeRequest();
        assertTrue(rr.getPath().contains("model=ssfm-v21"));
    }

    @Test
    void getVoice_v1_emptyArray_throwsNotFound() {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        @SuppressWarnings("deprecation")
        Executable call = () -> client.getVoice("nope");
        assertThrows(NotFoundException.class, call);
    }

    @Test
    void getVoice_v1_nullArray_throwsNotFound() {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("null"));
        @SuppressWarnings("deprecation")
        Executable call = () -> client.getVoice("nope");
        assertThrows(NotFoundException.class, call);
    }

    @Test
    void getVoice_v1_errorPath() {
        mockServer.enqueue(new MockResponse().setResponseCode(404).setBody("{\"detail\":\"missing\"}"));
        @SuppressWarnings("deprecation")
        Executable call = () -> client.getVoice("nope");
        assertThrows(NotFoundException.class, call);
    }

    @Test
    void getVoice_v1_ioException() throws IOException {
        mockServer.shutdown();
        @SuppressWarnings("deprecation")
        Executable call = () -> client.getVoice("nope");
        assertThrows(TypecastException.class, call);
    }

    // ==================== V2 voices error and filter branches ====================

    @Test
    void getVoicesV2_useCasesFilter() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        client.getVoicesV2(VoicesV2Filter.builder().useCases(UseCaseEnum.NEWS).build());
        RecordedRequest rr = mockServer.takeRequest();
        assertTrue(rr.getPath().contains("use_cases=news"));
    }

    @Test
    void getVoicesV2_emptyFilter() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));
        VoicesV2Filter f = new VoicesV2Filter();
        client.getVoicesV2(f);
        RecordedRequest rr = mockServer.takeRequest();
        assertEquals("/v2/voices", rr.getPath());
    }

    @Test
    void getVoicesV2_error() {
        mockServer.enqueue(new MockResponse().setResponseCode(500).setBody("{\"detail\":\"x\"}"));
        assertThrows(InternalServerException.class, () -> client.getVoicesV2());
    }

    @Test
    void getVoicesV2_ioException() throws IOException {
        mockServer.shutdown();
        assertThrows(TypecastException.class, () -> client.getVoicesV2());
    }

    @Test
    void getVoiceV2_ioException() throws IOException {
        mockServer.shutdown();
        assertThrows(TypecastException.class, () -> client.getVoiceV2("tc"));
    }

    @Test
    void getMySubscription_ioException() throws IOException {
        mockServer.shutdown();
        assertThrows(TypecastException.class, () -> client.getMySubscription());
    }

    @Test
    void getVoiceV2_errorBranch() {
        mockServer.enqueue(new MockResponse().setResponseCode(401).setBody("{\"detail\":\"x\"}"));
        assertThrows(UnauthorizedException.class, () -> client.getVoiceV2("tc"));
    }

    // ==================== Models: TTSRequest setters & toString ====================

    @Test
    void ttsRequest_settersChainAndToString() {
        TTSRequest r = new TTSRequest("tc", "hello", TTSModel.SSFM_V21);
        assertSame(r, r.setLanguage(LanguageCode.KOR));
        assertSame(r, r.setSeed(7));
        assertSame(r, r.setOutput(new Output()));
        assertSame(r, r.setPrompt(new Prompt()));
        assertSame(r, r.setPrompt(new PresetPrompt()));
        assertSame(r, r.setPrompt(new SmartPrompt()));
        assertEquals(LanguageCode.KOR, r.getLanguage());
        assertEquals(Integer.valueOf(7), r.getSeed());
        assertNotNull(r.getOutput());
        assertNotNull(r.getPrompt());
        assertNotNull(r.toString());
    }

    @Test
    void ttsRequest_toString_showsTextLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("a");
        TTSRequest r = new TTSRequest("tc", sb.toString(), TTSModel.SSFM_V30);
        assertTrue(r.toString().contains("text.length=100"));
        assertFalse(r.toString().contains("aaa"));
    }

    @Test
    void ttsRequest_emptyVoiceIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TTSRequest("", "hi", TTSModel.SSFM_V30));
    }

    @Test
    void ttsRequest_emptyTextThrows() {
        assertThrows(IllegalArgumentException.class, () -> new TTSRequest("tc", "", TTSModel.SSFM_V30));
    }

    // ==================== Models: Output ====================

    @Test
    void output_gettersAndSettersChain() {
        Output o = new Output();
        assertSame(o, o.setVolume(null));
        assertSame(o, o.setAudioPitch(null));
        assertSame(o, o.setAudioTempo(null));
        assertSame(o, o.setAudioFormat(AudioFormat.MP3));
        assertSame(o, o.setTargetLufs(null));
        assertNull(o.getVolume());
        assertNull(o.getAudioPitch());
        assertNull(o.getAudioTempo());
        assertEquals(AudioFormat.MP3, o.getAudioFormat());
        assertNull(o.getTargetLufs());
        assertNotNull(o.toString());
    }

    @Test
    void output_rejectsNaNAndInfinityTempo() {
        assertThrows(IllegalArgumentException.class, () -> new Output().setAudioTempo(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Output().setAudioTempo(Double.POSITIVE_INFINITY));
    }

    @Test
    void output_volumeAndTargetLufsConflict() {
        Output o = new Output();
        // Default has volume=100; setting targetLufs should fail
        assertThrows(IllegalArgumentException.class, () -> o.setTargetLufs(-14.0));
        Output o2 = new Output().setVolume(null).setTargetLufs(-14.0);
        assertThrows(IllegalArgumentException.class, () -> o2.setVolume(50));
    }

    @Test
    void output_targetLufsNotFiniteThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new Output().setVolume(null).setTargetLufs(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new Output().setVolume(null).setTargetLufs(Double.POSITIVE_INFINITY));
    }

    @Test
    void output_builderWithExplicitVolume() {
        Output o = Output.builder().volume(50).audioPitch(1).audioTempo(1.5).audioFormat(AudioFormat.MP3).build();
        assertEquals(Integer.valueOf(50), o.getVolume());
        assertEquals(Integer.valueOf(1), o.getAudioPitch());
        assertEquals(Double.valueOf(1.5), o.getAudioTempo());
        assertEquals(AudioFormat.MP3, o.getAudioFormat());
    }

    @Test
    void output_builderTargetLufsWithExplicitVolumeKeepsVolume() {
        // When both targetLufs is set AND volume is explicitly set, the explicit
        // volume should be kept (and conflict with targetLufs causes throw).
        // We test the (targetLufs != null && volumeExplicitlySet) branch by using
        // explicit volume(null) to avoid the conflict.
        Output o = Output.builder().volume(null).targetLufs(-14.0).build();
        assertNull(o.getVolume());
        assertEquals(-14.0, o.getTargetLufs());
    }

    @Test
    void output_builderDefaults() {
        Output o = Output.builder().build();
        assertEquals(Integer.valueOf(100), o.getVolume());
    }

    // ==================== Models: Prompt / PresetPrompt / SmartPrompt ====================

    @Test
    void prompt_defaultAndSettersAndToString() {
        Prompt p = new Prompt();
        assertEquals(EmotionPreset.NORMAL, p.getEmotionPreset());
        assertEquals(Double.valueOf(1.0), p.getEmotionIntensity());
        assertSame(p, p.setEmotionPreset(EmotionPreset.HAPPY));
        assertSame(p, p.setEmotionIntensity(0.5));
        assertNotNull(p.toString());
        Prompt p2 = new Prompt(EmotionPreset.SAD, 1.2);
        assertEquals(EmotionPreset.SAD, p2.getEmotionPreset());
    }

    @Test
    void presetPrompt_defaultsAndSetters() {
        PresetPrompt p = new PresetPrompt();
        assertEquals("preset", p.getEmotionType());
        assertEquals(EmotionPreset.NORMAL, p.getEmotionPreset());
        assertEquals(Double.valueOf(1.0), p.getEmotionIntensity());
        assertSame(p, p.setEmotionPreset(EmotionPreset.HAPPY));
        assertSame(p, p.setEmotionIntensity(0.0));
        assertThrows(IllegalArgumentException.class, () -> p.setEmotionIntensity(-0.1));
        assertThrows(IllegalArgumentException.class, () -> p.setEmotionIntensity(2.1));
        assertNotNull(p.toString());
        PresetPrompt p2 = new PresetPrompt(EmotionPreset.SAD, 0.8);
        assertEquals(EmotionPreset.SAD, p2.getEmotionPreset());
    }

    @Test
    void smartPrompt_settersWithNullExplicit() {
        SmartPrompt s = new SmartPrompt();
        assertSame(s, s.setPreviousText(null));
        assertSame(s, s.setNextText(null));
        assertNull(s.getPreviousText());
        assertNull(s.getNextText());
    }

    @Test
    void smartPrompt_defaultsAndSettersAndToString() {
        SmartPrompt s = new SmartPrompt();
        assertEquals("smart", s.getEmotionType());
        assertNull(s.getPreviousText());
        assertNull(s.getNextText());
        assertSame(s, s.setPreviousText("a"));
        assertSame(s, s.setNextText("b"));
        assertEquals("a", s.getPreviousText());
        assertEquals("b", s.getNextText());
        // toString with non-null short texts
        assertNotNull(s.toString());
        // toString with null text
        SmartPrompt s2 = new SmartPrompt();
        assertNotNull(s2.toString());
        // ctor variant
        SmartPrompt s3 = new SmartPrompt("p", "n");
        assertEquals("p", s3.getPreviousText());
        assertEquals("n", s3.getNextText());
    }

    // ==================== Enums ====================

    @Test
    void agEnum_branches() {
        for (AgeEnum a : AgeEnum.values()) {
            assertEquals(a, AgeEnum.fromValue(a.getValue().toUpperCase()));
            assertEquals(a.getValue(), a.toString());
        }
        assertNull(AgeEnum.fromValue(null));
        assertThrows(IllegalArgumentException.class, () -> AgeEnum.fromValue("???"));
    }

    @Test
    void genderEnum_branches() {
        for (GenderEnum g : GenderEnum.values()) {
            assertEquals(g, GenderEnum.fromValue(g.getValue()));
            assertEquals(g.getValue(), g.toString());
        }
        assertNull(GenderEnum.fromValue(null));
        assertThrows(IllegalArgumentException.class, () -> GenderEnum.fromValue("???"));
    }

    @Test
    void useCaseEnum_branches() {
        for (UseCaseEnum u : UseCaseEnum.values()) {
            assertEquals(u, UseCaseEnum.fromValue(u.getValue()));
            assertEquals(u.getValue(), u.toString());
        }
        assertNull(UseCaseEnum.fromValue(null));
        assertThrows(IllegalArgumentException.class, () -> UseCaseEnum.fromValue("???"));
    }

    @Test
    void audioFormat_branches() {
        for (AudioFormat f : AudioFormat.values()) {
            assertEquals(f, AudioFormat.fromValue(f.getValue()));
            assertEquals(f.getValue(), f.toString());
            assertTrue(f.getMimeType().startsWith("audio/"));
        }
        assertThrows(IllegalArgumentException.class, () -> AudioFormat.fromValue("???"));
    }

    @Test
    void emotionPreset_branches() {
        for (EmotionPreset e : EmotionPreset.values()) {
            assertEquals(e, EmotionPreset.fromValue(e.getValue()));
            assertEquals(e.getValue(), e.toString());
        }
        assertThrows(IllegalArgumentException.class, () -> EmotionPreset.fromValue("???"));
    }

    @Test
    void languageCode_branches() {
        for (LanguageCode l : LanguageCode.values()) {
            assertEquals(l, LanguageCode.fromValue(l.getValue()));
            assertEquals(l.getValue(), l.toString());
        }
        assertThrows(IllegalArgumentException.class, () -> LanguageCode.fromValue("???"));
    }

    @Test
    void ttsModel_branches() {
        for (TTSModel m : TTSModel.values()) {
            assertEquals(m, TTSModel.fromValue(m.getValue()));
            assertEquals(m.getValue(), m.toString());
        }
        assertThrows(IllegalArgumentException.class, () -> TTSModel.fromValue("???"));
    }

    // ==================== POJOs (Voices*, ModelInfo, TTSResponse) ====================

    @Test
    void voicesResponse_gettersSetters() {
        VoicesResponse v = new VoicesResponse();
        v.setVoiceId("a");
        v.setVoiceName("b");
        v.setModel("c");
        v.setEmotions(Arrays.asList("normal"));
        assertEquals("a", v.getVoiceId());
        assertEquals("b", v.getVoiceName());
        assertEquals("c", v.getModel());
        assertEquals(1, v.getEmotions().size());
        assertNotNull(v.toString());
        VoicesResponse v2 = new VoicesResponse("a", "b", "c", Collections.emptyList());
        assertEquals("a", v2.getVoiceId());
    }

    @Test
    void voiceV2Response_gettersSetters() {
        VoiceV2Response v = new VoiceV2Response();
        v.setVoiceId("a");
        v.setVoiceName("b");
        v.setModels(Arrays.asList(new ModelInfo("ssfm-v30", Arrays.asList("normal"))));
        v.setGender(GenderEnum.FEMALE);
        v.setAge(AgeEnum.YOUNG_ADULT);
        v.setUseCases(Arrays.asList("news"));
        assertEquals("a", v.getVoiceId());
        assertEquals("b", v.getVoiceName());
        assertEquals(1, v.getModels().size());
        assertEquals(GenderEnum.FEMALE, v.getGender());
        assertEquals(AgeEnum.YOUNG_ADULT, v.getAge());
        assertEquals(1, v.getUseCases().size());
        assertNotNull(v.toString());
        VoiceV2Response v2 = new VoiceV2Response("a", "b", null, GenderEnum.MALE, AgeEnum.ELDER, null);
        assertEquals(GenderEnum.MALE, v2.getGender());
    }

    @Test
    void modelInfo_gettersSetters() {
        ModelInfo m = new ModelInfo();
        m.setVersion("ssfm-v30");
        m.setEmotions(Arrays.asList("normal"));
        assertEquals("ssfm-v30", m.getVersion());
        assertEquals(1, m.getEmotions().size());
        assertNotNull(m.toString());
        ModelInfo m2 = new ModelInfo("ssfm-v21", Collections.emptyList());
        assertEquals("ssfm-v21", m2.getVersion());
    }

    @Test
    void ttsResponse_gettersToStringAndNullData() {
        TTSResponse r = new TTSResponse(new byte[]{1, 2, 3}, 1.5, "wav");
        assertEquals(3, r.getSize());
        assertEquals(1.5, r.getDuration());
        assertEquals("wav", r.getFormat());
        assertArrayEquals(new byte[]{1, 2, 3}, r.getAudioData());
        assertNotNull(r.toString());

        TTSResponse r2 = new TTSResponse(null, 0, "wav");
        assertEquals(0, r2.getSize());
    }

    // ==================== Subscription models ====================

    @Test
    void planTier_conversion() {
        for (PlanTier t : PlanTier.values()) {
            assertEquals(t, PlanTier.fromValue(t.getValue()));
            assertEquals(t, PlanTier.fromValue(t.getValue().toUpperCase()));
            assertEquals(t.getValue(), t.toString());
        }
        assertNull(PlanTier.fromValue(null));
        assertThrows(IllegalArgumentException.class, () -> PlanTier.fromValue("???"));
    }

    @Test
    void credits_gettersSetters() {
        Credits c = new Credits();
        c.setPlanCredits(100L);
        c.setUsedCredits(25L);
        assertEquals(100L, c.getPlanCredits());
        assertEquals(25L, c.getUsedCredits());
        assertNotNull(c.toString());
        Credits c2 = new Credits(50L, 10L);
        assertEquals(50L, c2.getPlanCredits());
        assertEquals(10L, c2.getUsedCredits());
    }

    @Test
    void limits_gettersSetters() {
        Limits l = new Limits();
        l.setConcurrencyLimit(7L);
        assertEquals(7L, l.getConcurrencyLimit());
        assertNotNull(l.toString());
        Limits l2 = new Limits(3L);
        assertEquals(3L, l2.getConcurrencyLimit());
    }

    @Test
    void subscriptionResponse_gettersSetters() {
        SubscriptionResponse s = new SubscriptionResponse();
        s.setPlan(PlanTier.FREE);
        s.setCredits(new Credits(10L, 1L));
        s.setLimits(new Limits(2L));
        assertEquals(PlanTier.FREE, s.getPlan());
        assertEquals(10L, s.getCredits().getPlanCredits());
        assertEquals(2L, s.getLimits().getConcurrencyLimit());
        assertNotNull(s.toString());
        SubscriptionResponse s2 = new SubscriptionResponse(
                PlanTier.CUSTOM, new Credits(0L, 0L), new Limits(0L));
        assertEquals(PlanTier.CUSTOM, s2.getPlan());
    }

    // ==================== VoicesV2Filter ====================

    @Test
    void voicesV2Filter_settersAndCtor() {
        VoicesV2Filter f = new VoicesV2Filter();
        assertSame(f, f.setModel(TTSModel.SSFM_V30));
        assertSame(f, f.setGender(GenderEnum.MALE));
        assertSame(f, f.setAge(AgeEnum.ELDER));
        assertSame(f, f.setUseCases(UseCaseEnum.NEWS));
        assertEquals(TTSModel.SSFM_V30, f.getModel());
        assertEquals(GenderEnum.MALE, f.getGender());
        assertEquals(AgeEnum.ELDER, f.getAge());
        assertEquals(UseCaseEnum.NEWS, f.getUseCases());
        assertNotNull(f.toString());
        VoicesV2Filter f2 = new VoicesV2Filter(TTSModel.SSFM_V21, GenderEnum.FEMALE, AgeEnum.CHILD, UseCaseEnum.GAME);
        assertEquals(TTSModel.SSFM_V21, f2.getModel());
    }

    // ==================== Exceptions ====================

    @Test
    void exceptions_messageOnlyAndCauseConstructors() {
        TypecastException e1 = new TypecastException("m");
        assertEquals(0, e1.getStatusCode());
        assertNull(e1.getResponseBody());
        assertNotNull(e1.toString());

        TypecastException e2 = new TypecastException("m", new RuntimeException("c"));
        assertNotNull(e2.getCause());

        TypecastException e3 = new TypecastException("m", 500, "body", new RuntimeException("c"));
        assertEquals(500, e3.getStatusCode());
        assertEquals("body", e3.getResponseBody());
        assertNotNull(e3.getCause());
    }

    @Test
    void allExceptionSubclasses_constructors() {
        Throwable cause = new RuntimeException();

        BadRequestException br = new BadRequestException("m", "b");
        assertEquals(400, br.getStatusCode());
        new BadRequestException("m", "b", cause);

        UnauthorizedException u = new UnauthorizedException("m", "b");
        assertEquals(401, u.getStatusCode());
        new UnauthorizedException("m", "b", cause);

        PaymentRequiredException p = new PaymentRequiredException("m", "b");
        assertEquals(402, p.getStatusCode());
        new PaymentRequiredException("m", "b", cause);

        ForbiddenException f = new ForbiddenException("m", "b");
        assertEquals(403, f.getStatusCode());
        new ForbiddenException("m", "b", cause);

        NotFoundException nf = new NotFoundException("m", "b");
        assertEquals(404, nf.getStatusCode());
        new NotFoundException("m", "b", cause);

        UnprocessableEntityException ue = new UnprocessableEntityException("m", "b");
        assertEquals(422, ue.getStatusCode());
        new UnprocessableEntityException("m", "b", cause);

        RateLimitException rl = new RateLimitException("m", "b");
        assertEquals(429, rl.getStatusCode());
        new RateLimitException("m", "b", cause);

        InternalServerException is = new InternalServerException("m", "b");
        assertEquals(500, is.getStatusCode());
        new InternalServerException("m", "b", cause);
    }

    // ==================== Helpers ====================

    private TTSRequest buildBasic() {
        return TTSRequest.builder()
                .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build();
    }

    @SuppressWarnings("unchecked")
    private static String setEnv(String key, String value) throws Exception {
        Map<String, String> env = System.getenv();
        Field f = env.getClass().getDeclaredField("m");
        f.setAccessible(true);
        Map<String, String> writable = (Map<String, String>) f.get(env);
        String prev = writable.get(key);
        if (value == null) writable.remove(key);
        else writable.put(key, value);
        return prev;
    }

    @SuppressWarnings("unchecked")
    private static String removeEnv(String key) throws Exception {
        Map<String, String> env = System.getenv();
        Field f = env.getClass().getDeclaredField("m");
        f.setAccessible(true);
        Map<String, String> writable = (Map<String, String>) f.get(env);
        String prev = writable.remove(key);
        return prev;
    }

    private static void restoreEnv(String key, String prev) throws Exception {
        if (prev == null) removeEnv(key);
        else setEnv(key, prev);
    }

    // ==================== Text-to-Speech Stream ====================

    private TTSRequestStream buildBasicStream() {
        return TTSRequestStream.builder()
                .voiceId("tc_test")
                .text("Hello, world!")
                .model(TTSModel.SSFM_V30)
                .build();
    }

    @Test
    void textToSpeech_nullRequestThrows() {
        assertThrows(IllegalArgumentException.class, () -> client.textToSpeech(null));
    }

    @Test
    void textToSpeechStream_nullRequestThrows() {
        assertThrows(IllegalArgumentException.class, () -> client.textToSpeechStream(null));
    }

    @Test
    void textToSpeechStream_success_readsBytesAndVerifiesRequest() throws Exception {
        byte[] audioBytes = new byte[]{0x52, 0x49, 0x46, 0x46, 0x10, 0x20, 0x30};
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(audioBytes)));

        TTSRequestStream req = buildBasicStream();
        InputStream is = client.textToSpeechStream(req);
        try {
            byte[] read = is.readAllBytes();
            assertArrayEquals(audioBytes, read);
        } finally {
            is.close();
        }

        RecordedRequest rr = mockServer.takeRequest();
        assertEquals("POST", rr.getMethod());
        assertEquals("/v1/text-to-speech/stream", rr.getPath());
        assertEquals("test-api-key", rr.getHeader("X-API-KEY"));
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"voice_id\":\"tc_test\""));
        assertTrue(body.contains("\"text\":\"Hello, world!\""));
        assertTrue(body.contains("\"model\":\"ssfm-v30\""));
    }

    @Test
    void textToSpeechStream_withAllOptionalFields_serializesBody() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setBody(new okio.Buffer().write(new byte[]{0})));

        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc_test")
                .text("Hi")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .seed(42)
                .prompt(PresetPrompt.builder()
                        .emotionPreset(EmotionPreset.HAPPY)
                        .emotionIntensity(1.5)
                        .build())
                .output(OutputStream.builder()
                        .audioPitch(2)
                        .audioTempo(1.2)
                        .audioFormat(AudioFormat.MP3)
                        .build())
                .build();

        InputStream is = client.textToSpeechStream(req);
        is.close();

        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"language\":\"eng\""));
        assertTrue(body.contains("\"seed\":42"));
        assertTrue(body.contains("\"emotion_type\":\"preset\""));
        assertTrue(body.contains("\"emotion_preset\":\"happy\""));
        assertTrue(body.contains("\"emotion_intensity\":1.5"));
        assertTrue(body.contains("\"audio_pitch\":2"));
        assertTrue(body.contains("\"audio_tempo\":1.2"));
        assertTrue(body.contains("\"audio_format\":\"mp3\""));
        assertFalse(body.contains("volume"));
        assertFalse(body.contains("target_lufs"));
    }

    @Test
    void textToSpeechStream_promptObjectBranch() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V21)
                .prompt(new Prompt(EmotionPreset.SAD, 0.5))
                .build();
        client.textToSpeechStream(req).close();
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_preset\":\"sad\""));
        assertTrue(body.contains("\"emotion_intensity\":0.5"));
    }

    @Test
    void textToSpeechStream_promptObjectWithNullsBranch() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        Prompt p = new Prompt();
        p.setEmotionPreset(null);
        p.setEmotionIntensity(null);
        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V21)
                .prompt(p).build();
        client.textToSpeechStream(req).close();
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"prompt\":{}"));
    }

    @Test
    void textToSpeechStream_presetPromptWithNullsBranch() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        PresetPrompt p = new PresetPrompt();
        p.setEmotionPreset(null);
        p.setEmotionIntensity(null);
        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(p).build();
        client.textToSpeechStream(req).close();
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"preset\""));
        assertFalse(body.contains("emotion_preset"));
        assertFalse(body.contains("emotion_intensity"));
    }

    @Test
    void textToSpeechStream_smartPromptWithoutNextText() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().previousText("only previous").build())
                .build();
        client.textToSpeechStream(req).close();
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"previous_text\":\"only previous\""));
        assertFalse(body.contains("next_text"));
    }

    @Test
    void textToSpeechStream_smartPromptWithoutPreviousText() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder().nextText("only next").build())
                .build();
        client.textToSpeechStream(req).close();
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"next_text\":\"only next\""));
        assertFalse(body.contains("previous_text"));
    }

    @Test
    void textToSpeechStream_outputAllNullsEmptyObject() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0})));
        OutputStream o = new OutputStream();
        o.setAudioPitch(null);
        o.setAudioTempo(null);
        o.setAudioFormat(null);
        TTSRequestStream req = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30).output(o).build();
        client.textToSpeechStream(req).close();
        RecordedRequest rr = mockServer.takeRequest();
        String body = rr.getBody().readUtf8();
        assertTrue(body.contains("\"output\":{}"));
    }

    @Test
    void textToSpeechStream_badRequest_400() {
        mockServer.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"detail\":\"bad\"}"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(400, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_unauthorized_401() {
        mockServer.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"detail\":\"nope\"}"));
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_paymentRequired_402() {
        mockServer.enqueue(new MockResponse().setResponseCode(402)
                .setBody("{\"detail\":\"pay\"}"));
        PaymentRequiredException ex = assertThrows(PaymentRequiredException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(402, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_notFound_404() {
        mockServer.enqueue(new MockResponse().setResponseCode(404)
                .setBody("{\"detail\":\"missing\"}"));
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_unprocessableEntity_422() {
        mockServer.enqueue(new MockResponse().setResponseCode(422)
                .setBody("{\"detail\":\"bad shape\"}"));
        UnprocessableEntityException ex = assertThrows(UnprocessableEntityException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(422, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_rateLimit_429() {
        mockServer.enqueue(new MockResponse().setResponseCode(429)
                .setBody("{\"detail\":\"slow\"}"));
        RateLimitException ex = assertThrows(RateLimitException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(429, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_internalServerError_500() {
        mockServer.enqueue(new MockResponse().setResponseCode(500)
                .setBody("{\"detail\":\"oops\"}"));
        InternalServerException ex = assertThrows(InternalServerException.class,
                () -> client.textToSpeechStream(buildBasicStream()));
        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void textToSpeechStream_ioException() throws IOException {
        mockServer.shutdown();
        assertThrows(IOException.class, () -> client.textToSpeechStream(buildBasicStream()));
    }

    // ==================== Models: OutputStream ====================

    @Test
    void outputStream_defaultsAndGetters() {
        OutputStream o = new OutputStream();
        assertEquals(Integer.valueOf(0), o.getAudioPitch());
        assertEquals(Double.valueOf(1.0), o.getAudioTempo());
        assertEquals(AudioFormat.WAV, o.getAudioFormat());
        assertNotNull(o.toString());
    }

    @Test
    void outputStream_settersChain() {
        OutputStream o = new OutputStream();
        assertSame(o, o.setAudioPitch(null));
        assertSame(o, o.setAudioTempo(null));
        assertSame(o, o.setAudioFormat(AudioFormat.MP3));
        assertNull(o.getAudioPitch());
        assertNull(o.getAudioTempo());
        assertEquals(AudioFormat.MP3, o.getAudioFormat());
    }

    @Test
    void outputStream_validatesAudioPitchRange() {
        assertThrows(IllegalArgumentException.class, () -> new OutputStream().setAudioPitch(-13));
        assertThrows(IllegalArgumentException.class, () -> new OutputStream().setAudioPitch(13));
        assertDoesNotThrow(() -> new OutputStream().setAudioPitch(-12));
        assertDoesNotThrow(() -> new OutputStream().setAudioPitch(12));
    }

    @Test
    void outputStream_validatesAudioTempoRange() {
        assertThrows(IllegalArgumentException.class, () -> new OutputStream().setAudioTempo(0.49));
        assertThrows(IllegalArgumentException.class, () -> new OutputStream().setAudioTempo(2.01));
        assertThrows(IllegalArgumentException.class, () -> new OutputStream().setAudioTempo(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new OutputStream().setAudioTempo(Double.POSITIVE_INFINITY));
        assertDoesNotThrow(() -> new OutputStream().setAudioTempo(0.5));
        assertDoesNotThrow(() -> new OutputStream().setAudioTempo(2.0));
    }

    @Test
    void outputStream_builderAllFields() {
        OutputStream o = OutputStream.builder()
                .audioPitch(3)
                .audioTempo(1.25)
                .audioFormat(AudioFormat.MP3)
                .build();
        assertEquals(Integer.valueOf(3), o.getAudioPitch());
        assertEquals(Double.valueOf(1.25), o.getAudioTempo());
        assertEquals(AudioFormat.MP3, o.getAudioFormat());
    }

    // ==================== Models: TTSRequestStream ====================

    @Test
    void ttsRequestStream_constructor_validatesRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestStream(null, "hi", TTSModel.SSFM_V30));
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestStream("", "hi", TTSModel.SSFM_V30));
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestStream("tc", null, TTSModel.SSFM_V30));
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestStream("tc", "", TTSModel.SSFM_V30));
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestStream("tc", "hi", null));
    }

    @Test
    void ttsRequestStream_validatesTextLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5001; i++) sb.append("a");
        assertThrows(IllegalArgumentException.class,
                () -> new TTSRequestStream("tc", sb.toString(), TTSModel.SSFM_V30));
    }

    @Test
    void ttsRequestStream_settersChainAndGetters() {
        TTSRequestStream r = new TTSRequestStream("tc", "hello", TTSModel.SSFM_V21);
        assertEquals("tc", r.getVoiceId());
        assertEquals("hello", r.getText());
        assertEquals(TTSModel.SSFM_V21, r.getModel());
        assertSame(r, r.setLanguage(LanguageCode.KOR));
        assertSame(r, r.setSeed(7));
        assertSame(r, r.setOutput(new OutputStream()));
        assertSame(r, r.setPrompt(new Prompt()));
        assertSame(r, r.setPrompt(new PresetPrompt()));
        assertSame(r, r.setPrompt(new SmartPrompt()));
        assertEquals(LanguageCode.KOR, r.getLanguage());
        assertEquals(Integer.valueOf(7), r.getSeed());
        assertNotNull(r.getOutput());
        assertNotNull(r.getPrompt());
        assertNotNull(r.toString());
    }

    @Test
    void ttsRequestStream_toString_showsTextLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("a");
        TTSRequestStream r = new TTSRequestStream("tc", sb.toString(), TTSModel.SSFM_V30);
        assertTrue(r.toString().contains("text.length=100"));
        assertFalse(r.toString().contains("aaa"));
    }

    @Test
    void ttsRequestStream_builderAllFields_promptOverloads() {
        // Prompt overload
        TTSRequestStream r1 = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V21)
                .language(LanguageCode.ENG)
                .seed(1)
                .output(OutputStream.builder().build())
                .prompt(new Prompt())
                .build();
        assertNotNull(r1.getPrompt());
        assertEquals(LanguageCode.ENG, r1.getLanguage());
        assertEquals(Integer.valueOf(1), r1.getSeed());
        assertNotNull(r1.getOutput());

        // PresetPrompt overload
        TTSRequestStream r2 = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(new PresetPrompt())
                .build();
        assertTrue(r2.getPrompt() instanceof PresetPrompt);

        // SmartPrompt overload
        TTSRequestStream r3 = TTSRequestStream.builder()
                .voiceId("tc").text("x").model(TTSModel.SSFM_V30)
                .prompt(new SmartPrompt())
                .build();
        assertTrue(r3.getPrompt() instanceof SmartPrompt);
    }
}
