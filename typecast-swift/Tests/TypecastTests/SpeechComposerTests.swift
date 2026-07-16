import XCTest

@testable import Typecast

final class SpeechComposerTests: TypecastClientMockTestCase {

  func testComposeSpeechUsesComposeApiAndMergesOverrides() async throws {
    var bodies: [[String: Any]] = []
    MockURLProtocol.requestHandler = { req in
      XCTAssertEqual(req.url?.path, "/v1/text-to-speech/compose")
      let body = try XCTUnwrap(MockURLProtocol.lastBody)
      bodies.append(try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any]))
      return (
        self.httpResponse(
          url: req.url!,
          status: 200,
          headers: ["Content-Type": "audio/mpeg", "X-Audio-Duration": "1.25"]
        ),
        Data("composed-audio".utf8)
      )
    }

    let response = try await client.composeSpeech()
      .defaults(
        ComposerSettings(
          voiceId: "voice-a",
          model: .ssfmV30,
          output: OutputSettings(audioPitch: 1, audioFormat: .mp3)
        )
      )
      .say(
        "Hello<|0.3s|>world",
        overrides: ComposerSettings(
          voiceId: "voice-b",
          output: OutputSettings(audioTempo: 1.1)
        )
      )
      .generate()

    XCTAssertEqual(bodies.count, 1)
    let segments = try XCTUnwrap(bodies[0]["segments"] as? [[String: Any]])
    XCTAssertEqual(segments.compactMap { $0["type"] as? String }, ["tts", "pause", "tts"])
    XCTAssertEqual(segments[0]["text"] as? String, "Hello")
    XCTAssertEqual(segments[0]["voice_id"] as? String, "voice-b")
    let output = try XCTUnwrap(segments[0]["output"] as? [String: Any])
    XCTAssertEqual(output["audio_format"] as? String, "mp3")
    XCTAssertEqual(output["audio_pitch"] as? Int, 1)
    XCTAssertEqual(output["audio_tempo"] as? Double, 1.1)
    XCTAssertEqual(segments[1]["duration_seconds"] as? Double, 0.3)
    XCTAssertEqual(segments[2]["text"] as? String, "world")
    XCTAssertEqual(response.audioData, Data("composed-audio".utf8))
    XCTAssertEqual(response.format, .mp3)
    XCTAssertEqual(response.duration, 1.25)
  }

  func testComposeSpeechValidatesBeforeNetwork() async {
    MockURLProtocol.requestHandler = { req in
      XCTFail("network should not be called: \(req)")
      return (self.httpResponse(url: req.url!, status: 500), nil)
    }

    do {
      _ = try await client.composeSpeech().say("Hello").generate()
      XCTFail("expected error")
    } catch let TypecastError.validationError(message) {
      XCTAssertTrue(message.contains("voiceId is required"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }

    do {
      _ = try await client.composeSpeech().pause(0).generate()
      XCTFail("expected error")
    } catch let TypecastError.validationError(message) {
      XCTAssertTrue(message.contains("pause seconds must be greater than 0"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }
  }

  func testParsePauseMarkupIsLenientForInvalidTokens() {
    XCTAssertEqual(
      parsePauseMarkup("a<|0.3s|>b<|abc|>c<|3s|>"),
      [.text("a"), .pause(0.3), .text("b<|abc|>c"), .pause(3)]
    )
  }
}
