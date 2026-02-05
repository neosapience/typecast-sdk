package io.typecast

import io.typecast.exceptions.ForbiddenException
import io.typecast.exceptions.NotFoundException
import io.typecast.models.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.charset.StandardCharsets

/**
 * Unit tests for TypecastClient using MockWebServer.
 */
class TypecastClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: TypecastClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        
        client = TypecastClient.builder()
            .apiKey("test-api-key")
            .baseUrl(mockServer.url("/").toString())
            .build()
    }

    @AfterEach
    fun tearDown() {
        client.close()
        mockServer.shutdown()
    }

    // ==================== TTS Tests ====================

    @Test
    @DisplayName("textToSpeech should successfully convert text to speech")
    fun textToSpeech_success() {
        val mockAudioData = "fake audio data".toByteArray(StandardCharsets.UTF_8)
        
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setHeader("X-Audio-Duration", "1.5")
                .setBody(okio.Buffer().write(mockAudioData))
        )

        val request = TTSRequest.builder()
            .voiceId("tc_test_voice")
            .text("Hello, world!")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .build()

        val response = client.textToSpeech(request)

        assertEquals(1.5, response.duration)
        assertEquals("wav", response.format)
        assertArrayEquals(mockAudioData, response.audioData)
        
        // Verify request
        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1/text-to-speech", recordedRequest.path)
        assertEquals("test-api-key", recordedRequest.getHeader("X-API-KEY"))
        assertTrue(recordedRequest.body.readUtf8().contains("tc_test_voice"))
    }

    @Test
    @DisplayName("textToSpeech should return MP3 format when requested")
    fun textToSpeech_mp3Format() {
        val mockAudioData = "fake mp3 data".toByteArray()
        
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setHeader("X-Audio-Duration", "2.0")
                .setBody(okio.Buffer().write(mockAudioData))
        )

        val request = TTSRequest.builder()
            .voiceId("tc_test_voice")
            .text("Hello!")
            .model(TTSModel.SSFM_V30)
            .output(Output.builder().audioFormat(AudioFormat.MP3).build())
            .build()

        val response = client.textToSpeech(request)

        assertEquals("mp3", response.format)
        assertEquals(2.0, response.duration)
    }

    @Test
    @DisplayName("textToSpeech with preset prompt should work")
    fun textToSpeech_withPresetPrompt() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write("audio".toByteArray()))
        )

        val request = TTSRequest.builder()
            .voiceId("tc_test")
            .text("I am happy!")
            .model(TTSModel.SSFM_V30)
            .prompt(PresetPrompt.builder()
                .emotionPreset(EmotionPreset.HAPPY)
                .emotionIntensity(1.5)
                .build())
            .build()

        val response = client.textToSpeech(request)
        assertNotNull(response)
        
        val recordedRequest = mockServer.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"emotion_type\":\"preset\""))
        assertTrue(body.contains("\"emotion_preset\":\"happy\""))
    }

    @Test
    @DisplayName("textToSpeech with smart prompt should work")
    fun textToSpeech_withSmartPrompt() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write("audio".toByteArray()))
        )

        val request = TTSRequest.builder()
            .voiceId("tc_test")
            .text("Everything is perfect.")
            .model(TTSModel.SSFM_V30)
            .prompt(SmartPrompt.builder()
                .previousText("After all that hard work,")
                .nextText("I couldn't be happier.")
                .build())
            .build()

        val response = client.textToSpeech(request)
        assertNotNull(response)
        
        val recordedRequest = mockServer.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"emotion_type\":\"smart\""))
        assertTrue(body.contains("\"previous_text\""))
    }

    // ==================== Voices V2 Tests ====================

    @Test
    @DisplayName("getVoicesV2 should return list of voices")
    fun getVoicesV2_success() {
        val mockResponse = """
            [
                {
                    "voice_id": "tc_voice1",
                    "voice_name": "Voice 1",
                    "models": [{"version": "ssfm-v30", "emotions": ["normal", "happy"]}],
                    "gender": "female",
                    "age": "young_adult"
                },
                {
                    "voice_id": "tc_voice2",
                    "voice_name": "Voice 2",
                    "models": [{"version": "ssfm-v21", "emotions": ["normal", "sad"]}],
                    "gender": "male",
                    "age": "middle_age"
                }
            ]
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponse)
        )

        val voices = client.getVoicesV2()

        assertEquals(2, voices.size)
        assertEquals("tc_voice1", voices[0].voiceId)
        assertEquals("Voice 1", voices[0].voiceName)
        assertEquals(GenderEnum.FEMALE, voices[0].gender)
        assertEquals(AgeEnum.YOUNG_ADULT, voices[0].age)
    }

    @Test
    @DisplayName("getVoicesV2 with filter should pass query parameters")
    fun getVoicesV2_withFilter() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        val filter = VoicesV2Filter.builder()
            .model(TTSModel.SSFM_V30)
            .gender(GenderEnum.FEMALE)
            .age(AgeEnum.YOUNG_ADULT)
            .build()

        client.getVoicesV2(filter)

        val recordedRequest = mockServer.takeRequest()
        val path = recordedRequest.path!!
        assertTrue(path.contains("model=ssfm-v30"))
        assertTrue(path.contains("gender=female"))
        assertTrue(path.contains("age=young_adult"))
    }

    @Test
    @DisplayName("getVoiceV2 should return specific voice")
    fun getVoiceV2_success() {
        val mockResponse = """
            {
                "voice_id": "tc_specific",
                "voice_name": "Specific Voice",
                "models": [{"version": "ssfm-v30", "emotions": ["normal"]}],
                "gender": "male",
                "age": "teenager"
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponse)
        )

        val voice = client.getVoiceV2("tc_specific")

        assertEquals("tc_specific", voice.voiceId)
        assertEquals("Specific Voice", voice.voiceName)
        assertEquals(GenderEnum.MALE, voice.gender)
    }

    @Test
    @DisplayName("getVoiceV2 should throw NotFoundException for invalid voice")
    fun getVoiceV2_notFound() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Voice not found"}""")
        )

        assertThrows(NotFoundException::class.java) {
            client.getVoiceV2("invalid_voice")
        }
    }

    // ==================== V1 API Tests (Deprecated) ====================

    @Test
    @DisplayName("getVoices (V1) should return list of voices")
    @Suppress("DEPRECATION")
    fun getVoices_v1_success() {
        val mockResponse = """
            [
                {"voice_id": "tc_v1", "voice_name": "V1 Voice", "model": "ssfm-v30", "emotions": ["normal"]}
            ]
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponse)
        )

        val voices = client.getVoices()

        assertEquals(1, voices.size)
        assertEquals("tc_v1", voices[0].voiceId)
    }

    // ==================== Error Handling Tests ====================

    @Test
    @DisplayName("Should throw ForbiddenException for 403 response")
    fun errorHandling_forbidden() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Invalid API key"}""")
        )

        val request = TTSRequest.builder()
            .voiceId("tc_test")
            .text("Test")
            .model(TTSModel.SSFM_V30)
            .build()

        val exception = assertThrows(ForbiddenException::class.java) {
            client.textToSpeech(request)
        }

        assertTrue(exception.message!!.contains("Invalid API key"))
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("TTSRequest should validate required fields")
    fun ttsRequest_validation() {
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest.builder()
                .voiceId("")
                .text("Test")
                .model(TTSModel.SSFM_V30)
                .build()
        }

        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest.builder()
                .voiceId("tc_test")
                .text("")
                .model(TTSModel.SSFM_V30)
                .build()
        }
    }

    @Test
    @DisplayName("Output should validate ranges")
    fun output_validation() {
        assertThrows(IllegalArgumentException::class.java) {
            Output(volume = 300) // max is 200
        }

        assertThrows(IllegalArgumentException::class.java) {
            Output(audioPitch = 20) // max is 12
        }

        assertThrows(IllegalArgumentException::class.java) {
            Output(audioTempo = 3.0) // max is 2.0
        }
    }
}
