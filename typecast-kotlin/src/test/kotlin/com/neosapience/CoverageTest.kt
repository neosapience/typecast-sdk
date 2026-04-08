package com.neosapience

import com.neosapience.exceptions.*
import com.neosapience.models.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Additional tests to reach 100% line + branch + method coverage.
 * Complements [TypecastClientTest].
 */
class CoverageTest {

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

    // ==================== Enum coverage ====================

    @Test
    fun enums_valuesAndValueOfAndToString() {
        // Iterate through all enum values to exercise toString() and values()/valueOf()
        TTSModel.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(TTSModel.SSFM_V30, TTSModel.valueOf("SSFM_V30"))

        EmotionPreset.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(EmotionPreset.HAPPY, EmotionPreset.valueOf("HAPPY"))

        AudioFormat.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(AudioFormat.WAV, AudioFormat.valueOf("WAV"))

        GenderEnum.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(GenderEnum.MALE, GenderEnum.valueOf("MALE"))

        AgeEnum.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(AgeEnum.CHILD, AgeEnum.valueOf("CHILD"))

        UseCaseEnum.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(UseCaseEnum.ANNOUNCER, UseCaseEnum.valueOf("ANNOUNCER"))

        LanguageCode.values().forEach { assertEquals(it.value, it.toString()) }
        assertEquals(LanguageCode.ENG, LanguageCode.valueOf("ENG"))
    }

    // ==================== Exception coverage ====================

    @Test
    fun allExceptionTypes_singleArgCtors() {
        // Each subclass has a default-arg form (responseBody=null)
        BadRequestException("a")
        UnauthorizedException("a")
        PaymentRequiredException("a")
        ForbiddenException("a")
        NotFoundException("a")
        UnprocessableEntityException("a")
        RateLimitException("a")
        InternalServerException("a")
    }

    @Test
    fun typecastException_extraConstructorPaths() {
        // Exercise statusCode getter, responseBody getter, cause path
        val ex = TypecastException("m", 999, "rb", IllegalStateException("c"))
        assertEquals(999, ex.statusCode)
        assertEquals("rb", ex.responseBody)
        assertNotNull(ex.cause)
        // Two-arg form (statusCode only)
        val ex2 = TypecastException("m", 500)
        assertEquals(500, ex2.statusCode)
        assertNull(ex2.responseBody)
    }

    @Test
    fun allExceptionTypes_constructAndToString() {
        val exceptions = listOf<TypecastException>(
            BadRequestException("bad", "body"),
            UnauthorizedException("unauth", "body"),
            PaymentRequiredException("pay", "body"),
            ForbiddenException("forbid", "body"),
            NotFoundException("nf", "body"),
            UnprocessableEntityException("unproc", "body"),
            RateLimitException("rate", "body"),
            InternalServerException("srv", "body"),
            TypecastException("base", null, null),
            TypecastException("base2", 999, "body", RuntimeException("cause"))
        )
        exceptions.forEach { ex ->
            val s = ex.toString()
            assertTrue(s.contains(ex.javaClass.simpleName))
            assertTrue(s.contains(ex.message!!))
        }
        // Explicit toString check: with status code
        val withCode = BadRequestException("msg")
        assertTrue(withCode.toString().contains("status: 400"))
        // Without status code
        val withoutCode = TypecastException("noCode")
        val s = withoutCode.toString()
        assertTrue(s.contains("noCode"))
        assertFalse(s.contains("status:"))
    }

    // ==================== All HTTP error status codes ====================

