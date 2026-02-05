//! Data models for the Typecast API
//!
//! This module contains all the data structures used for API requests and responses.

use serde::{Deserialize, Serialize};

/// TTS model version to use for speech synthesis
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TTSModel {
    /// Latest model with improved prosody and additional emotion presets (recommended)
    #[serde(rename = "ssfm-v30")]
    SsfmV30,
    /// Stable production model with proven reliability and consistent quality
    #[serde(rename = "ssfm-v21")]
    SsfmV21,
}

impl Default for TTSModel {
    fn default() -> Self {
        TTSModel::SsfmV30
    }
}

/// Emotion preset types for speech synthesis
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum EmotionPreset {
    /// Neutral, balanced tone
    Normal,
    /// Bright, cheerful expression
    Happy,
    /// Melancholic, subdued tone
    Sad,
    /// Strong, intense delivery
    Angry,
    /// Soft, quiet speech (ssfm-v30 only)
    Whisper,
    /// Higher tonal emphasis (ssfm-v30 only)
    #[serde(rename = "toneup")]
    ToneUp,
    /// Lower tonal emphasis (ssfm-v30 only)
    #[serde(rename = "tonedown")]
    ToneDown,
}

impl Default for EmotionPreset {
    fn default() -> Self {
        EmotionPreset::Normal
    }
}

/// Audio output format
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AudioFormat {
    /// Uncompressed PCM audio (16-bit depth, mono, 44100 Hz)
    Wav,
    /// Compressed MPEG Layer III audio (320 kbps, 44100 Hz)
    Mp3,
}

impl Default for AudioFormat {
    fn default() -> Self {
        AudioFormat::Wav
    }
}

/// Gender classification for voices
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Gender {
    Male,
    Female,
}

/// Age group classification for voices
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Age {
    /// Child voice (under 12 years old)
    Child,
    /// Teenage voice (13-19 years old)
    Teenager,
    /// Young adult voice (20-35 years old)
    YoungAdult,
    /// Middle-aged voice (36-60 years old)
    MiddleAge,
    /// Elder voice (over 60 years old)
    Elder,
}

/// Voice use case categories
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum UseCase {
    Announcer,
    Anime,
    Audiobook,
    Conversational,
    Documentary,
    #[serde(rename = "E-learning")]
    ELearning,
    Rapper,
    Game,
    #[serde(rename = "Tiktok/Reels")]
    TikTokReels,
    News,
    Podcast,
    Voicemail,
    Ads,
}

/// Audio output settings
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Output {
    /// Volume level (0-200, default: 100)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub volume: Option<i32>,
    /// Pitch adjustment in semitones (-12 to +12, default: 0)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audio_pitch: Option<i32>,
    /// Speech speed multiplier (0.5 to 2.0, default: 1.0)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audio_tempo: Option<f64>,
    /// Output audio format (wav or mp3, default: wav)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub audio_format: Option<AudioFormat>,
}

impl Output {
    /// Create a new Output with default values
    pub fn new() -> Self {
        Self::default()
    }

    /// Set the volume (0-200)
    pub fn volume(mut self, volume: i32) -> Self {
        self.volume = Some(volume.clamp(0, 200));
        self
    }

    /// Set the audio pitch (-12 to +12 semitones)
    pub fn audio_pitch(mut self, pitch: i32) -> Self {
        self.audio_pitch = Some(pitch.clamp(-12, 12));
        self
    }

    /// Set the audio tempo (0.5 to 2.0)
    pub fn audio_tempo(mut self, tempo: f64) -> Self {
        self.audio_tempo = Some(tempo.clamp(0.5, 2.0));
        self
    }

    /// Set the audio format
    pub fn audio_format(mut self, format: AudioFormat) -> Self {
        self.audio_format = Some(format);
        self
    }
}

