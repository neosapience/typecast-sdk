//! Unit tests for timestamp TTS types and captioning helpers.
//!
//! All fixture-based tests assert byte-for-byte equality against the shared
//! expected files in `test-fixtures/with-timestamps/expected/`.  These tests
//! never touch the real Typecast API.

use mockito::Server;
use serde_json;
use std::fs;
use std::path::PathBuf;
use std::time::Duration;
use typecast_rust::{
    timestamps::{TTSRequestWithTimestamps, TTSWithTimestampsResponse},
    AudioFormat, ClientConfig, ComposerSettings, EmotionPreset, Output, PresetPrompt, TTSModel,
    TypecastClient, TypecastError,
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Locate the `test-fixtures/with-timestamps` directory by walking up from cwd.
fn fixture_dir() -> PathBuf {
    let mut dir: PathBuf = std::env::current_dir().unwrap();
    for _ in 0..8 {
        let cand = dir.join("test-fixtures").join("with-timestamps");
        if cand.is_dir() {
            return cand;
        }
        dir.pop();
    }
    panic!(
        "test-fixtures/with-timestamps not found from {:?}",
        std::env::current_dir().unwrap()
    );
}

fn load_fixture(name: &str) -> String {
    fs::read_to_string(fixture_dir().join(name))
        .unwrap_or_else(|e| panic!("failed to read fixture {}: {}", name, e))
}

fn load_expected(name: &str) -> String {
    fs::read_to_string(fixture_dir().join("expected").join(name))
        .unwrap_or_else(|e| panic!("failed to read expected {}: {}", name, e))
}

fn parse_fixture(name: &str) -> TTSWithTimestampsResponse {
    let json = load_fixture(name);
    serde_json::from_str(&json)
        .unwrap_or_else(|e| panic!("failed to parse fixture {}: {}", name, e))
}

fn make_client(server: &Server) -> TypecastClient {
    let config = ClientConfig::new("test-api-key")
        .base_url(server.url())
        .timeout(Duration::from_secs(5));
    TypecastClient::new(config).expect("client builds")
}

fn small_wav() -> Vec<u8> {
    let mut buf = Vec::new();
    buf.extend_from_slice(b"RIFF");
    buf.extend_from_slice(&36u32.to_le_bytes());
    buf.extend_from_slice(b"WAVE");
    buf.extend_from_slice(b"fmt ");
    buf.extend_from_slice(&16u32.to_le_bytes());
    buf.extend_from_slice(&1u16.to_le_bytes());
    buf.extend_from_slice(&1u16.to_le_bytes());
    buf.extend_from_slice(&44100u32.to_le_bytes());
    buf.extend_from_slice(&88200u32.to_le_bytes());
    buf.extend_from_slice(&2u16.to_le_bytes());
    buf.extend_from_slice(&16u16.to_le_bytes());
    buf.extend_from_slice(b"data");
    buf.extend_from_slice(&0u32.to_le_bytes());
    buf
}

#[tokio::test]
async fn compose_speech_smoke_for_timestamps_binary_coverage() {
    let mut server = Server::new_async().await;
    let _m1 = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(small_wav())
        .create_async()
        .await;
    let _m2 = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(small_wav())
        .create_async()
        .await;
    let _m3 = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(small_wav())
        .create_async()
        .await;
    let _m4 = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(small_wav())
        .create_async()
        .await;

    let client = make_client(&server);
    let response = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("voice-a")
                .model(TTSModel::SsfmV30)
                .language("eng")
                .prompt(PresetPrompt::new().emotion_preset(EmotionPreset::Normal))
                .output(Output::new().audio_format(AudioFormat::Wav))
                .seed(1),
        )
        .say("Hello<|0.001s|>there")
        .say_with(
            "World<|0.001s|>again",
            ComposerSettings::new()
                .voice_id("voice-b")
                .model(TTSModel::SsfmV30),
        )
        .generate()
        .await
        .unwrap();

    assert_eq!(response.format, AudioFormat::Wav);
}

