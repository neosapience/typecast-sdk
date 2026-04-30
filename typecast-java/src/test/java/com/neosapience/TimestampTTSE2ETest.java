package com.neosapience;

import io.github.cdimascio.dotenv.Dotenv;
import com.neosapience.models.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-API E2E tests for text-to-speech with timestamps.
 *
 * <p>Requires TYPECAST_API_KEY environment variable. Tests are skipped when it is not set.</p>
 * <p>Run with: {@code mvn verify -Pe2e}</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TimestampTTSE2ETest {

    private static final String VOICE = "tc_60e5426de8b95f1d3000d7b5";

    private static TypecastClient client;

    @BeforeAll
    static void setUpClass() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("TYPECAST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("TYPECAST_API_KEY");
        }
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isEmpty(),
                "Skipping E2E tests: TYPECAST_API_KEY not set"
        );
        client = new TypecastClient(apiKey);
    }

    @AfterAll
    static void tearDownClass() {
        if (client != null) {
            client.close();
        }
    }

    private TTSRequestWithTimestamps buildRequest(String text, LanguageCode language) {
        return TTSRequestWithTimestamps.builder()
                .voiceId(VOICE)
                .text(text)
                .model(TTSModel.SSFM_V30)
                .language(language)
                .prompt(PresetPrompt.builder()
                        .emotionPreset(EmotionPreset.NORMAL)
                        .emotionIntensity(1.0)
                        .build())
                .seed(42)
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("E2E timestamp: no granularity -> words AND characters returned")
    void test_noGranularity() {
        TTSRequestWithTimestamps req = buildRequest("Hello.", LanguageCode.ENG);

        TTSWithTimestampsResponse resp = client.textToSpeechWithTimestamps(req, null);

        assertNotNull(resp);
        assertTrue(resp.getAudioDuration() > 0, "audio_duration should be > 0");
        List<AlignmentSegmentWord> words = resp.getWords();
        List<AlignmentSegmentCharacter> chars = resp.getCharacters();
        assertNotNull(words, "words should not be null");
        assertFalse(words.isEmpty(), "words should be non-empty");
        assertNotNull(chars, "characters should not be null");
        assertFalse(chars.isEmpty(), "characters should be non-empty");
        System.out.printf("no_granularity: duration=%.2fs words=%d chars=%d%n",
                resp.getAudioDuration(), words.size(), chars.size());
    }

    @Test
    @Order(2)
    @DisplayName("E2E timestamp: granularity=word -> words only, characters null")
    void test_wordGranularity() {
        TTSRequestWithTimestamps req = buildRequest("Hello.", LanguageCode.ENG);

        TTSWithTimestampsResponse resp = client.textToSpeechWithTimestamps(req, "word");

        assertNotNull(resp);
        List<AlignmentSegmentWord> words = resp.getWords();
        assertNotNull(words, "words should not be null");
        assertFalse(words.isEmpty(), "words should be non-empty");
        assertNull(resp.getCharacters(), "characters should be null for word granularity");
        System.out.printf("word granularity: words=%d%n", words.size());
    }

    @Test
    @Order(3)
    @DisplayName("E2E timestamp: granularity=char -> characters only, words null")
    void test_charGranularity() {
        TTSRequestWithTimestamps req = buildRequest("Hello.", LanguageCode.ENG);

        TTSWithTimestampsResponse resp = client.textToSpeechWithTimestamps(req, "char");

        assertNotNull(resp);
        List<AlignmentSegmentCharacter> chars = resp.getCharacters();
        assertNotNull(chars, "characters should not be null");
        assertFalse(chars.isEmpty(), "characters should be non-empty");
        assertNull(resp.getWords(), "words should be null for char granularity");
        System.out.printf("char granularity: chars=%d%n", chars.size());
    }

    @Test
    @Order(4)
    @DisplayName("E2E timestamp: Japanese + granularity=char -> >= 5 character segments")
    void test_jpnChar() {
        TTSRequestWithTimestamps req = buildRequest("こんにちは。お元気ですか?", LanguageCode.JPN);

        TTSWithTimestampsResponse resp = client.textToSpeechWithTimestamps(req, "char");

        assertNotNull(resp);
        List<AlignmentSegmentCharacter> chars = resp.getCharacters();
        assertNotNull(chars, "characters should not be null for jpn+char");
        assertTrue(chars.size() >= 5,
                "Expected >= 5 character segments for Japanese, got " + chars.size());
        System.out.printf("jpn+char: chars=%d%n", chars.size());
    }
}
