package com.neosapience

import io.github.cdimascio.dotenv.dotenv
import com.neosapience.models.*
import com.neosapience.TypecastClient
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Real-API E2E tests for text-to-speech with timestamps.
 *
 * Requires TYPECAST_API_KEY environment variable. Tests are skipped when not set.
 * Run with: `./gradlew e2eTest`
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TimestampTTSE2ETest {

    companion object {
        private const val VOICE = "tc_60e5426de8b95f1d3000d7b5"
        private lateinit var client: TypecastClient

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
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
        }

        @AfterAll
        @JvmStatic
        fun tearDownClass() {
            if (::client.isInitialized) client.close()
        }
    }

    private fun buildRequest(text: String, language: LanguageCode): TTSRequestWithTimestamps =
        TTSRequestWithTimestamps(
            voiceId = VOICE,
            text = text,
            model = TTSModel.SSFM_V30,
            language = language,
            prompt = TTSPromptSerializer(
                emotionType = "preset",
                emotionPreset = EmotionPreset.NORMAL,
                emotionIntensity = 1.0,
            ),
            seed = 42,
        )

    @Test
    @Order(1)
    @DisplayName("E2E timestamp: no granularity -> words AND characters returned")
    fun test_noGranularity() {
        val req = buildRequest("Hello.", LanguageCode.ENG)
        val resp = client.textToSpeechWithTimestamps(req, null)

        assertNotNull(resp)
        assertTrue(resp.audioDuration > 0, "audio_duration should be > 0")
        val words = resp.words
        val chars = resp.characters
        assertNotNull(words, "words should not be null")
        assertFalse(words!!.isEmpty(), "words should be non-empty")
        assertNotNull(chars, "characters should not be null")
        assertFalse(chars!!.isEmpty(), "characters should be non-empty")
        println("no_granularity: duration=${resp.audioDuration} words=${words.size} chars=${chars.size}")
    }

    @Test
    @Order(2)
    @DisplayName("E2E timestamp: granularity=word -> words only, characters null")
    fun test_wordGranularity() {
        val req = buildRequest("Hello.", LanguageCode.ENG)
        val resp = client.textToSpeechWithTimestamps(req, "word")

        assertNotNull(resp)
        val words = resp.words
        assertNotNull(words, "words should not be null")
        assertFalse(words!!.isEmpty(), "words should be non-empty")
        assertNull(resp.characters, "characters should be null for word granularity")
        println("word granularity: words=${words.size}")
    }

    @Test
    @Order(3)
    @DisplayName("E2E timestamp: granularity=char -> characters only, words null")
    fun test_charGranularity() {
        val req = buildRequest("Hello.", LanguageCode.ENG)
        val resp = client.textToSpeechWithTimestamps(req, "char")

        assertNotNull(resp)
        val chars = resp.characters
        assertNotNull(chars, "characters should not be null")
        assertFalse(chars!!.isEmpty(), "characters should be non-empty")
        assertNull(resp.words, "words should be null for char granularity")
        println("char granularity: chars=${chars.size}")
    }

    @Test
    @Order(4)
    @DisplayName("E2E timestamp: Japanese + granularity=char -> >= 5 character segments")
    fun test_jpnChar() {
        val req = buildRequest("こんにちは。お元気ですか?", LanguageCode.JPN)
        val resp = client.textToSpeechWithTimestamps(req, "char")

        assertNotNull(resp)
        val chars = resp.characters
        assertNotNull(chars, "characters should not be null for jpn+char")
        assertTrue(
            chars!!.size >= 5,
            "Expected >= 5 character segments for Japanese, got ${chars.size}"
        )
        println("jpn+char: chars=${chars.size}")
    }
}
