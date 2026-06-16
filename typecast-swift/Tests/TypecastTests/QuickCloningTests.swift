import XCTest
@testable import Typecast

final class QuickCloningTests: XCTestCase {

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

    // MARK: - Test 1: cloneVoice returns CustomVoice

    func testCloneVoiceReturnsCustomVoice() async throws {
        let responseJSON = """
        {
            "voice_id": "uc_abc123",
            "name": "My Voice",
            "model": "ssfm-v30"
        }
        """.data(using: .utf8)!

        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), responseJSON)
        }

        let audio = Data(repeating: 0x00, count: 1024)
        let result = try await client.cloneVoice(
            audio: audio,
            filename: "sample.wav",
            name: "My Voice",
            model: "ssfm-v30"
        )

        XCTAssertEqual(result.voiceId, "uc_abc123")
        XCTAssertEqual(result.name, "My Voice")
        XCTAssertEqual(result.model, "ssfm-v30")
    }

    // MARK: - Test 2: cloneVoice sends correct multipart body

    func testCloneVoiceSendsMultipartBody() async throws {
        let responseJSON = """
        {"voice_id": "uc_xyz", "name": "Test", "model": "ssfm-v30"}
        """.data(using: .utf8)!

        var capturedRequest: URLRequest?

        MockURLProtocol.requestHandler = { req in
            capturedRequest = req
            return (self.httpResponse(url: req.url!, status: 200), responseJSON)
        }

        // Use ASCII-safe bytes so the body is decodable as a UTF-8 string for assertions.
        let audio = Data(repeating: 0x20, count: 512)   // space bytes (0x20) are valid UTF-8
        _ = try await client.cloneVoice(
            audio: audio,
            filename: "sample.wav",
            name: "Test",
            model: "ssfm-v30"
        )

        let req = try XCTUnwrap(capturedRequest)

        // URL ends with /v1/voices/clone
        XCTAssertEqual(req.url?.path, "/v1/voices/clone", "URL path must be /v1/voices/clone")

        // HTTP method is POST
        XCTAssertEqual(req.httpMethod, "POST")

        // Content-Type starts with multipart/form-data; boundary=
        let contentType = try XCTUnwrap(req.value(forHTTPHeaderField: "Content-Type"))
        XCTAssertTrue(
            contentType.hasPrefix("multipart/form-data; boundary="),
            "Content-Type must start with 'multipart/form-data; boundary=', got: \(contentType)"
        )

        // Read the captured body from MockURLProtocol.lastBody
        let body = try XCTUnwrap(MockURLProtocol.lastBody, "lastBody must be captured by MockURLProtocol")

        // The multipart headers are ASCII, but the audio payload may not be fully UTF-8.
        // We search for the field-name substrings as raw bytes instead.
        func bodyContains(_ substring: String) -> Bool {
            guard let pattern = substring.data(using: .utf8) else { return false }
            return body.range(of: pattern) != nil
        }

        XCTAssertTrue(
            bodyContains("name=\"name\""),
            "Body must contain name field disposition"
        )
        XCTAssertTrue(
            bodyContains("name=\"model\""),
            "Body must contain model field disposition"
        )
        XCTAssertTrue(
            bodyContains("name=\"file\""),
            "Body must contain file field disposition"
        )
    }

    func testCloneVoiceSetsMp3AndDefaultMimeTypes() async throws {
        let cases = [
            ("sample.mp3", "audio/mpeg"),
            ("sample.bin", "application/octet-stream")
        ]

        for (filename, expectedMime) in cases {
            let responseJSON = #"{"voice_id":"uc_mime","name":"Mime","model":"ssfm-v30"}"#.data(using: .utf8)!
            MockURLProtocol.requestHandler = { req in
                (self.httpResponse(url: req.url!, status: 200), responseJSON)
            }

            _ = try await client.cloneVoice(
                audio: Data(repeating: 0x20, count: 32),
                filename: filename,
                name: "Mime",
                model: "ssfm-v30"
            )

            let body = try XCTUnwrap(MockURLProtocol.lastBody)
            let bodyText = String(decoding: body, as: UTF8.self)
            XCTAssertTrue(bodyText.contains("Content-Type: \(expectedMime)"))
        }
    }

    // MARK: - Test 3: rejects oversized audio

    func testCloneVoiceRejectsOversizedAudio() async throws {
        // 26 MB > 25 MB limit
        let oversizedAudio = Data(count: 26 * 1024 * 1024)

        var requestSent = false
        MockURLProtocol.requestHandler = { _ in
            requestSent = true
            let resp = self.httpResponse(url: URL(string: self.baseURL)!, status: 200)
            return (resp, nil)
        }

        do {
            _ = try await client.cloneVoice(
                audio: oversizedAudio,
                filename: "big.wav",
                name: "Test",
                model: "ssfm-v30"
            )
            XCTFail("Expected error for oversized audio")
        } catch let error as TypecastError {
            guard case .badRequest(let msg) = error else {
                XCTFail("Expected .badRequest, got \(error)"); return
            }
            XCTAssertTrue(msg.contains("25"), "Error should mention 25 MB limit")
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }

        XCTAssertFalse(requestSent, "No network request should be sent for oversized audio")
    }

    // MARK: - Test 4: rejects bad name length (empty and 31 chars)

    func testCloneVoiceRejectsBadNameLength() async throws {
        let audio = Data(repeating: 0x01, count: 100)

        // Empty name
        do {
            _ = try await client.cloneVoice(audio: audio, filename: "s.wav", name: "", model: "ssfm-v30")
            XCTFail("Expected error for empty name")
        } catch let error as TypecastError {
            guard case .badRequest = error else {
                XCTFail("Expected .badRequest for empty name, got \(error)"); return
            }
        } catch {
            XCTFail("Unexpected error type for empty name: \(error)")
        }

        // 31-character name (exceeds max of 30)
        let longName = String(repeating: "a", count: 31)
        do {
            _ = try await client.cloneVoice(audio: audio, filename: "s.wav", name: longName, model: "ssfm-v30")
            XCTFail("Expected error for 31-char name")
        } catch let error as TypecastError {
            guard case .badRequest = error else {
                XCTFail("Expected .badRequest for 31-char name, got \(error)"); return
            }
        } catch {
            XCTFail("Unexpected error type for 31-char name: \(error)")
        }
    }

    // MARK: - Test 5: deleteVoice succeeds on 204

    func testDeleteVoiceSucceedsOn204() async throws {
        var capturedRequest: URLRequest?

        MockURLProtocol.requestHandler = { req in
            capturedRequest = req
            return (self.httpResponse(url: req.url!, status: 204), nil)
        }

        // Should not throw
        try await client.deleteVoice("uc_xxx")

        let req = try XCTUnwrap(capturedRequest)
        XCTAssertTrue(
            req.url?.path.hasSuffix("/v1/voices/uc_xxx") ?? false,
            "URL must end with /v1/voices/uc_xxx, got: \(req.url?.path ?? "(nil)")"
        )
        XCTAssertEqual(req.httpMethod, "DELETE")
        XCTAssertEqual(req.value(forHTTPHeaderField: "X-API-KEY"), "test-key")
    }

    func testDeleteVoiceEncodesVoiceIdPathComponent() async throws {
        var capturedRequest: URLRequest?
        MockURLProtocol.requestHandler = { req in
            capturedRequest = req
            return (self.httpResponse(url: req.url!, status: 204), nil)
        }

        try await client.deleteVoice("uc/xxx #1")

        let req = try XCTUnwrap(capturedRequest)
        XCTAssertTrue(req.url?.absoluteString.contains("/v1/voices/uc%2Fxxx%20%231") ?? false)
    }

    // MARK: - Test 6: deleteVoice throws on 404

    func testDeleteVoiceThrowsOn404() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 404),
                #"{"detail":"voice not found"}"#.data(using: .utf8)
            )
        }

        do {
            try await client.deleteVoice("uc_nonexistent")
            XCTFail("Expected TypecastError.notFound")
        } catch let error as TypecastError {
            guard case .notFound(let msg) = error else {
                XCTFail("Expected .notFound, got \(error)"); return
            }
            XCTAssertEqual(msg, "voice not found")
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func testDeleteVoiceThrowsOnNonHTTPResponse() async {
        MockURLProtocol.rawResponseHandler = { req in
            let response = URLResponse(
                url: req.url!,
                mimeType: nil,
                expectedContentLength: 0,
                textEncodingName: nil
            )
            return (response, nil)
        }

        do {
            try await client.deleteVoice("uc_non_http")
            XCTFail("Expected invalidResponse")
        } catch let error as TypecastError {
            guard case .invalidResponse = error else {
                XCTFail("Expected .invalidResponse, got \(error)")
                return
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }

    func testCloneVoiceFileURLOverloadReadsFileAndUsesBasename() async throws {
        let responseJSON = #"{"voice_id":"uc_file","name":"File","model":"ssfm-v30"}"#.data(using: .utf8)!
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), responseJSON)
        }

        let fileURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("typecast-\(UUID().uuidString)")
            .appendingPathExtension("mp3")
        try Data(repeating: 0x20, count: 16).write(to: fileURL)
        defer { try? FileManager.default.removeItem(at: fileURL) }

        let result = try await client.cloneVoice(
            audioFileURL: fileURL,
            name: "File",
            model: "ssfm-v30"
        )

        XCTAssertEqual(result.voiceId, "uc_file")
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let bodyText = String(decoding: body, as: UTF8.self)
        XCTAssertTrue(bodyText.contains(fileURL.lastPathComponent))
        XCTAssertTrue(bodyText.contains("Content-Type: audio/mpeg"))
    }

    // MARK: - Bonus: valid boundary name lengths (edge cases)

    func testCloneVoiceAcceptsOneCharName() async throws {
        let responseJSON = """
        {"voice_id": "uc_1", "name": "A", "model": "ssfm-v30"}
        """.data(using: .utf8)!
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), responseJSON)
        }
        let result = try await client.cloneVoice(
            audio: Data(count: 100),
            filename: "s.wav",
            name: "A",
            model: "ssfm-v30"
        )
        XCTAssertEqual(result.voiceId, "uc_1")
    }

    func testCloneVoiceAcceptsThirtyCharName() async throws {
        let responseJSON = """
        {"voice_id": "uc_2", "name": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "model": "ssfm-v30"}
        """.data(using: .utf8)!
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), responseJSON)
        }
        let thirtyChars = String(repeating: "a", count: 30)
        let result = try await client.cloneVoice(
            audio: Data(count: 100),
            filename: "s.wav",
            name: thirtyChars,
            model: "ssfm-v30"
        )
        XCTAssertEqual(result.voiceId, "uc_2")
    }

    func testCloneVoiceAcceptsExactly25MBFile() async throws {
        let responseJSON = """
        {"voice_id": "uc_3", "name": "Big", "model": "ssfm-v30"}
        """.data(using: .utf8)!
        MockURLProtocol.requestHandler = { req in
            (self.httpResponse(url: req.url!, status: 200), responseJSON)
        }
        let exactly25MB = Data(count: 25 * 1024 * 1024)
        let result = try await client.cloneVoice(
            audio: exactly25MB,
            filename: "s.wav",
            name: "Big",
            model: "ssfm-v30"
        )
        XCTAssertEqual(result.voiceId, "uc_3")
    }

    func testCloneVoiceRejectsNonFileAudioURLBeforeRequest() async {
        MockURLProtocol.requestHandler = { _ in
            XCTFail("cloneVoice should reject non-file URLs before requesting")
            return (self.httpResponse(url: URL(string: self.baseURL)!, status: 200), nil)
        }

        do {
            _ = try await client.cloneVoice(
                audioFileURL: URL(string: "https://example.com/sample.wav")!,
                name: "Remote",
                model: "ssfm-v30"
            )
            XCTFail("expected file URL validation error")
        } catch let error as TypecastError {
            guard case .badRequest(let message) = error else {
                XCTFail("wrong error: \(error)")
                return
            }
            XCTAssertEqual(message, "audioFileURL must be a file URL")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    // MARK: - CustomVoice Codable round-trip

    func testCustomVoiceCodable() throws {
        let json = #"{"voice_id":"uc_abc","name":"Bob","model":"ssfm-v30"}"#.data(using: .utf8)!
        let voice = try JSONDecoder().decode(CustomVoice.self, from: json)
        XCTAssertEqual(voice.voiceId, "uc_abc")
        XCTAssertEqual(voice.name, "Bob")
        XCTAssertEqual(voice.model, "ssfm-v30")
    }

    func testCustomVoiceEquatable() {
        let a = CustomVoice(voiceId: "uc_1", name: "Alice", model: "ssfm-v30")
        let b = CustomVoice(voiceId: "uc_1", name: "Alice", model: "ssfm-v30")
        let c = CustomVoice(voiceId: "uc_2", name: "Bob", model: "ssfm-v21")
        XCTAssertEqual(a, b)
        XCTAssertNotEqual(a, c)
    }

    // MARK: - QuickCloningLimits values

    func testQuickCloningLimitsValues() {
        XCTAssertEqual(QuickCloningLimits.cloningMaxFileSize, 25 * 1024 * 1024)
        XCTAssertEqual(QuickCloningLimits.nameMinLength, 1)
        XCTAssertEqual(QuickCloningLimits.nameMaxLength, 30)
    }
}
