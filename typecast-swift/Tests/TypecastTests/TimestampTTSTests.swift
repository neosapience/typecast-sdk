import XCTest
@testable import Typecast

// MARK: - Fixture loading

/// Locates the shared `test-fixtures/with-timestamps` directory by walking up
/// from the Swift package working directory at test time.
private func findFixtureDir() -> URL {
    let cwd = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
    var dir = cwd
    for _ in 0..<10 {
        let candidate = dir.appendingPathComponent("test-fixtures")
            .appendingPathComponent("with-timestamps")
        if (try? candidate.checkResourceIsReachable()) == true {
            return candidate
        }
        dir.deleteLastPathComponent()
    }
    fatalError("test-fixtures/with-timestamps not found — run tests from the repo root or typecast-swift/ directory")
}

// MARK: - Unit tests

final class TimestampTTSTests: XCTestCase {

    private static let fixtureDir: URL = findFixtureDir()

    private func loadFixture(_ name: String) throws -> Data {
        let url = Self.fixtureDir.appendingPathComponent(name)
        return try Data(contentsOf: url)
    }

    private func loadExpected(_ name: String) throws -> String {
        let url = Self.fixtureDir.appendingPathComponent("expected").appendingPathComponent(name)
        return try String(contentsOf: url, encoding: .utf8)
    }

    private func parse(_ name: String) throws -> TTSWithTimestampsResponse {
        let data = try loadFixture(name)
        return try JSONDecoder().decode(TTSWithTimestampsResponse.self, from: data)
    }

    // MARK: - SRT byte-equality tests

    func testToSrtBoth() throws {
        let resp = try parse("both.json")
        let actual = try resp.toSrt()
        let expected = try loadExpected("both.srt")
        XCTAssertEqual(actual, expected, "SRT mismatch for 'both'")
    }

    func testToSrtWordOnly() throws {
        let resp = try parse("word_only.json")
        let actual = try resp.toSrt()
        let expected = try loadExpected("word_only.srt")
        XCTAssertEqual(actual, expected, "SRT mismatch for 'word_only'")
    }

    func testToSrtCharOnly() throws {
        let resp = try parse("char_only.json")
        let actual = try resp.toSrt()
        let expected = try loadExpected("char_only.srt")
        XCTAssertEqual(actual, expected, "SRT mismatch for 'char_only'")
    }

    func testToSrtJpnChar() throws {
        let resp = try parse("jpn_char.json")
        let actual = try resp.toSrt()
        let expected = try loadExpected("jpn_char.srt")
        XCTAssertEqual(actual, expected, "SRT mismatch for 'jpn_char'")
    }

    // MARK: - VTT byte-equality tests

    func testToVttBoth() throws {
        let resp = try parse("both.json")
        let actual = try resp.toVtt()
        let expected = try loadExpected("both.vtt")
        XCTAssertEqual(actual, expected, "VTT mismatch for 'both'")
    }

    func testToVttWordOnly() throws {
        let resp = try parse("word_only.json")
        let actual = try resp.toVtt()
        let expected = try loadExpected("word_only.vtt")
        XCTAssertEqual(actual, expected, "VTT mismatch for 'word_only'")
    }

    func testToVttCharOnly() throws {
        let resp = try parse("char_only.json")
        let actual = try resp.toVtt()
        let expected = try loadExpected("char_only.vtt")
        XCTAssertEqual(actual, expected, "VTT mismatch for 'char_only'")
    }

    func testToVttJpnChar() throws {
        let resp = try parse("jpn_char.json")
        let actual = try resp.toVtt()
        let expected = try loadExpected("jpn_char.vtt")
        XCTAssertEqual(actual, expected, "VTT mismatch for 'jpn_char'")
    }

    // MARK: - Audio helpers

    func testAudioBytesIsNonEmpty() throws {
        let resp = try parse("both.json")
        XCTAssertFalse(try resp.audioBytes().isEmpty, "audioBytes() should not be empty")
    }

    func testSaveAudioWritesFile() throws {
        let resp = try parse("both.json")
        let dir = FileManager.default.temporaryDirectory
        let out = dir.appendingPathComponent("\(UUID().uuidString).wav")
        defer { try? FileManager.default.removeItem(at: out) }
        try resp.saveAudio(to: out)
        let attrs = try FileManager.default.attributesOfItem(atPath: out.path)
        let size = attrs[.size] as? UInt64 ?? 0
        XCTAssertGreaterThan(size, 0, "saved audio file should be non-empty")
    }

