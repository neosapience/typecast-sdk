import XCTest
@testable import Typecast

final class SpeechComposerTests: TypecastClientMockTestCase {

  func testComposeSpeechComposesWavAndMergesOverrides() async throws {
    var responses = [
      makeTestWav(samples: [0, 1000, 2000, 0], sampleRate: 1000),
      makeTestWav(samples: [0, -1000, -2000, 0], sampleRate: 1000)
    ]
    var bodies: [[String: Any]] = []

    MockURLProtocol.requestHandler = { req in
      XCTAssertEqual(req.url?.path, "/v1/text-to-speech")
      let body = try XCTUnwrap(MockURLProtocol.lastBody)
      let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
      bodies.append(json)
      return (
        self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": "audio/wav"]),
        responses.removeFirst()
      )
    }

    let response = try await client
      .composeSpeech()
      .defaults(
        ComposerSettings(
          voiceId: "voice-a",
          model: .ssfmV30,
          language: .english,
          prompt: .basic(Prompt(emotionPreset: .happy, emotionIntensity: 1.0)),
          output: OutputSettings(audioPitch: 1, audioFormat: .wav),
          seed: 123
        )
      )
      .say(
        "Hello<|0.001s|>world",
        overrides: ComposerSettings(
          voiceId: "voice-b",
          prompt: .preset(PresetPrompt(emotionPreset: .sad, emotionIntensity: 0.5)),
          output: OutputSettings(volume: 80, audioTempo: 1.1)
        )
      )
      .generate()

    XCTAssertEqual(bodies.count, 2)
    XCTAssertEqual(bodies[0]["text"] as? String, "Hello")
    XCTAssertEqual(bodies[1]["text"] as? String, "world")
    XCTAssertEqual(bodies[0]["voice_id"] as? String, "voice-b")
    let output = try XCTUnwrap(bodies[0]["output"] as? [String: Any])
    XCTAssertEqual(output["audio_format"] as? String, "wav")
    XCTAssertEqual(output["audio_pitch"] as? Int, 1)
    XCTAssertEqual(output["audio_tempo"] as? Double, 1.1)
    XCTAssertEqual(output["volume"] as? Int, 80)
    XCTAssertEqual(bodies[0]["seed"] as? Int, 123)
    XCTAssertNotNil(bodies[0]["prompt"])

    XCTAssertEqual(response.format, AudioFormat.wav)
    XCTAssertEqual(samples(fromWav: response.audioData), [1000, 2000, 0, -1000, -2000])
    XCTAssertEqual(response.duration, 0.005, accuracy: 0.0001)
  }

