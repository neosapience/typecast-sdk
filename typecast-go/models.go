package typecast

import (
	"fmt"
	"strings"
	"unicode/utf8"
)

// TTSModel represents the TTS model version
type TTSModel string

const (
	// ModelSSFMV30 is the latest model with improved prosody and additional emotion presets
	ModelSSFMV30 TTSModel = "ssfm-v30"
	// ModelSSFMV21 is the stable production model with proven reliability
	ModelSSFMV21 TTSModel = "ssfm-v21"
)

// EmotionPreset represents available emotion presets
type EmotionPreset string

const (
	EmotionNormal   EmotionPreset = "normal"
	EmotionSad      EmotionPreset = "sad"
	EmotionHappy    EmotionPreset = "happy"
	EmotionAngry    EmotionPreset = "angry"
	EmotionWhisper  EmotionPreset = "whisper"  // ssfm-v30 only
	EmotionToneUp   EmotionPreset = "toneup"   // ssfm-v30 only
	EmotionToneDown EmotionPreset = "tonedown" // ssfm-v30 only
)

// AudioFormat represents the output audio format
type AudioFormat string

const (
	AudioFormatWAV AudioFormat = "wav"
	AudioFormatMP3 AudioFormat = "mp3"
)

// GenderEnum represents gender classification
type GenderEnum string

const (
	GenderMale   GenderEnum = "male"
	GenderFemale GenderEnum = "female"
)

// AgeEnum represents age group classification
type AgeEnum string

const (
	AgeChild      AgeEnum = "child"
	AgeTeenager   AgeEnum = "teenager"
	AgeYoungAdult AgeEnum = "young_adult"
	AgeMiddleAge  AgeEnum = "middle_age"
	AgeElder      AgeEnum = "elder"
)

// UseCaseEnum represents voice use case categories
type UseCaseEnum string

const (
	UseCaseAnnouncer      UseCaseEnum = "Announcer"
	UseCaseAnime          UseCaseEnum = "Anime"
	UseCaseAudiobook      UseCaseEnum = "Audiobook"
	UseCaseConversational UseCaseEnum = "Conversational"
	UseCaseDocumentary    UseCaseEnum = "Documentary"
	UseCaseELearning      UseCaseEnum = "E-learning"
	UseCaseRapper         UseCaseEnum = "Rapper"
	UseCaseGame           UseCaseEnum = "Game"
	UseCaseTikTokReels    UseCaseEnum = "Tiktok/Reels"
	UseCaseNews           UseCaseEnum = "News"
	UseCasePodcast        UseCaseEnum = "Podcast"
	UseCaseVoicemail      UseCaseEnum = "Voicemail"
	UseCaseAds            UseCaseEnum = "Ads"
)

// Output represents audio output settings
type Output struct {
	// Volume controls the volume level (0-200, default: 100).
	// Cannot be used simultaneously with TargetLUFS.
	Volume *int `json:"volume,omitempty"`
	// TargetLUFS sets absolute loudness normalization (-70 to 0).
	// Cannot be used simultaneously with Volume.
	TargetLUFS *float64 `json:"target_lufs,omitempty"`
	// AudioPitch adjusts pitch in semitones (-12 to +12, default: 0)
	AudioPitch *int `json:"audio_pitch,omitempty"`
	// AudioTempo controls speech speed (0.5 to 2.0, default: 1.0)
	AudioTempo *float64 `json:"audio_tempo,omitempty"`
	// AudioFormat is the output format (wav or mp3, default: wav)
	AudioFormat AudioFormat `json:"audio_format,omitempty"`
}