    // MARK: - Error cases

    func testToSrtNoSegmentsThrows() throws {
        // A response with nil words and nil characters should throw.
        let resp = TTSWithTimestampsResponse(
            audio: Data([0x00]).base64EncodedString(),
            audioFormat: "wav",
            audioDuration: 0.5,
            words: nil,
            characters: nil
        )
        XCTAssertThrowsError(try resp.toSrt()) { error in
            XCTAssertEqual(error as? TimestampError, .noAlignmentSegments)
        }
    }

    func testToVttNoSegmentsThrows() throws {
        let resp = TTSWithTimestampsResponse(
            audio: Data([0x00]).base64EncodedString(),
            audioFormat: "wav",
            audioDuration: 0.5,
            words: [],
            characters: []
        )
        XCTAssertThrowsError(try resp.toVtt()) { error in
            XCTAssertEqual(error as? TimestampError, .noAlignmentSegments)
        }
    }

    func testAudioBytesInvalidBase64Throws() {
        let resp = TTSWithTimestampsResponse(
            audio: "!!! not valid base64 !!!",
            audioFormat: "wav",
            audioDuration: 1.0,
            words: nil,
            characters: nil
        )
        XCTAssertThrowsError(try resp.audioBytes()) { error in
            XCTAssertEqual(error as? TimestampError, .invalidBase64)
        }
    }

    // MARK: - Request validation tests

    func testValidateEmptyVoiceIdThrows() throws {
        let req = TTSRequestWithTimestamps(voiceId: "", text: "Hello", model: .ssfmV30)
        XCTAssertThrowsError(try req.validate()) { error in
            guard case .invalidRequest(let msg) = error as? TimestampError else {
                XCTFail("Expected TimestampError.invalidRequest, got \(error)"); return
            }
            XCTAssertTrue(msg.contains("voice_id"), "Error message should mention voice_id")
        }
    }

    func testValidateEmptyTextThrows() throws {
        let req = TTSRequestWithTimestamps(voiceId: "tc_1", text: "", model: .ssfmV30)
        XCTAssertThrowsError(try req.validate()) { error in
            guard case .invalidRequest(let msg) = error as? TimestampError else {
                XCTFail("Expected TimestampError.invalidRequest, got \(error)"); return
            }
            XCTAssertTrue(msg.contains("text"), "Error message should mention text")
        }
    }

    func testValidateOversizedTextThrows() throws {
        let oversized = String(repeating: "a", count: 2001)
        let req = TTSRequestWithTimestamps(voiceId: "tc_1", text: oversized, model: .ssfmV30)
        XCTAssertThrowsError(try req.validate()) { error in
            guard case .invalidRequest(let msg) = error as? TimestampError else {
                XCTFail("Expected TimestampError.invalidRequest, got \(error)"); return
            }
            XCTAssertTrue(msg.contains("2000"), "Error message should mention 2000 character limit")
        }
    }

    func testValidateValidRequestDoesNotThrow() throws {
        let req = TTSRequestWithTimestamps(voiceId: "tc_1", text: "Hello, world!", model: .ssfmV30)
        XCTAssertNoThrow(try req.validate())
    }

    func testValidateMaxLengthTextDoesNotThrow() throws {
        let maxText = String(repeating: "a", count: 2000)
        let req = TTSRequestWithTimestamps(voiceId: "tc_1", text: maxText, model: .ssfmV30)
        XCTAssertNoThrow(try req.validate())
    }

    func testValidateWhitespaceOnlyVoiceIdThrows() throws {
        let req = TTSRequestWithTimestamps(voiceId: "   \t\n", text: "Hello", model: .ssfmV30)
        XCTAssertThrowsError(try req.validate()) { error in
            guard case .invalidRequest(let msg) = error as? TimestampError else {
                XCTFail("Expected TimestampError.invalidRequest, got \(error)"); return
            }
            XCTAssertTrue(msg.contains("voice_id"), "Error message should mention voice_id")
        }
    }

    func testValidateWhitespaceOnlyTextThrows() throws {
        let req = TTSRequestWithTimestamps(voiceId: "tc_1", text: "   \t\n", model: .ssfmV30)
        XCTAssertThrowsError(try req.validate()) { error in
            guard case .invalidRequest(let msg) = error as? TimestampError else {
                XCTFail("Expected TimestampError.invalidRequest, got \(error)"); return
            }
            XCTAssertTrue(msg.contains("text"), "Error message should mention text")
        }
    }