/// Emotion settings for ssfm-v21 model
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Prompt {
    /// Emotion preset to apply
    #[serde(skip_serializing_if = "Option::is_none")]
    pub emotion_preset: Option<EmotionPreset>,
    /// Emotion intensity (0.0 to 2.0, default: 1.0)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub emotion_intensity: Option<f64>,
}

impl Prompt {
    /// Create a new Prompt with default values
    pub fn new() -> Self {
        Self::default()
    }

    /// Set the emotion preset
    pub fn emotion_preset(mut self, preset: EmotionPreset) -> Self {
        self.emotion_preset = Some(preset);
        self
    }

    /// Set the emotion intensity (0.0 to 2.0)
    pub fn emotion_intensity(mut self, intensity: f64) -> Self {
        self.emotion_intensity = Some(intensity.clamp(0.0, 2.0));
        self
    }
}

/// Preset-based emotion control for ssfm-v30 model
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PresetPrompt {
    /// Must be "preset" for preset-based emotion control
    pub emotion_type: String,
    /// Emotion preset to apply
    #[serde(skip_serializing_if = "Option::is_none")]
    pub emotion_preset: Option<EmotionPreset>,
    /// Emotion intensity (0.0 to 2.0, default: 1.0)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub emotion_intensity: Option<f64>,
}

impl Default for PresetPrompt {
    fn default() -> Self {
        Self {
            emotion_type: "preset".to_string(),
            emotion_preset: None,
            emotion_intensity: None,
        }
    }
}

impl PresetPrompt {
    /// Create a new PresetPrompt
    pub fn new() -> Self {
        Self::default()
    }

    /// Set the emotion preset
    pub fn emotion_preset(mut self, preset: EmotionPreset) -> Self {
        self.emotion_preset = Some(preset);
        self
    }

    /// Set the emotion intensity (0.0 to 2.0)
    pub fn emotion_intensity(mut self, intensity: f64) -> Self {
        self.emotion_intensity = Some(intensity.clamp(0.0, 2.0));
        self
    }
}

/// Context-aware emotion inference for ssfm-v30 model
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SmartPrompt {
    /// Must be "smart" for context-aware emotion inference
    pub emotion_type: String,
    /// Text that comes before the main text (max 2000 chars)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub previous_text: Option<String>,
    /// Text that comes after the main text (max 2000 chars)
    #[serde(skip_serializing_if = "Option::is_none")]
    pub next_text: Option<String>,
}

impl Default for SmartPrompt {
    fn default() -> Self {
        Self {
            emotion_type: "smart".to_string(),
            previous_text: None,
            next_text: None,
        }
    }
}

impl SmartPrompt {
    /// Create a new SmartPrompt
    pub fn new() -> Self {
        Self::default()
    }

    /// Set the previous text for context
    pub fn previous_text(mut self, text: impl Into<String>) -> Self {
        self.previous_text = Some(text.into());
        self
    }

    /// Set the next text for context
    pub fn next_text(mut self, text: impl Into<String>) -> Self {
        self.next_text = Some(text.into());
        self
    }
}

/// Union type for all prompt types
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum TTSPrompt {
    /// Basic emotion control (ssfm-v21 compatible)
    Basic(Prompt),
    /// Explicit preset emotion control (ssfm-v30)
    Preset(PresetPrompt),
    /// Context-aware emotion inference (ssfm-v30)
    Smart(SmartPrompt),
}

impl From<Prompt> for TTSPrompt {
    fn from(prompt: Prompt) -> Self {
        TTSPrompt::Basic(prompt)
    }
}

impl From<PresetPrompt> for TTSPrompt {
    fn from(prompt: PresetPrompt) -> Self {
        TTSPrompt::Preset(prompt)
    }
}

impl From<SmartPrompt> for TTSPrompt {
    fn from(prompt: SmartPrompt) -> Self {
        TTSPrompt::Smart(prompt)
    }
}

