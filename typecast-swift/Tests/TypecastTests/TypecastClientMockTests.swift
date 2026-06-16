import XCTest
@testable import Typecast

final class TypecastClientMockTests: TypecastClientMockTestCase {

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

    func testTextToSpeechMPEGContentTypeMapsToMP3() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/mpeg"]
                ),
                Data([0xFF, 0xFB, 0x90])
            )
        }

        let request = TTSRequest(voiceId: "tc_1", text: "hello", model: .ssfmV30)
        let response = try await client.textToSpeech(request)
        XCTAssertEqual(response.format, .mp3)
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
}
