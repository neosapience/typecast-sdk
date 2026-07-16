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

    do {
      _ = try await client.composeSpeech().pause(0.25).generate()
      XCTFail("expected error")
    } catch let TypecastError.validationError(message) {
      XCTAssertTrue(message.contains("at least one speech segment"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }

    do {
      _ = try await client.composeSpeech()
        .defaults(ComposerSettings(voiceId: "voice"))
        .say("Hello")
        .generate()
      XCTFail("expected error")
    } catch let TypecastError.validationError(message) {
      XCTAssertTrue(message.contains("model is required"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }
  }

  func testComposeSpeechSendsExplicitPauseAndUsesResponseDefaults() async throws {
    MockURLProtocol.requestHandler = { req in
      return (self.httpResponse(url: req.url!, status: 200), Data("audio".utf8))
    }

    let response = try await client.composeSpeech()
      .defaults(ComposerSettings(voiceId: "voice", model: .ssfmV30))
      .pause(0.25)
      .say("Hello")
      .generate()

    let body = try XCTUnwrap(MockURLProtocol.lastBody)
    let payload = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
    let segments = try XCTUnwrap(payload["segments"] as? [[String: Any]])
    XCTAssertEqual(segments.first?["type"] as? String, "pause")
    XCTAssertEqual(segments.first?["duration_seconds"] as? Double, 0.25)
    XCTAssertEqual(response.format, .wav)
    XCTAssertEqual(response.duration, 0)
  }

  func testComposeSpeechPropagatesHttpError() async {
    MockURLProtocol.requestHandler = { req in
      return (
        self.httpResponse(url: req.url!, status: 422),
        #"{"detail":"invalid segments"}"#.data(using: .utf8)
      )
    }

    do {
      _ = try await client.composeSpeech()
        .defaults(ComposerSettings(voiceId: "voice", model: .ssfmV30))
        .say("Hello")
        .generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      XCTAssertEqual(error.statusCode, 422)
    } catch {
      XCTFail("unexpected error: \(error)")
    }
  }

  func testComposeSpeechRejectsNonHttpResponse() async {
    MockURLProtocol.rawResponseHandler = { req in
      return (
        URLResponse(
          url: req.url!, mimeType: nil, expectedContentLength: 0, textEncodingName: nil),
        nil
      )
    }

    do {
      _ = try await client.composeSpeech()
        .defaults(ComposerSettings(voiceId: "voice", model: .ssfmV30))
        .say("Hello")
        .generate()
      XCTFail("expected error")
    } catch TypecastError.invalidResponse {
      // Expected.
    } catch {
      XCTFail("unexpected error: \(error)")
    }
  }

  func testParsePauseMarkupIsLenientForInvalidTokens() {
    XCTAssertEqual(
      parsePauseMarkup("a<|0.3s|>b<|abc|>c<|3s|>"),
      [.text("a"), .pause(0.3), .text("b<|abc|>c"), .pause(3)]
    )
    XCTAssertEqual(parsePauseMarkup("hello<|0.3s"), [.text("hello<|0.3s")])
    XCTAssertEqual(parsePauseMarkup("a<|xs|>b<|1..2s|>c"), [.text("a<|xs|>b<|1..2s|>c")])
  }
}
