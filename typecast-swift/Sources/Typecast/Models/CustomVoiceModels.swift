import Foundation

/// Metadata for a custom voice (created via instant cloning) returned by POST /v1/custom-voices/instant-clone.
///
/// `voiceId` carries the "uc_" prefix and can be passed directly as
/// `voiceId` in `textToSpeech` calls.
public struct CustomVoice: Codable, Equatable, Sendable {
    /// The unique identifier for the cloned voice (e.g. "uc_abc123").
    public let voiceId: String
    /// Human-readable display name of the cloned voice.
    public let name: String
    /// The TTS model the voice was cloned for (e.g. "ssfm-v30").
    public let model: String
    public let source: String?
    public let status: String?
    public let error: String?
    public let createdAt: String?

    public init(voiceId: String, name: String, model: String, source: String? = nil, status: String? = nil, error: String? = nil, createdAt: String? = nil) {
        self.voiceId = voiceId; self.name = name; self.model = model
        self.source = source; self.status = status; self.error = error; self.createdAt = createdAt
    }

    public enum CodingKeys: String, CodingKey {
        case voiceId = "voice_id"
        case name
        case model
        case source, status, error
        case createdAt = "created_at"
    }
}

/** An audio sample for professional voice cloning. */
public struct CustomVoiceSample: Sendable {
    public let filename: String
    public let audio: Data
    public init(filename: String, audio: Data) { self.filename = filename; self.audio = audio }
}

/// Limit constants for the quick-voice-cloning endpoint.
///
/// These mirror the server-side validation rules in typecast-api so that
/// invalid requests are rejected locally before any network round-trip.
public enum QuickCloningLimits {
    /// Maximum audio file size accepted by the cloneVoice endpoint (25 MB).
    public static let cloningMaxFileSize: Int = 25 * 1024 * 1024
    /// Minimum allowed length for the custom voice name (1 character).
    public static let nameMinLength: Int = 1
    /// Maximum allowed length for the custom voice name (30 characters).
    public static let nameMaxLength: Int = 30
}
