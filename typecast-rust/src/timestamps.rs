//! Timestamp-aware TTS types and captioning helpers.
//!
//! This module exposes [`TTSRequestWithTimestamps`], [`TTSWithTimestampsResponse`],
//! and alignment segment types. The response type provides [`TTSWithTimestampsResponse::to_srt`]
//! and [`TTSWithTimestampsResponse::to_vtt`] for generating subtitle files from the
//! word- or character-level alignment data returned by the API.

use crate::errors::{Result, TypecastError};
use base64::{engine::general_purpose::STANDARD as B64, Engine};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

/// A word-level alignment segment returned by the with-timestamps endpoint.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq)]
pub struct AlignmentSegmentWord {
    /// The word text.
    pub text: String,
    /// Start time in seconds.
    pub start: f64,
    /// End time in seconds.
    pub end: f64,
}

/// A character-level alignment segment returned by the with-timestamps endpoint.
#[derive(Debug, Clone, Deserialize, Serialize, PartialEq)]
pub struct AlignmentSegmentCharacter {
    /// The character text.
    pub text: String,
    /// Start time in seconds.
    pub start: f64,
    /// End time in seconds.
    pub end: f64,
}

/// Request body for `POST /v1/text-to-speech/with-timestamps`.
#[derive(Debug, Clone, Serialize)]
pub struct TTSRequestWithTimestamps {
    /// Voice ID (e.g. `tc_60e5426de8b95f1d3000d7b5`).
    pub voice_id: String,
    /// Text to synthesize (max 2000 characters).
    pub text: String,
    /// TTS model to use (e.g. `"ssfm-v30"`).
    pub model: crate::models::TTSModel,
    /// Language code (ISO 639-3). Auto-detected when omitted.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub language: Option<String>,
    /// Emotion/style settings (accepts any serializable value that matches the
    /// API's `prompt` field — use [`crate::models::TTSPrompt`] or raw JSON).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub prompt: Option<serde_json::Value>,
    /// Audio output settings (accepts any serializable value that matches the
    /// API's `output` field — use [`crate::models::Output`] serialized to JSON).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub output: Option<serde_json::Value>,
    /// Random seed for reproducible results.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub seed: Option<u32>,
}

impl TTSRequestWithTimestamps {
    /// Create a new request with the required fields.
    pub fn new(
        voice_id: impl Into<String>,
        text: impl Into<String>,
        model: crate::models::TTSModel,
    ) -> Self {
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

    /// Set the language code (ISO 639-3).
    pub fn language(mut self, language: impl Into<String>) -> Self {
        self.language = Some(language.into());
        self
    }

    /// Set the prompt field as a raw JSON value.
    pub fn prompt(mut self, prompt: serde_json::Value) -> Self {
        self.prompt = Some(prompt);
        self
    }

    /// Set the output field as a raw JSON value.
    pub fn output(mut self, output: serde_json::Value) -> Self {
        self.output = Some(output);
        self
    }

    /// Set the random seed.
    pub fn seed(mut self, seed: u32) -> Self {
        self.seed = Some(seed);
        self
    }
}

/// Response from `POST /v1/text-to-speech/with-timestamps`.
///
/// The `audio` field contains Base64-encoded audio data. Use
/// [`audio_bytes`][TTSWithTimestampsResponse::audio_bytes] to decode it, or
/// [`save_audio`][TTSWithTimestampsResponse::save_audio] to write directly to a file.
///
/// Call [`to_srt`][TTSWithTimestampsResponse::to_srt] or
/// [`to_vtt`][TTSWithTimestampsResponse::to_vtt] to generate subtitle output
/// from the alignment data.
#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct TTSWithTimestampsResponse {
    /// Base64-encoded audio bytes.
    pub audio: String,
    /// Audio container format (e.g. `"wav"` or `"mp3"`).
    pub audio_format: String,
    /// Total audio duration in seconds.
    pub audio_duration: f64,
    /// Word-level alignment segments (present when `granularity` is `"word"` or
    /// when both granularities are returned).
    pub words: Option<Vec<AlignmentSegmentWord>>,
    /// Character-level alignment segments (present when `granularity` is
    /// `"char"` or when both granularities are returned).
    pub characters: Option<Vec<AlignmentSegmentCharacter>>,
}

