import { TTSModel } from './TextToSpeech';

/**
 * V1 Voices response (deprecated, use VoiceV2Response instead)
 */
export interface VoicesResponse {
  voice_id: string;
  voice_name: string;
  model: TTSModel;
  emotions: string[];
}

/**
 * Gender classification for voices
 */
export type GenderEnum = 'male' | 'female';

/**
 * Age group classification for voices
 */
export type AgeEnum = 'child' | 'teenager' | 'young_adult' | 'middle_age' | 'elder';

/**
 * Use case categories for voices
 */
export type UseCaseEnum =
  | 'Announcer'
  | 'Anime'
  | 'Audiobook'
  | 'Conversational'
  | 'Documentary'
  | 'E-learning'
  | 'Rapper'
  | 'Game'
  | 'Tiktok/Reels'
  | 'News'
  | 'Podcast'
  | 'Voicemail'
  | 'Ads';

/**
 * Model information with supported emotions
 */
export interface ModelInfo {
  /** TTS model version (e.g., ssfm-v21, ssfm-v30) */
  version: TTSModel;
  /** List of supported emotions for this model */
  emotions: string[];
}

/**
 * V2 Voice response with enhanced metadata
 */
export interface VoiceV2Response {
  /** Unique voice identifier */
  voice_id: string;
  /** Human-readable name of the voice */
  voice_name: string;
  /** List of supported TTS models with their available emotions */
  models: ModelInfo[];
  /** Voice gender classification */
  gender?: GenderEnum | null;
  /** Voice age group classification */
  age?: AgeEnum | null;
  /** List of use case categories this voice is suitable for */
  use_cases?: string[];
}

/**
 * Filter options for V2 voices endpoint
 */
export interface VoicesV2Filter {
  /** Filter by TTS model */
  model?: TTSModel;
  /** Filter by gender */
  gender?: GenderEnum;
  /** Filter by age group */
  age?: AgeEnum;
  /** Filter by use case */
  use_cases?: UseCaseEnum;
}
