package com.neosapience;

import com.neosapience.exceptions.*;
import com.neosapience.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TypecastClient using MockWebServer.
 */
class TypecastClientTest {
    
    private MockWebServer mockServer;
    private TypecastClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        
        String baseUrl = mockServer.url("/").toString();
        // Remove trailing slash
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        client = new TypecastClient("test-api-key", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        mockServer.shutdown();
    }

    // ==================== Text-to-Speech Tests ====================

    @Test
    @DisplayName("proxy base URL can be used without API key")
    void proxyBaseUrl_withoutApiKey_omitsAuthHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0x00})));

        TypecastClient proxyClient = new TypecastClient(null, mockServer.url("/").toString());
        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello, proxy!")
                .model(TTSModel.SSFM_V30)
                .build();

        proxyClient.textToSpeech(request);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertNull(recordedRequest.getHeader("X-API-KEY"));
        proxyClient.close();
    }

    @Test
    @DisplayName("proxy base URL treats blank API key as absent")
    void proxyBaseUrl_withBlankApiKey_omitsAuthHeader() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(new byte[]{0x00})));

        TypecastClient proxyClient = new TypecastClient("", mockServer.url("/").toString());
        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello, proxy!")
                .model(TTSModel.SSFM_V30)
                .build();

        proxyClient.textToSpeech(request);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertNull(recordedRequest.getHeader("X-API-KEY"));
        proxyClient.close();
    }

    @Test
    @DisplayName("textToSpeech should return audio data on success")
    void textToSpeech_success() throws Exception {
        // Prepare mock response
        byte[] audioBytes = new byte[]{0x52, 0x49, 0x46, 0x46}; // RIFF header
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setHeader("X-Audio-Duration", "2.5")
                .setBody(new okio.Buffer().write(audioBytes)));

        // Make request
        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello, world!")
                .model(TTSModel.SSFM_V30)
                .build();

        TTSResponse response = client.textToSpeech(request);

        // Verify response
        assertNotNull(response);
        assertArrayEquals(audioBytes, response.getAudioData());
        assertEquals(2.5, response.getDuration(), 0.01);
        assertEquals("wav", response.getFormat());

        // Verify request
        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/v1/text-to-speech", recordedRequest.getPath());
        assertEquals("test-api-key", recordedRequest.getHeader("X-API-KEY"));
        String userAgent = recordedRequest.getHeader("User-Agent");
        assertNotNull(userAgent);
        assertTrue(userAgent.startsWith("typecast-java/"));
        assertTrue(userAgent.contains(" OkHttp/"));
        assertTrue(userAgent.contains(" (base=custom; timeout=30-60-60; os="));
        assertTrue(userAgent.contains("; arch="));
        assertTrue(userAgent.endsWith("; sdk_env=java; platform=server)"));
        assertTrue(recordedRequest.getBody().readUtf8().contains("\"voice_id\":\"tc_test\""));
    }

    @Test
    @DisplayName("textToSpeech should include prompt in request")
    void textToSpeech_withPrompt() throws Exception {
        byte[] audioBytes = new byte[]{0x00};
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setBody(new okio.Buffer().write(audioBytes)));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .prompt(PresetPrompt.builder()
                        .emotionPreset(EmotionPreset.HAPPY)
                        .emotionIntensity(1.5)
                        .build())
                .build();

        TTSResponse response = client.textToSpeech(request);
        assertEquals("mp3", response.getFormat());

        RecordedRequest recordedRequest = mockServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"preset\""));
        assertTrue(body.contains("\"emotion_preset\":\"happy\""));
        assertTrue(body.contains("\"emotion_intensity\":1.5"));
    }

    @Test
    @DisplayName("textToSpeech should include smart prompt in request")
    void textToSpeech_withSmartPrompt() throws Exception {
        byte[] audioBytes = new byte[]{0x00};
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(audioBytes)));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Everything is perfect.")
                .model(TTSModel.SSFM_V30)
                .prompt(SmartPrompt.builder()
                        .previousText("I got great news!")
                        .nextText("Let's celebrate!")
                        .build())
                .build();

        client.textToSpeech(request);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("\"emotion_type\":\"smart\""));
        assertTrue(body.contains("\"previous_text\":\"I got great news!\""));
        assertTrue(body.contains("\"next_text\":\"Let's celebrate!\""));
    }

    @Test
    @DisplayName("textToSpeech should include output settings in request")
    void textToSpeech_withOutputSettings() throws Exception {
        byte[] audioBytes = new byte[]{0x00};
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setBody(new okio.Buffer().write(audioBytes)));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .output(Output.builder()
                        .volume(150)
                        .audioPitch(2)
                        .audioTempo(1.2)
                        .audioFormat(AudioFormat.MP3)
                        .build())
                .build();

        client.textToSpeech(request);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        String body = recordedRequest.getBody().readUtf8();
        assertTrue(body.contains("\"volume\":150"));
        assertTrue(body.contains("\"audio_pitch\":2"));
        assertTrue(body.contains("\"audio_tempo\":1.2"));
        assertTrue(body.contains("\"audio_format\":\"mp3\""));
    }

    @Test
    @DisplayName("generateToFile should infer mp3, default model, and write file")
    void generateToFile_infersMp3AndWritesFile() throws Exception {
        byte[] audioBytes = new byte[]{0x01, 0x02, 0x03};
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setHeader("X-Audio-Duration", "1.25")
                .setBody(new okio.Buffer().write(audioBytes)));
        Path output = Files.createTempFile("typecast-java-", ".mp3");
        Files.deleteIfExists(output);

        try {
            TTSResponse response = client.generateToFile(
                    output.toString(),
                    new GenerateToFileRequest("tc_test", "Hello")
                            .setLanguage(LanguageCode.ENG)
                            .setPrompt(Prompt.builder().emotionPreset(EmotionPreset.NORMAL).build())
                            .setSeed(7)
            );

            assertEquals("mp3", response.getFormat());
            assertArrayEquals(audioBytes, Files.readAllBytes(output));
            String body = mockServer.takeRequest().getBody().readUtf8();
            assertTrue(body.contains("\"model\":\"ssfm-v30\""));
            assertTrue(body.contains("\"audio_format\":\"mp3\""));
            assertTrue(body.contains("\"language\":\"eng\""));
            assertTrue(body.contains("\"seed\":7"));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    @DisplayName("generateToFile should keep explicit output and validate arguments")
    void generateToFile_keepsExplicitOutputAndValidates() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> client.generateToFile("", new GenerateToFileRequest("tc_test", "Hello")));
        assertThrows(IllegalArgumentException.class,
                () -> client.generateToFile(null, new GenerateToFileRequest("tc_test", "Hello")));
        assertThrows(IllegalArgumentException.class,
                () -> client.generateToFile("out.wav", null));

        byte[] audioBytes = new byte[]{0x04};
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(audioBytes)));
        Path output = Files.createTempFile("typecast-java-", ".wav");
        Files.deleteIfExists(output);

        try {
            client.generateToFile(
                    output.toString(),
                    GenerateToFileRequest.builder()
                            .voiceId("tc_test")
                            .text("Hello")
                            .model(TTSModel.SSFM_V21)
                            .prompt(PresetPrompt.builder().emotionPreset(EmotionPreset.HAPPY).build())
                            .output(Output.builder().audioFormat(AudioFormat.MP3).build())
                            .build()
            );
            String body = mockServer.takeRequest().getBody().readUtf8();
            assertTrue(body.contains("\"model\":\"ssfm-v21\""));
            assertTrue(body.contains("\"audio_format\":\"mp3\""));
            assertTrue(body.contains("\"emotion_type\":\"preset\""));
        } finally {
            Files.deleteIfExists(output);
        }

        TTSRequest wav = new GenerateToFileRequest("tc_test", "Hello").toTTSRequest("x.WAV");
        assertEquals(AudioFormat.WAV, wav.getOutput().getAudioFormat());
        TTSRequest nullPath = new GenerateToFileRequest("tc_test", "Hello", TTSModel.SSFM_V21)
                .toTTSRequest(null);
        assertEquals(TTSModel.SSFM_V21, nullPath.getModel());
        assertNull(nullPath.getOutput());
        TTSRequest nullModel = new GenerateToFileRequest("tc_test", "Hello", null)
                .toTTSRequest("x.bin");
        assertEquals(TTSModel.SSFM_V30, nullModel.getModel());
        TTSRequest unknown = new GenerateToFileRequest("tc_test", "Hello").toTTSRequest("x.bin");
        assertNull(unknown.getOutput());
        TTSRequest basic = new GenerateToFileRequest("tc_test", "Hello")
                .setPrompt(Prompt.builder().emotionPreset(EmotionPreset.NORMAL).build())
                .setOutput(Output.builder().audioFormat(AudioFormat.WAV).build())
                .toTTSRequest("x.bin");
        assertNotNull(basic.getPrompt());
        Output partialOutput = Output.builder().audioTempo(1.2).build().setAudioFormat(null);
        TTSRequest partial = new GenerateToFileRequest("tc_test", "Hello")
                .setOutput(partialOutput)
                .toTTSRequest("x.mp3");
        assertSame(partialOutput, partial.getOutput());
        assertEquals(AudioFormat.MP3, partial.getOutput().getAudioFormat());
        Output partialUnknownOutput = Output.builder().audioTempo(1.1).build().setAudioFormat(null);
        TTSRequest partialUnknown = new GenerateToFileRequest("tc_test", "Hello")
                .setOutput(partialUnknownOutput)
                .toTTSRequest("x.bin");
        assertSame(partialUnknownOutput, partialUnknown.getOutput());
        assertNull(partialUnknown.getOutput().getAudioFormat());
        TTSRequest presetSetter = new GenerateToFileRequest("tc_test", "Hello")
                .setPrompt(PresetPrompt.builder().emotionPreset(EmotionPreset.HAPPY).build())
                .toTTSRequest("x.bin");
        assertNotNull(presetSetter.getPrompt());
        TTSRequest smartSetter = new GenerateToFileRequest("tc_test", "Hello")
                .setPrompt(SmartPrompt.builder().previousText("before").build())
                .toTTSRequest("x.bin");
        assertNotNull(smartSetter.getPrompt());
        TTSRequest smart = GenerateToFileRequest.builder()
                .voiceId("tc_test")
                .text("Hello")
                .language(LanguageCode.ENG)
                .prompt(SmartPrompt.builder().previousText("before").build())
                .seed(3)
                .build()
                .toTTSRequest("x.bin");
        assertNotNull(smart.getPrompt());
        TTSRequest builderBasic = GenerateToFileRequest.builder()
                .voiceId("tc_test")
                .text("Hello")
                .prompt(Prompt.builder().emotionPreset(EmotionPreset.NORMAL).build())
                .build()
                .toTTSRequest("x.bin");
        assertNotNull(builderBasic.getPrompt());

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(new okio.Buffer().write(audioBytes)));
        Path directory = Files.createTempDirectory("typecast-java-dir-");
        try {
            TypecastException exception = assertThrows(
                    TypecastException.class,
                    () -> client.generateToFile(
                            directory.toString(),
                            new GenerateToFileRequest("tc_test", "Hello")
                    )
            );
            assertTrue(exception.getMessage().contains("Failed to write audio file"));
        } finally {
            Files.deleteIfExists(directory);
        }
    }

    @Test
    @DisplayName("textToSpeech should throw UnauthorizedException on 401")
    void textToSpeech_unauthorized() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"detail\": \"Invalid API key\"}"));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .build();

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> client.textToSpeech(request)
        );

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Invalid API key"));
    }

    @Test
    @DisplayName("textToSpeech should throw BadRequestException on 400")
    void textToSpeech_badRequest() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"detail\": \"Invalid voice_id\"}"));

        TTSRequest request = TTSRequest.builder()
                .voiceId("invalid")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .build();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> client.textToSpeech(request)
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    @DisplayName("textToSpeech should throw PaymentRequiredException on 402")
    void textToSpeech_paymentRequired() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(402)
                .setBody("{\"detail\": \"Insufficient credits\"}"));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .build();

        PaymentRequiredException exception = assertThrows(
                PaymentRequiredException.class,
                () -> client.textToSpeech(request)
        );

        assertEquals(402, exception.getStatusCode());
    }

    @Test
    @DisplayName("textToSpeech should throw RateLimitException on 429")
    void textToSpeech_rateLimit() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"detail\": \"Rate limit exceeded\"}"));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .build();

        RateLimitException exception = assertThrows(
                RateLimitException.class,
                () -> client.textToSpeech(request)
        );

        assertEquals(429, exception.getStatusCode());
    }

    @Test
    @DisplayName("textToSpeech should throw ForbiddenException on 403")
    void textToSpeech_forbidden() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(403)
                .setBody("{\"detail\": \"Invalid credentials\"}"));

        TTSRequest request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Hello!")
                .model(TTSModel.SSFM_V30)
                .build();

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> client.textToSpeech(request)
        );

        assertEquals(403, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Invalid credentials"));
    }

    // ==================== Voices V2 Tests ====================

    @Test
    @DisplayName("getVoicesV2 should return voices list")
    void getVoicesV2_success() throws Exception {
        String responseJson = "[" +
                "{\"voice_id\":\"tc_test1\",\"voice_name\":\"Test Voice 1\"," +
                "\"models\":[{\"version\":\"ssfm-v30\",\"emotions\":[\"normal\",\"happy\"]}]," +
                "\"gender\":\"female\",\"age\":\"young_adult\"}," +
                "{\"voice_id\":\"tc_test2\",\"voice_name\":\"Test Voice 2\"," +
                "\"models\":[{\"version\":\"ssfm-v21\",\"emotions\":[\"normal\"]}]," +
                "\"gender\":\"male\",\"age\":\"middle_age\"}" +
                "]";

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseJson));

        List<VoiceV2Response> voices = client.getVoicesV2();

        assertNotNull(voices);
        assertEquals(2, voices.size());
        assertEquals("tc_test1", voices.get(0).getVoiceId());
        assertEquals("Test Voice 1", voices.get(0).getVoiceName());
        assertNotNull(voices.get(0).getModels());
        assertEquals(1, voices.get(0).getModels().size());
    }

    @Test
    @DisplayName("getVoicesV2 should apply filter parameters")
    void getVoicesV2_withFilter() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        VoicesV2Filter filter = VoicesV2Filter.builder()
                .model(TTSModel.SSFM_V30)
                .gender(GenderEnum.FEMALE)
                .age(AgeEnum.YOUNG_ADULT)
                .build();

        client.getVoicesV2(filter);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        String path = recordedRequest.getPath();
        assertTrue(path.contains("model=ssfm-v30"));
        assertTrue(path.contains("gender=female"));
        assertTrue(path.contains("age=young_adult"));
    }

    @Test
    @DisplayName("getVoiceV2 should return single voice")
    void getVoiceV2_success() throws Exception {
        String responseJson = "{\"voice_id\":\"tc_test\",\"voice_name\":\"Test Voice\"," +
                "\"models\":[{\"version\":\"ssfm-v30\",\"emotions\":[\"normal\",\"happy\"]}]," +
                "\"gender\":\"female\",\"age\":\"young_adult\"}";

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseJson));

        VoiceV2Response voice = client.getVoiceV2("tc_test");

        assertNotNull(voice);
        assertEquals("tc_test", voice.getVoiceId());
        assertEquals("Test Voice", voice.getVoiceName());

        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertEquals("/v2/voices/tc_test", recordedRequest.getPath());
    }

    @Test
    @DisplayName("getVoiceV2 should throw NotFoundException on 404")
    void getVoiceV2_notFound() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"detail\": \"Voice not found\"}"));

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> client.getVoiceV2("nonexistent")
        );

        assertEquals(404, exception.getStatusCode());
    }

    @Test
    @DisplayName("recommendVoices should return scored voice recommendations")
    void recommendVoices_success() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[{\"voice_id\":\"tc_rec\",\"voice_name\":\"Recommended\",\"score\":0.97}]"));

        List<RecommendedVoice> voices = client.recommendVoices("warm narrator", 2);

        assertEquals(1, voices.size());
        assertEquals("tc_rec", voices.get(0).getVoiceId());
        assertEquals("Recommended", voices.get(0).getVoiceName());
        assertEquals(0.97, voices.get(0).getScore(), 0.001);

        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertEquals("/v1/voices/recommendations?query=warm%20narrator&count=2", recordedRequest.getPath());
    }

    @Test
    @DisplayName("recommendVoices should default count and validate range")
    void recommendVoices_defaultCountAndValidation() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]"));

        assertTrue(client.recommendVoices("voice", 0).isEmpty());
        assertEquals("/v1/voices/recommendations?query=voice&count=5", mockServer.takeRequest().getPath());
        assertTrue(client.recommendVoices("voice").isEmpty());
        assertEquals("/v1/voices/recommendations?query=voice&count=5", mockServer.takeRequest().getPath());
        assertThrows(IllegalArgumentException.class, () -> client.recommendVoices(null, 1));
        assertThrows(IllegalArgumentException.class, () -> client.recommendVoices("  ", 1));
        assertThrows(IllegalArgumentException.class, () -> client.recommendVoices("voice", -1));
        assertThrows(IllegalArgumentException.class, () -> client.recommendVoices("voice", 11));
    }

    @Test
    @DisplayName("recommendVoices should propagate API errors")
    void recommendVoices_error() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"detail\": \"Invalid API key\"}"));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> client.recommendVoices("voice", 1)
        );

        assertEquals(401, exception.getStatusCode());
    }

    // ==================== Subscription Tests ====================

    @Test
    @DisplayName("getMySubscription should return subscription on success")
    void getMySubscription_success() throws Exception {
        String responseJson = "{" +
                "\"plan\":\"plus\"," +
                "\"credits\":{\"plan_credits\":100000,\"used_credits\":1234}," +
                "\"limits\":{\"concurrency_limit\":5}" +
                "}";

        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        SubscriptionResponse subscription = client.getMySubscription();

        assertNotNull(subscription);
        assertEquals(PlanTier.PLUS, subscription.getPlan());
        assertNotNull(subscription.getCredits());
        assertEquals(100000L, subscription.getCredits().getPlanCredits());
        assertEquals(1234L, subscription.getCredits().getUsedCredits());
        assertNotNull(subscription.getLimits());
        assertEquals(5L, subscription.getLimits().getConcurrencyLimit());

        RecordedRequest recordedRequest = mockServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertEquals("/v1/users/me/subscription", recordedRequest.getPath());
        assertEquals("test-api-key", recordedRequest.getHeader("X-API-KEY"));
    }

    @Test
    @DisplayName("getMySubscription should throw UnauthorizedException on 401")
    void getMySubscription_unauthorized() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"detail\": \"Invalid API key\"}"));

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> client.getMySubscription()
        );

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Invalid API key"));
    }

    @Test
    @DisplayName("getMySubscription should throw RateLimitException on 429")
    void getMySubscription_rateLimit() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"detail\": \"Rate limit exceeded\"}"));

        RateLimitException exception = assertThrows(
                RateLimitException.class,
                () -> client.getMySubscription()
        );

        assertEquals(429, exception.getStatusCode());
    }

    @Test
    @DisplayName("getMySubscription should throw InternalServerException on 500")
    void getMySubscription_internalServerError() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"detail\": \"Internal server error\"}"));

        InternalServerException exception = assertThrows(
                InternalServerException.class,
                () -> client.getMySubscription()
        );

        assertEquals(500, exception.getStatusCode());
    }

    // ==================== Model Validation Tests ====================

    @Test
    @DisplayName("TTSRequest should validate required fields")
    void ttsRequest_validatesRequiredFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new TTSRequest(null, "Hello", TTSModel.SSFM_V30));

        assertThrows(IllegalArgumentException.class, () ->
                new TTSRequest("tc_test", null, TTSModel.SSFM_V30));

        assertThrows(IllegalArgumentException.class, () ->
                new TTSRequest("tc_test", "Hello", null));
    }

    @Test
    @DisplayName("TTSRequest should validate text length")
    void ttsRequest_validatesTextLength() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 5001; i++) {
            longText.append("a");
        }

        assertThrows(IllegalArgumentException.class, () ->
                new TTSRequest("tc_test", longText.toString(), TTSModel.SSFM_V30));
    }

}