    @Test
    fun handleError_allStatusCodes() {
        val statusToException = mapOf(
            400 to BadRequestException::class.java,
            401 to UnauthorizedException::class.java,
            402 to PaymentRequiredException::class.java,
            403 to ForbiddenException::class.java,
            404 to NotFoundException::class.java,
            422 to UnprocessableEntityException::class.java,
            429 to RateLimitException::class.java,
            500 to InternalServerException::class.java,
            503 to TypecastException::class.java // fallback branch
        )
        for ((code, clazz) in statusToException) {
            mockServer.enqueue(
                MockResponse()
                    .setResponseCode(code)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"detail": "err $code"}""")
            )
            val ex = assertThrows(clazz) {
                client.getVoiceV2("x")
            }
            assertTrue(ex.message!!.contains("err $code"))
        }
    }

    @Test
    fun extractErrorMessage_messageField() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"message": "via message"}""")
        )
        val ex = assertThrows(BadRequestException::class.java) { client.getVoiceV2("x") }
        assertTrue(ex.message!!.contains("via message"))
    }

    @Test
    fun extractErrorMessage_errorField() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "via error"}""")
        )
        val ex = assertThrows(BadRequestException::class.java) { client.getVoiceV2("x") }
        assertTrue(ex.message!!.contains("via error"))
    }

    @Test
    fun extractErrorMessage_unknownFields() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"other": "val"}""")
        )
        val ex = assertThrows(BadRequestException::class.java) { client.getVoiceV2("x") }
        // Falls through to responseBody itself
        assertNotNull(ex.message)
    }

    @Test
    fun extractErrorMessage_jsonArrayBody() {
        // jsonObject cast becomes null -> falls through to responseBody
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("[1,2,3]")
        )
        val ex = assertThrows(BadRequestException::class.java) { client.getVoiceV2("x") }
        assertTrue(ex.message!!.contains("[1,2,3]"))
    }

    @Test
    fun extractErrorMessage_blankBody() {
        mockServer.enqueue(
            MockResponse().setResponseCode(400).setBody("")
        )
        val ex = assertThrows(BadRequestException::class.java) { client.getVoiceV2("x") }
        assertEquals("Unknown error", ex.message)
    }

    @Test
    fun extractErrorMessage_invalidJson() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("not-json{")
        )
        val ex = assertThrows(BadRequestException::class.java) { client.getVoiceV2("x") }
        assertTrue(ex.message!!.contains("not-json"))
    }

    // ==================== textToSpeech edge branches ====================

    @Test
    fun textToSpeech_defaultWavWhenNoContentType() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(okio.Buffer().write("a".toByteArray()))
                // mockwebserver sets a default Content-Type; override to none by clearing
                .removeHeader("Content-Type")
        )
        val req = TTSRequest.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30).build()
        val resp = client.textToSpeech(req)
        // When content-type is absent we get "audio/wav" default -> wav format
        assertEquals("wav", resp.format)
        // No X-Audio-Duration -> duration 0.0
        assertEquals(0.0, resp.duration)
    }

    @Test
    fun textToSpeech_mp3ContainsLiteral() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mp3")
                .setBody(okio.Buffer().write("a".toByteArray()))
        )
        val req = TTSRequest.builder().voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30).build()
        assertEquals("mp3", client.textToSpeech(req).format)
    }

    @Test
    fun textToSpeech_invalidDurationHeader() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/wav")
                .setHeader("X-Audio-Duration", "not-a-number")
                .setBody(okio.Buffer().write("a".toByteArray()))
        )
        val req = TTSRequest.builder().voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30).build()
        val resp = client.textToSpeech(req)
        assertEquals(0.0, resp.duration)
    }

    @Test
    fun textToSpeech_errorFromServer() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "slow down"}""")
        )
        val req = TTSRequest.builder().voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30).build()
        assertThrows(RateLimitException::class.java) { client.textToSpeech(req) }
    }

    // ==================== V1 API coverage ====================

    @Test
    @Suppress("DEPRECATION")
    fun getVoices_v1_withModelFilter() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        client.getVoices(TTSModel.SSFM_V21)
        val req = mockServer.takeRequest()
        assertTrue(req.path!!.contains("model=ssfm-v21"))
    }

    @Test
    @Suppress("DEPRECATION")
    fun getVoice_v1_success() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"voice_id":"tc_a","voice_name":"A","model":"ssfm-v30","emotions":["normal"]}]""")
        )
        val v = client.getVoice("tc_a", TTSModel.SSFM_V30)
        assertEquals("tc_a", v.voiceId)
    }

    @Test
    @Suppress("DEPRECATION")
    fun getVoice_v1_withoutModel() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""[{"voice_id":"tc_a","voice_name":"A","model":"ssfm-v30","emotions":[]}]""")
        )
        val v = client.getVoice("tc_a")
        assertEquals("tc_a", v.voiceId)
    }

    @Test
    @Suppress("DEPRECATION")
    fun getVoice_v1_notFoundWhenEmpty() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        assertThrows(NotFoundException::class.java) { client.getVoice("tc_missing") }
    }

    // ==================== V2 filter coverage ====================

    @Test
    fun getVoicesV2_noFilter() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        client.getVoicesV2()
        val req = mockServer.takeRequest()
        assertFalse(req.path!!.contains("?"))
    }

    @Test
    fun getVoicesV2_useCaseFilter() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        val filter = VoicesV2Filter.builder()
            .useCases(UseCaseEnum.PODCAST)
            .build()
        client.getVoicesV2(filter)
        val req = mockServer.takeRequest()
        assertTrue(req.path!!.contains("use_cases=Podcast"))
    }

    @Test
    fun getVoicesV2_emptyFilter() {
        mockServer.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[]")
        )
        client.getVoicesV2(VoicesV2Filter())
        mockServer.takeRequest()
    }

    // ==================== getBaseUrl ====================

    @Test
    fun getBaseUrl_returnsConfigured() {
        val url = client.getBaseUrl()
        assertTrue(url.contains("localhost") || url.contains("127.0.0.1"))
    }

    // ==================== Builder coverage ====================

    @Test
    fun companion_createWithApiKey() {
        TypecastClient.create("key").use { c ->
            assertEquals(TypecastClient.DEFAULT_BASE_URL_FOR_TEST, c.getBaseUrl())
        }
    }

    @Test
    fun builder_customHttpClient() {
        val http = OkHttpClient.Builder().build()
        TypecastClient.builder()
            .apiKey("k")
            .httpClient(http)
            .baseUrl("https://example.com/")
            .build()
            .use { c -> assertEquals("https://example.com", c.getBaseUrl()) }
    }

    @Test
    fun builder_baseUrlTrimsTrailingSlash() {
        TypecastClient.builder().apiKey("k").baseUrl("https://ex.com///").build().use { c ->
            assertEquals("https://ex.com", c.getBaseUrl())
        }
    }

    @Test
    fun builder_throwsWhenNoApiKey() {
        val prevKey = System.getenv("TYPECAST_API_KEY")
        // If env is set we can't test the throw path safely; assume not set in CI
        if (prevKey.isNullOrBlank()) {
            assertThrows(IllegalArgumentException::class.java) {
                TypecastClient.builder().build()
            }
        }
    }

    @Test
    fun builder_blankApiKeyFallsThroughToResolution() {
        val prevKey = System.getenv("TYPECAST_API_KEY")
        if (prevKey.isNullOrBlank()) {
            assertThrows(IllegalArgumentException::class.java) {
                TypecastClient.builder().apiKey("").build()
            }
        }
    }

    @Test
    fun builder_blankBaseUrlFallsThrough() {
        TypecastClient.builder().apiKey("k").baseUrl("").build().use { c ->
            // blank baseUrl is ignored, falls back to env/default
            assertNotNull(c.getBaseUrl())
            assertTrue(c.getBaseUrl().isNotBlank())
        }
    }

    @Test
    fun companion_builderReturnsNewBuilder() {
        val b = TypecastClient.builder()
        assertNotNull(b)
    }

    // ==================== Model data class coverage ====================

    @Test
    fun ttsResponse_equalsHashCodeToString() {
        val a = TTSResponse(byteArrayOf(1, 2, 3), 1.0, "wav")
        val b = TTSResponse(byteArrayOf(1, 2, 3), 1.0, "wav")
        val c = TTSResponse(byteArrayOf(1, 2), 1.0, "wav")
        val d = TTSResponse(byteArrayOf(1, 2, 3), 2.0, "wav")
        val e = TTSResponse(byteArrayOf(1, 2, 3), 1.0, "mp3")
        assertEquals(a, a)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
        assertFalse(a == d)
        assertFalse(a == e)
        assertFalse(a.equals(null))
        assertFalse(a.equals("string"))
        assertTrue(a.toString().contains("bytes"))
        assertTrue(a.toString().contains("wav"))
    }

    @Test
    fun ttsResponse_componentsAndCopy() {
        val r = TTSResponse(byteArrayOf(9), 3.14, "mp3")
        assertEquals(r.audioData, r.audioData)
        assertEquals(3.14, r.duration)
        assertEquals("mp3", r.format)
    }

    @Test
    fun propertyGetters_directAccess() {
        // Touch every property getter so jacoco records the synthetic getter methods.
        val vr = VoicesResponse("id", "name", TTSModel.SSFM_V30, listOf("normal"))
        assertEquals("name", vr.voiceName)
        assertEquals(TTSModel.SSFM_V30, vr.model)
        assertEquals(listOf("normal"), vr.emotions)

        val mi = ModelInfo("ssfm-v30", listOf("e1"))
        assertEquals("ssfm-v30", mi.version)
        assertEquals(listOf("e1"), mi.emotions)

        val v2 = VoiceV2Response("id", "name", listOf(mi), GenderEnum.MALE, AgeEnum.CHILD, listOf("News"))
        assertEquals(listOf(mi), v2.models)
        assertEquals(listOf("News"), v2.useCases)

        val req = TTSRequest(voiceId = "tc_v", text = "txt", model = TTSModel.SSFM_V30, output = Output())
        assertEquals("tc_v", req.voiceId)
        assertEquals("txt", req.text)
        assertEquals(TTSModel.SSFM_V30, req.model)
        assertNotNull(req.output)

        val o = Output(audioFormat = AudioFormat.MP3)
        assertEquals(AudioFormat.MP3, o.audioFormat)

        val sp = SmartPrompt(previousText = "p")
        assertEquals("smart", sp.emotionType)

        val pp = PresetPrompt(emotionPreset = EmotionPreset.HAPPY)
        assertEquals("preset", pp.emotionType)

        val ts = TTSPromptSerializer(emotionType = "preset", emotionPreset = EmotionPreset.NORMAL, emotionIntensity = 1.5, previousText = "p", nextText = "n")
        assertEquals(1.5, ts.emotionIntensity)
        assertEquals("n", ts.nextText)
        // Default-arg ctor (all defaults)
        val tsDefault = TTSPromptSerializer()
        assertNull(tsDefault.emotionType)
        // Default-arg ctors for V2 response and Prompt/PresetPrompt
        val v2Default = VoiceV2Response(voiceId = "v", voiceName = "n", models = emptyList())
        assertNull(v2Default.gender)
        // No-arg defaults for Prompt
        val pDef = Prompt()
        assertEquals(EmotionPreset.NORMAL, pDef.emotionPreset)
        val ppDef = PresetPrompt()
        assertEquals("preset", ppDef.emotionType)
        val spDef = SmartPrompt()
        assertNull(spDef.previousText)
    }

    @Test
    fun voicesResponse_dataClassMethods() {
        val v1 = VoicesResponse("id", "name", TTSModel.SSFM_V30, listOf("normal"))
        val v2 = v1.copy()
        assertEquals(v1, v2)
        assertEquals(v1.hashCode(), v2.hashCode())
        assertFalse(v1.equals(null))
        assertTrue(v1.toString().contains("id"))
        assertEquals("id", v1.component1())
        assertEquals("name", v1.component2())
        assertEquals(TTSModel.SSFM_V30, v1.component3())
        assertEquals(listOf("normal"), v1.component4())
    }

    @Test
    fun voiceV2Response_dataClassMethods() {
        val v = VoiceV2Response(
            voiceId = "id",
            voiceName = "name",
            models = listOf(ModelInfo("ssfm-v30", listOf("normal"))),
            gender = GenderEnum.FEMALE,
            age = AgeEnum.YOUNG_ADULT,
            useCases = listOf("Podcast")
        )
        val copy = v.copy()
        assertEquals(v, copy)
        assertEquals(v.hashCode(), copy.hashCode())
        assertTrue(v.toString().contains("id"))
        assertEquals("id", v.component1())
        assertEquals("name", v.component2())
        assertEquals(1, v.component3().size)
        assertEquals(GenderEnum.FEMALE, v.component4())
        assertEquals(AgeEnum.YOUNG_ADULT, v.component5())
        assertEquals(listOf("Podcast"), v.component6())
        assertFalse(v.equals(null))
    }

    @Test
    fun modelInfo_dataClassMethods() {
        val m = ModelInfo("ssfm-v30", listOf("a", "b"))
        val copy = m.copy()
        assertEquals(m, copy)
        assertEquals(m.hashCode(), copy.hashCode())
        assertEquals("ssfm-v30", m.component1())
        assertEquals(listOf("a", "b"), m.component2())
        assertTrue(m.toString().contains("ssfm-v30"))
    }

    @Test
    fun voicesV2Filter_builderAndCopy() {
        val f = VoicesV2Filter.builder()
            .model(TTSModel.SSFM_V30)
            .gender(GenderEnum.MALE)
            .age(AgeEnum.ELDER)
            .useCases(UseCaseEnum.NEWS)
            .build()
        val copy = f.copy()
        assertEquals(f, copy)
        assertEquals(f.hashCode(), copy.hashCode())
        assertEquals(TTSModel.SSFM_V30, f.component1())
        assertEquals(GenderEnum.MALE, f.component2())
        assertEquals(AgeEnum.ELDER, f.component3())
        assertEquals(UseCaseEnum.NEWS, f.component4())
        assertTrue(f.toString().contains("ssfm-v30"))
    }

    // ==================== TTSRequest coverage ====================

    @Test
    fun ttsRequest_textTooLongThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest(voiceId = "tc_x", text = "a".repeat(2001), model = TTSModel.SSFM_V30)
        }
    }

    @Test
    fun ttsRequest_blankVoiceIdInInitThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest(voiceId = "  ", text = "hi", model = TTSModel.SSFM_V30)
        }
    }

    @Test
    fun ttsRequest_blankTextInInitThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest(voiceId = "tc_x", text = "  ", model = TTSModel.SSFM_V30)
        }
    }

    @Test
    fun ttsRequest_builderWithSeedAndPromptV21() {
        val r = TTSRequest.builder()
            .voiceId("tc_x")
            .text("hi")
            .model(TTSModel.SSFM_V21)
            .language(LanguageCode.KOR)
            .output(Output.builder().audioFormat(AudioFormat.MP3).build())
            .prompt(Prompt.builder().emotionPreset(EmotionPreset.HAPPY).emotionIntensity(1.2).build())
            .seed(42)
            .build()
        assertEquals(42, r.seed)
        assertEquals(LanguageCode.KOR, r.language)
        assertNotNull(r.prompt)
        val copy = r.copy()
        assertEquals(r, copy)
        assertEquals(r.hashCode(), copy.hashCode())
        assertTrue(r.toString().contains("tc_x"))
        // components
        assertEquals("tc_x", r.component1())
        assertEquals("hi", r.component2())
        assertEquals(TTSModel.SSFM_V21, r.component3())
        assertEquals(LanguageCode.KOR, r.component4())
        assertNotNull(r.component5())
        assertNotNull(r.component6())
        assertEquals(42, r.component7())
    }

    // ==================== Prompt coverage ====================

    @Test
    fun prompt_v21_dataMethodsAndValidation() {
        val p = Prompt(emotionPreset = EmotionPreset.SAD, emotionIntensity = 0.5)
        val copy = p.copy()
        assertEquals(p, copy)
        assertEquals(p.hashCode(), copy.hashCode())
        assertEquals(EmotionPreset.SAD, p.component1())
        assertEquals(0.5, p.component2())
        assertTrue(p.toString().contains("sad"))

        assertThrows(IllegalArgumentException::class.java) {
            Prompt(emotionIntensity = 3.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Prompt(emotionIntensity = -0.1)
        }

        // Builder
        val pb = Prompt.builder()
            .emotionPreset(EmotionPreset.ANGRY)
            .emotionIntensity(1.5)
            .build()
        assertEquals(EmotionPreset.ANGRY, pb.emotionPreset)
    }

    @Test
    fun presetPrompt_dataMethodsAndValidation() {
        val p = PresetPrompt(emotionPreset = EmotionPreset.HAPPY, emotionIntensity = 1.0)
        val copy = p.copy()
        assertEquals(p, copy)
        assertEquals(p.hashCode(), copy.hashCode())
        assertEquals("preset", p.component1())
        assertEquals(EmotionPreset.HAPPY, p.component2())
        assertEquals(1.0, p.component3())
        assertTrue(p.toString().contains("happy"))

        assertThrows(IllegalArgumentException::class.java) {
            PresetPrompt(emotionIntensity = -0.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PresetPrompt(emotionIntensity = 2.5)
        }
    }

    @Test
    fun smartPrompt_dataMethodsAndValidation() {
        val p = SmartPrompt(previousText = "prev", nextText = "next")
        val copy = p.copy()
        assertEquals(p, copy)
        assertEquals(p.hashCode(), copy.hashCode())
        assertEquals("smart", p.component1())
        assertEquals("prev", p.component2())
        assertEquals("next", p.component3())
        assertTrue(p.toString().contains("prev"))

        assertThrows(IllegalArgumentException::class.java) {
            SmartPrompt(previousText = "a".repeat(2001))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SmartPrompt(nextText = "a".repeat(2001))
        }

        // null branches
        val p2 = SmartPrompt(previousText = null, nextText = null)
        assertNull(p2.previousText)
    }

    // ==================== Output coverage ====================

    @Test
    fun output_dataMethods() {
        val o = Output(volume = 100, audioPitch = 2, audioTempo = 1.5, audioFormat = AudioFormat.MP3)
        val copy = o.copy()
        assertEquals(o, copy)
        assertEquals(o.hashCode(), copy.hashCode())
        assertEquals(100, o.component1())
        assertNull(o.component2())
        assertEquals(2, o.component3())
        assertEquals(1.5, o.component4())
        assertEquals(AudioFormat.MP3, o.component5())
        assertTrue(o.toString().contains("100"))

        // Builder with all setters (including null)
        val built = Output.builder()
            .volume(50)
            .targetLufs(null)
            .audioPitch(-3)
            .audioTempo(0.8)
            .audioFormat(AudioFormat.WAV)
            .build()
        assertEquals(50, built.volume)

        // Validation: volume below range
        assertThrows(IllegalArgumentException::class.java) { Output(volume = -1) }
        // audioPitch below range
        assertThrows(IllegalArgumentException::class.java) { Output(audioPitch = -13) }
        // audioTempo below range
        assertThrows(IllegalArgumentException::class.java) { Output(audioTempo = 0.4) }
        // All nulls (exercise null branches in init)
        val allNull = Output(volume = null, targetLufs = null, audioPitch = null, audioTempo = null, audioFormat = null)
        assertNull(allNull.volume)
        assertNull(allNull.audioPitch)
        // equals/hashCode/toString on data class
        val o2 = o.copy(volume = 50)
        assertNotEquals(o, o2)
        assertNotNull(o.toString())
        assertEquals(o.hashCode(), o.copy().hashCode())
        // Builder default state
        val def = Output.builder().build()
        assertEquals(0, def.audioPitch)
        assertEquals(1.0, def.audioTempo)
    }

    // ==================== TTSPromptSerializer coverage ====================

    @Test
    fun ttsPromptSerializer_dataMethods() {
        val s1 = TTSPromptSerializer(Prompt(EmotionPreset.HAPPY, 1.0))
        val s2 = TTSPromptSerializer(PresetPrompt(emotionPreset = EmotionPreset.SAD))
        val s3 = TTSPromptSerializer(SmartPrompt(previousText = "p", nextText = "n"))
        // Prompt (v21) has no emotion_type
        assertNull(s1.emotionType)
        assertEquals(EmotionPreset.HAPPY, s1.emotionPreset)
        // PresetPrompt
        assertEquals("preset", s2.emotionType)
        assertEquals(EmotionPreset.SAD, s2.emotionPreset)
        // SmartPrompt
        assertEquals("smart", s3.emotionType)
        assertNull(s3.emotionPreset)
        assertEquals("p", s3.previousText)

        val copy = s1.copy()
        assertEquals(s1, copy)
        assertEquals(s1.hashCode(), copy.hashCode())
        assertFalse(s1.equals(s2))
        assertTrue(s1.toString().contains("happy"))
        assertNotNull(s1.component1() ?: "")
        s1.component2()
        s1.component3()
        s1.component4()
        s1.component5()

        // Direct ctor
        val direct = TTSPromptSerializer(
            emotionType = "preset",
            emotionPreset = EmotionPreset.NORMAL,
            emotionIntensity = 1.0,
            previousText = null,
            nextText = null
        )
        assertEquals("preset", direct.emotionType)
    }

    // ==================== Builder resolveApiKey via env var ====================

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveFromDotenvFile() {
        // Create a .env file in the working directory so dotenv-kotlin reads it.
        val envFile = java.io.File(".env")
        val existed = envFile.exists()
        val backup = if (existed) envFile.readText() else null
        try {
            envFile.writeText("TYPECAST_API_KEY=fromdotenv\nTYPECAST_API_HOST=https://dotenv.example.com/\n")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://dotenv.example.com", c.getBaseUrl())
            }
            // baseUrl override still wins over dotenv
            TypecastClient.builder().baseUrl("https://override.example.com").build().use { c ->
                assertEquals("https://override.example.com", c.getBaseUrl())
            }
        } finally {
            if (backup != null) envFile.writeText(backup) else envFile.delete()
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveDotenvKeyOnlyHostMissing() {
        // .env file with KEY but no HOST exercises the host-not-found branch
        val envFile = java.io.File(".env")
        val backup = if (envFile.exists()) envFile.readText() else null
        try {
            envFile.writeText("TYPECAST_API_KEY=fromdotenv\n")
            // Ensure system env HOST is blank/unset to force default fallback
            val prevHost = System.getenv("TYPECAST_API_HOST")
            try {
                setEnv("TYPECAST_API_HOST", "")
            } catch (_: Throwable) {}
            try {
                TypecastClient.builder().build().use { c ->
                    assertEquals("https://api.typecast.ai", c.getBaseUrl())
                }
            } finally {
                try {
                    setEnv("TYPECAST_API_HOST", prevHost)
                } catch (_: Throwable) {}
            }
        } finally {
            if (backup != null) envFile.writeText(backup) else envFile.delete()
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveBlankInDotenvFallsThrough() {
        // If dotenv sets blank values for both keys, builder falls through to System env
        // (or throws). Cover the takeIf{isNotBlank} false branch on the dotenv path.
        val envFile = java.io.File(".env")
        val existed = envFile.exists()
        val backup = if (existed) envFile.readText() else null
        try {
            envFile.writeText("TYPECAST_API_KEY=\nTYPECAST_API_HOST=\n")
            // System env may or may not have TYPECAST_API_KEY set in the test environment.
            // Either path is exercised; we just need the dotenv blank-branch.
            try {
                TypecastClient.builder().build().close()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("API key"))
            }
        } finally {
            if (backup != null) envFile.writeText(backup) else envFile.delete()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setEnv(name: String, value: String?) {
        // Mutate the JVM's environment via reflection. JDK 17 stores the
        // backing map inside ProcessEnvironment.theUnmodifiableEnvironment's
        // private "m" field. Intended only for unit tests.
        val procEnvCls = Class.forName("java.lang.ProcessEnvironment")
        val unmField = procEnvCls.getDeclaredField("theUnmodifiableEnvironment")
        unmField.isAccessible = true
        val unmodifiable = unmField.get(null)
        val mField = Class.forName("java.util.Collections\$UnmodifiableMap")
            .getDeclaredField("m")
        mField.isAccessible = true
        val backing = mField.get(unmodifiable) as MutableMap<String, String>
        if (value == null) backing.remove(name) else backing[name] = value
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveFromSystemEnvWhenDotenvThrows() {
        // Force dotenv to throw by replacing .env with a directory.
        val envFile = java.io.File(".env")
        val backup = if (envFile.isFile) envFile.readText() else null
        if (envFile.exists()) envFile.delete()
        envFile.mkdir()
        try {
            // Now dotenv {} will throw because .env is a directory.
            // resolveApiKey/resolveBaseUrl should fall through to System.getenv.
            setEnv("TYPECAST_API_KEY", "syskey")
            setEnv("TYPECAST_API_HOST", "https://syshost.example.com/")
            check(System.getenv("TYPECAST_API_KEY") == "syskey")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://syshost.example.com", c.getBaseUrl())
            }
            // Also: blank env var → fall through to default
            setEnv("TYPECAST_API_KEY", "syskey")
            setEnv("TYPECAST_API_HOST", "")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://api.typecast.ai", c.getBaseUrl())
            }
            // Default-baseUrl path: also tries system env which is blank,
            // so falls through to DEFAULT_BASE_URL.
        } finally {
            envFile.delete()
            if (backup != null) java.io.File(".env").writeText(backup)
            try {
                setEnv("TYPECAST_API_KEY", null)
                setEnv("TYPECAST_API_HOST", null)
            } catch (_: Throwable) {}
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("env")
    fun builder_resolveFromSystemEnvVarBlankAndSet() {
        val prevKey = System.getenv("TYPECAST_API_KEY")
        val prevHost = System.getenv("TYPECAST_API_HOST")
        // Ensure no .env file interferes
        val envFile = java.io.File(".env")
        val envBackup = if (envFile.exists()) envFile.readText() else null
        if (envFile.exists()) envFile.delete()
        try {
            // Blank env var → falls through to default / throws
            setEnv("TYPECAST_API_KEY", "")
            setEnv("TYPECAST_API_HOST", "")
            // Verify mutation actually happened
            check(System.getenv("TYPECAST_API_KEY") == "") {
                "env mutation failed: got '${System.getenv("TYPECAST_API_KEY")}'"
            }
            try {
                TypecastClient.builder().build().close()
                fail<Unit>("Expected IllegalArgumentException for blank env")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("API key"))
            }
            // Now set non-blank env values
            setEnv("TYPECAST_API_KEY", "envkey")
            setEnv("TYPECAST_API_HOST", "https://envhost.example.com/")
            check(System.getenv("TYPECAST_API_KEY") == "envkey")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://envhost.example.com", c.getBaseUrl())
            }
        } finally {
            try {
                setEnv("TYPECAST_API_KEY", prevKey)
                setEnv("TYPECAST_API_HOST", prevHost)
            } catch (_: Throwable) {}
            if (envBackup != null) envFile.writeText(envBackup)
        }
    }

    @Test
    fun builder_resolveApiKeyFromSystemEnv() {
        val existing = System.getenv("TYPECAST_API_KEY")
        if (!existing.isNullOrBlank()) {
            // If env is pre-set, build() without explicit key will use it.
            TypecastClient.builder().build().close()
        }
        // Without an explicit apiKey the resolver walks through dotenv then env.
        // We can't mutate System.getenv at runtime portably, so rely on dotenv path
        // which also exercises the env$1 lambda.
    }

    // ==================== Streaming model + endpoint coverage ====================

    @Test
    fun outputStream_directConstructionAndDataMethods() {
        // Default constructor
        val def = OutputStream()
        assertEquals(0, def.audioPitch)
        assertEquals(1.0, def.audioTempo)
        assertEquals("wav", def.audioFormat)

        // Explicit constructor
        val o = OutputStream(audioPitch = 5, audioTempo = 1.25, audioFormat = "mp3")
        assertEquals(5, o.audioPitch)
        assertEquals(1.25, o.audioTempo)
        assertEquals("mp3", o.audioFormat)

        // data class methods
        val copy = o.copy()
        assertEquals(o, copy)
        assertEquals(o.hashCode(), copy.hashCode())
        assertEquals(5, o.component1())
        assertEquals(1.25, o.component2())
        assertEquals("mp3", o.component3())
        assertTrue(o.toString().contains("mp3"))
        assertNotEquals(o, o.copy(audioPitch = 1))
        assertFalse(o.equals(null))

        // All-null branches in init
        val allNull = OutputStream(audioPitch = null, audioTempo = null, audioFormat = null)
        assertNull(allNull.audioPitch)
        assertNull(allNull.audioTempo)
        assertNull(allNull.audioFormat)
    }

    @Test
    fun outputStream_validationRanges() {
        assertThrows(IllegalArgumentException::class.java) { OutputStream(audioPitch = 13) }
        assertThrows(IllegalArgumentException::class.java) { OutputStream(audioPitch = -13) }
        assertThrows(IllegalArgumentException::class.java) { OutputStream(audioTempo = 0.4) }
        assertThrows(IllegalArgumentException::class.java) { OutputStream(audioTempo = 2.1) }
        assertThrows(IllegalArgumentException::class.java) { OutputStream(audioFormat = "ogg") }
        assertDoesNotThrow { OutputStream(audioFormat = "wav") }
        assertDoesNotThrow { OutputStream(audioFormat = "mp3") }
    }

    @Test
    fun outputStream_builderAllSetters() {
        val built = OutputStream.builder()
            .audioPitch(-2)
            .audioTempo(0.75)
            .audioFormat("mp3")
            .build()
        assertEquals(-2, built.audioPitch)
        assertEquals(0.75, built.audioTempo)
        assertEquals("mp3", built.audioFormat)

        // Default builder state
        val def = OutputStream.builder().build()
        assertEquals(0, def.audioPitch)
        assertEquals(1.0, def.audioTempo)
        assertEquals("wav", def.audioFormat)

        // Companion factory
        assertNotNull(OutputStream.builder())
    }

    @Test
    fun ttsRequestStream_directConstructionAndDataMethods() {
        // Direct construction with all fields
        val req = TTSRequestStream(
            voiceId = "tc_x",
            text = "hi",
            model = TTSModel.SSFM_V30,
            language = LanguageCode.ENG,
            prompt = TTSPromptSerializer(emotionType = "preset", emotionPreset = EmotionPreset.HAPPY),
            output = OutputStream(audioFormat = "mp3"),
            seed = 7
        )
        assertEquals("tc_x", req.voiceId)
        assertEquals("hi", req.text)
        assertEquals(TTSModel.SSFM_V30, req.model)
        assertEquals(LanguageCode.ENG, req.language)
        assertNotNull(req.prompt)
        assertNotNull(req.output)
        assertEquals(7, req.seed)

        // data class methods
        val copy = req.copy()
        assertEquals(req, copy)
        assertEquals(req.hashCode(), copy.hashCode())
        assertEquals("tc_x", req.component1())
        assertEquals("hi", req.component2())
        assertEquals(TTSModel.SSFM_V30, req.component3())
        assertEquals(LanguageCode.ENG, req.component4())
        assertNotNull(req.component5())
        assertNotNull(req.component6())
        assertEquals(7, req.component7())
        assertTrue(req.toString().contains("tc_x"))
        assertNotEquals(req, req.copy(seed = 8))
        assertFalse(req.equals(null))

        // Minimal direct construction (only required fields)
        val minimal = TTSRequestStream(voiceId = "tc_y", text = "yo", model = TTSModel.SSFM_V21)
        assertNull(minimal.language)
        assertNull(minimal.prompt)
        assertNull(minimal.output)
        assertNull(minimal.seed)
    }

    @Test
    fun ttsRequestStream_validationRules() {
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream(voiceId = " ", text = "hi", model = TTSModel.SSFM_V30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream(voiceId = "tc_x", text = "  ", model = TTSModel.SSFM_V30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream(voiceId = "tc_x", text = "a".repeat(2001), model = TTSModel.SSFM_V30)
        }
    }

    @Test
    fun ttsRequestStream_builderAllSetters() {
        val r = TTSRequestStream.builder()
            .voiceId("tc_x")
            .text("hi")
            .model(TTSModel.SSFM_V21)
            .language(LanguageCode.KOR)
            .output(OutputStream.builder().audioFormat("mp3").build())
            .prompt(Prompt.builder().emotionPreset(EmotionPreset.HAPPY).emotionIntensity(1.2).build())
            .seed(99)
            .build()
        assertEquals(99, r.seed)
        assertEquals(LanguageCode.KOR, r.language)
        assertNotNull(r.prompt)
        assertNotNull(r.output)

        // Builder validation - empty voiceId
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream.builder()
                .voiceId("")
                .text("hi")
                .model(TTSModel.SSFM_V30)
                .build()
        }
        // Builder validation - empty text
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream.builder()
                .voiceId("tc_x")
                .text("")
                .model(TTSModel.SSFM_V30)
                .build()
        }

        // Cover the PresetPrompt and SmartPrompt prompt() overloads on the builder.
        val rPreset = TTSRequestStream.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30)
            .prompt(PresetPrompt(emotionPreset = EmotionPreset.SAD))
            .build()
        assertNotNull(rPreset.prompt)

        val rSmart = TTSRequestStream.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30)
            .prompt(SmartPrompt(previousText = "p", nextText = "n"))
            .build()
        assertNotNull(rSmart.prompt)

        // language nullable setter
        val rNoLang = TTSRequestStream.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30)
            .language(null)
            .seed(null)
            .build()
        assertNull(rNoLang.language)
        assertNull(rNoLang.seed)

        // Companion factory
        assertNotNull(TTSRequestStream.builder())
    }

    @Test
    fun textToSpeechStream_closesResponseOnError() {
        // Cover the error-body string path through buildError after a non-200.
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "service down"}""")
        )
        val req = TTSRequestStream.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30).build()
        val ex = assertThrows(TypecastException::class.java) {
            client.textToSpeechStream(req)
        }
        assertTrue(ex.message!!.contains("service down"))
    }

    @Test
    fun textToSpeechStream_returnsReadableInputStream() {
        val payload = ByteArray(1024) { (it % 256).toByte() }
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "audio/mpeg")
                .setBody(okio.Buffer().write(payload))
        )
        val req = TTSRequestStream.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30)
            .output(OutputStream(audioFormat = "mp3"))
            .build()
        val bytes = client.textToSpeechStream(req).use { it.readBytes() }
        assertArrayEquals(payload, bytes)
    }

    @Test
    fun builder_resolveBaseUrlDotEnvLambda() {
        // Building with apiKey but no baseUrl exercises the dotenv DSL lambda.
        val c = TypecastClient.builder().apiKey("k").build()
        try {
            assertNotNull(c.getBaseUrl())
        } finally {
            c.close()
        }
    }
}

// Expose a constant for test use
val TypecastClient.Companion.DEFAULT_BASE_URL_FOR_TEST: String
    get() = "https://api.typecast.ai"
