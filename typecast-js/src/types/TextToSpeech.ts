export type TTSModel = 'ssfm-v21' | 'ssfm-v30';

/**
 * Language code following ISO 639-3 standard
 * Supported languages for text-to-speech conversion
 *
 * ssfm-v21: 27 languages
 * ssfm-v30: 37 languages (includes all v21 languages plus additional ones)
 */
export type LanguageCode =
  | 'eng' // English
  | 'kor' // Korean
  | 'jpn' // Japanese
  | 'spa' // Spanish
  | 'deu' // German
  | 'fra' // French
  | 'ita' // Italian
  | 'pol' // Polish
  | 'nld' // Dutch
  | 'rus' // Russian
  | 'ell' // Greek
  | 'tam' // Tamil
  | 'tgl' // Tagalog
  | 'fin' // Finnish
  | 'zho' // Chinese
  | 'slk' // Slovak
  | 'ara' // Arabic
  | 'hrv' // Croatian
  | 'ukr' // Ukrainian
  | 'ind' // Indonesian
  | 'dan' // Danish
  | 'swe' // Swedish
  | 'msa' // Malay
  | 'ces' // Czech
  | 'por' // Portuguese
  | 'bul' // Bulgarian
  | 'ron' // Romanian
  // ssfm-v30 additional languages
  | 'ben' // Bengali
  | 'hin' // Hindi
  | 'hun' // Hungarian
  | 'nan' // Min Nan
  | 'nor' // Norwegian
  | 'pan' // Punjabi
  | 'tha' // Thai
  | 'tur' // Turkish
  | 'vie' // Vietnamese
  | 'yue'; // Cantonese

/**
 * Emotion preset types
 * ssfm-v21: normal, happy, sad, angry
 * ssfm-v30: normal, happy, sad, angry, whisper, toneup, tonedown
 */
export type EmotionPreset = 'normal' | 'happy' | 'sad' | 'angry' | 'whisper' | 'toneup' | 'tonedown';

/**
 * Emotion and style settings for ssfm-v21 model
 */
export interface Prompt {
  /** Emotion preset for the voice (default: 'normal') */
  emotion_preset?: EmotionPreset;
  /**
   * Emotion intensity
   * @min 0.0
   * @max 2.0
   * @default 1.0
   */
  emotion_intensity?: number;
}

/**
 * Preset-based emotion control for ssfm-v30 model
 * Use this when you want to specify a specific emotion preset
 */
export interface PresetPrompt {
  /** Must be 'preset' for preset-based emotion control */
  emotion_type: 'preset';
  /** Emotion preset to apply (default: 'normal') */
  emotion_preset?: EmotionPreset;
  /**
   * Emotion intensity
   * @min 0.0
   * @max 2.0
   * @default 1.0
   */
  emotion_intensity?: number;
}

/**
 * Context-aware emotion inference for ssfm-v30 model
 * The model analyzes surrounding context to infer appropriate emotion
 */
export interface SmartPrompt {
  /** Must be 'smart' for context-aware emotion inference */
  emotion_type: 'smart';
  /** Text that comes BEFORE the main text (max 2000 chars) */
  previous_text?: string;
  /** Text that comes AFTER the main text (max 2000 chars) */
  next_text?: string;
}

/**
 * Union type for all prompt types
 * - Prompt: Basic emotion control (ssfm-v21 compatible)
 * - PresetPrompt: Explicit preset emotion control (ssfm-v30)
 * - SmartPrompt: Context-aware emotion inference (ssfm-v30)
 */
export type TTSPrompt = Prompt | PresetPrompt | SmartPrompt;

/**
 * Audio output settings for controlling the final audio characteristics
 */
export interface Output {
  /** 
   * Output volume
   * @min 0
   * @max 200
   * @default 100
   */
  volume?: number;
  /** 
   * Audio pitch adjustment in semitones
   * @min -12
   * @max 12
   * @default 0
   */
  audio_pitch?: number;
  /** 
   * Audio tempo (speed multiplier)
   * @min 0.5
   * @max 2.0
   * @default 1.0
   */
  audio_tempo?: number;
  /** 
   * Audio output format
   * @default 'wav'
   */
  audio_format?: 'wav' | 'mp3';
}

/**
 * Text-to-Speech request parameters
 */
export interface TTSRequest {
  /** 
   * Text to convert to speech
   * @maxLength 5000
   */
  text: string;
  /** Voice ID in format 'tc_' (Typecast voice) or 'uc_' (User-created voice) followed by a unique identifier */
  voice_id: string;
  /** Voice model to use */
  model: TTSModel;
  /** Language code (ISO 639-3). If not provided, will be auto-detected based on text content */
  language?: LanguageCode;
  /** Emotion and style settings for the generated speech */
  prompt?: TTSPrompt;
  /** Audio output settings */
  output?: Output;
  /** Random seed for reproducible results (same seed + same parameters = same output) */
  seed?: number;
}

/**
 * Text-to-Speech response
 */
export interface TTSResponse {
  /** Generated audio data as ArrayBuffer */
  audioData: ArrayBuffer;
  /** Audio duration in seconds */
  duration: number;
  /** Audio format (wav or mp3) */
  format: 'wav' | 'mp3';
}