/// Text-to-Speech request parameters
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TTSRequest {
    /// Voice ID in format 'tc_' followed by a unique identifier
    pub voice_id: String,
    /// Text to convert to speech (max 2000 chars)
    pub text: String,
    /// TTS model to use
    pub model: TTSModel,
    /// Language code (ISO 639-3). Auto-detected if not provided
    #[serde(skip_serializing_if = "Option::is_none")]
    pub language: Option<String>,
    /// Emotion and style settings
    #[serde(skip_serializing_if = "Option::is_none")]
    pub prompt: Option<TTSPrompt>,
    /// Audio output settings
    #[serde(skip_serializing_if = "Option::is_none")]
    pub output: Option<Output>,
    /// Random seed for reproducible results
    #[serde(skip_serializing_if = "Option::is_none")]
    pub seed: Option<i32>,
}

impl TTSRequest {
    /// Create a new TTSRequest with required fields
    pub fn new(voice_id: impl Into<String>, text: impl Into<String>, model: TTSModel) -> Self {
        Self {
            voice_id: voice_id.into(),
            text: text.into(),
            model,
            language: None,
            prompt: None,
            output: None,
            seed: None,
        }
    }

    /// Set the language code (ISO 639-3)
    pub fn language(mut self, language: impl Into<String>) -> Self {
        self.language = Some(language.into());
        self
    }

    /// Set the prompt (emotion settings)
    pub fn prompt(mut self, prompt: impl Into<TTSPrompt>) -> Self {
        self.prompt = Some(prompt.into());
        self
    }

    /// Set the output settings
    pub fn output(mut self, output: Output) -> Self {
        self.output = Some(output);
        self
    }

    /// Set the random seed for reproducible results
    pub fn seed(mut self, seed: i32) -> Self {
        self.seed = Some(seed);
        self
    }
}

/// Text-to-Speech response
#[derive(Debug, Clone)]
pub struct TTSResponse {
    /// Generated audio data
    pub audio_data: Vec<u8>,
    /// Audio duration in seconds
    pub duration: f64,
    /// Audio format (wav or mp3)
    pub format: AudioFormat,
}

/// Model information with supported emotions
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModelInfo {
    /// TTS model version
    pub version: TTSModel,
    /// List of supported emotions for this model
    pub emotions: Vec<String>,
}

/// Voice from V2 API with enhanced metadata
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VoiceV2 {
    /// Unique voice identifier
    pub voice_id: String,
    /// Human-readable name of the voice
    pub voice_name: String,
    /// List of supported TTS models with their emotions
    pub models: Vec<ModelInfo>,
    /// Voice gender classification
    #[serde(skip_serializing_if = "Option::is_none")]
    pub gender: Option<Gender>,
    /// Voice age group classification
    #[serde(skip_serializing_if = "Option::is_none")]
    pub age: Option<Age>,
    /// List of use case categories
    #[serde(skip_serializing_if = "Option::is_none")]
    pub use_cases: Option<Vec<String>>,
}

/// Filter options for V2 voices endpoint
#[derive(Debug, Clone, Default)]
pub struct VoicesV2Filter {
    /// Filter by TTS model
    pub model: Option<TTSModel>,
    /// Filter by gender
    pub gender: Option<Gender>,
    /// Filter by age group
    pub age: Option<Age>,
    /// Filter by use case
    pub use_cases: Option<UseCase>,
}

impl VoicesV2Filter {
    /// Create a new empty filter
    pub fn new() -> Self {
        Self::default()
    }

    /// Filter by model
    pub fn model(mut self, model: TTSModel) -> Self {
        self.model = Some(model);
        self
    }

    /// Filter by gender
    pub fn gender(mut self, gender: Gender) -> Self {
        self.gender = Some(gender);
        self
    }

    /// Filter by age
    pub fn age(mut self, age: Age) -> Self {
        self.age = Some(age);
        self
    }

    /// Filter by use case
    pub fn use_cases(mut self, use_case: UseCase) -> Self {
        self.use_cases = Some(use_case);
        self
    }
}

/// API error response
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ErrorResponse {
    /// Error message describing the issue
    pub detail: String,
}