// ---------------------------------------------------------------------------
// SRT fixture tests
// ---------------------------------------------------------------------------

#[test]
fn to_srt_both_matches_expected() {
    let resp = parse_fixture("both.json");
    let actual = resp.to_srt().unwrap();
    let expected = load_expected("both.srt");
    assert_eq!(actual, expected, "SRT mismatch for both");
}

#[test]
fn to_srt_word_only_matches_expected() {
    let resp = parse_fixture("word_only.json");
    let actual = resp.to_srt().unwrap();
    let expected = load_expected("word_only.srt");
    assert_eq!(actual, expected, "SRT mismatch for word_only");
}

#[test]
fn to_srt_char_only_matches_expected() {
    let resp = parse_fixture("char_only.json");
    let actual = resp.to_srt().unwrap();
    let expected = load_expected("char_only.srt");
    assert_eq!(actual, expected, "SRT mismatch for char_only");
}

#[test]
fn to_srt_jpn_char_matches_expected() {
    let resp = parse_fixture("jpn_char.json");
    let actual = resp.to_srt().unwrap();
    let expected = load_expected("jpn_char.srt");
    assert_eq!(actual, expected, "SRT mismatch for jpn_char");
}

// ---------------------------------------------------------------------------
// VTT fixture tests
// ---------------------------------------------------------------------------

#[test]
fn to_vtt_both_matches_expected() {
    let resp = parse_fixture("both.json");
    let actual = resp.to_vtt().unwrap();
    let expected = load_expected("both.vtt");
    assert_eq!(actual, expected, "VTT mismatch for both");
}

#[test]
fn to_vtt_word_only_matches_expected() {
    let resp = parse_fixture("word_only.json");
    let actual = resp.to_vtt().unwrap();
    let expected = load_expected("word_only.vtt");
    assert_eq!(actual, expected, "VTT mismatch for word_only");
}

#[test]
fn to_vtt_char_only_matches_expected() {
    let resp = parse_fixture("char_only.json");
    let actual = resp.to_vtt().unwrap();
    let expected = load_expected("char_only.vtt");
    assert_eq!(actual, expected, "VTT mismatch for char_only");
}

#[test]
fn to_vtt_jpn_char_matches_expected() {
    let resp = parse_fixture("jpn_char.json");
    let actual = resp.to_vtt().unwrap();
    let expected = load_expected("jpn_char.vtt");
    assert_eq!(actual, expected, "VTT mismatch for jpn_char");
}

// ---------------------------------------------------------------------------
// Batch loop versions (documents which 4 fixtures are covered)
// ---------------------------------------------------------------------------

#[test]
fn to_srt_matches_all_fixtures() {
    for name in &["both", "word_only", "char_only", "jpn_char"] {
        let resp = parse_fixture(&format!("{}.json", name));
        let actual = resp.to_srt().unwrap();
        let expected = load_expected(&format!("{}.srt", name));
        assert_eq!(actual, expected, "SRT mismatch for {}", name);
    }
}

#[test]
fn to_vtt_matches_all_fixtures() {
    for name in &["both", "word_only", "char_only", "jpn_char"] {
        let resp = parse_fixture(&format!("{}.json", name));
        let actual = resp.to_vtt().unwrap();
        let expected = load_expected(&format!("{}.vtt", name));
        assert_eq!(actual, expected, "VTT mismatch for {}", name);
    }
}

// ---------------------------------------------------------------------------
// audio_bytes / save_audio
// ---------------------------------------------------------------------------

#[test]
fn audio_bytes_decodes_successfully() {
    let resp = parse_fixture("both.json");
    let bytes = resp.audio_bytes().unwrap();
    assert!(!bytes.is_empty(), "decoded audio bytes should be non-empty");
}

