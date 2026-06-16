import XCTest
@testable import Typecast

final class TypecastClientStreamTests: TypecastClientMockTestCase {

    // MARK: - textToSpeechStream

    private func collectStream(
        _ stream: AsyncThrowingStream<Data, Error>
    ) async throws -> Data {
        var collected = Data()
        for try await chunk in stream {
            collected.append(chunk)
        }
        return collected
    }

    func testTextToSpeechStreamSuccessYieldsChunks() async throws {
        // 20000 bytes -> 8192 + 8192 + 3616 chunks
        var bytes = [UInt8]()
        for i in 0..<20000 { bytes.append(UInt8(i % 251)) }
        let expectedAudio = Data(bytes)

        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/text-to-speech/stream")
            XCTAssertEqual(req.httpMethod, "POST")
            XCTAssertEqual(req.value(forHTTPHeaderField: "X-API-KEY"), "test-key")
            XCTAssertEqual(req.value(forHTTPHeaderField: "Content-Type"), "application/json")
            return (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav"]
                ),
                expectedAudio
            )
        }

        let request = TTSRequestStream(
            voiceId: "tc_1",
            text: "stream me",
            model: .ssfmV30,
            language: .english,
            prompt: .preset(PresetPrompt(emotionPreset: .happy, emotionIntensity: 1.1)),
            output: Typecast.OutputStream(audioPitch: 0, audioTempo: 1.0, audioFormat: .wav),
            seed: 42
        )
        let stream = try await client.textToSpeechStream(request)

        var chunks: [Data] = []
        for try await chunk in stream {
            chunks.append(chunk)
        }
        XCTAssertEqual(chunks.count, 3)
        XCTAssertEqual(chunks[0].count, 8192)
        XCTAssertEqual(chunks[1].count, 8192)
        XCTAssertEqual(chunks[2].count, 20000 - 16384)
        let merged = chunks.reduce(Data(), +)
        XCTAssertEqual(merged, expectedAudio)

        // Body assertion
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        XCTAssertEqual(json?["voice_id"] as? String, "tc_1")
        XCTAssertEqual(json?["text"] as? String, "stream me")
        XCTAssertEqual(json?["model"] as? String, "ssfm-v30")
        XCTAssertEqual(json?["seed"] as? Int, 42)
        let output = json?["output"] as? [String: Any]
        XCTAssertEqual(output?["audio_format"] as? String, "wav")
        XCTAssertNil(output?["volume"])
        XCTAssertNil(output?["target_lufs"])
    }

    func testTextToSpeechStreamSmallBodySingleChunk() async throws {
        let expectedAudio = Data([0x01, 0x02, 0x03])
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), expectedAudio)
        }
        let stream = try await client.textToSpeechStream(
            TTSRequestStream(voiceId: "tc_1", text: "hi", model: .ssfmV30)
        )
        let collected = try await collectStream(stream)
        XCTAssertEqual(collected, expectedAudio)
    }

    func testTextToSpeechStreamEmptyBody() async throws {
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), Data())
        }
        let stream = try await client.textToSpeechStream(
            TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
        )
        let collected = try await collectStream(stream)
        XCTAssertEqual(collected, Data())
    }

    func testTextToSpeechStreamBadRequest() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 400),
                #"{"detail":"bad"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .badRequest(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "bad")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamUnauthorized() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 401),
                #"{"detail":"nope"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .unauthorized(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "nope")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamPaymentRequired() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 402),
                #"{"detail":"need credits"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .paymentRequired(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "need credits")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamNotFound() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 404),
                #"{"detail":"missing"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .notFound(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "missing")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamValidationError() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 422),
                #"{"detail":"invalid"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .validationError(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "invalid")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamRateLimited() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 429),
                #"{"detail":"slow down"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .rateLimitExceeded(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "slow down")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamServerError() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 500),
                #"{"detail":"boom"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .serverError(let message) = error else {
                XCTFail("wrong case: \(error)"); return
            }
            XCTAssertEqual(message, "boom")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testTextToSpeechStreamNonHTTPResponseThrows() async {
        MockURLProtocol.rawResponseHandler = { req in
            (URLResponse(url: req.url!, mimeType: "text/plain", expectedContentLength: 0, textEncodingName: nil), nil)
        }
        do {
            _ = try await client.textToSpeechStream(
                TTSRequestStream(voiceId: "tc_1", text: "x", model: .ssfmV30)
            )
            XCTFail("expected throw")
        } catch let error as TypecastError {
            guard case .invalidResponse = error else {
                XCTFail("wrong: \(error)"); return
            }
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }
}