// Validate checks the Output fields for invalid values.
func (o *Output) Validate() error {
	if o == nil {
		return nil
	}
	if o.Volume != nil && o.TargetLUFS != nil {
		return fmt.Errorf("volume and target_lufs are mutually exclusive")
	}
	if o.Volume != nil && (*o.Volume < 0 || *o.Volume > 200) {
		return fmt.Errorf("volume must be between 0 and 200")
	}
	if o.TargetLUFS != nil && (*o.TargetLUFS < -70 || *o.TargetLUFS > 0) {
		return fmt.Errorf("target_lufs must be between -70 and 0")
	}
	if o.AudioPitch != nil && (*o.AudioPitch < -12 || *o.AudioPitch > 12) {
		return fmt.Errorf("audio_pitch must be between -12 and 12")
	}
	if o.AudioTempo != nil && (*o.AudioTempo < 0.5 || *o.AudioTempo > 2.0) {
		return fmt.Errorf("audio_tempo must be between 0.5 and 2.0")
	}
	if o.AudioFormat != "" && o.AudioFormat != AudioFormatWAV && o.AudioFormat != AudioFormatMP3 {
		return fmt.Errorf("audio_format must be one of wav or mp3")
	}
	return nil
}

// Prompt represents emotion settings for ssfm-v21 model
type Prompt struct {
	// EmotionPreset is the emotion preset to apply
	EmotionPreset EmotionPreset `json:"emotion_preset,omitempty"`
	// EmotionIntensity controls strength of emotion (0.0 to 2.0, default: 1.0)
	EmotionIntensity *float64 `json:"emotion_intensity,omitempty"`
}

// PresetPrompt represents preset-based emotion control for ssfm-v30 model
type PresetPrompt struct {
	// EmotionType must be "preset" for preset-based emotion control
	EmotionType string `json:"emotion_type"`
	// EmotionPreset is the emotion preset to apply
	EmotionPreset EmotionPreset `json:"emotion_preset,omitempty"`
	// EmotionIntensity controls strength of emotion (0.0 to 2.0, default: 1.0)
	EmotionIntensity *float64 `json:"emotion_intensity,omitempty"`
}

// SmartPrompt represents context-aware emotion inference for ssfm-v30 model
type SmartPrompt struct {
	// EmotionType must be "smart" for context-aware emotion inference
	EmotionType string `json:"emotion_type"`
	// PreviousText is the text before the main text for context
	PreviousText string `json:"previous_text,omitempty"`
	// NextText is the text after the main text for context
	NextText string `json:"next_text,omitempty"`
}

// TTSRequest represents a text-to-speech request
type TTSRequest struct {
	// VoiceID is the voice identifier (required).
	// Browse available API voices at https://typecast.ai/developers/api/voices.
	VoiceID string `json:"voice_id"`
	// Text is the text to convert to speech (required, max 2000 chars)
	Text string `json:"text"`
	// Model is the TTS model to use (required)
	Model TTSModel `json:"model"`
	// Language is the ISO 639-3 language code (optional, auto-detected if not provided)
	Language string `json:"language,omitempty"`
	// Prompt contains emotion and style settings (optional)
	Prompt interface{} `json:"prompt,omitempty"`
	// Output contains audio output settings (optional)
	Output *Output `json:"output,omitempty"`
	// Seed is the random seed for reproducible results (optional)
	Seed *int `json:"seed,omitempty"`
}

// GenerateToFileRequest represents a convenience request for generating audio
// directly to a file. Model defaults to ModelSSFMV30 when omitted.
type GenerateToFileRequest struct {
	// VoiceID is the voice identifier (required).
	// Browse available API voices at https://typecast.ai/developers/api/voices.
	VoiceID string
	// Text is the text to convert to speech (required, max 2000 chars)
	Text string
	// Model is the TTS model to use (optional, defaults to ssfm-v30)
	Model TTSModel
	// Language is the ISO 639-3 language code (optional, auto-detected if not provided)
	Language string
	// Prompt contains emotion and style settings (optional)
	Prompt interface{}
	// Output contains audio output settings (optional)
	Output *Output
	// Seed is the random seed for reproducible results (optional)
	Seed *int
}