    // MARK: - CaptioningHelpers unit tests

    func testFormatSrtTime() {
        // 0 seconds
        XCTAssertEqual(CaptioningHelpers.formatSrtTime(0.0), "00:00:00,000")
        // 1.5 seconds → 00:00:01,500
        XCTAssertEqual(CaptioningHelpers.formatSrtTime(1.5), "00:00:01,500")
        // 3661.101 seconds → 01:01:01,101
        XCTAssertEqual(CaptioningHelpers.formatSrtTime(3661.101), "01:01:01,101")
    }

    func testFormatVttTime() {
        XCTAssertEqual(CaptioningHelpers.formatVttTime(0.0), "00:00:00.000")
        XCTAssertEqual(CaptioningHelpers.formatVttTime(1.5), "00:00:01.500")
    }

    func testCodepointCount() {
        XCTAssertEqual(CaptioningHelpers.codepointCount("Hello"), 5)
        XCTAssertEqual(CaptioningHelpers.codepointCount("こんにちは"), 5)
        XCTAssertEqual(CaptioningHelpers.codepointCount(""), 0)
    }

    func testGroupIntoCuesWordMode() {
        let segments: [CaptioningHelpers.Segment] = [
            .init(text: "Hello.", start: 0.0, end: 0.5),
            .init(text: "World", start: 0.6, end: 1.0),
        ]
        let cues = CaptioningHelpers.groupIntoCues(segments: segments, wordMode: true)
        XCTAssertEqual(cues.count, 2)
        XCTAssertEqual(cues[0].text, "Hello.")
        XCTAssertEqual(cues[1].text, "World")
    }

    func testGroupIntoCuesCharMode() {
        let segments: [CaptioningHelpers.Segment] = [
            .init(text: "H", start: 0.0, end: 0.1),
            .init(text: "i", start: 0.1, end: 0.2),
            .init(text: "!", start: 0.2, end: 0.3),
        ]
        let cues = CaptioningHelpers.groupIntoCues(segments: segments, wordMode: false)
        XCTAssertEqual(cues.count, 1)
        XCTAssertEqual(cues[0].text, "Hi!")
    }

    func testGroupIntoCuesSplitsOnHardCharCap() {
        // Build a segment list that would exceed 42 codepoints if joined.
        let longWord = String(repeating: "a", count: 30)
        let segments: [CaptioningHelpers.Segment] = [
            .init(text: longWord, start: 0.0, end: 1.0),
            .init(text: longWord, start: 1.1, end: 2.0),
        ]
        let cues = CaptioningHelpers.groupIntoCues(segments: segments, wordMode: true)
        // The two 30-char words joined ("aaa...aaa aaa...aaa") = 61 chars > 42, so should split.
        XCTAssertEqual(cues.count, 2)
    }

    func testGroupIntoCuesSplitsOnHardTimeCap() {
        // Span > 7 seconds should trigger a hard split.
        let segments: [CaptioningHelpers.Segment] = [
            .init(text: "First", start: 0.0, end: 1.0),
            .init(text: "Second", start: 1.0, end: 7.1),  // end - curStart = 7.1 > 7.0
        ]
        let cues = CaptioningHelpers.groupIntoCues(segments: segments, wordMode: true)
        XCTAssertEqual(cues.count, 2)
        XCTAssertEqual(cues[0].text, "First")
        XCTAssertEqual(cues[1].text, "Second")
    }

    // MARK: - Client HTTP mock tests

    private let baseURL = "https://test.typecast.local"

    private func makeClient() -> TypecastClient {
        let session = MockSession.make()
        return TypecastClient(
            configuration: TypecastConfiguration(apiKey: "test-key", baseURL: baseURL),
            session: session
        )
    }

    private func mockResponse(url: URL, status: Int, headers: [String: String] = [:]) -> HTTPURLResponse {
        HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
    }

