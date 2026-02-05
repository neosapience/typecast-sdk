package io.typecast;

import io.github.cdimascio.dotenv.Dotenv;
import io.typecast.exceptions.ForbiddenException;
import io.typecast.exceptions.NotFoundException;
import io.typecast.models.*;
import org.junit.jupiter.api.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End tests for TypecastClient against the real API.
 * 
 * <p>These tests require a valid API key set in the environment or .env file.</p>
 * <p>Run with: {@code mvn verify -Pe2e}</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TypecastClientE2ETest {

    private static TypecastClient client;
    private static String testVoiceId;
    private static Path tempDir;

    @BeforeAll
    static void setUpClass() throws IOException {
        // Load API key from .env file or environment
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        
        String apiKey = dotenv.get("TYPECAST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("TYPECAST_API_KEY");
        }

        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isEmpty(),
                "Skipping E2E tests: TYPECAST_API_KEY not set"
        );

        client = new TypecastClient(apiKey);
        tempDir = Files.createTempDirectory("typecast-e2e-");

        // Validate API key by making a test call
        try {
            List<VoiceV2Response> voices = client.getVoicesV2();
            if (!voices.isEmpty()) {
                testVoiceId = voices.get(0).getVoiceId();
            }
        } catch (ForbiddenException e) {
            System.err.println("API key is invalid, E2E tests will be skipped");
            // Set a flag to skip tests
            testVoiceId = null;
        } catch (Exception e) {
            System.err.println("Failed to validate API key: " + e.getMessage());
            testVoiceId = null;
        }
    }

    @AfterAll
    static void tearDownClass() {
        if (client != null) {
            client.close();
        }
    }

    // ==================== Voice Discovery Tests ====================

    @Test
    @Order(1)
    @DisplayName("E2E: getVoicesV2 should return list of voices")
    void getVoicesV2_returnsVoices() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid");

        List<VoiceV2Response> voices = client.getVoicesV2();

        assertNotNull(voices);
        assertFalse(voices.isEmpty(), "Should return at least one voice");

        // Verify voice structure
        VoiceV2Response firstVoice = voices.get(0);
        assertNotNull(firstVoice.getVoiceId());
        assertNotNull(firstVoice.getVoiceName());
        assertNotNull(firstVoice.getModels());
        assertFalse(firstVoice.getModels().isEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("E2E: getVoicesV2 with filter should return filtered voices")
    void getVoicesV2_withFilter() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid");

        VoicesV2Filter filter = VoicesV2Filter.builder()
                .model(TTSModel.SSFM_V30)
                .build();

        List<VoiceV2Response> voices = client.getVoicesV2(filter);

        assertNotNull(voices);
        // All returned voices should support ssfm-v30
        for (VoiceV2Response voice : voices) {
            boolean supportsSsfmV30 = voice.getModels().stream()
                    .anyMatch(m -> "ssfm-v30".equals(m.getVersion()));
            assertTrue(supportsSsfmV30, 
                    "Voice " + voice.getVoiceId() + " should support ssfm-v30");
        }
    }

    @Test
    @Order(3)
    @DisplayName("E2E: getVoiceV2 should return specific voice")
    void getVoiceV2_returnsVoice() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available");

        VoiceV2Response voice = client.getVoiceV2(testVoiceId);

        assertNotNull(voice);
        assertEquals(testVoiceId, voice.getVoiceId());
        assertNotNull(voice.getVoiceName());
    }

    @Test
    @Order(4)
    @DisplayName("E2E: getVoiceV2 with invalid ID should throw NotFoundException")
    void getVoiceV2_notFound() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key may be invalid");
        
        assertThrows(NotFoundException.class, () -> 
                client.getVoiceV2("invalid_voice_id_that_does_not_exist"));
    }

    // ==================== Text-to-Speech Tests ====================

    @Test
    @Order(10)
    @DisplayName("E2E: textToSpeech should generate WAV audio")
    void textToSpeech_generatesWavAudio() throws IOException {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available");

        TTSRequest request = TTSRequest.builder()
                .voiceId(testVoiceId)
                .text("Hello, this is a test of the Typecast Java SDK.")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .build();

        TTSResponse response = client.textToSpeech(request);

        assertNotNull(response);
        assertNotNull(response.getAudioData());
        assertTrue(response.getAudioData().length > 0, "Audio data should not be empty");
        assertTrue(response.getDuration() > 0, "Duration should be positive");
        assertEquals("wav", response.getFormat());

        // Save audio for manual verification
        Path audioFile = tempDir.resolve("test_wav.wav");
        try (FileOutputStream fos = new FileOutputStream(audioFile.toFile())) {
            fos.write(response.getAudioData());
        }
        System.out.println("Saved WAV audio to: " + audioFile);
    }

    @Test
    @Order(11)
    @DisplayName("E2E: textToSpeech should generate MP3 audio")
    void textToSpeech_generatesMp3Audio() throws IOException {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available");

        TTSRequest request = TTSRequest.builder()
                .voiceId(testVoiceId)
                .text("This is an MP3 format test.")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .output(Output.builder()
                        .audioFormat(AudioFormat.MP3)
                        .build())
                .build();

        TTSResponse response = client.textToSpeech(request);

        assertNotNull(response);
        assertEquals("mp3", response.getFormat());
        assertTrue(response.getAudioData().length > 0);

        // Save audio for manual verification
        Path audioFile = tempDir.resolve("test_mp3.mp3");
        try (FileOutputStream fos = new FileOutputStream(audioFile.toFile())) {
            fos.write(response.getAudioData());
        }
        System.out.println("Saved MP3 audio to: " + audioFile);
    }

    @Test
    @Order(12)
    @DisplayName("E2E: textToSpeech with preset emotion should work")
    void textToSpeech_withPresetEmotion() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available");

        TTSRequest request = TTSRequest.builder()
                .voiceId(testVoiceId)
                .text("I am so happy today!")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .prompt(PresetPrompt.builder()
                        .emotionPreset(EmotionPreset.HAPPY)
                        .emotionIntensity(1.5)
                        .build())
                .build();

        TTSResponse response = client.textToSpeech(request);

        assertNotNull(response);
        assertTrue(response.getAudioData().length > 0);
        assertTrue(response.getDuration() > 0);
    }

    @Test
    @Order(13)
    @DisplayName("E2E: textToSpeech with smart emotion should work")
    void textToSpeech_withSmartEmotion() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available");

        TTSRequest request = TTSRequest.builder()
                .voiceId(testVoiceId)
                .text("Everything turned out perfectly.")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .prompt(SmartPrompt.builder()
                        .previousText("After all that hard work,")
                        .nextText("I couldn't be happier.")
                        .build())
                .build();

        TTSResponse response = client.textToSpeech(request);

        assertNotNull(response);
        assertTrue(response.getAudioData().length > 0);
    }

    @Test
    @Order(14)
    @DisplayName("E2E: textToSpeech with output settings should work")
    void textToSpeech_withOutputSettings() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available");

        TTSRequest request = TTSRequest.builder()
                .voiceId(testVoiceId)
                .text("Testing output settings.")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .output(Output.builder()
                        .volume(120)
                        .audioPitch(2)
                        .audioTempo(1.1)
                        .audioFormat(AudioFormat.WAV)
                        .build())
                .build();

        TTSResponse response = client.textToSpeech(request);

        assertNotNull(response);
        assertTrue(response.getAudioData().length > 0);
    }

    @Test
    @Order(15)
    @DisplayName("E2E: textToSpeech with Korean text should work")
    void textToSpeech_koreanText() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid");

        TTSRequest request = TTSRequest.builder()
                .voiceId(testVoiceId)
                .text("안녕하세요, 타입캐스트 자바 SDK 테스트입니다.")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.KOR)
                .build();

        TTSResponse response = client.textToSpeech(request);

        assertNotNull(response);
        assertTrue(response.getAudioData().length > 0);
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(20)
    @DisplayName("E2E: Invalid API key should throw ForbiddenException")
    void invalidApiKey_throwsForbidden() {
        TypecastClient invalidClient = new TypecastClient("invalid_api_key");
        
        try {
            TTSRequest request = TTSRequest.builder()
                    .voiceId("tc_test")
                    .text("Test")
                    .model(TTSModel.SSFM_V30)
                    .build();

            assertThrows(ForbiddenException.class, () -> 
                    invalidClient.textToSpeech(request));
        } finally {
            invalidClient.close();
        }
    }

    // ==================== Deprecated V1 API Tests ====================

    @Test
    @Order(30)
    @DisplayName("E2E: getVoices (V1) should return voices")
    @SuppressWarnings("deprecation")
    void getVoices_v1_returnsVoices() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid");

        List<VoicesResponse> voices = client.getVoices();

        assertNotNull(voices);
        assertFalse(voices.isEmpty());
    }

    @Test
    @Order(31)
    @DisplayName("E2E: getVoice (V1) should return specific voice")
    @SuppressWarnings("deprecation")
    void getVoice_v1_returnsVoice() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid");

        VoicesResponse voice = client.getVoice(testVoiceId);

        assertNotNull(voice);
        assertEquals(testVoiceId, voice.getVoiceId());
    }
}