// Validate checks the GenerateToFileRequest fields for invalid values.
func (r *GenerateToFileRequest) Validate() error {
	if r == nil {
		return fmt.Errorf("request cannot be nil")
	}
	if strings.TrimSpace(r.VoiceID) == "" {
		return fmt.Errorf("voice_id is required")
	}
	if strings.TrimSpace(r.Text) == "" {
		return fmt.Errorf("text is required")
	}
	if utf8.RuneCountInString(r.Text) > 2000 {
		return fmt.Errorf("text must not exceed 2000 characters")
	}
	return r.Output.Validate()
}

func (r GenerateToFileRequest) toTTSRequest() *TTSRequest {
	model := r.Model
	if model == "" {
		model = ModelSSFMV30
	}
	return &TTSRequest{
		VoiceID:  r.VoiceID,
		Text:     r.Text,
		Model:    model,
		Language: r.Language,
		Prompt:   r.Prompt,
		Output:   r.Output,
		Seed:     r.Seed,
	}
}

// OutputStream represents audio output settings for the streaming endpoint.
// Unlike Output, it does not support Volume or TargetLUFS because the
// streaming endpoint cannot apply loudness normalization.
type OutputStream struct {
	// AudioPitch adjusts pitch in semitones (-12 to +12, default: 0)
	AudioPitch *int `json:"audio_pitch,omitempty"`
	// AudioTempo controls speech speed (0.5 to 2.0, default: 1.0)
	AudioTempo *float64 `json:"audio_tempo,omitempty"`
	// AudioFormat is the output format (wav or mp3, default: wav)
	AudioFormat AudioFormat `json:"audio_format,omitempty"`
}

// Validate checks the OutputStream fields for invalid values.
func (o *OutputStream) Validate() error {
	if o == nil {
		return nil
	}
	if o.AudioPitch != nil && (*o.AudioPitch < -12 || *o.AudioPitch > 12) {
		return fmt.Errorf("audio_pitch must be between -12 and 12")
	}
	if o.AudioTempo != nil && (*o.AudioTempo < 0.5 || *o.AudioTempo > 2.0) {
		return fmt.Errorf("audio_tempo must be between 0.5 and 2.0")
	}
	if o.AudioFormat != "" && o.AudioFormat != AudioFormatWAV && o.AudioFormat != AudioFormatMP3 {
		return fmt.Errorf("audio_format must be one of wav or mp3")
	}
	return nil
}

// TTSRequestStream represents a streaming text-to-speech request.
// It mirrors TTSRequest but uses OutputStream which omits volume / target_lufs.
type TTSRequestStream struct {
	// VoiceID is the voice identifier (required)
	VoiceID string `json:"voice_id"`
	// Text is the text to convert to speech (required, max 2000 chars)
	Text string `json:"text"`
	// Model is the TTS model to use (required)
	Model TTSModel `json:"model"`
	// Language is the ISO 639-3 language code (optional, auto-detected if not provided)
	Language string `json:"language,omitempty"`
	// Prompt contains emotion and style settings (optional)
	Prompt interface{} `json:"prompt,omitempty"`
	// Output contains streaming audio output settings (optional)
	Output *OutputStream `json:"output,omitempty"`
	// Seed is the random seed for reproducible results (optional)
	Seed *int `json:"seed,omitempty"`
}

// Validate checks the TTSRequestStream fields for invalid values.
func (r *TTSRequestStream) Validate() error {
	if r.VoiceID == "" {
		return fmt.Errorf("voice_id is required")
	}
	if r.Text == "" {
		return fmt.Errorf("text is required")
	}
	if utf8.RuneCountInString(r.Text) > 2000 {
		return fmt.Errorf("text must not exceed 2000 characters")
	}
	if r.Model == "" {
		return fmt.Errorf("model is required")
	}
	return r.Output.Validate()
}

