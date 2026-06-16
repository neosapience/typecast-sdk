import XCTest
@testable import Typecast

final class TypecastClientGenerateToFileTests: TypecastClientMockTestCase {

    // MARK: - generateToFile

    func testGenerateToFileInfersMP3DefaultModelAndWritesFile() async throws {
        let expectedAudio = Data([0x01, 0x02, 0x03])
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/text-to-speech")
            return (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/mp3", "X-Audio-Duration": "1.25"]
                ),
                expectedAudio
            )
        }

        let fileURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("typecast-swift-\(UUID().uuidString).mp3")
        defer { try? FileManager.default.removeItem(at: fileURL) }

        let response = try await client.generateToFile(
            fileURL,
            request: GenerateToFileRequest(
                voiceId: "tc_1",
                text: "hello",
                language: .english,
                prompt: .preset(PresetPrompt(emotionPreset: .happy)),
                seed: 7
            )
        )

        XCTAssertEqual(response.format, .mp3)
        XCTAssertEqual(try Data(contentsOf: fileURL), expectedAudio)
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(json["model"] as? String, "ssfm-v30")
        XCTAssertEqual(json["language"] as? String, "eng")
        XCTAssertEqual(json["seed"] as? Int, 7)
        let output = try XCTUnwrap(json["output"] as? [String: Any])
        XCTAssertEqual(output["audio_format"] as? String, "mp3")
    }

    func testGenerateToFileKeepsExplicitOutputAndCoversExtensions() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": "audio/wav"]),
                Data([0x04])
            )
        }

        let wavURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("typecast-swift-\(UUID().uuidString).wav")
        defer { try? FileManager.default.removeItem(at: wavURL) }

        _ = try await client.generateToFile(
            wavURL.path,
            request: GenerateToFileRequest(
                voiceId: "tc_1",
                text: "hello",
                model: .ssfmV21,
                output: OutputSettings(audioFormat: .mp3)
            )
        )

        let explicitBody = try XCTUnwrap(MockURLProtocol.lastBody)
        let explicitJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: explicitBody) as? [String: Any])
        XCTAssertEqual(explicitJSON["model"] as? String, "ssfm-v21")
        let explicitOutput = try XCTUnwrap(explicitJSON["output"] as? [String: Any])
        XCTAssertEqual(explicitOutput["audio_format"] as? String, "mp3")

        let inferredWav = GenerateToFileRequest(voiceId: "tc_1", text: "hello")
            .toTTSRequest(fileURL: URL(fileURLWithPath: "x.WAV"))
        XCTAssertEqual(inferredWav.output?.audioFormat, .wav)

        let unknown = GenerateToFileRequest(voiceId: "tc_1", text: "hello")
            .toTTSRequest(fileURL: URL(fileURLWithPath: "x.bin"))
        XCTAssertNil(unknown.output)
    }

    func testGenerateToFileRejectsInvalidPathsBeforeRequest() async {
        MockURLProtocol.requestHandler = { _ in
            XCTFail("generateToFile should validate paths before requesting")
            return (self.httpResponse(url: URL(string: self.baseURL)!, status: 200), Data())
        }

        do {
            _ = try await client.generateToFile(
                URL(string: "https://example.com/speech.mp3")!,
                request: GenerateToFileRequest(voiceId: "tc_1", text: "hello")
            )
            XCTFail("expected file URL validation error")
        } catch let error as TypecastError {
            guard case .badRequest(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "fileURL must be a file URL")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }

        do {
            _ = try await client.generateToFile(
                "  ",
                request: GenerateToFileRequest(voiceId: "tc_1", text: "hello")
            )
            XCTFail("expected file path validation error")
        } catch let error as TypecastError {
            guard case .badRequest(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "filePath cannot be empty")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }
}
