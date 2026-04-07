import XCTest
@testable import Typecast

/// Unit tests that exercise `TypecastClient` end-to-end against a mocked
/// `URLSession`. These cover request construction, response parsing, and the
/// full set of error mappings.
final class TypecastClientMockTests: XCTestCase {

    private let baseURL = "https://test.typecast.local"
    private var client: TypecastClient!

    override func setUp() {
        super.setUp()
        MockURLProtocol.reset()
        let session = MockSession.make()
        client = TypecastClient(
            configuration: TypecastConfiguration(apiKey: "test-key", baseURL: baseURL),
            session: session
        )
    }

    override func tearDown() {
        MockURLProtocol.reset()
        client = nil
        super.tearDown()
    }

    private func httpResponse(url: URL, status: Int, headers: [String: String] = [:]) -> HTTPURLResponse {
        HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
    }

    // MARK: - textToSpeech

    func testTextToSpeechWavSuccess() async throws {
        let expectedAudio = Data([0x52, 0x49, 0x46, 0x46, 0x00, 0x01, 0x02])
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/text-to-speech")
            XCTAssertEqual(req.httpMethod, "POST")
            XCTAssertEqual(req.value(forHTTPHeaderField: "X-API-KEY"), "test-key")
            XCTAssertEqual(req.value(forHTTPHeaderField: "Content-Type"), "application/json")
            return (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: [
                        "Content-Type": "audio/wav",
                        "X-Audio-Duration": "2.5"
                    ]
                ),
                expectedAudio
            )
        }

        let request = TTSRequest(voiceId: "tc_1", text: "hi", model: .ssfmV30)
        let response = try await client.textToSpeech(request)

        XCTAssertEqual(response.audioData, expectedAudio)
        XCTAssertEqual(response.duration, 2.5, accuracy: 0.0001)
        XCTAssertEqual(response.format, .wav)

        // Body assertion
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        XCTAssertEqual(json?["voice_id"] as? String, "tc_1")
        XCTAssertEqual(json?["text"] as? String, "hi")
        XCTAssertEqual(json?["model"] as? String, "ssfm-v30")
    }

    func testTextToSpeechMP3WithoutDurationHeader() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/mp3"]
                ),
                Data([0xFF, 0xFB, 0x90])
            )
        }

        let request = TTSRequest(voiceId: "tc_1", text: "hello", model: .ssfmV21)
        let response = try await client.textToSpeech(request)
        XCTAssertEqual(response.format, .mp3)
        XCTAssertEqual(response.duration, 0)
    }

    func testTextToSpeechFallsBackToWavWhenContentTypeEmpty() async throws {
        // Empty Content-Type header value exercises the `?? "wav"` autoclosure
        // (split returns an empty sequence so `.last` is nil).
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": ""]),
                Data([0x00])
            )
        }
        let request = TTSRequest(voiceId: "tc_1", text: "x", model: .ssfmV30)
        let response = try await client.textToSpeech(request)
        XCTAssertEqual(response.format, .wav)
    }

    func testTextToSpeechFallsBackToWavWhenContentTypeMissing() async throws {
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), Data([0x00]))
        }

        let request = TTSRequest(voiceId: "tc_1", text: "hello", model: .ssfmV30)
        let response = try await client.textToSpeech(request)
        XCTAssertEqual(response.format, .wav)
    }

    func testTextToSpeechErrorMappingPropagatesUnauthorized() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 401),
                #"{"detail":"bad key"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeech(TTSRequest(voiceId: "tc_1", text: "x", model: .ssfmV30))
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .unauthorized(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "bad key")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    // MARK: - getVoices V2

    func testGetVoicesNoFilter() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v2/voices")
            XCTAssertNil(req.url?.query)
            let body = """
            [
              {
                "voice_id": "tc_1",
                "voice_name": "Alice",
                "models": [{"version":"ssfm-v30","emotions":["normal"]}]
              }
            ]
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let voices = try await client.getVoices()
        XCTAssertEqual(voices.count, 1)
        XCTAssertEqual(voices[0].voiceId, "tc_1")
        XCTAssertNil(voices[0].gender)
        XCTAssertNil(voices[0].age)
        XCTAssertNil(voices[0].useCases)
    }

    func testGetVoicesWithFilter() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v2/voices")
            let query = req.url?.query ?? ""
            XCTAssertTrue(query.contains("model=ssfm-v30"))
            XCTAssertTrue(query.contains("gender=female"))
            XCTAssertTrue(query.contains("age=young_adult"))
            XCTAssertTrue(query.contains("use_cases=Audiobook"))
            return (self.httpResponse(url: req.url!, status: 200), "[]".data(using: .utf8))
        }

        let filter = VoicesV2Filter(
            model: .ssfmV30,
            gender: .female,
            age: .youngAdult,
            useCases: .audiobook
        )
        let voices = try await client.getVoices(filter: filter)
        XCTAssertTrue(voices.isEmpty)
    }

    func testGetVoiceByIdSuccess() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v2/voices/tc_42")
            let body = """
            {
              "voice_id":"tc_42",
              "voice_name":"Bob",
              "models":[{"version":"ssfm-v21","emotions":["normal","happy"]}],
              "gender":"male",
              "age":"middle_age",
              "use_cases":["News"]
            }
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let voice = try await client.getVoice(voiceId: "tc_42")
        XCTAssertEqual(voice.voiceId, "tc_42")
        XCTAssertEqual(voice.gender, .male)
        XCTAssertEqual(voice.age, .middleAge)
        XCTAssertEqual(voice.useCases, ["News"])
    }

    func testGetVoiceNotFound() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 404),
                #"{"detail":"missing"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.getVoice(voiceId: "tc_404")
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .notFound(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "missing")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    // MARK: - getVoicesV1 (deprecated)

    @available(*, deprecated)
    func testGetVoicesV1NoModel() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/voices")
            XCTAssertNil(req.url?.query)
            let body = """
            [{"voice_id":"tc_v1","voice_name":"V1","model":"ssfm-v21","emotions":["normal"]}]
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }
        let voices = try await client.getVoicesV1()
        XCTAssertEqual(voices.count, 1)
        XCTAssertEqual(voices[0].voiceId, "tc_v1")
        XCTAssertEqual(voices[0].model, .ssfmV21)
    }

    @available(*, deprecated)
    func testGetVoicesV1WithModel() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/voices")
            XCTAssertEqual(req.url?.query, "model=ssfm-v30")
            return (self.httpResponse(url: req.url!, status: 200), "[]".data(using: .utf8))
        }
        let voices = try await client.getVoicesV1(model: .ssfmV30)
        XCTAssertTrue(voices.isEmpty)
    }

    // MARK: - Convenience speak

    func testConvenienceSpeak() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav", "X-Audio-Duration": "1.0"]
                ),
                Data([0x01])
            )
        }
        let response = try await client.speak("hi", voiceId: "tc_1")
        XCTAssertEqual(response.duration, 1.0)
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        XCTAssertEqual(json?["model"] as? String, "ssfm-v30")
    }

    func testConvenienceSpeakWithEmotionV30UsesPresetPrompt() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav"]
                ),
                Data([0x01])
            )
        }
        _ = try await client.speak(
            "hi",
            voiceId: "tc_1",
            model: .ssfmV30,
            emotion: .happy,
            intensity: 1.4
        )
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        let prompt = json?["prompt"] as? [String: Any]
        XCTAssertEqual(prompt?["emotion_type"] as? String, "preset")
        XCTAssertEqual(prompt?["emotion_preset"] as? String, "happy")
        XCTAssertEqual(prompt?["emotion_intensity"] as? Double, 1.4)
    }

    func testConvenienceSpeakWithEmotionV21UsesBasicPrompt() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav"]
                ),
                Data([0x01])
            )
        }
        _ = try await client.speak(
            "hi",
            voiceId: "tc_1",
            model: .ssfmV21,
            emotion: .sad,
            intensity: 0.5
        )
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        let prompt = json?["prompt"] as? [String: Any]
        XCTAssertNil(prompt?["emotion_type"])
        XCTAssertEqual(prompt?["emotion_preset"] as? String, "sad")
        XCTAssertEqual(prompt?["emotion_intensity"] as? Double, 0.5)
    }

    // MARK: - URL building

    func testTextToSpeechNonHTTPResponseThrows() async {
        MockURLProtocol.rawResponseHandler = { req in
            (URLResponse(url: req.url!, mimeType: "text/plain", expectedContentLength: 0, textEncodingName: nil), nil)
        }
        do {
            _ = try await client.textToSpeech(TTSRequest(voiceId: "tc_1", text: "x", model: .ssfmV30))
            XCTFail("expected throw")
        } catch let error as TypecastError {
            guard case .invalidResponse = error else {
                XCTFail("wrong: \(error)")
                return
            }
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testGetVoiceNonHTTPResponseThrows() async {
        MockURLProtocol.rawResponseHandler = { req in
            (URLResponse(url: req.url!, mimeType: "text/plain", expectedContentLength: 0, textEncodingName: nil), nil)
        }
        do {
            _ = try await client.getVoice(voiceId: "tc_1")
            XCTFail("expected throw")
        } catch let error as TypecastError {
            guard case .invalidResponse = error else {
                XCTFail("wrong: \(error)")
                return
            }
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testInvalidBaseURLThrows() async {
        let badClient = TypecastClient(
            configuration: TypecastConfiguration(apiKey: "k", baseURL: "ht tp://broken url"),
            session: MockSession.make()
        )
        do {
            _ = try await badClient.getVoice(voiceId: "tc_1")
            XCTFail("expected throw")
        } catch let error as TypecastError {
            guard case .invalidResponse = error else {
                XCTFail("wrong error: \(error)")
                return
            }
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }
}