#[test]
fn audio_bytes_error_on_invalid_base64() {
    let resp = TTSWithTimestampsResponse {
        audio: "not!valid!base64!!!!".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 1.0,
        words: None,
        characters: None,
    };
    let err = resp.audio_bytes().unwrap_err();
    assert!(
        matches!(err, TypecastError::DecodeError(_)),
        "expected DecodeError, got {:?}",
        err
    );
}

#[test]
fn save_audio_writes_non_empty_file() {
    let resp = parse_fixture("both.json");
    let dir = std::env::temp_dir();
    let path = dir.join(format!(
        "rust_tts_timestamps_test_{}.wav",
        std::process::id()
    ));
    resp.save_audio(&path).unwrap();
    let meta = fs::metadata(&path).unwrap();
    assert!(meta.len() > 0, "written file should be non-empty");
    let _ = fs::remove_file(&path);
}

// ---------------------------------------------------------------------------
// Edge cases: no segments
// ---------------------------------------------------------------------------

#[test]
fn to_srt_errors_when_no_segments() {
    let resp = TTSWithTimestampsResponse {
        audio: "".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 0.0,
        words: None,
        characters: None,
    };
    let err = resp.to_srt().unwrap_err();
    assert!(
        matches!(err, TypecastError::CaptioningError(_)),
        "expected CaptioningError, got {:?}",
        err
    );
}

#[test]
fn to_vtt_errors_when_no_segments() {
    let resp = TTSWithTimestampsResponse {
        audio: "".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 0.0,
        words: None,
        characters: None,
    };
    let err = resp.to_vtt().unwrap_err();
    assert!(
        matches!(err, TypecastError::CaptioningError(_)),
        "expected CaptioningError, got {:?}",
        err
    );
}

// ---------------------------------------------------------------------------
// TTSRequestWithTimestamps builder
// ---------------------------------------------------------------------------

#[test]
fn request_builder_sets_all_fields() {
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30)
        .language("eng")
        .prompt(serde_json::json!({"emotion_type": "preset"}))
        .output(serde_json::json!({"audio_format": "wav"}))
        .seed(42);
    assert_eq!(req.voice_id, "tc_x");
    assert_eq!(req.text, "hello");
    assert_eq!(req.language.as_deref(), Some("eng"));
    assert!(req.prompt.is_some());
    assert!(req.output.is_some());
    assert_eq!(req.seed, Some(42));

    // Cover Debug + Clone
    let _ = format!("{req:?}");
    let _ = req.clone();
}

#[test]
fn request_serializes_without_skip_fields_when_none() {
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let json = serde_json::to_string(&req).unwrap();
    assert!(!json.contains("language"));
    assert!(!json.contains("prompt"));
    assert!(!json.contains("output"));
    assert!(!json.contains("seed"));
}

// ---------------------------------------------------------------------------
// AlignmentSegment derives
// ---------------------------------------------------------------------------

#[test]
fn alignment_segment_derives() {
    use typecast_rust::timestamps::{AlignmentSegmentCharacter, AlignmentSegmentWord};

    let w = AlignmentSegmentWord {
        text: "hello".into(),
        start: 0.1,
        end: 0.4,
    };
    let _ = format!("{w:?}");
    let cloned = w.clone();
    assert_eq!(w, cloned);
    let json = serde_json::to_string(&w).unwrap();
    let parsed: AlignmentSegmentWord = serde_json::from_str(&json).unwrap();
    assert_eq!(parsed.text, "hello");

    let c = AlignmentSegmentCharacter {
        text: "A".into(),
        start: 0.0,
        end: 0.05,
    };
    let _ = format!("{c:?}");
    let _ = c.clone();
    assert_eq!(c, c.clone());
}

// ---------------------------------------------------------------------------
// Client method — mock-based integration tests
// ---------------------------------------------------------------------------

