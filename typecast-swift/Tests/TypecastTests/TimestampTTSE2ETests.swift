import XCTest
@testable import Typecast

/// Real-API E2E tests for text-to-speech with timestamps.
/// Set TYPECAST_API_KEY before running. Tests are skipped when the variable is absent.
final class TimestampTTSE2ETests: XCTestCase {

    private let voice = "tc_60e5426de8b95f1d3000d7b5"
    private var client: TypecastClient!

    override func setUp() async throws {
        guard let apiKey = ProcessInfo.processInfo.environment["TYPECAST_API_KEY"] else {
            throw XCTSkip("TYPECAST_API_KEY environment variable is required for E2E tests")
        }
        let host = ProcessInfo.processInfo.environment["TYPECAST_API_HOST"]
        if let host = host {
            client = TypecastClient(apiKey: apiKey, baseURL: host)
        } else {
            client = TypecastClient(apiKey: apiKey)
        }
    }

    private func buildRequest(text: String, language: LanguageCode) -> TTSRequestWithTimestamps {
        TTSRequestWithTimestamps(
            voiceId: voice,
            text: text,
            model: .ssfmV30,
            language: language,
            prompt: .preset(PresetPrompt(emotionPreset: .normal, emotionIntensity: 1.0)),
            seed: 42
        )
    }

    func testWithTimestamps_NoGranularity_WordsAndCharactersReturned() async throws {
        let req = buildRequest(text: "Hello.", language: .english)
        let resp = try await client.textToSpeechWithTimestamps(req)

        XCTAssertGreaterThan(resp.audioDuration, 0, "audio_duration should be > 0")
        XCTAssertNotNil(resp.words, "words should not be nil")
        XCTAssertFalse(resp.words!.isEmpty, "words should be non-empty")
        XCTAssertNotNil(resp.characters, "characters should not be nil")
        XCTAssertFalse(resp.characters!.isEmpty, "characters should be non-empty")
        print("✅ no_granularity: duration=\(resp.audioDuration) words=\(resp.words!.count) chars=\(resp.characters!.count)")
    }

    func testWithTimestamps_WordGranularity_WordsOnlyCharactersNil() async throws {
        let req = buildRequest(text: "Hello.", language: .english)
        let resp = try await client.textToSpeechWithTimestamps(req, granularity: "word")

        XCTAssertNotNil(resp.words, "words should not be nil")
        XCTAssertFalse(resp.words!.isEmpty, "words should be non-empty for word granularity")
        let chars = resp.characters
        XCTAssertTrue(chars == nil || chars!.isEmpty, "characters should be nil/empty for word granularity")
        print("✅ word granularity: words=\(resp.words!.count)")
    }

    func testWithTimestamps_CharGranularity_CharactersOnlyWordsNil() async throws {
        let req = buildRequest(text: "Hello.", language: .english)
        let resp = try await client.textToSpeechWithTimestamps(req, granularity: "char")

        XCTAssertNotNil(resp.characters, "characters should not be nil")
        XCTAssertFalse(resp.characters!.isEmpty, "characters should be non-empty for char granularity")
        let words = resp.words
        XCTAssertTrue(words == nil || words!.isEmpty, "words should be nil/empty for char granularity")
        print("✅ char granularity: chars=\(resp.characters!.count)")
    }

    func testWithTimestamps_JpnChar_AtLeastFiveSegments() async throws {
        let req = buildRequest(text: "こんにちは。お元気ですか?", language: .japanese)
        let resp = try await client.textToSpeechWithTimestamps(req, granularity: "char")

        XCTAssertNotNil(resp.characters, "characters should not be nil for jpn+char")
        XCTAssertGreaterThanOrEqual(resp.characters!.count, 5,
            "Expected >= 5 character segments for Japanese")
        print("✅ jpn+char: chars=\(resp.characters!.count)")
    }
}
