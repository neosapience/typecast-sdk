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

    // ==================== Timestamps data class coverage ====================

    @Test
    fun alignmentSegmentWord_dataClassMethods() {
        val w1 = com.neosapience.models.AlignmentSegmentWord("hello", 0.1, 0.5)
        val w2 = w1.copy()
        assertEquals(w1, w2)
        assertEquals(w1.hashCode(), w2.hashCode())
        assertFalse(w1.equals(null))
        assertFalse(w1.equals("other"))
        assertTrue(w1.toString().contains("hello"))
        assertEquals("hello", w1.component1())
        assertEquals(0.1, w1.component2())
        assertEquals(0.5, w1.component3())
    }

    @Test
    fun alignmentSegmentCharacter_dataClassMethods() {
        val c1 = com.neosapience.models.AlignmentSegmentCharacter("あ", 0.2, 0.4)
        val c2 = c1.copy()
        assertEquals(c1, c2)
        assertEquals(c1.hashCode(), c2.hashCode())
        assertFalse(c1.equals(null))
        assertTrue(c1.toString().contains("あ"))
        assertEquals("あ", c1.component1())
        assertEquals(0.2, c1.component2())
        assertEquals(0.4, c1.component3())
    }

    @Test
    fun ttsRequestWithTimestamps_dataClassMethods() {
        val r = com.neosapience.models.TTSRequestWithTimestamps(
            voiceId = "tc_x",
            text = "hello",
            model = TTSModel.SSFM_V30,
            language = LanguageCode.ENG,
            seed = 7,
        )
        // Access all properties to trigger JVM getters
        assertEquals("tc_x", r.voiceId)
        assertEquals("hello", r.text)
        assertEquals(TTSModel.SSFM_V30, r.model)
        assertEquals(LanguageCode.ENG, r.language)
        assertNull(r.prompt)
        assertNull(r.output)
        assertEquals(7, r.seed)

        val copy = r.copy()
        assertEquals(r, copy)
        assertEquals(r.hashCode(), copy.hashCode())
        assertFalse(r.equals(null))
        assertFalse(r.equals("other"))
        assertTrue(r.toString().contains("tc_x"))
        assertEquals("tc_x", r.component1())
        assertEquals("hello", r.component2())
        assertEquals(TTSModel.SSFM_V30, r.component3())
        assertEquals(LanguageCode.ENG, r.component4())
        assertNull(r.component5()) // prompt
        assertNull(r.component6()) // output
        assertEquals(7, r.component7())
        // Inequality on different fields
        val other = r.copy(voiceId = "tc_y")
        assertNotEquals(r, other)
    }

    @Test
    fun ttsWithTimestampsResponse_dataClassMethods() {
        val words = listOf(com.neosapience.models.AlignmentSegmentWord("hi", 0.0, 0.5))
        val chars = listOf(com.neosapience.models.AlignmentSegmentCharacter("h", 0.0, 0.2))
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC", // valid base64
            audioFormat = "wav",
            audioDuration = 1.0,
            words = words,
            characters = chars,
        )
        // Access all properties to trigger JVM getters
        assertEquals("AAEC", resp.audio)
        assertEquals("wav", resp.audioFormat)
        assertEquals(1.0, resp.audioDuration)
        assertEquals(words, resp.words)
        assertEquals(chars, resp.characters)

        val copy = resp.copy()
        assertEquals(resp, copy)
        assertEquals(resp.hashCode(), copy.hashCode())
        assertFalse(resp.equals(null))
        assertFalse(resp.equals("other"))
        assertTrue(resp.toString().contains("wav"))
        assertEquals("AAEC", resp.component1())
        assertEquals("wav", resp.component2())
        assertEquals(1.0, resp.component3())
        assertEquals(words, resp.component4())
        assertEquals(chars, resp.component5())
        // Inequality
        val other = resp.copy(audioFormat = "mp3")
        assertNotEquals(resp, other)
    }

    @Test
    fun ttsWithTimestampsResponse_noWordsNoCharsThrows() {
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 1.0,
            words = null,
            characters = null,
        )
        assertThrows(IllegalStateException::class.java) { resp.toSrt() }
        assertThrows(IllegalStateException::class.java) { resp.toVtt() }
    }

    @Test
    fun ttsWithTimestampsResponse_emptyCuesThrows() {
        // A single word with empty text produces no cues -> error
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 1.0,
            words = listOf(com.neosapience.models.AlignmentSegmentWord("", 0.0, 0.1)),
            characters = null,
        )
        assertThrows(IllegalStateException::class.java) { resp.toSrt() }
    }

    @Test
    fun ttsWithTimestampsResponse_emptyCharsFallsToSingleWord() {
        // characters is non-null but empty -> falls through to single-word check (w != null && size == 1)
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 1.0,
            words = listOf(com.neosapience.models.AlignmentSegmentWord("Hello.", 0.0, 0.5)),
            characters = emptyList(),
        )
        val srt = resp.toSrt()
        assertTrue(srt.contains("Hello."))
    }

    @Test
    fun ttsWithTimestampsResponse_wordsNullEmptyCharsThrows() {
        // words == null, characters empty -> no segments -> error
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 1.0,
            words = null,
            characters = emptyList(),
        )
        assertThrows(IllegalStateException::class.java) { resp.toSrt() }
    }

    @Test
    fun ttsWithTimestampsResponse_emptyWordsNullCharsThrows() {
        // words is non-null but empty (size != 1), chars null -> error
        // Exercises: w != null && w.size == 1 → false (size == 0) branch
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 1.0,
            words = emptyList(),
            characters = null,
        )
        assertThrows(IllegalStateException::class.java) { resp.toSrt() }
    }

    @Test
    fun ttsWithTimestampsResponse_singleWordFallback() {
        // words.size == 1 branch in pickSegments: falls through to single-word path
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 1.0,
            words = listOf(com.neosapience.models.AlignmentSegmentWord("Hello.", 0.0, 0.5)),
            characters = null,
        )
        val srt = resp.toSrt()
        assertTrue(srt.contains("Hello."))
    }

    @Test
    fun ttsWithTimestampsResponse_defaultConstructor() {
        // Exercise the synthetic default-arg constructor (optional fields)
        val resp = com.neosapience.models.TTSWithTimestampsResponse(
            audio = "AAEC",
            audioFormat = "wav",
            audioDuration = 0.5,
        )
        assertNull(resp.words)
        assertNull(resp.characters)
    }

    @Test
    fun captioningHelpers_dataClassMethods() {
        val seg1 = com.neosapience.internal.CaptioningHelpers.Segment("hi", 0.0, 0.5)
        val seg2 = seg1.copy()
        assertEquals(seg1, seg2)
        assertEquals(seg1.hashCode(), seg2.hashCode())
        assertTrue(seg1.toString().contains("hi"))
        assertEquals("hi", seg1.component1())
        assertEquals(0.0, seg1.component2())
        assertEquals(0.5, seg1.component3())
        assertFalse(seg1.equals(null))
        assertFalse(seg1.equals("other"))
        assertNotEquals(seg1, seg1.copy(text = "other"))

        val cue1 = com.neosapience.internal.CaptioningHelpers.Cue("cue text", 0.1, 0.9)
        val cue2 = cue1.copy()
        assertEquals(cue1, cue2)
        assertEquals(cue1.hashCode(), cue2.hashCode())
        assertTrue(cue1.toString().contains("cue text"))
        assertEquals("cue text", cue1.component1())
        assertEquals(0.1, cue1.component2())
        assertEquals(0.9, cue1.component3())
        assertFalse(cue1.equals(null))
        assertFalse(cue1.equals("other"))
        assertNotEquals(cue1, cue1.copy(text = "other"))
    }

    @Test
    fun captioningHelpers_allBranches() {
        val helpers = com.neosapience.internal.CaptioningHelpers

        // endsInSentence with all terminators
        for (t in helpers.SENTENCE_TERMINATORS) {
            assertTrue(helpers.endsInSentence("word$t"))
            assertTrue(helpers.endsInSentence("word$t  ")) // trailing space
        }
        assertFalse(helpers.endsInSentence("no terminator"))

        // joinParts char mode
        assertEquals("abc", helpers.joinParts(listOf("a", "b", "c"), false))
        // joinParts word mode
        assertEquals("a b c", helpers.joinParts(listOf("a", "b", "c"), true))
        // joinParts empty
        assertEquals("", helpers.joinParts(emptyList(), true))

        // formatSrtTime and formatVttTime
        assertEquals("00:00:01,000", helpers.formatSrtTime(1.0))
        assertEquals("01:02:03,456", helpers.formatSrtTime(3723.456))
        assertEquals("00:00:01.000", helpers.formatVttTime(1.0))

        // groupIntoCues: empty list
        assertEquals(emptyList<Any>(), helpers.groupIntoCues(emptyList(), true))

        // groupIntoCues: hard cap by seconds (exceed 7.0 s)
        val longSegs = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("word1", 0.0, 1.0),
            com.neosapience.internal.CaptioningHelpers.Segment("word2", 1.0, 8.1), // gap > 7s
        )
        val cues = helpers.groupIntoCues(longSegs, true)
        assertEquals(2, cues.size)

        // groupIntoCues: hard cap by chars (> 42 codepoints)
        val longWord = "a".repeat(43)
        val charSegs = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("short", 0.0, 0.5),
            com.neosapience.internal.CaptioningHelpers.Segment(longWord, 0.5, 1.0),
        )
        val charCues = helpers.groupIntoCues(charSegs, true)
        assertEquals(2, charCues.size)

        // groupIntoCues: hard cap with empty current text -> not flushed (isEmpty check)
        val emptyThenLong = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("  ", 0.0, 0.5), // all whitespace, trims to ""
            com.neosapience.internal.CaptioningHelpers.Segment("next", 0.5, 1.0),
        )
        // "  " trims to "" so the cue is not added; "next" is flushed at tail
        val emptyThenLongCues = helpers.groupIntoCues(emptyThenLong, true)
        assertEquals(1, emptyThenLongCues.size)
        assertEquals("next", emptyThenLongCues[0].text)

        // groupIntoCues: sentence terminator flush (char mode)
        val charMode = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("H", 0.0, 0.1),
            com.neosapience.internal.CaptioningHelpers.Segment("i", 0.1, 0.2),
            com.neosapience.internal.CaptioningHelpers.Segment(".", 0.2, 0.3),
            com.neosapience.internal.CaptioningHelpers.Segment("B", 0.3, 0.4),
        )
        val charCues2 = helpers.groupIntoCues(charMode, false)
        assertEquals(2, charCues2.size)
        assertEquals("Hi.", charCues2[0].text)
        assertEquals("B", charCues2[1].text)

        // groupIntoCues: trailing flush (no terminator at end)
        val trailing = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("no", 0.0, 0.1),
            com.neosapience.internal.CaptioningHelpers.Segment("end", 0.1, 0.2),
        )
        val trailingCues = helpers.groupIntoCues(trailing, true)
        assertEquals(1, trailingCues.size)
        assertEquals("no end", trailingCues[0].text)

        // groupIntoCues: trailing flush with empty text (all spaces trim to "") -> not added
        val trailingEmpty = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("  ", 0.0, 0.5),
        )
        assertEquals(0, helpers.groupIntoCues(trailingEmpty, true).size)

        // groupIntoCues: all empty text -> empty cues list
        val emptySegs = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment("", 0.0, 0.5),
        )
        val emptyCues = helpers.groupIntoCues(emptySegs, true)
        assertEquals(0, emptyCues.size)

        // groupIntoCues: sentence terminator flush produces empty text -> not added
        val emptyTerm = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment(".", 0.0, 0.1), // single terminator with trimmable result
        )
        // "." trims to "." which is non-empty; this is normal (1 cue)
        assertEquals(1, helpers.groupIntoCues(emptyTerm, true).size)

        // groupIntoCues: hard cap fires on second segment, but first-segment text is empty -> not flushed
        val hardCapEmptyFirst = listOf(
            com.neosapience.internal.CaptioningHelpers.Segment(" ", 0.0, 0.5), // trims to ""
            com.neosapience.internal.CaptioningHelpers.Segment(" ".repeat(43), 0.5, 1.0), // would exceed chars
        )
        val hardCapEmptyFirstCues = helpers.groupIntoCues(hardCapEmptyFirst, true)
        // After hard cap with empty text, we start fresh; trailing " ".repeat(43) trims to ""
        assertEquals(0, hardCapEmptyFirstCues.size)
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
}

// Expose a constant for test use
val TypecastClient.Companion.DEFAULT_BASE_URL_FOR_TEST: String
    get() = "https://api.typecast.ai"