/// Build a minimal valid response JSON with a tiny valid Base64 WAV (4 bytes).
fn mock_ts_response_json() -> String {
    // "AAAA" in base64 decodes to three zero bytes — fine for unit tests.
    r#"{
        "audio": "AAAA",
        "audio_format": "wav",
        "audio_duration": 2.4,
        "words": [
            {"text": "Hello.", "start": 0.1, "end": 0.444},
            {"text": "How", "start": 1.271, "end": 1.6},
            {"text": "are", "start": 1.6, "end": 1.9},
            {"text": "you?", "start": 1.9, "end": 2.4}
        ],
        "characters": null
    }"#
    .to_string()
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_no_granularity() {
    let mut server = Server::new_async().await;
    let body = mock_ts_response_json();
    let _m = server
        .mock("POST", "/v1/text-to-speech/with-timestamps")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(&body)
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "Hello. How are you?", TTSModel::SsfmV30);
    let resp = client
        .text_to_speech_with_timestamps(&req, None)
        .await
        .unwrap();
    assert_eq!(resp.audio_format, "wav");
    assert!((resp.audio_duration - 2.4).abs() < 1e-9);
    assert!(resp.words.is_some());
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_word_granularity() {
    let mut server = Server::new_async().await;
    let body = mock_ts_response_json();
    let _m = server
        .mock("POST", "/v1/text-to-speech/with-timestamps")
        .match_query(mockito::Matcher::UrlEncoded(
            "granularity".into(),
            "word".into(),
        ))
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(&body)
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let resp = client
        .text_to_speech_with_timestamps(&req, Some("word"))
        .await
        .unwrap();
    assert!(resp.words.is_some());
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_char_granularity() {
    let mut server = Server::new_async().await;
    let char_body = r#"{
        "audio": "AAAA",
        "audio_format": "wav",
        "audio_duration": 1.0,
        "words": null,
        "characters": [
            {"text": "H", "start": 0.0, "end": 0.1},
            {"text": "i", "start": 0.1, "end": 0.2}
        ]
    }"#;
    let _m = server
        .mock("POST", "/v1/text-to-speech/with-timestamps")
        .match_query(mockito::Matcher::UrlEncoded(
            "granularity".into(),
            "char".into(),
        ))
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(char_body)
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "Hi", TTSModel::SsfmV30);
    let resp = client
        .text_to_speech_with_timestamps(&req, Some("char"))
        .await
        .unwrap();
    assert!(resp.characters.is_some());
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_invalid_granularity_is_validation_error() {
    let server = Server::new_async().await;
    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let err = client
        .text_to_speech_with_timestamps(&req, Some("sentence"))
        .await
        .unwrap_err();
    assert!(
        err.is_validation_error(),
        "expected ValidationError, got {:?}",
        err
    );
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_propagates_api_errors() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech/with-timestamps")
        .with_status(401)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"bad key"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let err = client
        .text_to_speech_with_timestamps(&req, None)
        .await
        .unwrap_err();
    assert!(err.is_unauthorized());
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_propagates_server_error() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech/with-timestamps")
        .with_status(500)
        .with_header("content-type", "text/plain")
        .with_body("internal boom")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let err = client
        .text_to_speech_with_timestamps(&req, None)
        .await
        .unwrap_err();
    assert!(err.is_server_error());
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_propagates_network_error() {
    // Use a port that nobody is listening on to trigger a connection-refused send error.
    use typecast_rust::ClientConfig;
    let config = ClientConfig::new("test-api-key")
        .base_url("http://127.0.0.1:1") // port 1 is always refused
        .timeout(Duration::from_secs(2));
    let client = TypecastClient::new(config).expect("client builds");
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let err = client
        .text_to_speech_with_timestamps(&req, None)
        .await
        .unwrap_err();
    assert!(
        matches!(err, TypecastError::HttpError(_)),
        "expected HttpError on connection failure, got {err:?}"
    );
}

#[tokio::test]
async fn client_text_to_speech_with_timestamps_propagates_bad_json() {
    // 200 OK but body is not valid JSON → DecodeError on response.json().
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech/with-timestamps")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("this is not json at all")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestWithTimestamps::new("tc_x", "hello", TTSModel::SsfmV30);
    let err = client
        .text_to_speech_with_timestamps(&req, None)
        .await
        .unwrap_err();
    assert!(
        matches!(err, TypecastError::DecodeError(_)),
        "expected DecodeError, got {err:?}"
    );
}

// ---------------------------------------------------------------------------
// single-word fallback (words.len() == 1, no characters)
// ---------------------------------------------------------------------------

#[test]
fn to_srt_single_word_fallback() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 0.5,
        words: Some(vec![AlignmentSegmentWord {
            text: "Hello.".to_string(),
            start: 0.0,
            end: 0.5,
        }]),
        characters: None,
    };
    let srt = resp.to_srt().expect("single-word SRT should succeed");
    assert!(srt.contains("Hello."), "SRT should contain the word text");
    assert!(srt.starts_with("1\n"), "SRT should start with cue index 1");
}

