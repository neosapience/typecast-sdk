package com.neosapience

import com.neosapience.models.TTSModel
import com.neosapience.models.TTSRequestWithTimestamps
import com.neosapience.models.TTSWithTimestampsResponse
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TimestampTTSTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TypecastClient
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val url = server.url("/").toString().trimEnd('/')
        client = TypecastClient.builder().apiKey("k").baseUrl(url).build()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        client.close()
    }

    private val fixtureDir: Path
        get() {
            var dir = Paths.get(System.getProperty("user.dir"))
            repeat(6) {
                val candidate = dir.resolve("test-fixtures/with-timestamps")
                if (Files.isDirectory(candidate)) return candidate
                dir = dir.parent ?: error("test-fixtures not found")
            }
            error("test-fixtures not found")
        }

    private fun loadFixture(name: String) = String(Files.readAllBytes(fixtureDir.resolve(name)))
    private fun loadExpected(name: String) =
        String(Files.readAllBytes(fixtureDir.resolve("expected").resolve(name)))

    // ─── Caption output tests ──────────────────────────────────────────────

    @Test
    fun `toSrt matches all fixtures`() {
        for (name in listOf("both", "word_only", "char_only", "jpn_char")) {
            val resp = json.decodeFromString<TTSWithTimestampsResponse>(loadFixture("$name.json"))
            assertEquals(loadExpected("$name.srt"), resp.toSrt(), "SRT mismatch for $name")
        }
    }

    @Test
    fun `toVtt matches all fixtures`() {
        for (name in listOf("both", "word_only", "char_only", "jpn_char")) {
            val resp = json.decodeFromString<TTSWithTimestampsResponse>(loadFixture("$name.json"))
            assertEquals(loadExpected("$name.vtt"), resp.toVtt(), "VTT mismatch for $name")
        }
    }

    // ─── Audio helpers ─────────────────────────────────────────────────────

    @Test
    fun `audioBytes decodes`() {
        val resp = json.decodeFromString<TTSWithTimestampsResponse>(loadFixture("both.json"))
        assertTrue(resp.audioBytes().isNotEmpty())
    }

    @Test
    fun `saveAudio writes`(@TempDir tmp: Path) {
        val resp = json.decodeFromString<TTSWithTimestampsResponse>(loadFixture("both.json"))
        val out = tmp.resolve("o.wav")
        resp.saveAudio(out)
        assertTrue(Files.size(out) > 0)
    }

    // ─── Client HTTP tests ─────────────────────────────────────────────────

    @Test
    fun `client no granularity`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(loadFixture("both.json")),
        )
        val req = TTSRequestWithTimestamps(voiceId = "tc_x", text = "Hi", model = TTSModel.SSFM_V30)
        client.textToSpeechWithTimestamps(req, null)
        val rr = server.takeRequest()
        assertEquals("/v1/text-to-speech/with-timestamps", rr.requestUrl?.encodedPath)
        assertEquals(null, rr.requestUrl?.queryParameter("granularity"))
    }

    @Test
    fun `client word granularity`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("word_only.json")),
        )
        val req = TTSRequestWithTimestamps(voiceId = "tc_x", text = "Hi", model = TTSModel.SSFM_V30)
        client.textToSpeechWithTimestamps(req, "word")
        assertEquals("word", server.takeRequest().requestUrl?.queryParameter("granularity"))
    }

    @Test
    fun `client char granularity`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(loadFixture("char_only.json")),
        )
        val req = TTSRequestWithTimestamps(voiceId = "tc_x", text = "Hi", model = TTSModel.SSFM_V30)
        client.textToSpeechWithTimestamps(req, "char")
        assertEquals("char", server.takeRequest().requestUrl?.queryParameter("granularity"))
    }

    @Test
    fun `client invalid granularity`() {
        val req = TTSRequestWithTimestamps(voiceId = "tc_x", text = "Hi", model = TTSModel.SSFM_V30)
        assertFailsWith<IllegalArgumentException> {
            client.textToSpeechWithTimestamps(req, "words")
        }
    }
}
