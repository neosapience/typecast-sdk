import Foundation

// MARK: - Voice Enums

/// Gender classification for voices
public enum GenderEnum: String, Codable, Sendable {
    case male
    case female
}

/// Age group classification for voices
public enum AgeEnum: String, Codable, Sendable {
    case child
    case teenager
    case youngAdult = "young_adult"
    case middleAge = "middle_age"
    case elder
}

/// Use case categories for voices
public enum UseCaseEnum: String, Codable, Sendable {
    case announcer = "Announcer"
    case anime = "Anime"
    case audiobook = "Audiobook"
    case conversational = "Conversational"
    case documentary = "Documentary"
    case eLearning = "E-learning"
    case rapper = "Rapper"
    case game = "Game"
    case tiktokReels = "Tiktok/Reels"
    case news = "News"
    case podcast = "Podcast"
    case voicemail = "Voicemail"
    case ads = "Ads"
}

// MARK: - Voice Response Models

/// V1 Voice response (deprecated, use VoiceV2 instead)
public struct Voice: Codable, Sendable {
    public let voiceId: String
    public let voiceName: String
    public let model: TTSModel
    public let emotions: [String]
    
    enum CodingKeys: String, CodingKey {
        case voiceId = "voice_id"
        case voiceName = "voice_name"
        case model
        case emotions
    }
}

/// Model information with supported emotions
public struct ModelInfo: Codable, Sendable {
    /// TTS model version (e.g., ssfm-v21, ssfm-v30)
    public let version: TTSModel
    /// List of supported emotions for this model
    public let emotions: [String]
}

/// V2 Voice response with enhanced metadata
public struct VoiceV2: Codable, Sendable {
    /// Unique voice identifier
    public let voiceId: String
    /// Human-readable name of the voice
    public let voiceName: String
    /// List of supported TTS models with their available emotions
    public let models: [ModelInfo]
    /// Voice gender classification
    public let gender: GenderEnum?
    /// Voice age group classification
    public let age: AgeEnum?
    /// List of use case categories this voice is suitable for
    public let useCases: [String]?
    
    enum CodingKeys: String, CodingKey {
        case voiceId = "voice_id"
        case voiceName = "voice_name"
        case models
        case gender
        case age
        case useCases = "use_cases"
    }
}

// MARK: - Voice Filter

/// Filter options for V2 voices endpoint
public struct VoicesV2Filter: Sendable {
    /// Filter by TTS model
    public var model: TTSModel?
    /// Filter by gender
    public var gender: GenderEnum?
    /// Filter by age group
    public var age: AgeEnum?
    /// Filter by use case
    public var useCases: UseCaseEnum?
    
    public init(
        model: TTSModel? = nil,
        gender: GenderEnum? = nil,
        age: AgeEnum? = nil,
        useCases: UseCaseEnum? = nil
    ) {
        self.model = model
        self.gender = gender
        self.age = age
        self.useCases = useCases
    }
    
    /// Convert to query parameters dictionary
    func toQueryParams() -> [String: String] {
        var params: [String: String] = [:]
        if let model = model {
            params["model"] = model.rawValue
        }
        if let gender = gender {
            params["gender"] = gender.rawValue
        }
        if let age = age {
            params["age"] = age.rawValue
        }
        if let useCases = useCases {
            params["use_cases"] = useCases.rawValue
        }
        return params
    }
}
