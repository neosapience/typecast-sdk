package io.typecast

import io.github.cdimascio.dotenv.dotenv
import io.typecast.exceptions.ForbiddenException
import io.typecast.exceptions.NotFoundException
import io.typecast.models.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files

/**
 * End-to-End tests for TypecastClient against the real API.
 *
 * These tests require a valid API key set in the environment or .env file.
 * Run with: `./gradlew e2eTest`
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TypecastClientE2ETest {

    companion object {
        private lateinit var client: TypecastClient
        private var testVoiceId: String? = null
        private lateinit var tempDir: File

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
            // Load API key from .env file or environment
            val apiKey = try {
                val env = dotenv { ignoreIfMissing = true }
                env["TYPECAST_API_KEY"] ?: System.getenv("TYPECAST_API_KEY")
            } catch (e: Exception) {
                System.getenv("TYPECAST_API_KEY")
            }

            Assumptions.assumeTrue(
                !apiKey.isNullOrBlank(),
                "Skipping E2E tests: TYPECAST_API_KEY not set"
            )

            client = TypecastClient.create(apiKey!!)
            tempDir = Files.createTempDirectory("typecast-e2e-kotlin-").toFile()

            // Validate API key by making a test call
            // Find a voice that supports ssfm-v30 model
            try {
                val filter = VoicesV2Filter.builder()
                    .model(TTSModel.SSFM_V30)
                    .build()
                val voices = client.getVoicesV2(filter)
                if (voices.isNotEmpty()) {
                    testVoiceId = voices.first().voiceId
                    println("Using test voice: $testVoiceId (${voices.first().voiceName})")
                }
            } catch (e: ForbiddenException) {
                println("API key is invalid, E2E tests will be skipped")
                testVoiceId = null
            } catch (e: Exception) {
                println("Failed to validate API key: ${e.message}")
                testVoiceId = null
            }
        }

        @AfterAll
        @JvmStatic
        fun tearDownClass() {
            if (::client.isInitialized) {
                client.close()
            }
        }
    }

    // ==================== Voice Discovery Tests ====================

    @Test
    @Order(1)
    @DisplayName("E2E: getVoicesV2 should return list of voices")
    fun getVoicesV2_returnsVoices() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid")

        val voices = client.getVoicesV2()

        assertNotNull(voices)
        assertTrue(voices.isNotEmpty(), "Should return at least one voice")

        // Verify voice structure
        val firstVoice = voices.first()
        assertNotNull(firstVoice.voiceId)
        assertNotNull(firstVoice.voiceName)
        assertNotNull(firstVoice.models)
        assertTrue(firstVoice.models.isNotEmpty())
    }

    @Test
    @Order(2)
    @DisplayName("E2E: getVoicesV2 with filter should return filtered voices")
    fun getVoicesV2_withFilter() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid")

        val filter = VoicesV2Filter.builder()
            .model(TTSModel.SSFM_V30)
            .build()

        val voices = client.getVoicesV2(filter)

        assertNotNull(voices)
        // All returned voices should support ssfm-v30
        voices.forEach { voice ->
            val supportsSsfmV30 = voice.models.any { it.version == "ssfm-v30" }
            assertTrue(supportsSsfmV30, "Voice ${voice.voiceId} should support ssfm-v30")
        }
    }

    @Test
    @Order(3)
    @DisplayName("E2E: getVoiceV2 should return specific voice")
    fun getVoiceV2_returnsVoice() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available")

        val voice = client.getVoiceV2(testVoiceId!!)

        assertNotNull(voice)
        assertEquals(testVoiceId, voice.voiceId)
        assertNotNull(voice.voiceName)
    }

    @Test
    @Order(4)
    @DisplayName("E2E: getVoiceV2 with invalid ID should throw exception")
    fun getVoiceV2_notFound() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key may be invalid")

        // API may return 400 (BadRequest) or 404 (NotFound) for invalid voice IDs
        val exception = assertThrows(io.typecast.exceptions.TypecastException::class.java) {
            client.getVoiceV2("invalid_voice_id_that_does_not_exist")
        }
        assertTrue(exception is NotFoundException || exception is io.typecast.exceptions.BadRequestException,
            "Expected NotFoundException or BadRequestException, got ${exception::class.simpleName}")
    }

    // ==================== Text-to-Speech Tests ====================

    @Test
    @Order(10)
    @DisplayName("E2E: textToSpeech should generate WAV audio")
    fun textToSpeech_generatesWavAudio() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available")

        val request = TTSRequest.builder()
            .voiceId(testVoiceId!!)
            .text("Hello, this is a test of the Typecast Kotlin SDK.")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .build()

        val response = client.textToSpeech(request)

        assertNotNull(response)
        assertNotNull(response.audioData)
        assertTrue(response.audioData.isNotEmpty(), "Audio data should not be empty")
        assertTrue(response.duration >= 0, "Duration should not be negative")
        assertEquals("wav", response.format)

        // Save audio for manual verification
        val audioFile = File(tempDir, "test_wav.wav")
        audioFile.writeBytes(response.audioData)
        println("Saved WAV audio to: ${audioFile.absolutePath}")
    }

    @Test
    @Order(11)
    @DisplayName("E2E: textToSpeech should generate MP3 audio")
    fun textToSpeech_generatesMp3Audio() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available")

        val request = TTSRequest.builder()
            .voiceId(testVoiceId!!)
            .text("This is an MP3 format test.")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .output(Output.builder()
                .audioFormat(AudioFormat.MP3)
                .build())
            .build()

        val response = client.textToSpeech(request)

        assertNotNull(response)
        assertEquals("mp3", response.format)
        assertTrue(response.audioData.isNotEmpty())

        // Save audio for manual verification
        val audioFile = File(tempDir, "test_mp3.mp3")
        audioFile.writeBytes(response.audioData)
        println("Saved MP3 audio to: ${audioFile.absolutePath}")
    }

    @Test
    @Order(12)
    @DisplayName("E2E: textToSpeech with preset emotion should work")
    fun textToSpeech_withPresetEmotion() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available")

        val request = TTSRequest.builder()
            .voiceId(testVoiceId!!)
            .text("I am so happy today!")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .prompt(PresetPrompt.builder()
                .emotionPreset(EmotionPreset.HAPPY)
                .emotionIntensity(1.5)
                .build())
            .build()

        val response = client.textToSpeech(request)

        assertNotNull(response)
        assertTrue(response.audioData.isNotEmpty())
        // Duration may be 0 if X-Audio-Duration header is not provided
    }

    @Test
    @Order(13)
    @DisplayName("E2E: textToSpeech with smart emotion should work")
    fun textToSpeech_withSmartEmotion() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available")

        val request = TTSRequest.builder()
            .voiceId(testVoiceId!!)
            .text("Everything turned out perfectly.")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .prompt(SmartPrompt.builder()
                .previousText("After all that hard work,")
                .nextText("I couldn't be happier.")
                .build())
            .build()

        val response = client.textToSpeech(request)

        assertNotNull(response)
        assertTrue(response.audioData.isNotEmpty())
    }

    @Test
    @Order(14)
    @DisplayName("E2E: textToSpeech with output settings should work")
    fun textToSpeech_withOutputSettings() {
        Assumptions.assumeTrue(testVoiceId != null, "Test voice ID not available")

        val request = TTSRequest.builder()
            .voiceId(testVoiceId!!)
            .text("Testing output settings.")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .output(Output.builder()
                .volume(120)
                .audioPitch(2)
                .audioTempo(1.1)
                .audioFormat(AudioFormat.WAV)
                .build())
            .build()

        val response = client.textToSpeech(request)

        assertNotNull(response)
        assertTrue(response.audioData.isNotEmpty())
    }

    @Test
    @Order(15)
    @DisplayName("E2E: textToSpeech with Korean text should work")
    fun textToSpeech_koreanText() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid")

        val request = TTSRequest.builder()
            .voiceId(testVoiceId!!)
            .text("안녕하세요, 타입캐스트 코틀린 SDK 테스트입니다.")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.KOR)
            .build()

        val response = client.textToSpeech(request)

        assertNotNull(response)
        assertTrue(response.audioData.isNotEmpty())
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(20)
    @DisplayName("E2E: Invalid API key should throw ForbiddenException")
    fun invalidApiKey_throwsForbidden() {
        val invalidClient = TypecastClient.create("invalid_api_key")

        try {
            val request = TTSRequest.builder()
                .voiceId("tc_test")
                .text("Test")
                .model(TTSModel.SSFM_V30)
                .build()

            assertThrows(ForbiddenException::class.java) {
                invalidClient.textToSpeech(request)
            }
        } finally {
            invalidClient.close()
        }
    }

    // ==================== Deprecated V1 API Tests ====================

    @Test
    @Order(30)
    @DisplayName("E2E: getVoices (V1) should return voices")
    @Suppress("DEPRECATION")
    fun getVoices_v1_returnsVoices() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid")

        val voices = client.getVoices()

        assertNotNull(voices)
        assertTrue(voices.isNotEmpty())
    }

    @Test
    @Order(31)
    @DisplayName("E2E: getVoice (V1) should return specific voice")
    @Suppress("DEPRECATION")
    fun getVoice_v1_returnsVoice() {
        Assumptions.assumeTrue(testVoiceId != null, "Skipping: API key is invalid")

        val voice = client.getVoice(testVoiceId!!)

        assertNotNull(voice)
        assertEquals(testVoiceId, voice.voiceId)
    }
}
