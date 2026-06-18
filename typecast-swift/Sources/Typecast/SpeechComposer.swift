import Foundation

public enum SpeechPart: Equatable, Sendable {
  case text(String)
  case pause(Double)
}

public struct ComposerSettings: Sendable {
  public var voiceId: String?
  public var model: TTSModel?
  public var language: LanguageCode?
  public var prompt: TTSPrompt?
  public var output: OutputSettings?
  public var seed: Int?

  public init(
    voiceId: String? = nil,
    model: TTSModel? = nil,
    language: LanguageCode? = nil,
    prompt: TTSPrompt? = nil,
    output: OutputSettings? = nil,
    seed: Int? = nil
  ) {
    self.voiceId = voiceId
    self.model = model
    self.language = language
    self.prompt = prompt
    self.output = output
    self.seed = seed
  }
}

private enum ComposerPart {
  case speech(String, ComposerSettings)
  case pause(Double)
}

private struct WAVSpec: Equatable {
  let sampleRate: UInt32
  let channels: UInt16
  let bitsPerSample: UInt16
}

private struct ParsedWAV {
  let spec: WAVSpec
  let samples: [Int16]
}

public final class SpeechComposer {
  private let client: TypecastClient
  private var defaultSettings = ComposerSettings()
  private var parts: [ComposerPart] = []

  init(client: TypecastClient) {
    self.client = client
  }

  public func defaults(_ settings: ComposerSettings) -> SpeechComposer {
    defaultSettings = mergeSettings(defaultSettings, settings)
    return self
  }

  public func say(_ text: String, overrides: ComposerSettings = ComposerSettings()) -> SpeechComposer {
    parts.append(.speech(text, mergeSettings(defaultSettings, overrides)))
    return self
  }

  /// Inserts silence between speech segments.
  ///
  /// `seconds` is a duration in seconds. Use `0.3` for 300 ms, `3` for three seconds.
  public func pause(_ seconds: Double) -> SpeechComposer {
    parts.append(.pause(seconds))
    return self
  }

  public func generate() async throws -> TTSResponse {
    let plan = try buildPlan()
    guard plan.contains(where: { if case .speech = $0 { return true }; return false }) else {
      throw TypecastError.validationError("at least one speech segment is required")
    }

    let outputFormat = defaultSettings.output?.audioFormat ?? .wav
    var wavSpec: WAVSpec?
    var outputSamples: [Int16] = []

    for part in plan {
      switch part {
      case .pause(let seconds):
        guard let spec = wavSpec else {
          throw TypecastError.validationError("pause cannot be the first composed part")
        }
        outputSamples.append(contentsOf: Array(repeating: 0, count: secondsToSamples(seconds, sampleRate: spec.sampleRate)))

      case .speech(let text, let settings):
        let response = try await client.textToSpeech(try request(text: text, settings: settings))
        let wav = try parseWAV(response.audioData)
        if let spec = wavSpec, spec != wav.spec {
          throw TypecastError.validationError("all composed WAV segments must use the same PCM format")
        }
        wavSpec = wav.spec
        outputSamples.append(contentsOf: trimSilence(wav.samples))
      }
    }

    let finalSpec = wavSpec!
    let wavData = encodeWAV(samples: outputSamples, spec: finalSpec)
    if outputFormat == .mp3 {
      throw TypecastError.validationError("MP3 conversion is app-level responsibility for composed speech")
    }
    return TTSResponse(
      audioData: wavData,
      duration: Double(outputSamples.count) / Double(finalSpec.sampleRate),
      format: .wav
    )
  }

