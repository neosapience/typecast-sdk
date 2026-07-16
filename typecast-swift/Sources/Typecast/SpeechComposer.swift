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

struct ComposeSegment: Encodable {
  let type: String
  var voiceId: String?
  var text: String?
  var model: TTSModel?
  var language: LanguageCode?
  var prompt: TTSPrompt?
  var output: OutputSettings?
  var seed: Int?
  var durationSeconds: Double?

  enum CodingKeys: String, CodingKey {
    case type, text, model, language, prompt, output, seed
    case voiceId = "voice_id"
    case durationSeconds = "duration_seconds"
  }

  static func tts(_ request: TTSRequest) -> Self {
    Self(
      type: "tts", voiceId: request.voiceId, text: request.text, model: request.model,
      language: request.language, prompt: request.prompt, output: request.output, seed: request.seed
    )
  }

  static func pause(_ seconds: Double) -> Self {
    Self(type: "pause", durationSeconds: seconds)
  }
}

struct ComposeRequest: Encodable {
  let segments: [ComposeSegment]
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

  public func say(_ text: String, overrides: ComposerSettings = ComposerSettings())
    -> SpeechComposer
  {
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
    guard
      plan.contains(where: {
        if case .speech = $0 { return true }
        return false
      })
    else {
      throw TypecastError.validationError("at least one speech segment is required")
    }

    let outputFormat = defaultSettings.output?.audioFormat ?? .wav
    var segments: [ComposeSegment] = []
    for part in plan {
      switch part {
      case .pause(let seconds):
        segments.append(.pause(seconds))

      case .speech(let text, let settings):
        segments.append(.tts(try request(text: text, settings: settings, format: outputFormat)))
      }
    }
    return try await client.composeTextToSpeech(segments)
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
              throw TypecastError.validationError(
                "voiceId is required for composed speech segments")
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

  private func request(text: String, settings: ComposerSettings, format: AudioFormat) throws
    -> TTSRequest
  {
    return TTSRequest(
      voiceId: settings.voiceId!,
      text: text,
      model: settings.model!,
      language: settings.language,
      prompt: settings.prompt,
      output: mergeOutput(settings.output, OutputSettings(audioFormat: format)),
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

extension TypecastClient {
  public func composeSpeech() -> SpeechComposer {
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

private func mergeSettings(_ base: ComposerSettings, _ override: ComposerSettings)
  -> ComposerSettings
{
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