    func testTextToSpeechWithTimestampsSuccess() async throws {
        MockURLProtocol.reset()
        defer { MockURLProtocol.reset() }

        let audioB64 = Data([0x52, 0x49, 0x46, 0x46]).base64EncodedString()
        let responseJSON = """
        {
            "audio": "\(audioB64)",
            "audio_format": "wav",
            "audio_duration": 1.23,
            "words": [
                {"text": "Hello.", "start": 0.1, "end": 0.5}
            ],
            "characters": null
        }
        """.data(using: .utf8)!

        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/text-to-speech/with-timestamps")
            XCTAssertEqual(req.httpMethod, "POST")
            XCTAssertEqual(req.value(forHTTPHeaderField: "X-API-KEY"), "test-key")
            return (self.mockResponse(url: req.url!, status: 200), responseJSON)
        }

        let client = makeClient()
        let request = TTSRequestWithTimestamps(
            voiceId: "tc_1",
            text: "Hello.",
            model: .ssfmV30
        )
        let resp = try await client.textToSpeechWithTimestamps(request)

        XCTAssertEqual(resp.audioFormat, "wav")
        XCTAssertEqual(resp.audioDuration, 1.23, accuracy: 0.001)
        XCTAssertEqual(resp.words?.count, 1)
        XCTAssertNil(resp.characters)

        // Verify request body
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        XCTAssertEqual(json?["voice_id"] as? String, "tc_1")
        XCTAssertEqual(json?["text"] as? String, "Hello.")
        XCTAssertEqual(json?["model"] as? String, "ssfm-v30")
    }

    func testTextToSpeechWithTimestampsGranularityQueryParam() async throws {
        MockURLProtocol.reset()
        defer { MockURLProtocol.reset() }

        let audioB64 = Data([0x00]).base64EncodedString()
        let responseJSON = """
        {
            "audio": "\(audioB64)",
            "audio_format": "wav",
            "audio_duration": 0.5,
            "words": [{"text": "Hi.", "start": 0.0, "end": 0.5}],
            "characters": null
        }
        """.data(using: .utf8)!

        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.query, "granularity=word")
            return (self.mockResponse(url: req.url!, status: 200), responseJSON)
        }

        let client = makeClient()
        let request = TTSRequestWithTimestamps(voiceId: "tc_1", text: "Hi.", model: .ssfmV30)
        _ = try await client.textToSpeechWithTimestamps(request, granularity: "word")
    }

    func testTextToSpeechWithTimestampsInvalidGranularityThrows() async {
        MockURLProtocol.reset()
        defer { MockURLProtocol.reset() }

        let client = makeClient()
        let request = TTSRequestWithTimestamps(voiceId: "tc_1", text: "Hi.", model: .ssfmV30)
        do {
            _ = try await client.textToSpeechWithTimestamps(request, granularity: "sentence")
            XCTFail("Expected TimestampError.invalidGranularity")
        } catch let error as TimestampError {
            XCTAssertEqual(error, .invalidGranularity("sentence"))
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func testTextToSpeechWithTimestampsUnauthorized() async {
        MockURLProtocol.reset()
        defer { MockURLProtocol.reset() }

        MockURLProtocol.requestHandler = { req in
            (self.mockResponse(url: req.url!, status: 401),
             #"{"detail":"bad key"}"#.data(using: .utf8))
        }

        let client = makeClient()
        let request = TTSRequestWithTimestamps(voiceId: "tc_1", text: "x", model: .ssfmV30)
        do {
            _ = try await client.textToSpeechWithTimestamps(request)
            XCTFail("Expected TypecastError.unauthorized")
        } catch let error as TypecastError {
            guard case .unauthorized(let msg) = error else {
                XCTFail("Wrong case: \(error)"); return
            }
            XCTAssertEqual(msg, "bad key")
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func testTextToSpeechWithTimestampsNoGranularityParam() async throws {
        // When granularity is nil, no query string should be appended.
        MockURLProtocol.reset()
        defer { MockURLProtocol.reset() }

        let audioB64 = Data([0x00]).base64EncodedString()
        let responseJSON = """
        {
            "audio": "\(audioB64)",
            "audio_format": "wav",
            "audio_duration": 0.5,
            "words": [{"text": "Hi.", "start": 0.0, "end": 0.5}],
            "characters": null
        }
        """.data(using: .utf8)!

        MockURLProtocol.requestHandler = { req in
            XCTAssertNil(req.url?.query, "No query string expected when granularity is nil")
            return (self.mockResponse(url: req.url!, status: 200), responseJSON)
        }

        let client = makeClient()
        let request = TTSRequestWithTimestamps(voiceId: "tc_1", text: "Hi.", model: .ssfmV30)
        _ = try await client.textToSpeechWithTimestamps(request)
    }
}
