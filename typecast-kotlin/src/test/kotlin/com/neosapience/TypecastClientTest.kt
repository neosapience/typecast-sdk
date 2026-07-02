package com.neosapience

import com.neosapience.exceptions.BadRequestException
import com.neosapience.exceptions.ForbiddenException
import com.neosapience.exceptions.InternalServerException
import com.neosapience.exceptions.NotFoundException
import com.neosapience.exceptions.PaymentRequiredException
import com.neosapience.exceptions.RateLimitException
import com.neosapience.exceptions.UnauthorizedException
import com.neosapience.exceptions.UnprocessableEntityException
import com.neosapience.models.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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
    @DisplayName("proxy base URL can be used without API key")
    fun proxyBaseUrl_withoutApiKey_omitsAuthHeader() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write("audio".toByteArray()))
        )

        val proxyClient = TypecastClient.builder()
            .baseUrl(mockServer.url("/").toString())
            .build()

        val request = TTSRequest.builder()
            .voiceId("tc_test_voice")
            .text("Hello, proxy!")
            .model(TTSModel.SSFM_V30)
            .build()

        proxyClient.textToSpeech(request)

        val recordedRequest = mockServer.takeRequest()
        assertNull(recordedRequest.getHeader("X-API-KEY"))
        proxyClient.close()
    }

    @Test
    @DisplayName("proxy base URL treats blank API key as absent")
    fun proxyBaseUrl_withBlankApiKey_omitsAuthHeader() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write("audio".toByteArray()))
        )

        val proxyClient = TypecastClient.builder()
            .apiKey("")
            .baseUrl(mockServer.url("/").toString())
            .build()

        val request = TTSRequest.builder()
            .voiceId("tc_test_voice")
            .text("Hello, proxy!")
            .model(TTSModel.SSFM_V30)
            .build()

        proxyClient.textToSpeech(request)

        val recordedRequest = mockServer.takeRequest()
        assertNull(recordedRequest.getHeader("X-API-KEY"))
        proxyClient.close()
    }

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
        assertTrue(recordedRequest.getHeader("User-Agent")!!.contains("typecast-kotlin/"))
        assertTrue(recordedRequest.getHeader("User-Agent")!!.contains("sdk_env=kotlin"))
        assertTrue(recordedRequest.body.readUtf8().contains("tc_test_voice"))
    }

    @Test
    @DisplayName("User-Agent helpers should cover platform metadata variants")
    fun userAgent_platformMetadataVariants() {
        assertEquals("macos", TypecastClient.normalizeOsName("Mac OS X"))
        assertEquals("windows", TypecastClient.normalizeOsName("Windows 11"))
        assertEquals("linux", TypecastClient.normalizeOsName("Linux"))
        assertEquals("unknown", TypecastClient.normalizeOsName("Solaris"))

        assertEquals("arm64", TypecastClient.normalizeArchName("aarch64"))
        assertEquals("arm64", TypecastClient.normalizeArchName("arm64"))
        assertEquals("x64", TypecastClient.normalizeArchName("x86_64"))
        assertEquals("x64", TypecastClient.normalizeArchName("amd64"))
        assertEquals("x86", TypecastClient.normalizeArchName("x86"))
        assertEquals("x86", TypecastClient.normalizeArchName("i386"))
        assertEquals("x86", TypecastClient.normalizeArchName("i686"))
        assertEquals("arm", TypecastClient.normalizeArchName("armv7"))
        assertEquals("unknown", TypecastClient.normalizeArchName("mips"))

        TypecastClient.builder().apiKey("key").build().use { defaultClient ->
            assertTrue(defaultClient.buildUserAgent().contains("base=default"))
        }
        TypecastClient.builder()
            .apiKey("key")
            .baseUrl("https://proxy.example")
            .httpClient(OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build())
            .build()
            .use { customClient ->
                val userAgent = customClient.buildUserAgent()
                assertTrue(userAgent.contains("base=custom"))
                assertTrue(userAgent.contains("timeout=5000ms"))
            }
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

    @Test
    @DisplayName("generateToFile should infer mp3, default model, and write file")
    fun generateToFile_infersMp3AndWritesFile() {
        val audio = byteArrayOf(1, 2, 3)
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setHeader("X-Audio-Duration", "1.25")
                .setBody(okio.Buffer().write(audio))
        )
        val output = Files.createTempFile("typecast-kotlin-", ".mp3")
        Files.deleteIfExists(output)

        try {
            val response = client.generateToFile(
                output.toString(),
                GenerateToFileRequest(
                    voiceId = "tc_test",
                    text = "Hello",
                    language = LanguageCode.ENG,
                    prompt = Prompt(emotionPreset = EmotionPreset.NORMAL),
                    seed = 7,
                )
            )

            assertEquals("mp3", response.format)
            assertArrayEquals(audio, Files.readAllBytes(output))
            val body = mockServer.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"model\":\"ssfm-v30\""))
            assertTrue(body.contains("\"audio_format\":\"mp3\""))
            assertTrue(body.contains("\"language\":\"eng\""))
            assertTrue(body.contains("\"seed\":7"))
        } finally {
            Files.deleteIfExists(output)
        }
    }

    @Test
    @DisplayName("generateToFile should keep explicit output and validate arguments")
    fun generateToFile_keepsExplicitOutputAndValidates() {
        assertThrows<IllegalArgumentException> {
            client.generateToFile("", GenerateToFileRequest("tc_test", "Hello"))
        }
        assertThrows<IllegalArgumentException> { GenerateToFileRequest("", "Hello") }
        assertThrows<IllegalArgumentException> { GenerateToFileRequest("tc_test", "") }
        assertThrows<IllegalArgumentException> {
            GenerateToFileRequest("tc_test", "가".repeat(2001))
        }
        val inspected = GenerateToFileRequest(
            voiceId = "tc_test",
            text = "Hello",
            model = TTSModel.SSFM_V21,
            language = LanguageCode.ENG,
            prompt = SmartPrompt(previousText = "before"),
            output = Output(audioFormat = AudioFormat.WAV),
            seed = 9,
        )
        assertEquals("tc_test", inspected.voiceId)
        assertEquals("Hello", inspected.text)
        assertEquals(TTSModel.SSFM_V21, inspected.model)
        assertEquals(LanguageCode.ENG, inspected.language)
        assertNotNull(inspected.prompt)
        assertEquals(AudioFormat.WAV, inspected.output?.audioFormat)
        assertEquals(9, inspected.seed)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write(byteArrayOf(4)))
        )
        val output = Files.createTempFile("typecast-kotlin-", ".wav")
        Files.deleteIfExists(output)

        try {
            client.generateToFile(
                output.toString(),
                GenerateToFileRequest(
                    voiceId = "tc_test",
                    text = "Hello",
                    model = TTSModel.SSFM_V21,
                    prompt = PresetPrompt(emotionPreset = EmotionPreset.HAPPY),
                    output = Output(audioFormat = AudioFormat.MP3),
                )
            )
            val body = mockServer.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"model\":\"ssfm-v21\""))
            assertTrue(body.contains("\"audio_format\":\"mp3\""))
            assertTrue(body.contains("\"emotion_type\":\"preset\""))
        } finally {
            Files.deleteIfExists(output)
        }

        assertEquals(
            AudioFormat.WAV,
            GenerateToFileRequest("tc_test", "Hello").toTTSRequest("x.WAV").output?.audioFormat,
        )
        assertNull(GenerateToFileRequest("tc_test", "Hello").toTTSRequest("x.bin").output)
    }

    // ==================== TTS Stream Tests ====================

    @Test
    @DisplayName("textToSpeechStream should successfully stream audio bytes")
    fun textToSpeechStream_success() {
        val mockAudioData = "fake wav stream data".toByteArray(StandardCharsets.UTF_8)

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setBody(okio.Buffer().write(mockAudioData))
        )

        val request = TTSRequestStream.builder()
            .voiceId("tc_test_voice")
            .text("Hello, world!")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .output(OutputStream.builder().audioFormat("wav").targetLufs(-14.0).build())
            .build()

        val received = client.textToSpeechStream(request).use { it.readBytes() }
        assertArrayEquals(mockAudioData, received)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1/text-to-speech/stream", recordedRequest.path)
        assertEquals("test-api-key", recordedRequest.getHeader("X-API-KEY"))
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"voice_id\":\"tc_test_voice\""))
        assertTrue(body.contains("\"text\":\"Hello, world!\""))
        assertTrue(body.contains("\"audio_format\":\"wav\""))
        // OutputStream must NOT serialize volume, but accepts target_lufs.
        assertFalse(body.contains("volume"))
        assertTrue(body.contains("\"target_lufs\":-14.0"))
    }

    @Test
    @DisplayName("textToSpeechStream should throw BadRequestException on 400")
    fun textToSpeechStream_badRequest() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "bad request"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(BadRequestException::class.java) { client.textToSpeechStream(request) }
    }

    @Test
    @DisplayName("textToSpeechStream should throw UnauthorizedException on 401")
    fun textToSpeechStream_unauthorized() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "no auth"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(UnauthorizedException::class.java) { client.textToSpeechStream(request) }
    }

    @Test
    @DisplayName("textToSpeechStream should throw PaymentRequiredException on 402")
    fun textToSpeechStream_paymentRequired() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "pay up"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(PaymentRequiredException::class.java) { client.textToSpeechStream(request) }
    }

    @Test
    @DisplayName("textToSpeechStream should throw NotFoundException on 404")
    fun textToSpeechStream_notFound() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "missing"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(NotFoundException::class.java) { client.textToSpeechStream(request) }
    }

    @Test
    @DisplayName("textToSpeechStream should throw UnprocessableEntityException on 422")
    fun textToSpeechStream_unprocessable() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "bad params"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(UnprocessableEntityException::class.java) { client.textToSpeechStream(request) }
    }

    @Test
    @DisplayName("textToSpeechStream should throw RateLimitException on 429")
    fun textToSpeechStream_rateLimited() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "slow down"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(RateLimitException::class.java) { client.textToSpeechStream(request) }
    }

    @Test
    @DisplayName("textToSpeechStream should throw InternalServerException on 500")
    fun textToSpeechStream_internalServerError() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "boom"}""")
        )
        val request = TTSRequestStream.builder()
            .voiceId("tc_test").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(InternalServerException::class.java) { client.textToSpeechStream(request) }
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

    @Test
    @DisplayName("recommendVoices should return scored voice recommendations")
    fun recommendVoices_success() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"voice_id":"tc_rec","voice_name":"Recommended","score":0.97}]""")
        )

        val voices = client.recommendVoices("warm narrator", 2)

        assertEquals(1, voices.size)
        assertEquals("tc_rec", voices.single().voiceId)
        assertEquals("Recommended", voices.single().voiceName)
        assertEquals(0.97, voices.single().score)
        assertEquals(
            "/v1/voices/recommendations?query=warm%20narrator&count=2",
            mockServer.takeRequest().path
        )
    }

    @Test
    @DisplayName("recommendVoices should default count and validate range")
    fun recommendVoices_defaultCountAndValidation() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )

        assertTrue(client.recommendVoices("voice", 0).isEmpty())
        assertEquals("/v1/voices/recommendations?query=voice&count=5", mockServer.takeRequest().path)
        assertTrue(client.recommendVoices("voice").isEmpty())
        assertEquals("/v1/voices/recommendations?query=voice&count=5", mockServer.takeRequest().path)
        assertThrows(IllegalArgumentException::class.java) {
            client.recommendVoices("  ", 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.recommendVoices("voice", -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.recommendVoices("voice", 11)
        }
    }

    @Test
    @DisplayName("recommendVoices should propagate API errors")
    fun recommendVoices_error() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail":"Invalid API key"}""")
        )

        assertThrows(UnauthorizedException::class.java) {
            client.recommendVoices("voice", 1)
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
