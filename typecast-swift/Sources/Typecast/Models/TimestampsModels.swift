import Foundation

// MARK: - Timestamp TTS Segment Types

/// A single word-level alignment segment returned by the timestamps endpoint.
public struct AlignmentSegmentWord: Codable, Equatable, Sendable {
    /// The word text.
    public let text: String
    /// Start time in seconds.
    public let start: Double
    /// End time in seconds.
    public let end: Double

    public init(text: String, start: Double, end: Double) {
        self.text = text
        self.start = start
        self.end = end
    }
}

/// A single character-level alignment segment returned by the timestamps endpoint.
public struct AlignmentSegmentCharacter: Codable, Equatable, Sendable {
    /// The character text.
    public let text: String
    /// Start time in seconds.
    public let start: Double
    /// End time in seconds.
    public let end: Double

    public init(text: String, start: Double, end: Double) {
        self.text = text
        self.start = start
        self.end = end
    }
}

// MARK: - TTS with Timestamps Request

/// Text-to-Speech request for the `/v1/text-to-speech/with-timestamps` endpoint.
public struct TTSRequestWithTimestamps: Codable, Sendable {
    /// Voice ID in format 'tc_' followed by a unique identifier.
    public var voiceId: String
    /// Text to convert to speech (max 2000 characters).
    public var text: String
    /// Voice model to use.
    public var model: TTSModel
    /// Language code (ISO 639-3). If not provided, will be auto-detected.
    public var language: LanguageCode?
    /// Emotion and style settings for the generated speech.
    public var prompt: TTSPrompt?
    /// Audio output settings.
    public var output: OutputSettings?
    /// Random seed for reproducible results.
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
        output: OutputSettings? = nil,
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

    /// Validates the request parameters before sending.
    /// - Throws: `TimestampError.invalidRequest` if any required field is missing or invalid.
    public func validate() throws {
        let trimmedVoiceId = voiceId.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedText = text.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmedVoiceId.isEmpty {
            throw TimestampError.invalidRequest("voice_id is required")
        }
        if trimmedText.isEmpty {
            throw TimestampError.invalidRequest("text is required")
        }
        if trimmedText.count > 2000 {
            throw TimestampError.invalidRequest("text must not exceed 2000 characters")
        }
    }
}

// MARK: - TTS with Timestamps Response

/// Response from the `/v1/text-to-speech/with-timestamps` endpoint.
public struct TTSWithTimestampsResponse: Codable, Sendable {
    /// Base64-encoded audio data.
    public let audio: String
    /// Audio format string (e.g. "wav", "mp3").
    public let audioFormat: String
    /// Total audio duration in seconds.
    public let audioDuration: Double
    /// Word-level alignment segments (present when granularity includes "word").
    public let words: [AlignmentSegmentWord]?
    /// Character-level alignment segments (present when granularity includes "character").
    public let characters: [AlignmentSegmentCharacter]?

    enum CodingKeys: String, CodingKey {
        case audio
        case audioFormat = "audio_format"
        case audioDuration = "audio_duration"
        case words
        case characters
    }

    public init(
        audio: String,
        audioFormat: String,
        audioDuration: Double,
        words: [AlignmentSegmentWord]? = nil,
        characters: [AlignmentSegmentCharacter]? = nil
    ) {
        self.audio = audio
        self.audioFormat = audioFormat
        self.audioDuration = audioDuration
        self.words = words
        self.characters = characters
    }

    // MARK: - Audio helpers

    /// Decodes the base64-encoded `audio` field into raw bytes.
    /// - Throws: `TimestampError.invalidBase64` when the `audio` string is not valid base64.
    public func audioBytes() throws -> Data {
        guard let data = Data(base64Encoded: audio) else {
            throw TimestampError.invalidBase64
        }
        return data
    }

    /// Writes the decoded audio bytes to the given file URL.
    /// - Parameter url: Destination file URL.
    /// - Throws: `TimestampError.invalidBase64` when `audio` is not valid base64, or a file-system error.
    public func saveAudio(to url: URL) throws {
        try audioBytes().write(to: url)
    }

    // MARK: - Caption generation

    /// Returns the alignment data formatted as an SRT document.
    /// - Throws: `TimestampError.noAlignmentSegments` when neither `words` nor `characters` contains usable data.
    public func toSrt() throws -> String {
        let (segs, wordMode) = try pickSegments()
        let cues = CaptioningHelpers.groupIntoCues(segments: segs, wordMode: wordMode)
        guard !cues.isEmpty else {
            throw TimestampError.noAlignmentSegments
        }
        return CaptioningHelpers.buildSrt(cues: cues)
    }

    /// Returns the alignment data formatted as a WebVTT document.
    /// - Throws: `TimestampError.noAlignmentSegments` when neither `words` nor `characters` contains usable data.
    public func toVtt() throws -> String {
        let (segs, wordMode) = try pickSegments()
        let cues = CaptioningHelpers.groupIntoCues(segments: segs, wordMode: wordMode)
        guard !cues.isEmpty else {
            throw TimestampError.noAlignmentSegments
        }
        return CaptioningHelpers.buildVtt(cues: cues)
    }

    // MARK: - Private

    private func pickSegments() throws -> ([CaptioningHelpers.Segment], Bool) {
        // Prefer words when there are >= 2 entries (single-word response falls through to chars).
        if let w = words, w.count >= 2 {
            return (w.map { CaptioningHelpers.Segment(text: $0.text, start: $0.start, end: $0.end) }, true)
        }
        if let c = characters, !c.isEmpty {
            return (c.map { CaptioningHelpers.Segment(text: $0.text, start: $0.start, end: $0.end) }, false)
        }
        if let w = words, w.count == 1 {
            return (w.map { CaptioningHelpers.Segment(text: $0.text, start: $0.start, end: $0.end) }, true)
        }
        throw TimestampError.noAlignmentSegments
    }
}

// MARK: - Errors

/// Errors thrown by the timestamp TTS helpers.
public enum TimestampError: Error, Equatable {
    /// Neither `words` nor `characters` returned usable alignment data.
    case noAlignmentSegments
    /// An unsupported granularity string was provided.
    case invalidGranularity(String)
    /// The `audio` field contains a string that is not valid base64.
    case invalidBase64
    /// The request parameters are invalid.
    case invalidRequest(String)
}