// TTSResponse represents the response from text-to-speech API
type TTSResponse struct {
	// AudioData contains the generated audio data
	AudioData []byte
	// Duration is the audio duration in seconds
	Duration float64
	// Format is the audio format (wav or mp3)
	Format AudioFormat
}

// ModelInfo represents model information with supported emotions
type ModelInfo struct {
	// Version is the TTS model version
	Version TTSModel `json:"version"`
	// Emotions is the list of supported emotions for this model
	Emotions []string `json:"emotions"`
}

// VoiceV1 represents a voice from V1 API (deprecated)
type VoiceV1 struct {
	// VoiceID is the unique voice identifier
	VoiceID string `json:"voice_id"`
	// VoiceName is the human-readable name
	VoiceName string `json:"voice_name"`
	// Model is the TTS model version
	Model TTSModel `json:"model"`
	// Emotions is the list of supported emotions
	Emotions []string `json:"emotions"`
}

// VoiceV2 represents a voice from V2 API with enhanced metadata
type VoiceV2 struct {
	// VoiceID is the unique voice identifier
	VoiceID string `json:"voice_id"`
	// VoiceName is the human-readable name
	VoiceName string `json:"voice_name"`
	// Models is the list of supported TTS models with their emotions
	Models []ModelInfo `json:"models"`
	// Gender is the voice gender classification
	Gender *GenderEnum `json:"gender,omitempty"`
	// Age is the voice age group classification
	Age *AgeEnum `json:"age,omitempty"`
	// UseCases is the list of use case categories
	UseCases []string `json:"use_cases,omitempty"`
}

// VoicesV2Filter represents filter options for V2 voices endpoint
type VoicesV2Filter struct {
	// Model filters by TTS model
	Model TTSModel `url:"model,omitempty"`
	// Gender filters by gender
	Gender GenderEnum `url:"gender,omitempty"`
	// Age filters by age group
	Age AgeEnum `url:"age,omitempty"`
	// UseCases filters by use case
	UseCases UseCaseEnum `url:"use_cases,omitempty"`
}

// ErrorResponse represents an API error response
type ErrorResponse struct {
	Detail string `json:"detail"`
}

// CustomVoice is the response body of POST /v1/voices/clone.
// VoiceID has the "uc_" prefix and can be used directly as voice_id in TextToSpeech.
type CustomVoice struct {
	VoiceID string `json:"voice_id"`
	Name    string `json:"name"`
	Model   string `json:"model"`
}

const (
	// CloningMaxFileSize is the maximum allowed audio file size for CloneVoice (25 MB).
	// Must match typecast-api `cloning_max_file_size` env (default 25 * 1024 * 1024).
	CloningMaxFileSize int64 = 25 * 1024 * 1024
	// NameMinLength is the minimum character length for a custom voice name.
	NameMinLength int = 1
	// NameMaxLength is the maximum character length for a custom voice name.
	NameMaxLength int = 30
)

// PlanTier represents the subscription plan tier
type PlanTier string

const (
	PlanTierFree   PlanTier = "free"
	PlanTierLite   PlanTier = "lite"
	PlanTierPlus   PlanTier = "plus"
	PlanTierCustom PlanTier = "custom"
)

// Credits represents subscription credit usage
type Credits struct {
	// PlanCredits is the total credits provided by the plan
	PlanCredits int `json:"plan_credits"`
	// UsedCredits is the number of credits used
	UsedCredits int `json:"used_credits"`
}

// Limits represents subscription usage limits
type Limits struct {
	// ConcurrencyLimit is the maximum number of concurrent requests
	ConcurrencyLimit int `json:"concurrency_limit"`
}

// SubscriptionResponse represents the authenticated user's subscription
type SubscriptionResponse struct {
	// Plan is the subscription plan tier
	Plan PlanTier `json:"plan"`
	// Credits contains credit usage information
	Credits Credits `json:"credits"`
	// Limits contains usage limits
	Limits Limits `json:"limits"`
}