impl TTSWithTimestampsResponse {
    /// Decode the Base64-encoded audio field into raw bytes.
    pub fn audio_bytes(&self) -> Result<Vec<u8>> {
        B64.decode(&self.audio)
            .map_err(|e| TypecastError::DecodeError(e.to_string()))
    }

    /// Decode the audio and write it to `path`.
    pub fn save_audio<P: AsRef<Path>>(&self, path: P) -> Result<()> {
        let bytes = self.audio_bytes()?;
        fs::write(path, bytes).map_err(|e| TypecastError::IoError(e.to_string()))
    }

    /// Generate an SRT subtitle string from the alignment data.
    ///
    /// Word segments are preferred when there are at least two words; otherwise
    /// character segments are used.
    pub fn to_srt(&self) -> Result<String> {
        format_captions(self, true)
    }

    /// Generate a WebVTT subtitle string from the alignment data.
    ///
    /// Word segments are preferred when there are at least two words; otherwise
    /// character segments are used.
    pub fn to_vtt(&self) -> Result<String> {
        format_captions(self, false)
    }
}

// ---------------------------------------------------------------------------
// Internal captioning helpers
// ---------------------------------------------------------------------------

const MAX_CAPTION_SECONDS: f64 = 7.0;
const MAX_CAPTION_CHARS: usize = 42;
const SENTENCE_TERMINATORS: &[&str] = &[".", "?", "!", "\u{3002}", "\u{ff1f}", "\u{ff01}"];

struct Segment {
    text: String,
    start: f64,
    end: f64,
}

struct Cue {
    text: String,
    start: f64,
    end: f64,
}

/// Choose which set of segments to use for captioning, and whether word-joining
/// (space-separated) mode applies.
///
/// Priority: words (≥ 2) → characters (non-empty) → words (exactly 1) → error.
fn pick_segments(resp: &TTSWithTimestampsResponse) -> Result<(Vec<Segment>, bool)> {
    let word_segs = |words: &[crate::timestamps::AlignmentSegmentWord]| -> Vec<Segment> {
        words
            .iter()
            .map(|w| Segment {
                text: w.text.clone(),
                start: w.start,
                end: w.end,
            })
            .collect()
    };
    let char_segs = |chars: &[crate::timestamps::AlignmentSegmentCharacter]| -> Vec<Segment> {
        chars
            .iter()
            .map(|c| Segment {
                text: c.text.clone(),
                start: c.start,
                end: c.end,
            })
            .collect()
    };

    // Prefer words when there are at least 2 (single-word edge case falls through).
    let multi_words = resp.words.as_deref().filter(|w| w.len() >= 2);
    // Fall back to characters.
    let chars = resp.characters.as_deref().filter(|c| !c.is_empty());
    // Single-word fallback.
    let single_word = resp.words.as_deref().filter(|w| w.len() == 1);

    if let Some(words) = multi_words {
        Ok((word_segs(words), true))
    } else if let Some(c) = chars {
        Ok((char_segs(c), false))
    } else if let Some(words) = single_word {
        Ok((word_segs(words), true))
    } else {
        Err(TypecastError::CaptioningError(
            "no alignment segments to caption from".into(),
        ))
    }
}

/// Concatenate parts into a cue text.  In word mode parts are joined with a
/// space; in character mode they are concatenated directly.
fn join_parts(parts: &[String], word_mode: bool) -> String {
    let sep = if word_mode { " " } else { "" };
    parts.join(sep).trim().to_string()
}

/// Return `true` if `text` ends with a sentence-terminating punctuation mark.
fn ends_in_sentence(text: &str) -> bool {
    let trimmed = text.trim_end();
    SENTENCE_TERMINATORS.iter().any(|t| trimmed.ends_with(t))
}

