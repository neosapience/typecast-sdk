import Foundation

// MARK: - Convenience Extensions

extension TypecastClient {
  /// Simple text-to-speech with minimal parameters
  /// - Parameters:
  ///   - text: Text to convert to speech
  ///   - voiceId: Voice ID to use
  ///   - model: TTS model to use (default: ssfm-v30)
  /// - Returns: TTSResponse containing audio data
  public func speak(
    _ text: String,
    voiceId: String,
    model: TTSModel = .ssfmV30
  ) async throws -> TTSResponse {
    let request = TTSRequest(voiceId: voiceId, text: text, model: model)
    return try await textToSpeech(request)
  }

  /// Text-to-speech with emotion preset
  /// - Parameters:
  ///   - text: Text to convert to speech
  ///   - voiceId: Voice ID to use
  ///   - model: TTS model to use
  ///   - emotion: Emotion preset to apply
  ///   - intensity: Emotion intensity (0.0 to 2.0)
  /// - Returns: TTSResponse containing audio data
  public func speak(
    _ text: String,
    voiceId: String,
    model: TTSModel = .ssfmV30,
    emotion: EmotionPreset,
    intensity: Double = 1.0
  ) async throws -> TTSResponse {
    let prompt: TTSPrompt
    if model == .ssfmV30 {
      prompt = .preset(PresetPrompt(emotionPreset: emotion, emotionIntensity: intensity))
    } else {
      prompt = .basic(Prompt(emotionPreset: emotion, emotionIntensity: intensity))
    }

    let request = TTSRequest(voiceId: voiceId, text: text, model: model, prompt: prompt)
    return try await textToSpeech(request)
  }
}