#[test]
fn to_vtt_single_word_fallback() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 0.3,
        words: Some(vec![AlignmentSegmentWord {
            text: "Hi.".to_string(),
            start: 0.0,
            end: 0.3,
        }]),
        characters: None,
    };
    let vtt = resp.to_vtt().expect("single-word VTT should succeed");
    assert!(
        vtt.starts_with("WEBVTT\n\n"),
        "VTT should start with WEBVTT header"
    );
    assert!(vtt.contains("Hi."), "VTT should contain the word text");
}

// ---------------------------------------------------------------------------
// groupIntoCues: hard-cap flush (time-based and char-based)
// ---------------------------------------------------------------------------

#[test]
fn to_srt_hard_cap_by_time_produces_multiple_cues() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    // Words span > 7.0 s in total — triggers a time-based hard cap flush
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 8.0,
        words: Some(vec![
            AlignmentSegmentWord {
                text: "Word1".to_string(),
                start: 0.0,
                end: 2.0,
            },
            AlignmentSegmentWord {
                text: "Word2".to_string(),
                start: 2.0,
                end: 4.0,
            },
            AlignmentSegmentWord {
                text: "Word3".to_string(),
                start: 4.0,
                end: 6.0,
            },
            // Adding Word4 with end=8.0 makes span (8.0 - 0.0) = 8.0 > 7.0
            AlignmentSegmentWord {
                text: "Word4".to_string(),
                start: 6.0,
                end: 8.0,
            },
        ]),
        characters: None,
    };
    let srt = resp.to_srt().expect("hard-cap SRT should succeed");
    // Should have at least cue index "2"
    assert!(
        srt.contains("2\n"),
        "expected at least 2 cues after time hard-cap:\n{srt}"
    );
}

#[test]
fn to_srt_hard_cap_by_chars_produces_multiple_cues() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    // 4 × "AAAAAAAAAA" joined with spaces = 43 chars > MAX_CAPTION_CHARS=42
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 2.0,
        words: Some(vec![
            AlignmentSegmentWord {
                text: "AAAAAAAAAA".to_string(),
                start: 0.0,
                end: 0.5,
            },
            AlignmentSegmentWord {
                text: "AAAAAAAAAA".to_string(),
                start: 0.5,
                end: 1.0,
            },
            AlignmentSegmentWord {
                text: "AAAAAAAAAA".to_string(),
                start: 1.0,
                end: 1.5,
            },
            AlignmentSegmentWord {
                text: "AAAAAAAAAA".to_string(),
                start: 1.5,
                end: 2.0,
            },
        ]),
        characters: None,
    };
    let srt = resp.to_srt().expect("char hard-cap SRT should succeed");
    assert!(
        srt.contains("2\n"),
        "expected at least 2 cues after char hard-cap:\n{srt}"
    );
}

// ---------------------------------------------------------------------------
// groupIntoCues: final flush without sentence terminator
// ---------------------------------------------------------------------------

