import XCTest
@testable import Typecast

final class TypecastClientURLValidationTests: TypecastClientMockTestCase {

    // MARK: - URL and auth validation

    func testAPIKeyRequiresHTTPSBaseURL() async {
        let insecureClient = TypecastClient(
            configuration: TypecastConfiguration(apiKey: "k", baseURL: "http://proxy.local"),
            session: MockSession.make()
        )

        do {
            _ = try await insecureClient.getVoices()
            XCTFail("expected HTTPS validation error")
        } catch let error as TypecastError {
            guard case .invalidResponse(let message) = error else {
                XCTFail("wrong error: \(error)")
                return
            }
            XCTAssertEqual(message, "HTTPS is required for all network communication")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testCustomBaseURLWithoutAPIKeyRequiresHTTPS() async {
        let insecureClient = TypecastClient(
            configuration: TypecastConfiguration(baseURL: "http://proxy.local"),
            session: MockSession.make()
        )

        do {
            _ = try await insecureClient.getVoices()
            XCTFail("expected HTTPS validation error")
        } catch let error as TypecastError {
            guard case .invalidResponse(let message) = error else {
                XCTFail("wrong error: \(error)")
                return
            }
            XCTAssertEqual(message, "HTTPS is required for all network communication")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
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

    func testInvalidCustomBaseURLWithoutAPIKeyThrows() async {
        let badClient = TypecastClient(
            configuration: TypecastConfiguration(baseURL: "ht tp://broken url"),
            session: MockSession.make()
        )
        do {
            _ = try await badClient.getVoices()
            XCTFail("expected throw")
        } catch let error as TypecastError {
            guard case .invalidResponse(let message) = error else {
                XCTFail("wrong error: \(error)")
                return
            }
            XCTAssertEqual(message, "Invalid base URL")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }
}
