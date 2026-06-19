import Foundation

// MARK: - Streaming Output Settings

/// Audio output settings supported by the streaming TTS endpoint.
///
/// Unlike `OutputSettings`, this struct intentionally omits `volume`.
/// The `/v1/text-to-speech/stream` endpoint supports `target_lufs`.
public struct OutputStream: Codable, Sendable {
    /// Target loudness in LUFS (-70 to 0)
    public var targetLufs: Double?
    /// Audio pitch adjustment in semitones (-12 to +12, default: 0)
    public var audioPitch: Int?
    /// Audio tempo/speed multiplier (0.5 to 2.0, default: 1.0)
    public var audioTempo: Double?
    /// Audio output format (wav or mp3, default: wav)
    public var audioFormat: AudioFormat?

    enum CodingKeys: String, CodingKey {
        case targetLufs = "target_lufs"
        case audioPitch = "audio_pitch"
        case audioTempo = "audio_tempo"
        case audioFormat = "audio_format"
    }

    private static func isValidTargetLufs(_ value: Double?) -> Bool {
        guard let value else { return true }
        return value.isFinite && value >= -70.0 && value <= 0.0
    }

    public init(
        targetLufs: Double? = nil,
        audioPitch: Int? = nil,
        audioTempo: Double? = nil,
        audioFormat: AudioFormat? = nil
    ) {
        precondition(Self.isValidTargetLufs(targetLufs))
        self.targetLufs = targetLufs
        self.audioPitch = audioPitch
        self.audioTempo = audioTempo
        self.audioFormat = audioFormat
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let targetLufs = try container.decodeIfPresent(Double.self, forKey: .targetLufs)
        guard Self.isValidTargetLufs(targetLufs) else {
            throw DecodingError.dataCorruptedError(
                forKey: .targetLufs,
                in: container,
                debugDescription: "targetLufs must be between -70 and 0"
            )
        }
        self.targetLufs = targetLufs
        self.audioPitch = try container.decodeIfPresent(Int.self, forKey: .audioPitch)
        self.audioTempo = try container.decodeIfPresent(Double.self, forKey: .audioTempo)
        self.audioFormat = try container.decodeIfPresent(AudioFormat.self, forKey: .audioFormat)
    }
}

// MARK: - Streaming TTS Request

/// Text-to-Speech request parameters for the streaming endpoint
/// (`POST /v1/text-to-speech/stream`).
///
/// Mirrors `TTSRequest` but uses `OutputStream` for the `output` field
/// because the streaming endpoint does not accept volume settings.
public struct TTSRequestStream: Codable, Sendable {
    /// Voice ID in format 'tc_' followed by a unique identifier.
    /// Browse available API voices at https://typecast.ai/developers/api/voices.
    public var voiceId: String
    /// Text to convert to speech (max 2000 characters)
    public var text: String
    /// Voice model to use
    public var model: TTSModel
    /// Language code (ISO 639-3). If not provided, will be auto-detected
    public var language: LanguageCode?
    /// Emotion and style settings for the generated speech
    public var prompt: TTSPrompt?
    /// Streaming-specific audio output settings
    public var output: OutputStream?
    /// Random seed for reproducible results
    public var seed: Int?

    enum CodingKeys: String, CodingKey {
        case voiceId = "voice_id"
        case text
        case model
        case language
        case prompt
        case output
        case seed
    }

    public init(
        voiceId: String,
        text: String,
        model: TTSModel,
        language: LanguageCode? = nil,
        prompt: TTSPrompt? = nil,
        output: OutputStream? = nil,
        seed: Int? = nil
    ) {
        self.voiceId = voiceId
        self.text = text
        self.model = model
        self.language = language
        self.prompt = prompt
        self.output = output
        self.seed = seed
    }
}