  private func buildPlan() throws -> [ComposerPart] {
    var plan: [ComposerPart] = []
    for part in parts {
      switch part {
      case .pause(let seconds):
        guard seconds.isFinite, seconds > 0 else {
          throw TypecastError.validationError("pause seconds must be greater than 0")
        }
        plan.append(.pause(seconds))

      case .speech(let text, let settings):
        for parsed in parsePauseMarkup(text) {
          switch parsed {
          case .pause(let seconds):
            plan.append(.pause(seconds))
          case .text(let text):
            guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            guard let voiceId = settings.voiceId, !voiceId.isEmpty else {
              throw TypecastError.validationError("voiceId is required for composed speech segments")
            }
            guard settings.model != nil else {
              throw TypecastError.validationError("model is required for composed speech segments")
            }
            plan.append(.speech(text, settings))
          }
        }
      }
    }
    return plan
  }

  private func request(text: String, settings: ComposerSettings) throws -> TTSRequest {
    return TTSRequest(
      voiceId: settings.voiceId!,
      text: text,
      model: settings.model!,
      language: settings.language,
      prompt: settings.prompt,
      output: mergeOutput(settings.output, OutputSettings(audioFormat: .wav)),
      seed: settings.seed
    )
  }
}

public func parsePauseMarkup(_ text: String) -> [SpeechPart] {
  var parts: [SpeechPart] = []
  var lastEmit = text.startIndex
  var searchFrom = text.startIndex

  while let startRange = text.range(of: "<|", range: searchFrom..<text.endIndex) {
    let bodyStart = startRange.upperBound
    guard let endRange = text.range(of: "|>", range: bodyStart..<text.endIndex) else {
      break
    }

    let tokenBody = String(text[bodyStart..<endRange.lowerBound])
    if tokenBody.hasSuffix("s") {
      let secondsText = String(tokenBody.dropLast())
      if validSecondsLiteral(secondsText), let seconds = Double(secondsText) {
        if startRange.lowerBound > lastEmit {
          parts.append(.text(String(text[lastEmit..<startRange.lowerBound])))
        }
        parts.append(.pause(seconds))
        lastEmit = endRange.upperBound
        searchFrom = endRange.upperBound
        continue
      }
    }

    searchFrom = bodyStart
  }

  if lastEmit < text.endIndex {
    parts.append(.text(String(text[lastEmit..<text.endIndex])))
  }
  return parts
}

public extension TypecastClient {
  func composeSpeech() -> SpeechComposer {
    SpeechComposer(client: self)
  }
}

private func validSecondsLiteral(_ value: String) -> Bool {
  guard !value.isEmpty else { return false }
  let pieces = value.split(separator: ".", omittingEmptySubsequences: false)
  guard pieces.count <= 2, !pieces[0].isEmpty, pieces[0].allSatisfy(\.isNumber) else {
    return false
  }
  if pieces.count == 2 {
    return !pieces[1].isEmpty && pieces[1].allSatisfy(\.isNumber)
  }
  return true
}

private func mergeSettings(_ base: ComposerSettings, _ override: ComposerSettings) -> ComposerSettings {
  ComposerSettings(
    voiceId: override.voiceId ?? base.voiceId,
    model: override.model ?? base.model,
    language: override.language ?? base.language,
    prompt: override.prompt ?? base.prompt,
    output: mergeOutput(base.output, override.output),
    seed: override.seed ?? base.seed
  )
}

private func mergeOutput(_ base: OutputSettings?, _ override: OutputSettings?) -> OutputSettings? {
  guard base != nil || override != nil else { return nil }
  return OutputSettings(
    volume: override?.volume ?? base?.volume,
    targetLufs: override?.targetLufs ?? base?.targetLufs,
    audioPitch: override?.audioPitch ?? base?.audioPitch,
    audioTempo: override?.audioTempo ?? base?.audioTempo,
    audioFormat: override?.audioFormat ?? base?.audioFormat
  )
}

