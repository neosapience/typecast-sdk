import XCTest
@testable import Typecast

final class RemoveSilenceTests: XCTestCase {
    func testOptionalZeroSerialization() throws {
        let encoder = JSONEncoder()
        XCTAssertFalse(String(decoding: try encoder.encode(OutputSettings()), as: UTF8.self).contains("remove_silence_ms"))
        XCTAssertFalse(String(decoding: try encoder.encode(Typecast.OutputStream()), as: UTF8.self).contains("remove_silence_ms"))
        for value in [0, 300, 1000] {
            let output = OutputSettings(removeSilenceMs: value)
            let stream = Typecast.OutputStream(removeSilenceMs: value)
            XCTAssertEqual(try JSONDecoder().decode(OutputSettings.self, from: encoder.encode(output)).removeSilenceMs, value)
            XCTAssertEqual(try JSONDecoder().decode(Typecast.OutputStream.self, from: encoder.encode(stream)).removeSilenceMs, value)
        }
    }

    func testInvalidRangeFailsBeforeNetworking() async throws {
        let client = TypecastClient(apiKey: "test")
        for value in [-1, 1001] {
            let request = TTSRequest(voiceId: "test", text: "test", model: .ssfmV30,
                output: OutputSettings(removeSilenceMs: value))
            do {
                _ = try await client.textToSpeech(request)
                XCTFail("Invalid silence accepted")
            } catch is EncodingError {}
        }
    }
}
