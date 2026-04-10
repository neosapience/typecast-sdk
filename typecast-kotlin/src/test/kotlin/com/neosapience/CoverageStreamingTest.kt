package com.neosapience

import com.neosapience.exceptions.*
import com.neosapience.models.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Streaming model + endpoint coverage.
 * Extracted from CoverageTest to satisfy the 450-line file limit.
 */
class CoverageStreamingTest {

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

    @Test
    fun outputStream_directConstructionAndDataMethods() {
        val def = OutputStream()
        assertEquals(0, def.audioPitch)
        assertEquals(1.0, def.audioTempo)
        assertEquals("wav", def.audioFormat)

        val o = OutputStream(audioPitch = 5, audioTempo = 1.25, audioFormat = "mp3")
        assertEquals(5, o.audioPitch)
        assertEquals(1.25, o.audioTempo)
        assertEquals("mp3", o.audioFormat)

        val copy = o.copy()
        assertEquals(o, copy)
        assertEquals(o.hashCode(), copy.hashCode())
        assertEquals(5, o.component1())
        assertEquals(1.25, o.component2())
        assertEquals("mp3", o.component3())
        assertTrue(o.toString().contains("mp3"))
        assertNotEquals(o, o.copy(audioPitch = 1))
        assertFalse(o.equals(null))

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

        val def = OutputStream.builder().build()
        assertEquals(0, def.audioPitch)
        assertEquals(1.0, def.audioTempo)
        assertEquals("wav", def.audioFormat)

        assertNotNull(OutputStream.builder())
    }

    @Test
    fun ttsRequestStream_directConstructionAndDataMethods() {
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

        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream.builder()
                .voiceId("")
                .text("hi")
                .model(TTSModel.SSFM_V30)
                .build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequestStream.builder()
                .voiceId("tc_x")
                .text("")
                .model(TTSModel.SSFM_V30)
                .build()
        }

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

        val rNoLang = TTSRequestStream.builder()
            .voiceId("tc_x").text("hi").model(TTSModel.SSFM_V30)
            .language(null)
            .seed(null)
            .build()
        assertNull(rNoLang.language)
        assertNull(rNoLang.seed)

        assertNotNull(TTSRequestStream.builder())
    }

    @Test
    fun textToSpeechStream_closesResponseOnError() {
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
        val c = TypecastClient.builder().apiKey("k").build()
        try {
            assertNotNull(c.getBaseUrl())
        } finally {
            c.close()
        }
    }
}
