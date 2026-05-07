import Foundation

/// Metadata for a quick-cloned custom voice returned by POST /v1/voices/clone.
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

    public enum CodingKeys: String, CodingKey {
        case voiceId = "voice_id"
        case name
        case model
    }
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
