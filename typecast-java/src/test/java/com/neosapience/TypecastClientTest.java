package com.neosapience;

import com.neosapience.exceptions.*;
import com.neosapience.models.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.IOException;
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

    @Test
    @DisplayName("Output should validate volume range")
    void output_validatesVolumeRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setVolume(-1));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setVolume(201));

        assertDoesNotThrow(() -> new Output().setVolume(0));
        assertDoesNotThrow(() -> new Output().setVolume(200));
    }

    @Test
    @DisplayName("Output should validate audio pitch range")
    void output_validatesAudioPitchRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioPitch(-13));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioPitch(13));

        assertDoesNotThrow(() -> new Output().setAudioPitch(-12));
        assertDoesNotThrow(() -> new Output().setAudioPitch(12));
    }

    @Test
    @DisplayName("Output should validate audio tempo range")
    void output_validatesAudioTempoRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioTempo(0.4));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioTempo(2.1));

        assertDoesNotThrow(() -> new Output().setAudioTempo(0.5));
        assertDoesNotThrow(() -> new Output().setAudioTempo(2.0));
    }

    @Test
    @DisplayName("Prompt should validate emotion intensity range")
    void prompt_validatesEmotionIntensityRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Prompt().setEmotionIntensity(-0.1));

        assertThrows(IllegalArgumentException.class, () ->
                new Prompt().setEmotionIntensity(2.1));

        assertDoesNotThrow(() -> new Prompt().setEmotionIntensity(0.0));
        assertDoesNotThrow(() -> new Prompt().setEmotionIntensity(2.0));
    }

    @Test
    @DisplayName("SmartPrompt should validate text length")
    void smartPrompt_validatesTextLength() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 2001; i++) {
            longText.append("a");
        }

        assertThrows(IllegalArgumentException.class, () ->
                new SmartPrompt().setPreviousText(longText.toString()));

        assertThrows(IllegalArgumentException.class, () ->
                new SmartPrompt().setNextText(longText.toString()));
    }

    // ==================== Enum Tests ====================

    @Test
    @DisplayName("TTSModel should convert to and from string")
    void ttsModel_conversion() {
        assertEquals("ssfm-v21", TTSModel.SSFM_V21.getValue());
        assertEquals("ssfm-v30", TTSModel.SSFM_V30.getValue());

        assertEquals(TTSModel.SSFM_V21, TTSModel.fromValue("ssfm-v21"));
        assertEquals(TTSModel.SSFM_V30, TTSModel.fromValue("ssfm-v30"));

        assertThrows(IllegalArgumentException.class, () ->
                TTSModel.fromValue("invalid"));
    }

    @Test
    @DisplayName("EmotionPreset should convert to and from string")
    void emotionPreset_conversion() {
        assertEquals("normal", EmotionPreset.NORMAL.getValue());
        assertEquals("happy", EmotionPreset.HAPPY.getValue());
        assertEquals("whisper", EmotionPreset.WHISPER.getValue());

        assertEquals(EmotionPreset.NORMAL, EmotionPreset.fromValue("normal"));
        assertEquals(EmotionPreset.HAPPY, EmotionPreset.fromValue("HAPPY"));
    }

    @Test
    @DisplayName("LanguageCode should convert to and from string")
    void languageCode_conversion() {
        assertEquals("eng", LanguageCode.ENG.getValue());
        assertEquals("kor", LanguageCode.KOR.getValue());

        assertEquals(LanguageCode.ENG, LanguageCode.fromValue("eng"));
        assertEquals(LanguageCode.KOR, LanguageCode.fromValue("KOR"));
    }

    @Test
    @DisplayName("AudioFormat should return correct MIME type")
    void audioFormat_mimeType() {
        assertEquals("audio/wav", AudioFormat.WAV.getMimeType());
        assertEquals("audio/mp3", AudioFormat.MP3.getMimeType());
    }
}