/// Group flat segments into captioning cues obeying the max-duration and
/// max-char-count constraints and breaking on sentence-terminating punctuation.
///
/// TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
/// TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
fn group_into_cues(segs: &[Segment], word_mode: bool) -> Vec<Cue> {
    let mut cues: Vec<Cue> = Vec::new();
    let mut parts: Vec<String> = Vec::new();
    // Invariant: cur_start and last_end are always set whenever parts is non-empty.
    // Using 0.0 as default sentinels; they are only read when parts is non-empty.
    let mut cur_start: f64 = 0.0;
    let mut last_end: f64 = 0.0;

    /// Appends a cue only when its text is non-empty.
    fn emit(cues: &mut Vec<Cue>, text: String, start: f64, end: f64) {
        if !text.is_empty() {
            cues.push(Cue { text, start, end });
        }
    }

    for seg in segs {
        // If we already have content, check whether adding this segment would
        // violate a hard limit.
        if !parts.is_empty() {
            let mut tentative = parts.clone();
            tentative.push(seg.text.clone());
            let would_be = join_parts(&tentative, word_mode);
            let too_long_secs = (seg.end - cur_start) > MAX_CAPTION_SECONDS;
            let too_long_chars = would_be.chars().count() > MAX_CAPTION_CHARS;
            if too_long_secs || too_long_chars {
                // Flush the current cue before starting a new one.
                emit(
                    &mut cues,
                    join_parts(&parts, word_mode),
                    cur_start,
                    last_end,
                );
                parts.clear();
            }
        }

        // Record the start of a new cue.
        if parts.is_empty() {
            cur_start = seg.start;
        }
        parts.push(seg.text.clone());
        last_end = seg.end;

        // Break on sentence-terminating punctuation.
        if ends_in_sentence(&seg.text) {
            emit(&mut cues, join_parts(&parts, word_mode), cur_start, seg.end);
            parts.clear();
        }
    }

    // Flush any remaining parts.
    if !parts.is_empty() {
        emit(
            &mut cues,
            join_parts(&parts, word_mode),
            cur_start,
            last_end,
        );
    }

    cues
}

/// Format `seconds` as `HH:MM:SS,mmm` (SRT comma separator).
fn format_srt_time(seconds: f64) -> String {
    let total_ms = (seconds * 1000.0).round() as i64;
    let ms = total_ms % 1000;
    let total_sec = total_ms / 1000;
    let ss = total_sec % 60;
    let total_min = total_sec / 60;
    let mm = total_min % 60;
    let hh = total_min / 60;
    format!("{:02}:{:02}:{:02},{:03}", hh, mm, ss, ms)
}

/// Format `seconds` as `HH:MM:SS.mmm` (VTT dot separator).
fn format_vtt_time(seconds: f64) -> String {
    format_srt_time(seconds).replace(',', ".")
}

/// Core captioning formatter.  When `srt` is `true` emits SRT; otherwise VTT.
fn format_captions(resp: &TTSWithTimestampsResponse, srt: bool) -> Result<String> {
    let (segs, word_mode) = pick_segments(resp)?;
    let cues = group_into_cues(&segs, word_mode);
    if cues.is_empty() {
        return Err(TypecastError::CaptioningError(
            "no alignment segments to caption from".into(),
        ));
    }

    let mut out = String::new();
    if !srt {
        out.push_str("WEBVTT\n\n");
    }
    for (i, cue) in cues.iter().enumerate() {
        if srt {
            out.push_str(&format!("{}\n", i + 1));
        }
        let (s, e) = if srt {
            (format_srt_time(cue.start), format_srt_time(cue.end))
        } else {
            (format_vtt_time(cue.start), format_vtt_time(cue.end))
        };
        out.push_str(&format!("{} --> {}\n", s, e));
        out.push_str(&cue.text);
        out.push_str("\n\n");
    }
    Ok(out)
}
