import Foundation

// MARK: - TTS Model Types

/// TTS model version to use for speech synthesis
public enum TTSModel: String, Codable, Sendable {
    /// Latest model with improved prosody and additional emotion presets (recommended)
    case ssfmV30 = "ssfm-v30"
    /// Stable production model with proven reliability
    case ssfmV21 = "ssfm-v21"
}

/// Language code following ISO 639-3 standard
public enum LanguageCode: String, Codable, Sendable {
    // Common languages
    case english = "eng"
    case korean = "kor"
    case japanese = "jpn"
    case spanish = "spa"
    case german = "deu"
    case french = "fra"
    case italian = "ita"
    case polish = "pol"
    case dutch = "nld"
    case russian = "rus"
    case greek = "ell"
    case tamil = "tam"
    case tagalog = "tgl"
    case finnish = "fin"
    case chinese = "zho"
    case slovak = "slk"
    case arabic = "ara"
    case croatian = "hrv"
    case ukrainian = "ukr"
    case indonesian = "ind"
    case danish = "dan"
    case swedish = "swe"
    case malay = "msa"
    case czech = "ces"
    case portuguese = "por"
    case bulgarian = "bul"
    case romanian = "ron"
    // ssfm-v30 additional languages
    case bengali = "ben"
    case hindi = "hin"
    case hungarian = "hun"
    case minNan = "nan"
    case norwegian = "nor"
    case punjabi = "pan"
    case thai = "tha"
    case turkish = "tur"
    case vietnamese = "vie"
    case cantonese = "yue"
}

/// Emotion preset types for speech synthesis
public enum EmotionPreset: String, Codable, Sendable {
    case normal
    case happy
    case sad
    case angry
    /// ssfm-v30 only
    case whisper
    /// ssfm-v30 only
    case toneup
    /// ssfm-v30 only
    case tonedown
}

/// Audio output format
public enum AudioFormat: String, Codable, Sendable {
    case wav
    case mp3
}

// MARK: - Prompt Types

/// Basic emotion settings for ssfm-v21 model
public struct Prompt: Codable, Sendable {
    /// Emotion preset for the voice (default: normal)
    public var emotionPreset: EmotionPreset?
    /// Emotion intensity (0.0 to 2.0, default: 1.0)
    public var emotionIntensity: Double?
    
    enum CodingKeys: String, CodingKey {
        case emotionPreset = "emotion_preset"
        case emotionIntensity = "emotion_intensity"
    }
    
    public init(emotionPreset: EmotionPreset? = nil, emotionIntensity: Double? = nil) {
        self.emotionPreset = emotionPreset
        self.emotionIntensity = emotionIntensity
    }
}

/// Preset-based emotion control for ssfm-v30 model
public struct PresetPrompt: Codable, Sendable {
    /// Must be "preset" for preset-based emotion control
    public let emotionType: String = "preset"
    /// Emotion preset to apply (default: normal)
    public var emotionPreset: EmotionPreset?
    /// Emotion intensity (0.0 to 2.0, default: 1.0)
    public var emotionIntensity: Double?
    
    enum CodingKeys: String, CodingKey {
        case emotionType = "emotion_type"
        case emotionPreset = "emotion_preset"
        case emotionIntensity = "emotion_intensity"
    }
    
    public init(emotionPreset: EmotionPreset? = nil, emotionIntensity: Double? = nil) {
        self.emotionPreset = emotionPreset
        self.emotionIntensity = emotionIntensity
    }
}

/// Context-aware emotion inference for ssfm-v30 model
public struct SmartPrompt: Codable, Sendable {
    /// Must be "smart" for context-aware emotion inference
    public let emotionType: String = "smart"
    /// Text that comes BEFORE the main text (max 2000 chars)
    public var previousText: String?
    /// Text that comes AFTER the main text (max 2000 chars)
    public var nextText: String?
    
    enum CodingKeys: String, CodingKey {
        case emotionType = "emotion_type"
        case previousText = "previous_text"
        case nextText = "next_text"
    }
    
    public init(previousText: String? = nil, nextText: String? = nil) {
        self.previousText = previousText
        self.nextText = nextText
    }
}

/// Union type for all prompt types
public enum TTSPrompt: Codable, Sendable {
    case basic(Prompt)
    case preset(PresetPrompt)
    case smart(SmartPrompt)
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .basic(let prompt):
            try container.encode(prompt)
        case .preset(let prompt):
            try container.encode(prompt)
        case .smart(let prompt):
            try container.encode(prompt)
        }
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        
        // Try to decode as SmartPrompt first
        if let smart = try? container.decode(SmartPrompt.self),
           smart.emotionType == "smart" {
            self = .smart(smart)
            return
        }
        
        // Try PresetPrompt
        if let preset = try? container.decode(PresetPrompt.self),
           preset.emotionType == "preset" {
            self = .preset(preset)
            return
        }
        
        // Default to basic Prompt
        let basic = try container.decode(Prompt.self)
        self = .basic(basic)
    }
}

// MARK: - Output Settings

/// Audio output settings for controlling the final audio characteristics
public struct OutputSettings: Codable, Sendable {
    /// Output volume (0-200, default: 100)
    public var volume: Int?
    /// Audio pitch adjustment in semitones (-12 to +12, default: 0)
    public var audioPitch: Int?
    /// Audio tempo/speed multiplier (0.5 to 2.0, default: 1.0)
    public var audioTempo: Double?
    /// Audio output format (wav or mp3, default: wav)
    public var audioFormat: AudioFormat?
    
    enum CodingKeys: String, CodingKey {
        case volume
        case audioPitch = "audio_pitch"
        case audioTempo = "audio_tempo"
        case audioFormat = "audio_format"
    }
    
    public init(
        volume: Int? = nil,
        audioPitch: Int? = nil,
        audioTempo: Double? = nil,
        audioFormat: AudioFormat? = nil
    ) {
        self.volume = volume
        self.audioPitch = audioPitch
        self.audioTempo = audioTempo
        self.audioFormat = audioFormat
    }
}

// MARK: - TTS Request/Response

/// Text-to-Speech request parameters
public struct TTSRequest: Codable, Sendable {
    /// Voice ID in format 'tc_' followed by a unique identifier
    public var voiceId: String
    /// Text to convert to speech (max 2000 characters)
    public var text: String
    /// Voice model to use
    public var model: TTSModel
    /// Language code (ISO 639-3). If not provided, will be auto-detected
    public var language: LanguageCode?
    /// Emotion and style settings for the generated speech
    public var prompt: TTSPrompt?
    /// Audio output settings
    public var output: OutputSettings?
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
}

/// Text-to-Speech response
public struct TTSResponse: Codable, Sendable {
    /// Generated audio data
    public let audioData: Data
    /// Audio duration in seconds
    public let duration: TimeInterval
    /// Audio format (wav or mp3)
    public let format: AudioFormat
    
    enum CodingKeys: String, CodingKey {
        case audioData = "audio_data"
        case duration
        case format
    }
    
    public init(audioData: Data, duration: TimeInterval, format: AudioFormat) {
        self.audioData = audioData
        self.duration = duration
        self.format = format
    }
}