private func parseWAV(_ data: Data) throws -> ParsedWAV {
  let bytes = [UInt8](data)
  guard bytes.count >= 12,
    String(bytes: bytes[0..<4], encoding: .ascii) == "RIFF",
    String(bytes: bytes[8..<12], encoding: .ascii) == "WAVE"
  else {
    throw TypecastError.validationError("unsupported WAV data")
  }

  var offset = 12
  var spec: WAVSpec?
  var samples: [Int16]?
  while offset + 8 <= bytes.count {
    let chunkId = String(bytes: bytes[offset..<(offset + 4)], encoding: .ascii)
    let chunkSize = Int(readUInt32(bytes, offset + 4))
    let chunkDataOffset = offset + 8
    let chunkEnd = chunkDataOffset + chunkSize
    guard chunkEnd <= bytes.count else {
      throw TypecastError.validationError("unsupported WAV data")
    }

    if chunkId == "fmt " {
      guard chunkSize >= 16 else {
        throw TypecastError.validationError("unsupported WAV data")
      }
      let audioFormat = readUInt16(bytes, chunkDataOffset)
      let channels = readUInt16(bytes, chunkDataOffset + 2)
      let sampleRate = readUInt32(bytes, chunkDataOffset + 4)
      let bitsPerSample = readUInt16(bytes, chunkDataOffset + 14)
      guard audioFormat == 1, channels == 1, bitsPerSample == 16 else {
        throw TypecastError.validationError("only mono 16-bit PCM WAV is supported for composed speech")
      }
      spec = WAVSpec(sampleRate: sampleRate, channels: channels, bitsPerSample: bitsPerSample)
    } else if chunkId == "data" {
      samples = stride(from: chunkDataOffset, to: chunkEnd, by: 2).map {
        Int16(littleEndian: Int16(bitPattern: readUInt16(bytes, $0)))
      }
    }

    offset = chunkEnd + (chunkSize % 2)
  }

  guard let finalSpec = spec, let finalSamples = samples else {
    throw TypecastError.validationError("unsupported WAV data")
  }
  return ParsedWAV(spec: finalSpec, samples: finalSamples)
}

private func encodeWAV(samples: [Int16], spec: WAVSpec) -> Data {
  var data = Data()
  data.append(contentsOf: "RIFF".utf8)
  data.append(UInt32(36 + samples.count * 2).littleEndianData)
  data.append(contentsOf: "WAVE".utf8)
  data.append(contentsOf: "fmt ".utf8)
  data.append(UInt32(16).littleEndianData)
  data.append(UInt16(1).littleEndianData)
  data.append(spec.channels.littleEndianData)
  data.append(spec.sampleRate.littleEndianData)
  data.append((spec.sampleRate * UInt32(spec.channels) * 2).littleEndianData)
  data.append((spec.channels * 2).littleEndianData)
  data.append(spec.bitsPerSample.littleEndianData)
  data.append(contentsOf: "data".utf8)
  data.append(UInt32(samples.count * 2).littleEndianData)
  samples.forEach { data.append($0.littleEndianData) }
  return data
}

private func trimSilence(_ samples: [Int16]) -> [Int16] {
  var start = 0
  var end = samples.count
  while start < end, abs(Int(samples[start])) <= 0 {
    start += 1
  }
  while end > start, abs(Int(samples[end - 1])) <= 0 {
    end -= 1
  }
  return Array(samples[start..<end])
}

private func secondsToSamples(_ seconds: Double, sampleRate: UInt32) -> Int {
  Int((seconds * Double(sampleRate)).rounded())
}

private func readUInt16(_ bytes: [UInt8], _ offset: Int) -> UInt16 {
  UInt16(bytes[offset]) | (UInt16(bytes[offset + 1]) << 8)
}

private func readUInt32(_ bytes: [UInt8], _ offset: Int) -> UInt32 {
  UInt32(bytes[offset])
    | (UInt32(bytes[offset + 1]) << 8)
    | (UInt32(bytes[offset + 2]) << 16)
    | (UInt32(bytes[offset + 3]) << 24)
}

private extension FixedWidthInteger {
  var littleEndianData: Data {
    var value = self.littleEndian
    return Data(bytes: &value, count: MemoryLayout<Self>.size)
  }
}
