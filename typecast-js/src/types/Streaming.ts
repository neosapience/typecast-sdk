import { TTSModel, LanguageCode, TTSPrompt } from './TextToSpeech';

/**
 * Audio output settings for the streaming TTS endpoint.
 *
 * Unlike the non-streaming `Output`, this intentionally omits `volume` and
 * `target_lufs`: the streaming endpoint emits audio chunk-by-chunk and cannot
 * apply post-hoc loudness normalization.
 */
export interface OutputStream {
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
 * Text-to-Speech streaming request parameters.
 *
 * Same shape as `TTSRequest`, but `output` uses `OutputStream` (no volume /
 * target_lufs).
 */
export interface TTSRequestStream {
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
  /** Audio output settings (no volume / target_lufs in streaming mode) */
  output?: OutputStream;
  /** Random seed for reproducible results (same seed + same parameters = same output) */
  seed?: number;
}