#[test]
fn to_srt_final_flush_without_sentence_terminator() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    // Words without sentence terminator — tests the trailing flush
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 1.0,
        words: Some(vec![
            AlignmentSegmentWord {
                text: "Hello".to_string(),
                start: 0.0,
                end: 0.5,
            },
            AlignmentSegmentWord {
                text: "World".to_string(),
                start: 0.5,
                end: 1.0,
            },
        ]),
        characters: None,
    };
    let srt = resp.to_srt().expect("final flush SRT should succeed");
    assert!(
        srt.contains("Hello World"),
        "SRT should contain the joined text"
    );
    // Only one cue (no sentence split)
    assert!(!srt.contains("2\n"), "expected exactly 1 cue");
}

// ---------------------------------------------------------------------------
// format_captions: empty cues path (all segment text is empty)
// ---------------------------------------------------------------------------

#[test]
fn to_srt_all_empty_text_segments_errors() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 1.0,
        words: Some(vec![
            AlignmentSegmentWord {
                text: "".to_string(),
                start: 0.0,
                end: 0.5,
            },
            AlignmentSegmentWord {
                text: "".to_string(),
                start: 0.5,
                end: 1.0,
            },
        ]),
        characters: None,
    };
    let err = resp.to_srt().unwrap_err();
    assert!(
        matches!(err, TypecastError::CaptioningError(_)),
        "expected CaptioningError, got {:?}",
        err
    );
}

#[test]
fn to_vtt_all_empty_text_segments_errors() {
    use typecast_rust::timestamps::AlignmentSegmentWord;
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 1.0,
        words: Some(vec![
            AlignmentSegmentWord {
                text: "".to_string(),
                start: 0.0,
                end: 0.5,
            },
            AlignmentSegmentWord {
                text: "".to_string(),
                start: 0.5,
                end: 1.0,
            },
        ]),
        characters: None,
    };
    let err = resp.to_vtt().unwrap_err();
    assert!(
        matches!(err, TypecastError::CaptioningError(_)),
        "expected CaptioningError, got {:?}",
        err
    );
}

// ---------------------------------------------------------------------------
// save_audio and audio_bytes
// ---------------------------------------------------------------------------

#[test]
fn save_audio_writes_to_file() {
    let path = std::env::temp_dir().join("typecast_rust_test_save_audio.wav");
    let resp = TTSWithTimestampsResponse {
        // "AAAA" decodes to 3 zero bytes
        audio: "AAAA".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 0.0,
        words: None,
        characters: None,
    };
    resp.save_audio(&path).expect("save_audio should succeed");
    assert!(path.exists(), "output file should exist");
    let bytes = std::fs::read(&path).unwrap();
    assert_eq!(bytes.len(), 3, "decoded 'AAAA' should be 3 bytes");
    let _ = std::fs::remove_file(&path);
}

#[test]
fn save_audio_propagates_decode_error() {
    // When audio contains invalid base64, save_audio must propagate the error.
    let path = std::env::temp_dir().join("typecast_rust_test_save_audio_error.wav");
    let resp = TTSWithTimestampsResponse {
        audio: "!!!invalid-base64!!!".to_string(),
        audio_format: "wav".to_string(),
        audio_duration: 0.0,
        words: None,
        characters: None,
    };
    assert!(
        resp.save_audio(&path).is_err(),
        "save_audio should propagate audio_bytes() decode error"
    );
}

#[test]
fn save_audio_propagates_write_error() {
    // Writing to a nonexistent directory should cause fs::write to fail.
    let path = std::env::temp_dir()
        .join("typecast_rust_nonexistent_dir_xyzzy_12345")
        .join("output.wav");
    let resp = TTSWithTimestampsResponse {
        audio: "AAAA".to_string(), // valid base64
        audio_format: "wav".to_string(),
        audio_duration: 0.0,
        words: None,
        characters: None,
    };
    assert!(
        resp.save_audio(&path).is_err(),
        "save_audio should propagate fs::write error when parent dir does not exist"
    );
}
