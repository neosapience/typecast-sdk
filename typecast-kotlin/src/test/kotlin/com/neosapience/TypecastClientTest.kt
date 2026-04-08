package com.neosapience

import com.neosapience.exceptions.ForbiddenException
import com.neosapience.exceptions.InternalServerException
import com.neosapience.exceptions.NotFoundException
import com.neosapience.exceptions.RateLimitException
import com.neosapience.exceptions.UnauthorizedException
import com.neosapience.models.*
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

    // ==================== Subscription Tests ====================

    @Test
    @DisplayName("getMySubscription should return subscription information")
    fun getMySubscription_success() {
        val mockResponse = """
            {
                "plan": "plus",
                "credits": {
                    "plan_credits": 10000,
                    "used_credits": 1234
                },
                "limits": {
                    "concurrency_limit": 5
                }
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(mockResponse)
        )

        val subscription = client.getMySubscription()

        assertEquals(PlanTier.PLUS, subscription.plan)
        assertEquals("plus", subscription.plan.value)
        assertEquals(10000L, subscription.credits.planCredits)
        assertEquals(1234L, subscription.credits.usedCredits)
        assertEquals(5L, subscription.limits.concurrencyLimit)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("GET", recordedRequest.method)
        assertEquals("/v1/users/me/subscription", recordedRequest.path)
        assertEquals("test-api-key", recordedRequest.getHeader("X-API-KEY"))
    }

    @Test
    @DisplayName("Subscription model classes should be constructable directly")
    fun subscription_modelsConstructable() {
        // Exercise the public primary constructors directly. kotlinx.serialization
        // uses a synthetic deserialization constructor instead, so the primary
        // constructors are not invoked by getMySubscription() alone.
        val credits = Credits(planCredits = 100L, usedCredits = 25L)
        assertEquals(100L, credits.planCredits)
        assertEquals(25L, credits.usedCredits)

        val limits = Limits(concurrencyLimit = 4L)
        assertEquals(4L, limits.concurrencyLimit)

        val sub = SubscriptionResponse(plan = PlanTier.FREE, credits = credits, limits = limits)
        assertEquals(PlanTier.FREE, sub.plan)
        assertEquals(credits, sub.credits)
        assertEquals(limits, sub.limits)
    }

    @Test
    @DisplayName("getMySubscription should support all plan tiers")
    fun getMySubscription_allPlanTiers() {
        // Cover all enum values to ensure they (de)serialize correctly.
        val tiers = listOf(
            "free" to PlanTier.FREE,
            "lite" to PlanTier.LITE,
            "plus" to PlanTier.PLUS,
            "custom" to PlanTier.CUSTOM
        )

        for ((wire, expected) in tiers) {
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                            "plan": "$wire",
                            "credits": {"plan_credits": 0, "used_credits": 0},
                            "limits": {"concurrency_limit": 1}
                        }
                        """.trimIndent()
                    )
            )

            val subscription = client.getMySubscription()
            assertEquals(expected, subscription.plan)
            assertEquals(wire, subscription.plan.value)
        }
    }

    @Test
    @DisplayName("getMySubscription should throw UnauthorizedException for 401")
    fun getMySubscription_unauthorized() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Invalid API key"}""")
        )

        val exception = assertThrows(UnauthorizedException::class.java) {
            client.getMySubscription()
        }
        assertTrue(exception.message!!.contains("Invalid API key"))
    }

    @Test
    @DisplayName("getMySubscription should throw RateLimitException for 429")
    fun getMySubscription_rateLimited() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Too many requests"}""")
        )

        assertThrows(RateLimitException::class.java) {
            client.getMySubscription()
        }
    }

    @Test
    @DisplayName("getMySubscription should throw InternalServerException for 500")
    fun getMySubscription_internalServerError() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Internal server error"}""")
        )

        assertThrows(InternalServerException::class.java) {
            client.getMySubscription()
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

        assertThrows(IllegalArgumentException::class.java) {
            Output(targetLufs = -71.0) // min is -70
        }

        assertThrows(IllegalArgumentException::class.java) {
            Output(targetLufs = 1.0) // max is 0
        }

        assertThrows(IllegalArgumentException::class.java) {
            Output(volume = 100, targetLufs = -14.0) // cannot use both
        }

        assertDoesNotThrow {
            Output(targetLufs = -14.0) // valid target_lufs without volume
        }

        val output = Output(targetLufs = -14.0)
        assertNull(output.volume)
        assertEquals(-14.0, output.targetLufs)
    }
}