  func testComposeSpeechValidatesBeforeNetwork() async {
    MockURLProtocol.requestHandler = { req in
      XCTFail("network should not be called: \(req)")
      return (self.httpResponse(url: req.url!, status: 500), nil)
    }

    do {
      _ = try await client.composeSpeech().say("Hello").generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("voiceId is required"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }

    do {
      _ = try await client.composeSpeech().pause(0).generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("pause seconds must be greater than 0"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }

    do {
      _ = try await client
        .composeSpeech()
        .defaults(ComposerSettings(voiceId: "voice-a"))
        .say("Hello")
        .generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("model is required"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }

    do {
      _ = try await client
        .composeSpeech()
        .defaults(ComposerSettings(voiceId: "voice-a", model: .ssfmV30))
        .pause(0.1)
        .say("Hello")
        .generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("pause cannot be the first"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }

    do {
      _ = try await client.composeSpeech().generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("at least one speech segment"))
    } catch {
      XCTFail("unexpected error: \(error)")
    }
  }

  func testParsePauseMarkupIsLenientForInvalidTokens() {
    let parts = parsePauseMarkup("a<|0.3s|>b<|abc|>c<|s|>d<|.3s|>e<|3.s|>f<|3..1s|>g<|3xs|>h<|3s|>")
    XCTAssertEqual(
      parts,
      [
        SpeechPart.text("a"),
        SpeechPart.pause(0.3),
        SpeechPart.text("b<|abc|>c<|s|>d<|.3s|>e<|3.s|>f<|3..1s|>g<|3xs|>h"),
        SpeechPart.pause(3)
      ]
    )
  }

  func testParsePauseMarkupKeepsUnclosedTokenAsText() {
    XCTAssertEqual(parsePauseMarkup("hello<|0.3s"), [.text("hello<|0.3s")])
  }

  func testComposeSpeechRejectsBadWavMismatchedSpecsAndMp3() async throws {
    for wav in [
      Data("not wav".utf8),
      makeTestWav(samples: [100], sampleRate: 1000, audioFormat: 2),
      makeTestWavWithShortFmtChunk(),
      makeTestWavWithInvalidChunkSize(),
      makeTestWavWithDataOnly(),
      makeTestWavWithoutData(extraChunk: true),
      makeTestWavWithoutData(extraChunk: false)
    ] {
      MockURLProtocol.requestHandler = { req in
        (self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": "audio/wav"]), wav)
      }

      do {
        _ = try await client
          .composeSpeech()
          .defaults(ComposerSettings(voiceId: "voice-a", model: .ssfmV30))
          .say("Hello")
          .generate()
        XCTFail("expected error")
      } catch let error as TypecastError {
        guard case .validationError(let message) = error else {
          XCTFail("wrong error: \(error)")
          return
        }
        XCTAssertTrue(message.contains("unsupported WAV data") || message.contains("only mono 16-bit PCM WAV"))
      }
    }

    var mismatchResponses = [
      makeTestWav(samples: [1000], sampleRate: 1000),
      makeTestWav(samples: [1000], sampleRate: 2000)
    ]
    MockURLProtocol.requestHandler = { req in
      (self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": "audio/wav"]), mismatchResponses.removeFirst())
    }
    do {
      _ = try await client
        .composeSpeech()
        .defaults(ComposerSettings(voiceId: "voice-a", model: .ssfmV30))
        .say("one<|0.001s|>two")
        .generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("same PCM format"))
    }

    MockURLProtocol.requestHandler = { req in
      let body = try XCTUnwrap(MockURLProtocol.lastBody)
      let json = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
      let output = try XCTUnwrap(json["output"] as? [String: Any])
      XCTAssertEqual(output["audio_format"] as? String, "wav")
      return (
        self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": "audio/wav"]),
        self.makeTestWav(samples: [1000], sampleRate: 1000)
      )
    }
    do {
      _ = try await client
        .composeSpeech()
        .defaults(
          ComposerSettings(
            voiceId: "voice-a",
            model: .ssfmV30,
            output: OutputSettings(audioFormat: .mp3)
          )
        )
        .say("Hello")
        .generate()
      XCTFail("expected error")
    } catch let error as TypecastError {
      guard case .validationError(let message) = error else {
        XCTFail("wrong error: \(error)")
        return
      }
      XCTAssertTrue(message.contains("MP3 conversion is app-level responsibility"))
    }
  }

  func testComposeSpeechTrimsAllZeroAudioToEmptyWav() async throws {
    MockURLProtocol.requestHandler = { req in
      (
        self.httpResponse(url: req.url!, status: 200, headers: ["Content-Type": "audio/wav"]),
        self.makeTestWav(samples: [0, 0], sampleRate: 1000)
      )
    }

    let response = try await client
      .composeSpeech()
      .defaults(ComposerSettings(voiceId: "voice-a", model: .ssfmV30))
      .say("Silence")
      .generate()

    XCTAssertEqual(samples(fromWav: response.audioData), [])
    XCTAssertEqual(response.duration, 0)
  }

  private func makeTestWav(samples: [Int16], sampleRate: UInt32, audioFormat: UInt16 = 1) -> Data {
    var data = Data()
    data.append(contentsOf: "RIFF".utf8)
    data.append(UInt32(36 + samples.count * 2).littleEndianData)
    data.append(contentsOf: "WAVE".utf8)
    data.append(contentsOf: "fmt ".utf8)
    data.append(UInt32(16).littleEndianData)
    data.append(audioFormat.littleEndianData)
    data.append(UInt16(1).littleEndianData)
    data.append(sampleRate.littleEndianData)
    data.append((sampleRate * 2).littleEndianData)
    data.append(UInt16(2).littleEndianData)
    data.append(UInt16(16).littleEndianData)
    data.append(contentsOf: "data".utf8)
    data.append(UInt32(samples.count * 2).littleEndianData)
    samples.forEach { data.append($0.littleEndianData) }
    return data
  }

  private func makeTestWavWithoutData(extraChunk: Bool) -> Data {
    var data = Data()
    let payloadLength: UInt32 = extraChunk ? 12 : 8
    data.append(contentsOf: "RIFF".utf8)
    data.append(UInt32(28 + payloadLength).littleEndianData)
    data.append(contentsOf: "WAVE".utf8)
    data.append(contentsOf: "fmt ".utf8)
    data.append(UInt32(16).littleEndianData)
    data.append(UInt16(1).littleEndianData)
    data.append(UInt16(1).littleEndianData)
    data.append(UInt32(1000).littleEndianData)
    data.append(UInt32(2000).littleEndianData)
    data.append(UInt16(2).littleEndianData)
    data.append(UInt16(16).littleEndianData)
    if extraChunk {
      data.append(contentsOf: "JUNK".utf8)
      data.append(UInt32(4).littleEndianData)
      data.append(UInt32(123).littleEndianData)
    }
    return data
  }

  private func makeTestWavWithInvalidChunkSize() -> Data {
    var data = makeTestWav(samples: [], sampleRate: 1000)
    data.replaceSubrange(36..<40, with: Data("JUNK".utf8))
    data.replaceSubrange(40..<44, with: UInt32(1000).littleEndianData)
    return data
  }

  private func makeTestWavWithShortFmtChunk() -> Data {
    var data = Data()
    data.append(contentsOf: "RIFF".utf8)
    data.append(UInt32(12).littleEndianData)
    data.append(contentsOf: "WAVE".utf8)
    data.append(contentsOf: "fmt ".utf8)
    data.append(UInt32(4).littleEndianData)
    data.append(UInt32(1).littleEndianData)
    return data
  }

  private func makeTestWavWithDataOnly() -> Data {
    var data = Data()
    data.append(contentsOf: "RIFF".utf8)
    data.append(UInt32(40).littleEndianData)
    data.append(contentsOf: "WAVE".utf8)
    data.append(contentsOf: "data".utf8)
    data.append(UInt32(2).littleEndianData)
    data.append(Int16(100).littleEndianData)
    return data
  }

  private func samples(fromWav data: Data) -> [Int16] {
    let bytes = [UInt8](data)
    let dataOffset = bytes.windows(of: Array("data".utf8)).first! + 8
    return stride(from: dataOffset, to: bytes.count, by: 2).map {
      Int16(littleEndian: Int16(bitPattern: UInt16(bytes[$0]) | (UInt16(bytes[$0 + 1]) << 8)))
    }
  }
}

private extension FixedWidthInteger {
  var littleEndianData: Data {
    var value = self.littleEndian
    return Data(bytes: &value, count: MemoryLayout<Self>.size)
  }
}

private extension Array where Element == UInt8 {
  func windows(of needle: [UInt8]) -> [Int] {
    guard count >= needle.count else { return [] }
    return (0...(count - needle.count)).filter { Array(self[$0..<($0 + needle.count)]) == needle }
  }
}
