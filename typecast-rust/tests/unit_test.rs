//! Unit tests for the Typecast Rust SDK.
//!
//! These tests use `mockito` to spin up an in-process HTTP server for every
//! test, so they never touch the real Typecast API and never require an API
//! key. They are designed to give 100% line, function, and region coverage of
//! the SDK source files.

use futures_util::StreamExt;
use mockito::Server;
use std::fs;
use std::time::Duration;
use typecast_rust::{
    parse_pause_markup, Age, AudioFormat, ClientConfig, ComposerSettings, Credits, EmotionPreset,
    ErrorResponse, Gender, GenerateToFileRequest, Limits, ModelInfo, Output, OutputStream,
    PlanTier, PresetPrompt, Prompt, SmartPrompt, SpeechPart, SubscriptionResponse, TTSModel,
    TTSPrompt, TTSRequest, TTSRequestStream, TypecastClient, TypecastError, UseCase, VoiceV2,
    VoicesV2Filter, DEFAULT_BASE_URL, DEFAULT_TIMEOUT_SECS,
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

fn make_client(server: &Server) -> TypecastClient {
    let config = ClientConfig::new("test-api-key")
        .base_url(server.url())
        .timeout(Duration::from_secs(5));
    TypecastClient::new(config).expect("client builds")
}

fn make_test_wav(samples: &[i16], sample_rate: u32) -> Vec<u8> {
    make_test_wav_with_format(samples, sample_rate, 1, 1, 16)
}

fn make_test_wav_with_format(
    samples: &[i16],
    sample_rate: u32,
    audio_format: u16,
    channels: u16,
    bits_per_sample: u16,
) -> Vec<u8> {
    let data_size = (samples.len() * 2) as u32;
    let mut wav = Vec::with_capacity(44 + samples.len() * 2);
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&(36 + data_size).to_le_bytes());
    wav.extend_from_slice(b"WAVE");
    wav.extend_from_slice(b"fmt ");
    wav.extend_from_slice(&16u32.to_le_bytes());
    wav.extend_from_slice(&audio_format.to_le_bytes());
    wav.extend_from_slice(&channels.to_le_bytes());
    wav.extend_from_slice(&sample_rate.to_le_bytes());
    wav.extend_from_slice(&(sample_rate * 2).to_le_bytes());
    wav.extend_from_slice(&2u16.to_le_bytes());
    wav.extend_from_slice(&bits_per_sample.to_le_bytes());
    wav.extend_from_slice(b"data");
    wav.extend_from_slice(&data_size.to_le_bytes());
    for sample in samples {
        wav.extend_from_slice(&sample.to_le_bytes());
    }
    wav
}

fn make_test_wav_without_data(extra_chunk: bool) -> Vec<u8> {
    let payload_len = if extra_chunk { 12 } else { 8 };
    let mut wav = Vec::with_capacity(36 + payload_len);
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&(28u32 + payload_len as u32).to_le_bytes());
    wav.extend_from_slice(b"WAVE");
    wav.extend_from_slice(b"fmt ");
    wav.extend_from_slice(&16u32.to_le_bytes());
    wav.extend_from_slice(&1u16.to_le_bytes());
    wav.extend_from_slice(&1u16.to_le_bytes());
    wav.extend_from_slice(&1000u32.to_le_bytes());
    wav.extend_from_slice(&2000u32.to_le_bytes());
    wav.extend_from_slice(&2u16.to_le_bytes());
    wav.extend_from_slice(&16u16.to_le_bytes());
    if extra_chunk {
        wav.extend_from_slice(b"JUNK");
        wav.extend_from_slice(&4u32.to_le_bytes());
        wav.extend_from_slice(&123u32.to_le_bytes());
    }
    wav
}

fn make_test_wav_with_invalid_chunk_size() -> Vec<u8> {
    let mut wav = make_test_wav(&[], 1000);
    wav[36..40].copy_from_slice(b"JUNK");
    wav[40..44].copy_from_slice(&1000u32.to_le_bytes());
    wav
}

fn make_test_wav_with_short_fmt_chunk() -> Vec<u8> {
    let mut wav = Vec::new();
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&12u32.to_le_bytes());
    wav.extend_from_slice(b"WAVE");
    wav.extend_from_slice(b"fmt ");
    wav.extend_from_slice(&4u32.to_le_bytes());
    wav.extend_from_slice(&1u32.to_le_bytes());
    wav
}

fn make_test_wav_with_data_only() -> Vec<u8> {
    let mut wav = Vec::new();
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&40u32.to_le_bytes());
    wav.extend_from_slice(b"WAVE");
    wav.extend_from_slice(b"data");
    wav.extend_from_slice(&2u32.to_le_bytes());
    wav.extend_from_slice(&100i16.to_le_bytes());
    wav
}

fn samples_from_wav(data: &[u8]) -> Vec<i16> {
    let data_offset = data
        .windows(4)
        .position(|window| window == b"data")
        .expect("data chunk")
        + 8;
    data[data_offset..]
        .chunks_exact(2)
        .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]))
        .collect()
}

// ---------------------------------------------------------------------------
// errors.rs
// ---------------------------------------------------------------------------

#[test]
fn error_from_response_maps_all_known_status_codes() {
    let detail_resp = || {
        Some(ErrorResponse {
            detail: "boom".to_string(),
        })
    };

    let cases = [
        (400u16, "BadRequest"),
        (401, "Unauthorized"),
        (402, "PaymentRequired"),
        (403, "Forbidden"),
        (404, "NotFound"),
        (422, "ValidationError"),
        (429, "RateLimited"),
        (500, "ServerError"),
        (503, "ServerError"),
        (599, "ServerError"),
        (418, "Unknown"),
    ];

    for (code, name) in cases {
        let err = TypecastError::from_response(code, detail_resp());
        let variant_name = match err {
            TypecastError::BadRequest { .. } => "BadRequest",
            TypecastError::Unauthorized { .. } => "Unauthorized",
            TypecastError::PaymentRequired { .. } => "PaymentRequired",
            TypecastError::Forbidden { .. } => "Forbidden",
            TypecastError::NotFound { .. } => "NotFound",
            TypecastError::ValidationError { .. } => "ValidationError",
            TypecastError::RateLimited { .. } => "RateLimited",
            TypecastError::ServerError { .. } => "ServerError",
            TypecastError::Unknown { .. } => "Unknown",
            _ => "Other",
        };
        assert_eq!(variant_name, name, "for status {code}");
    }
}

#[test]
fn error_from_response_uses_default_detail_when_missing() {
    let err = TypecastError::from_response(400, None);
    match err {
        TypecastError::BadRequest { detail } => assert_eq!(detail, "Unknown error"),
        other => panic!("unexpected variant: {other:?}"),
    }
}

#[test]
fn error_predicate_methods_cover_every_variant() {
    let bad = TypecastError::BadRequest { detail: "x".into() };
    let unauth = TypecastError::Unauthorized { detail: "x".into() };
    let pay = TypecastError::PaymentRequired { detail: "x".into() };
    let forbid = TypecastError::Forbidden { detail: "x".into() };
    let nf = TypecastError::NotFound { detail: "x".into() };
    let val = TypecastError::ValidationError { detail: "x".into() };
    let rate = TypecastError::RateLimited { detail: "x".into() };
    let server = TypecastError::ServerError { detail: "x".into() };
    let unknown = TypecastError::Unknown {
        status_code: 418,
        detail: "x".into(),
    };

    assert!(bad.is_bad_request());
    assert!(!bad.is_unauthorized());
    assert!(unauth.is_unauthorized());
    assert!(pay.is_payment_required());
    assert!(forbid.is_forbidden());
    assert!(nf.is_not_found());
    assert!(val.is_validation_error());
    assert!(rate.is_rate_limited());
    assert!(server.is_server_error());

    assert_eq!(bad.status_code(), Some(400));
    assert_eq!(unauth.status_code(), Some(401));
    assert_eq!(pay.status_code(), Some(402));
    assert_eq!(forbid.status_code(), Some(403));
    assert_eq!(nf.status_code(), Some(404));
    assert_eq!(val.status_code(), Some(422));
    assert_eq!(rate.status_code(), Some(429));
    assert_eq!(server.status_code(), Some(500));
    assert_eq!(unknown.status_code(), Some(418));

    // Display impls (covers `#[error("...")]` formatters)
    assert!(bad.to_string().contains("Bad Request"));
    assert!(unauth.to_string().contains("Unauthorized"));
    assert!(pay.to_string().contains("Payment Required"));
    assert!(forbid.to_string().contains("Forbidden"));
    assert!(nf.to_string().contains("Not Found"));
    assert!(val.to_string().contains("Validation Error"));
    assert!(rate.to_string().contains("Too Many Requests"));
    assert!(server.to_string().contains("Internal Server Error"));
    assert!(unknown.to_string().contains("418"));
}

#[test]
fn error_status_code_is_none_for_transport_errors() {
    // serde_json::Error -> JsonError via From
    let json_err: serde_json::Error = serde_json::from_str::<i32>("not a number").unwrap_err();
    let err: TypecastError = json_err.into();
    assert!(matches!(err, TypecastError::JsonError(_)));
    assert_eq!(err.status_code(), None);
    assert!(err.to_string().contains("JSON error"));
    assert!(!err.is_bad_request());
    assert!(!err.is_unauthorized());
    assert!(!err.is_payment_required());
    assert!(!err.is_forbidden());
    assert!(!err.is_not_found());
    assert!(!err.is_validation_error());
    assert!(!err.is_rate_limited());
    assert!(!err.is_server_error());
}

#[tokio::test]
async fn error_status_code_is_none_for_http_errors() {
    // Build a client with a 1ms timeout pointed at a server that never replies
    // to force a reqwest::Error -> HttpError conversion.
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices")
        .with_status(200)
        .with_body("[]")
        // Long delay forces timeout.
        .with_chunked_body(|w| {
            std::thread::sleep(std::time::Duration::from_millis(500));
            w.write_all(b"[]")
        })
        .create_async()
        .await;

    let config = ClientConfig::new("k")
        .base_url(server.url())
        .timeout(Duration::from_millis(20));
    let client = TypecastClient::new(config).unwrap();
    let err = client.get_voices_v2(None).await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
    assert_eq!(err.status_code(), None);
    assert!(err.to_string().contains("HTTP error"));
}

// ---------------------------------------------------------------------------
// models.rs
// ---------------------------------------------------------------------------

#[test]
fn defaults_for_enums_and_structs() {
    assert_eq!(TTSModel::default(), TTSModel::SsfmV30);
    assert_eq!(EmotionPreset::default(), EmotionPreset::Normal);
    assert_eq!(AudioFormat::default(), AudioFormat::Wav);

    let out = Output::default();
    assert!(out.volume.is_none());
    assert!(out.target_lufs.is_none());
    assert!(out.audio_pitch.is_none());
    assert!(out.audio_tempo.is_none());
    assert!(out.audio_format.is_none());

    let p = Prompt::default();
    assert!(p.emotion_preset.is_none());
    assert!(p.emotion_intensity.is_none());

    let pp = PresetPrompt::default();
    assert_eq!(pp.emotion_type, "preset");

    let sp = SmartPrompt::default();
    assert_eq!(sp.emotion_type, "smart");

    let f = VoicesV2Filter::default();
    assert!(f.model.is_none());
}

#[test]
fn output_builder_clamps_values() {
    let out = Output::new()
        .volume(500)
        .audio_pitch(20)
        .audio_tempo(5.0)
        .audio_format(AudioFormat::Mp3);
    assert_eq!(out.volume, Some(200));
    assert_eq!(out.audio_pitch, Some(12));
    assert_eq!(out.audio_tempo, Some(2.0));
    assert_eq!(out.audio_format, Some(AudioFormat::Mp3));

    let out2 = Output::new()
        .volume(-10)
        .audio_pitch(-100)
        .audio_tempo(0.1)
        .target_lufs(-200.0);
    assert_eq!(out2.volume, Some(0));
    assert_eq!(out2.audio_pitch, Some(-12));
    assert_eq!(out2.audio_tempo, Some(0.5));
    assert_eq!(out2.target_lufs, Some(-70.0));

    let out3 = Output::new().target_lufs(50.0);
    assert_eq!(out3.target_lufs, Some(0.0));
}

#[test]
fn prompt_builders_clamp_intensity() {
    let p = Prompt::new()
        .emotion_preset(EmotionPreset::Happy)
        .emotion_intensity(5.0);
    assert_eq!(p.emotion_preset, Some(EmotionPreset::Happy));
    assert_eq!(p.emotion_intensity, Some(2.0));

    let p2 = Prompt::new().emotion_intensity(-1.0);
    assert_eq!(p2.emotion_intensity, Some(0.0));

    let pp = PresetPrompt::new()
        .emotion_preset(EmotionPreset::Sad)
        .emotion_intensity(10.0);
    assert_eq!(pp.emotion_preset, Some(EmotionPreset::Sad));
    assert_eq!(pp.emotion_intensity, Some(2.0));

    let sp = SmartPrompt::new()
        .previous_text("before")
        .next_text("after");
    assert_eq!(sp.previous_text.as_deref(), Some("before"));
    assert_eq!(sp.next_text.as_deref(), Some("after"));
}

#[test]
fn tts_prompt_from_conversions() {
    let basic: TTSPrompt = Prompt::new().into();
    let preset: TTSPrompt = PresetPrompt::new().into();
    let smart: TTSPrompt = SmartPrompt::new().into();
    assert!(matches!(basic, TTSPrompt::Basic(_)));
    assert!(matches!(preset, TTSPrompt::Preset(_)));
    assert!(matches!(smart, TTSPrompt::Smart(_)));
}

#[test]
fn tts_request_builder_sets_all_fields() {
    let req = TTSRequest::new("tc_voice", "hello", TTSModel::SsfmV21)
        .language("eng")
        .prompt(Prompt::new().emotion_preset(EmotionPreset::Angry))
        .output(Output::new().volume(100))
        .seed(7);
    assert_eq!(req.voice_id, "tc_voice");
    assert_eq!(req.text, "hello");
    assert_eq!(req.model, TTSModel::SsfmV21);
    assert_eq!(req.language.as_deref(), Some("eng"));
    assert!(req.prompt.is_some());
    assert!(req.output.is_some());
    assert_eq!(req.seed, Some(7));
}

#[test]
fn output_stream_builder_clamps_values() {
    let out = OutputStream::default();
    assert!(out.audio_pitch.is_none());
    assert!(out.audio_tempo.is_none());
    assert!(out.audio_format.is_none());

    let out = OutputStream::new()
        .audio_pitch(20)
        .audio_tempo(5.0)
        .audio_format(AudioFormat::Mp3);
    assert_eq!(out.audio_pitch, Some(12));
    assert_eq!(out.audio_tempo, Some(2.0));
    assert_eq!(out.audio_format, Some(AudioFormat::Mp3));

    let out2 = OutputStream::new().audio_pitch(-100).audio_tempo(0.1);
    assert_eq!(out2.audio_pitch, Some(-12));
    assert_eq!(out2.audio_tempo, Some(0.5));

    // Cover Debug + Clone
    let _ = format!("{out2:?}");
    let _ = out2.clone();

    // Ensure volume is NOT serialized and target_lufs is supported for OutputStream.
    let json = serde_json::to_string(
        &OutputStream::new()
            .audio_format(AudioFormat::Wav)
            .target_lufs(-14.0),
    )
    .unwrap();
    let value: serde_json::Value = serde_json::from_str(&json).unwrap();
    let obj = value.as_object().unwrap();
    assert!(obj.get("volume").is_none());
    assert_eq!(
        obj.get("target_lufs").and_then(|value| value.as_f64()),
        Some(-14.0)
    );
}

#[test]
fn tts_request_stream_builder_sets_all_fields() {
    let req = TTSRequestStream::new("tc_voice", "hello", TTSModel::SsfmV21)
        .language("eng")
        .prompt(Prompt::new().emotion_preset(EmotionPreset::Angry))
        .output(OutputStream::new().audio_format(AudioFormat::Mp3))
        .seed(7);
    assert_eq!(req.voice_id, "tc_voice");
    assert_eq!(req.text, "hello");
    assert_eq!(req.model, TTSModel::SsfmV21);
    assert_eq!(req.language.as_deref(), Some("eng"));
    assert!(req.prompt.is_some());
    assert!(req.output.is_some());
    assert_eq!(req.seed, Some(7));

    // Cover Debug + Clone
    let _ = format!("{req:?}");
    let _ = req.clone();

    // Default-only request (no optional builders) to cover the unset branches.
    let bare = TTSRequestStream::new("tc_x", "hi", TTSModel::SsfmV30);
    assert!(bare.language.is_none());
    assert!(bare.prompt.is_none());
    assert!(bare.output.is_none());
    assert!(bare.seed.is_none());
}

#[test]
fn voices_v2_filter_builder_sets_all_fields() {
    let f = VoicesV2Filter::new()
        .model(TTSModel::SsfmV30)
        .gender(Gender::Female)
        .age(Age::YoungAdult)
        .use_cases(UseCase::Audiobook);
    assert_eq!(f.model, Some(TTSModel::SsfmV30));
    assert_eq!(f.gender, Some(Gender::Female));
    assert_eq!(f.age, Some(Age::YoungAdult));
    assert!(f.use_cases.is_some());
}

#[test]
fn enums_serialize_with_expected_strings() {
    // Cover serde rename / rename_all branches.
    assert_eq!(
        serde_json::to_string(&TTSModel::SsfmV30).unwrap(),
        "\"ssfm-v30\""
    );
    assert_eq!(
        serde_json::to_string(&TTSModel::SsfmV21).unwrap(),
        "\"ssfm-v21\""
    );

    for emo in [
        EmotionPreset::Normal,
        EmotionPreset::Happy,
        EmotionPreset::Sad,
        EmotionPreset::Angry,
        EmotionPreset::Whisper,
        EmotionPreset::ToneUp,
        EmotionPreset::ToneDown,
    ] {
        let _ = serde_json::to_string(&emo).unwrap();
    }

    for fmt in [AudioFormat::Wav, AudioFormat::Mp3] {
        let _ = serde_json::to_string(&fmt).unwrap();
    }

    for g in [Gender::Male, Gender::Female] {
        let _ = serde_json::to_string(&g).unwrap();
    }

    for a in [
        Age::Child,
        Age::Teenager,
        Age::YoungAdult,
        Age::MiddleAge,
        Age::Elder,
    ] {
        let _ = serde_json::to_string(&a).unwrap();
    }

    for uc in [
        UseCase::Announcer,
        UseCase::Anime,
        UseCase::Audiobook,
        UseCase::Conversational,
        UseCase::Documentary,
        UseCase::ELearning,
        UseCase::Rapper,
        UseCase::Game,
        UseCase::TikTokReels,
        UseCase::News,
        UseCase::Podcast,
        UseCase::Voicemail,
        UseCase::Ads,
    ] {
        let _ = serde_json::to_string(&uc).unwrap();
    }

    let mi = ModelInfo {
        version: TTSModel::SsfmV30,
        emotions: vec!["happy".into()],
    };
    let _ = serde_json::to_string(&mi).unwrap();

    let voice = VoiceV2 {
        voice_id: "tc_x".into(),
        voice_name: "name".into(),
        models: vec![mi],
        gender: Some(Gender::Male),
        age: Some(Age::Elder),
        use_cases: Some(vec!["news".into()]),
    };
    let _ = serde_json::to_string(&voice).unwrap();

    // Cover Clone/Debug for ErrorResponse and TTSRequest.
    let er = ErrorResponse { detail: "x".into() };
    let _ = format!("{er:?}");
    let _ = er.clone();
}

// ---------------------------------------------------------------------------
// client.rs - construction and accessors
// ---------------------------------------------------------------------------

#[test]
fn client_config_default_reads_env_or_default() {
    // Don't set env to anything specific - just exercise the path.
    let cfg = ClientConfig::default();
    // Either env-driven or fallback default; we only assert it parses.
    assert!(!cfg.base_url.is_empty());
    assert_eq!(cfg.timeout, Duration::from_secs(DEFAULT_TIMEOUT_SECS));
}

#[test]
fn client_config_new_and_builders() {
    let cfg = ClientConfig::new("k")
        .base_url("http://example.com")
        .timeout(Duration::from_secs(10));
    assert_eq!(cfg.api_key, "k");
    assert_eq!(cfg.base_url, "http://example.com");
    assert_eq!(cfg.timeout, Duration::from_secs(10));
    let _ = format!("{cfg:?}"); // Debug
    let _ = cfg.clone();
}

#[test]
fn client_with_api_key_and_accessors() {
    let client = TypecastClient::with_api_key("abcdefghij").unwrap();
    assert_eq!(client.api_key_masked(), "abcd...ghij");
    let _ = client.base_url();
    let _ = format!("{client:?}");
    let _ = client.clone();
}

#[test]
fn client_api_key_masked_short_key() {
    let client = TypecastClient::with_api_key("short").unwrap();
    assert_eq!(client.api_key_masked(), "****");
}

#[test]
fn client_new_rejects_invalid_api_key_header() {
    // Newline characters are not valid in HTTP header values.
    let result = TypecastClient::with_api_key("bad\nkey");
    let err = result.unwrap_err();
    assert!(err.is_bad_request());
    assert!(err.to_string().contains("Invalid API key format"));
}

#[test]
fn client_new_rejects_empty_api_key_for_default_host() {
    let result = TypecastClient::new(ClientConfig {
        api_key: "   ".to_string(),
        base_url: format!("{DEFAULT_BASE_URL}/"),
        timeout: Duration::from_secs(DEFAULT_TIMEOUT_SECS),
    });
    let err = result.unwrap_err();
    assert!(err.is_unauthorized());
    assert!(err.to_string().contains("Invalid or missing API key"));
}

#[test]
fn client_from_env_uses_default_config() {
    // Even with no env, default api key may be empty - just validate it parses
    // when we set a value. We rely on the existing value (or empty) being a
    // valid header string; empty string is acceptable for HeaderValue.
    let prev = std::env::var("TYPECAST_API_KEY").ok();
    std::env::set_var("TYPECAST_API_KEY", "env-key");
    let client = TypecastClient::from_env().expect("from_env should succeed");
    assert!(client.base_url().starts_with("http"));
    // Restore
    match prev {
        Some(v) => std::env::set_var("TYPECAST_API_KEY", v),
        None => std::env::remove_var("TYPECAST_API_KEY"),
    }

    // Sanity-check that DEFAULT_BASE_URL is the public re-export.
    assert!(DEFAULT_BASE_URL.starts_with("http"));
}

// ---------------------------------------------------------------------------
// client.rs - text_to_speech
// ---------------------------------------------------------------------------

#[tokio::test]
async fn text_to_speech_returns_wav_with_duration_header() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_header("X-Audio-Duration", "1.25")
        .with_body(b"RIFFwavfakebody")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequest::new("tc_x", "hello", TTSModel::SsfmV30);
    let resp = client.text_to_speech(&req).await.unwrap();
    assert_eq!(resp.format, AudioFormat::Wav);
    assert!((resp.duration - 1.25).abs() < f64::EPSILON);
    assert_eq!(&resp.audio_data[..4], b"RIFF");
    let _ = format!("{resp:?}");
    let _ = resp.clone();
}

#[tokio::test]
async fn text_to_speech_with_proxy_base_url_omits_auth_header_without_api_key() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .match_header("x-api-key", mockito::Matcher::Missing)
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(b"RIFFproxy")
        .create_async()
        .await;

    let config = ClientConfig {
        api_key: String::new(),
        base_url: server.url(),
        timeout: Duration::from_secs(5),
    };
    let client = TypecastClient::new(config).unwrap();
    let req = TTSRequest::new("tc_x", "hello proxy", TTSModel::SsfmV30);
    let resp = client.text_to_speech(&req).await.unwrap();
    assert_eq!(resp.format, AudioFormat::Wav);
}

#[tokio::test]
async fn text_to_speech_returns_mp3_when_content_type_says_mp3() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/mp3")
        .with_body(b"mp3data")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequest::new("tc_x", "hi", TTSModel::SsfmV30);
    let resp = client.text_to_speech(&req).await.unwrap();
    assert_eq!(resp.format, AudioFormat::Mp3);
    assert_eq!(resp.duration, 0.0);
}

#[tokio::test]
async fn text_to_speech_returns_mp3_for_audio_mpeg() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/mpeg")
        .with_body(b"mpegdata")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequest::new("tc_x", "hi", TTSModel::SsfmV30)
        .language("eng")
        .prompt(SmartPrompt::new().previous_text("a"))
        .output(Output::new().audio_format(AudioFormat::Mp3))
        .seed(11);
    let resp = client.text_to_speech(&req).await.unwrap();
    assert_eq!(resp.format, AudioFormat::Mp3);
}

#[tokio::test]
async fn text_to_speech_propagates_api_errors() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(401)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"bad key"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequest::new("tc_x", "hi", TTSModel::SsfmV30);
    let err = client.text_to_speech(&req).await.unwrap_err();
    assert!(err.is_unauthorized());
}

#[tokio::test]
async fn text_to_speech_handles_error_with_unparseable_body() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(500)
        .with_header("content-type", "text/plain")
        .with_body("internal boom")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequest::new("tc_x", "hi", TTSModel::SsfmV30);
    let err = client.text_to_speech(&req).await.unwrap_err();
    assert!(err.is_server_error());
}

#[tokio::test]
async fn generate_to_file_infers_mp3_default_model_and_writes_file() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::AllOf(vec![
            mockito::Matcher::Regex(r#""model":"ssfm-v30""#.into()),
            mockito::Matcher::Regex(r#""audio_format":"mp3""#.into()),
            mockito::Matcher::Regex(r#""language":"eng""#.into()),
            mockito::Matcher::Regex(r#""seed":7"#.into()),
        ]))
        .with_status(200)
        .with_header("content-type", "audio/mp3")
        .with_body(b"MP3DATA")
        .create_async()
        .await;

    let client = make_client(&server);
    let path = std::env::temp_dir().join(format!("typecast-rust-{}.mp3", std::process::id()));
    let _ = fs::remove_file(&path);
    let resp = client
        .generate_to_file(
            &path,
            GenerateToFileRequest::new("tc_x", "hello")
                .language("eng")
                .prompt(PresetPrompt::new().emotion_preset(EmotionPreset::Happy))
                .seed(7),
        )
        .await
        .unwrap();

    assert_eq!(resp.format, AudioFormat::Mp3);
    assert_eq!(fs::read(&path).unwrap(), b"MP3DATA");
    let _ = fs::remove_file(path);
}

#[test]
fn generate_to_file_request_new_validates_required_fields() {
    assert!(std::panic::catch_unwind(|| GenerateToFileRequest::new("", "hello")).is_err());
    assert!(std::panic::catch_unwind(|| GenerateToFileRequest::new("   ", "hello")).is_err());
    assert!(std::panic::catch_unwind(|| GenerateToFileRequest::new("tc_x", "")).is_err());
    assert!(std::panic::catch_unwind(|| GenerateToFileRequest::new("tc_x", "   ")).is_err());
    let long_text = "가".repeat(2001);
    assert!(std::panic::catch_unwind(|| GenerateToFileRequest::new("tc_x", long_text)).is_err());
}

#[tokio::test]
async fn generate_to_file_keeps_explicit_output_and_covers_extensions() {
    let mut server = Server::new_async().await;
    let _m1 = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::Regex(r#""audio_format":"mp3""#.into()))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(b"WAV")
        .create_async()
        .await;
    let _m2 = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::Regex(r#""model":"ssfm-v21""#.into()))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(b"WAV")
        .create_async()
        .await;
    let _m3 = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::Regex(r#""audio_format":"wav""#.into()))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(b"WAV")
        .create_async()
        .await;
    let _m4 = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::Regex(r#""model":"ssfm-v30""#.into()))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(b"WAV")
        .create_async()
        .await;

    let client = make_client(&server);
    let wav_path = std::env::temp_dir().join(format!("typecast-rust-{}.wav", std::process::id()));
    let bin_path = std::env::temp_dir().join(format!("typecast-rust-{}.bin", std::process::id()));
    let no_extension_path =
        std::env::temp_dir().join(format!("typecast-rust-noext-{}", std::process::id()));
    let _ = fs::remove_file(&wav_path);
    let _ = fs::remove_file(&bin_path);
    let _ = fs::remove_file(&no_extension_path);

    client
        .generate_to_file(
            &wav_path,
            GenerateToFileRequest::new("tc_x", "hello")
                .model(TTSModel::SsfmV21)
                .output(Output::new().audio_format(AudioFormat::Mp3)),
        )
        .await
        .unwrap();
    client
        .generate_to_file(
            &bin_path,
            GenerateToFileRequest::new("tc_x", "hello").model(TTSModel::SsfmV21),
        )
        .await
        .unwrap();
    client
        .generate_to_file(
            &wav_path,
            GenerateToFileRequest::new("tc_x", "hello").output(Output::new()),
        )
        .await
        .unwrap();
    client
        .generate_to_file(
            &no_extension_path,
            GenerateToFileRequest::new("tc_x", "hello"),
        )
        .await
        .unwrap();

    let _ = fs::remove_file(wav_path);
    let _ = fs::remove_file(bin_path);
    let _ = fs::remove_file(no_extension_path);

    let request = GenerateToFileRequest::new("tc_x", "hello")
        .model(TTSModel::SsfmV21)
        .output(Output::new());
    let tts = request.into_tts_request();
    assert_eq!(tts.model, TTSModel::SsfmV21);
    assert!(tts.output.unwrap().audio_format.is_none());
}

#[tokio::test]
async fn generate_to_file_with_explicit_format_hits_no_inference_branch() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::Regex(r#""audio_format":"mp3""#.into()))
        .with_status(200)
        .with_header("content-type", "audio/mp3")
        .with_body(b"MP3")
        .create_async()
        .await;

    let client = make_client(&server);
    let path =
        std::env::temp_dir().join(format!("typecast-rust-explicit-{}.wav", std::process::id()));
    let _ = fs::remove_file(&path);
    client
        .generate_to_file(
            &path,
            GenerateToFileRequest::new("tc_x", "hello")
                .output(Output::new().audio_format(AudioFormat::Mp3)),
        )
        .await
        .unwrap();
    let _ = fs::remove_file(path);
}

#[tokio::test]
async fn generate_to_file_propagates_api_and_write_errors() {
    let mut server = Server::new_async().await;
    let _m1 = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(401)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"bad key"}"#)
        .create_async()
        .await;
    let _m2 = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(b"WAV")
        .create_async()
        .await;

    let client = make_client(&server);
    let path = std::env::temp_dir().join(format!("typecast-rust-error-{}.wav", std::process::id()));
    let err = client
        .generate_to_file(&path, GenerateToFileRequest::new("tc_x", "hello"))
        .await
        .unwrap_err();
    assert!(err.is_unauthorized());

    let dir = std::env::temp_dir();
    let err = client
        .generate_to_file(&dir, GenerateToFileRequest::new("tc_x", "hello"))
        .await
        .unwrap_err();
    assert!(matches!(err, TypecastError::IoError(_)));
}

#[tokio::test]
async fn compose_speech_composes_wav_and_merges_overrides() {
    let mut server = Server::new_async().await;
    let _m1 = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::AllOf(vec![
            mockito::Matcher::Regex(r#""text":"Hello""#.into()),
            mockito::Matcher::Regex(r#""voice_id":"voice-b""#.into()),
            mockito::Matcher::Regex(r#""audio_format":"wav""#.into()),
            mockito::Matcher::Regex(r#""audio_pitch":1"#.into()),
            mockito::Matcher::Regex(r#""audio_tempo":1.1"#.into()),
        ]))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(make_test_wav(&[0, 1000, 2000, 0], 1000))
        .create_async()
        .await;
    let _m2 = server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::AllOf(vec![
            mockito::Matcher::Regex(r#""text":"world""#.into()),
            mockito::Matcher::Regex(r#""voice_id":"voice-b""#.into()),
            mockito::Matcher::Regex(r#""audio_format":"wav""#.into()),
        ]))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(make_test_wav(&[0, -1000, -2000, 0], 1000))
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
                .prompt(
                    Prompt::new()
                        .emotion_preset(EmotionPreset::Happy)
                        .emotion_intensity(1.0),
                )
                .output(Output::new().audio_format(AudioFormat::Wav).audio_pitch(1))
                .seed(123),
        )
        .say_with(
            "Hello<|0.001s|>world",
            ComposerSettings::new()
                .voice_id("voice-b")
                .prompt(
                    PresetPrompt::new()
                        .emotion_preset(EmotionPreset::Sad)
                        .emotion_intensity(0.5),
                )
                .output(Output::new().volume(80).audio_tempo(1.1)),
        )
        .generate()
        .await
        .unwrap();

    assert_eq!(response.format, AudioFormat::Wav);
    assert_eq!(
        samples_from_wav(&response.audio_data),
        vec![1000, 2000, 0, -1000, -2000]
    );
    assert!((response.duration - 0.005).abs() < 0.0001);
}

#[tokio::test]
async fn compose_speech_validates_before_network() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .expect(0)
        .create_async()
        .await;
    let client = make_client(&server);

    let err = client
        .compose_speech()
        .say("Hello")
        .generate()
        .await
        .unwrap_err();
    assert!(matches!(err, TypecastError::ValidationError { .. }));
    assert!(err.to_string().contains("voice_id is required"));

    let err = client
        .compose_speech()
        .pause(0.0)
        .generate()
        .await
        .unwrap_err();
    assert!(err
        .to_string()
        .contains("pause seconds must be greater than 0"));

    let err = client.compose_speech().generate().await.unwrap_err();
    assert!(err
        .to_string()
        .contains("at least one speech segment is required"));

    let err = client
        .compose_speech()
        .defaults(ComposerSettings::new().voice_id("voice-a"))
        .say("Hello")
        .generate()
        .await
        .unwrap_err();
    assert!(err.to_string().contains("model is required"));

    let err = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("voice-a")
                .model(TTSModel::SsfmV30),
        )
        .pause(0.1)
        .say("Hello")
        .generate()
        .await
        .unwrap_err();
    assert!(err.to_string().contains("pause cannot be the first"));

    let err = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("")
                .model(TTSModel::SsfmV30),
        )
        .say("Hello")
        .generate()
        .await
        .unwrap_err();
    assert!(err.to_string().contains("voice_id is required"));
}

#[test]
fn parse_pause_markup_is_lenient_for_invalid_tokens() {
    let parts =
        parse_pause_markup("a<|0.3s|>b<|abc|>c<|s|>d<|.3s|>e<|3.s|>f<|3..1s|>g<|3xs|>h<|3s|>");

    assert_eq!(
        parts,
        vec![
            SpeechPart::Text("a".to_string()),
            SpeechPart::Pause(0.3),
            SpeechPart::Text("b<|abc|>c<|s|>d<|.3s|>e<|3.s|>f<|3..1s|>g<|3xs|>h".to_string()),
            SpeechPart::Pause(3.0),
        ]
    );
    assert_eq!(
        parse_pause_markup("hello<|0.3s"),
        vec![SpeechPart::Text("hello<|0.3s".to_string())]
    );
}

#[tokio::test]
async fn compose_speech_rejects_bad_wav_mismatched_specs_and_mp3() {
    let malformed_cases = vec![
        b"not wav".to_vec(),
        {
            let mut wav = make_test_wav(&[100], 1000);
            wav[0..4].copy_from_slice(b"NOPE");
            wav
        },
        {
            let mut wav = make_test_wav(&[100], 1000);
            wav[8..12].copy_from_slice(b"NOPE");
            wav
        },
        make_test_wav_with_format(&[100], 1000, 2, 1, 16),
        make_test_wav_with_format(&[100], 1000, 1, 2, 16),
        make_test_wav_with_format(&[100], 1000, 1, 1, 8),
        make_test_wav_with_short_fmt_chunk(),
        make_test_wav_with_invalid_chunk_size(),
        make_test_wav_with_data_only(),
        make_test_wav_without_data(true),
        make_test_wav_without_data(false),
    ];
    for wav in malformed_cases {
        let mut bad_server = Server::new_async().await;
        let _bad = bad_server
            .mock("POST", "/v1/text-to-speech")
            .with_status(200)
            .with_header("content-type", "audio/wav")
            .with_body(wav)
            .create_async()
            .await;
        let client = make_client(&bad_server);
        let err = client
            .compose_speech()
            .defaults(
                ComposerSettings::new()
                    .voice_id("voice-a")
                    .model(TTSModel::SsfmV30),
            )
            .say("Hello")
            .generate()
            .await
            .unwrap_err();
        assert!(
            err.to_string().contains("unsupported WAV data")
                || err
                    .to_string()
                    .contains("only mono 16-bit PCM WAV is supported")
        );
    }

    let mut mismatch_server = Server::new_async().await;
    let _m1 = mismatch_server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(make_test_wav(&[1000], 1000))
        .create_async()
        .await;
    let _m2 = mismatch_server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(make_test_wav(&[1000], 2000))
        .create_async()
        .await;
    let client = make_client(&mismatch_server);
    let err = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("voice-a")
                .model(TTSModel::SsfmV30),
        )
        .say("one<|0.001s|>two")
        .generate()
        .await
        .unwrap_err();
    assert!(err.to_string().contains("same PCM format"));

    let mut mp3_server = Server::new_async().await;
    let _mp3 = mp3_server
        .mock("POST", "/v1/text-to-speech")
        .match_body(mockito::Matcher::Regex(r#""audio_format":"wav""#.into()))
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(make_test_wav(&[1000], 1000))
        .create_async()
        .await;
    let client = make_client(&mp3_server);
    let err = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("voice-a")
                .model(TTSModel::SsfmV30)
                .output(Output::new().audio_format(AudioFormat::Mp3)),
        )
        .say("Hello")
        .generate()
        .await
        .unwrap_err();
    assert!(err.to_string().contains("ffmpeg is required"));
}

#[tokio::test]
async fn compose_speech_ignores_blank_text_left_by_pause_markup() {
    let server = Server::new_async().await;
    let client = make_client(&server);

    let err = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("voice-a")
                .model(TTSModel::SsfmV30),
        )
        .say("   <|0.1s|>")
        .generate()
        .await
        .unwrap_err();

    assert!(matches!(err, TypecastError::ValidationError { .. }));
}

#[tokio::test]
async fn compose_speech_trims_all_zero_segments_to_empty_audio() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(make_test_wav(&[0, 0], 1000))
        .create_async()
        .await;
    let client = make_client(&server);
    let response = client
        .compose_speech()
        .defaults(
            ComposerSettings::new()
                .voice_id("voice-a")
                .model(TTSModel::SsfmV30),
        )
        .say("Silence")
        .generate()
        .await
        .unwrap();

    assert_eq!(samples_from_wav(&response.audio_data), Vec::<i16>::new());
    assert_eq!(response.duration, 0.0);
}

// ---------------------------------------------------------------------------
// client.rs - text_to_speech_stream
// ---------------------------------------------------------------------------

#[tokio::test]
async fn text_to_speech_stream_returns_chunked_bytes() {
    let mut server = Server::new_async().await;
    let body = b"RIFFwavfakebodychunk1chunk2";
    let _m = server
        .mock("POST", "/v1/text-to-speech/stream")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_body(body)
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestStream::new("tc_x", "hello", TTSModel::SsfmV30)
        .output(OutputStream::new().audio_format(AudioFormat::Wav));
    let mut stream = client.text_to_speech_stream(&req).await.unwrap();

    let mut collected: Vec<u8> = Vec::new();
    let mut chunk_count: usize = 0;
    while let Some(chunk) = stream.next().await {
        let bytes = chunk.unwrap();
        collected.extend_from_slice(&bytes);
        chunk_count += 1;
    }
    assert_eq!(collected, body);
    assert!(chunk_count >= 1, "expected at least one chunk from stream");
}

#[tokio::test]
async fn text_to_speech_stream_maps_all_error_statuses() {
    let cases: &[(u16, fn(&TypecastError) -> bool)] = &[
        (400, TypecastError::is_bad_request),
        (401, TypecastError::is_unauthorized),
        (402, TypecastError::is_payment_required),
        (404, TypecastError::is_not_found),
        (422, TypecastError::is_validation_error),
        (429, TypecastError::is_rate_limited),
        (500, TypecastError::is_server_error),
    ];

    for (status, predicate) in cases {
        let mut server = Server::new_async().await;
        let _m = server
            .mock("POST", "/v1/text-to-speech/stream")
            .with_status(*status as usize)
            .with_header("content-type", "application/json")
            .with_body(r#"{"detail":"nope"}"#)
            .create_async()
            .await;

        let client = make_client(&server);
        let req = TTSRequestStream::new("tc_x", "hi", TTSModel::SsfmV30);
        let err = match client.text_to_speech_stream(&req).await {
            Ok(_) => panic!("expected error for status {status}"),
            Err(e) => e,
        };
        assert!(predicate(&err), "status {status} did not map correctly");
    }
}

#[tokio::test]
async fn text_to_speech_stream_handles_error_with_unparseable_body() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech/stream")
        .with_status(500)
        .with_header("content-type", "text/plain")
        .with_body("internal boom")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestStream::new("tc_x", "hi", TTSModel::SsfmV30);
    let err = match client.text_to_speech_stream(&req).await {
        Ok(_) => panic!("expected server error"),
        Err(e) => e,
    };
    assert!(err.is_server_error());
}

#[tokio::test]
async fn text_to_speech_stream_chunk_error_when_connection_drops() {
    // Mock a 200 OK that closes the connection mid-body so consuming the
    // stream yields a chunk-level transport error mapped through `From`.
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech/stream")
        .with_status(200)
        .with_header("content-type", "audio/wav")
        .with_header("content-length", "1024")
        .with_body(b"truncated")
        .create_async()
        .await;

    let client = make_client(&server);
    let req = TTSRequestStream::new("tc_x", "hi", TTSModel::SsfmV30);

    // The truncated body may surface either as an error from the initial
    // `send().await` (caught by `?` and mapped via `From<reqwest::Error>`),
    // or as a per-chunk error when the stream is consumed. Both paths must
    // resolve to a transport HttpError.
    let mut saw_error = false;
    match client.text_to_speech_stream(&req).await {
        Err(e) => {
            assert!(matches!(e, TypecastError::HttpError(_)));
            saw_error = true;
        }
        Ok(mut stream) => {
            while let Some(item) = stream.next().await {
                if let Err(e) = item {
                    assert!(matches!(e, TypecastError::HttpError(_)));
                    saw_error = true;
                    break;
                }
            }
        }
    }
    assert!(saw_error, "expected a transport error from truncated body");
}

// ---------------------------------------------------------------------------
// client.rs - get_voices_v2
// ---------------------------------------------------------------------------

#[tokio::test]
async fn get_voices_v2_no_filter_returns_list() {
    let mut server = Server::new_async().await;
    let body = r#"[{
        "voice_id":"tc_a",
        "voice_name":"Alice",
        "models":[{"version":"ssfm-v30","emotions":["normal"]}],
        "gender":"female",
        "age":"young_adult",
        "use_cases":["news"]
    }]"#;
    let _m = server
        .mock("GET", "/v2/voices")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(body)
        .create_async()
        .await;

    let client = make_client(&server);
    let voices = client.get_voices_v2(None).await.unwrap();
    assert_eq!(voices.len(), 1);
    assert_eq!(voices[0].voice_id, "tc_a");
}

#[tokio::test]
async fn get_voices_v2_with_full_filter_appends_query_params() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices")
        .match_query(mockito::Matcher::AllOf(vec![
            mockito::Matcher::UrlEncoded("model".into(), "ssfm-v30".into()),
            mockito::Matcher::UrlEncoded("gender".into(), "male".into()),
            mockito::Matcher::UrlEncoded("age".into(), "elder".into()),
        ]))
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("[]")
        .create_async()
        .await;

    let client = make_client(&server);
    let filter = VoicesV2Filter::new()
        .model(TTSModel::SsfmV30)
        .gender(Gender::Male)
        .age(Age::Elder)
        .use_cases(UseCase::News);
    let voices = client.get_voices_v2(Some(filter)).await.unwrap();
    assert!(voices.is_empty());
}

#[tokio::test]
async fn get_voices_v2_propagates_api_errors() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices")
        .with_status(429)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"slow down"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_voices_v2(None).await.unwrap_err();
    assert!(err.is_rate_limited());
}

// ---------------------------------------------------------------------------
// client.rs - get_voice_v2
// ---------------------------------------------------------------------------

#[tokio::test]
async fn get_voice_v2_returns_voice() {
    let mut server = Server::new_async().await;
    let body = r#"{
        "voice_id":"tc_a",
        "voice_name":"Alice",
        "models":[{"version":"ssfm-v21","emotions":[]}]
    }"#;
    let _m = server
        .mock("GET", "/v2/voices/tc_a")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(body)
        .create_async()
        .await;

    let client = make_client(&server);
    let voice = client.get_voice_v2("tc_a").await.unwrap();
    assert_eq!(voice.voice_id, "tc_a");
}

#[tokio::test]
async fn get_voice_v2_propagates_404() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices/missing")
        .with_status(404)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"not found"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_voice_v2("missing").await.unwrap_err();
    assert!(err.is_not_found());
}

// ---------------------------------------------------------------------------
// client.rs - urlencoding helper
// ---------------------------------------------------------------------------

#[tokio::test]
async fn get_voices_v2_filter_covers_every_enum_variant() {
    // Each filter call must hit a fresh mock; use a regex matcher that
    // accepts any query string so we can iterate through every enum value
    // and exercise every helper match arm.
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", mockito::Matcher::Regex("^/v2/voices".into()))
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("[]")
        .expect_at_least(1)
        .create_async()
        .await;

    let client = make_client(&server);

    // Models
    for model in [TTSModel::SsfmV30, TTSModel::SsfmV21] {
        let f = VoicesV2Filter::new().model(model);
        client.get_voices_v2(Some(f)).await.unwrap();
    }
    // Genders
    for g in [Gender::Male, Gender::Female] {
        let f = VoicesV2Filter::new().gender(g);
        client.get_voices_v2(Some(f)).await.unwrap();
    }
    // Ages
    for a in [
        Age::Child,
        Age::Teenager,
        Age::YoungAdult,
        Age::MiddleAge,
        Age::Elder,
    ] {
        let f = VoicesV2Filter::new().age(a);
        client.get_voices_v2(Some(f)).await.unwrap();
    }
    // Use cases
    for uc in [
        UseCase::Announcer,
        UseCase::Anime,
        UseCase::Audiobook,
        UseCase::Conversational,
        UseCase::Documentary,
        UseCase::ELearning,
        UseCase::Rapper,
        UseCase::Game,
        UseCase::TikTokReels,
        UseCase::News,
        UseCase::Podcast,
        UseCase::Voicemail,
        UseCase::Ads,
    ] {
        let f = VoicesV2Filter::new().use_cases(uc);
        client.get_voices_v2(Some(f)).await.unwrap();
    }
}

/// Bind a TCP listener and immediately drop it to obtain a port that is
/// almost certainly free. Connecting to it will produce a connection-refused
/// error, which lets us hit `.send().await?` failure paths.
fn dead_base_url() -> String {
    let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap();
    drop(listener);
    format!("http://{}", addr)
}

#[tokio::test]
async fn text_to_speech_send_error_when_connection_refused() {
    let config = ClientConfig::new("k")
        .base_url(dead_base_url())
        .timeout(Duration::from_secs(2));
    let client = TypecastClient::new(config).unwrap();
    let req = TTSRequest::new("tc_x", "hi", TTSModel::SsfmV30);
    let err = client.text_to_speech(&req).await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[tokio::test]
async fn get_voices_v2_send_error_when_connection_refused() {
    let config = ClientConfig::new("k")
        .base_url(dead_base_url())
        .timeout(Duration::from_secs(2));
    let client = TypecastClient::new(config).unwrap();
    let err = client.get_voices_v2(None).await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[tokio::test]
async fn get_voice_v2_send_error_when_connection_refused() {
    let config = ClientConfig::new("k")
        .base_url(dead_base_url())
        .timeout(Duration::from_secs(2));
    let client = TypecastClient::new(config).unwrap();
    let err = client.get_voice_v2("tc_a").await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[tokio::test]
async fn text_to_speech_send_error_on_timeout() {
    // Force a transport-level error from `.send().await?` by giving the
    // client a sub-millisecond timeout against a server that delays.
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/text-to-speech")
        .with_status(200)
        .with_chunked_body(|w| {
            std::thread::sleep(Duration::from_millis(500));
            w.write_all(b"x")
        })
        .create_async()
        .await;

    let config = ClientConfig::new("k")
        .base_url(server.url())
        .timeout(Duration::from_millis(20));
    let client = TypecastClient::new(config).unwrap();
    let req = TTSRequest::new("tc_x", "hi", TTSModel::SsfmV30);
    let err = client.text_to_speech(&req).await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[tokio::test]
async fn get_voice_v2_send_error_on_timeout() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices/tc_a")
        .with_status(200)
        .with_chunked_body(|w| {
            std::thread::sleep(Duration::from_millis(500));
            w.write_all(b"{}")
        })
        .create_async()
        .await;

    let config = ClientConfig::new("k")
        .base_url(server.url())
        .timeout(Duration::from_millis(20));
    let client = TypecastClient::new(config).unwrap();
    let err = client.get_voice_v2("tc_a").await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[tokio::test]
async fn get_voice_v2_propagates_invalid_json_body() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices/tc_a")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("not json at all")
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_voice_v2("tc_a").await.unwrap_err();
    // reqwest converts JSON parse failures into reqwest::Error.
    assert!(matches!(err, TypecastError::HttpError(_)));
}

// ---------------------------------------------------------------------------
// client.rs - get_my_subscription
// ---------------------------------------------------------------------------

#[tokio::test]
async fn get_my_subscription_returns_subscription() {
    let mut server = Server::new_async().await;
    let body = r#"{
        "plan":"plus",
        "credits":{"plan_credits":10000,"used_credits":2500},
        "limits":{"concurrency_limit":8}
    }"#;
    let _m = server
        .mock("GET", "/v1/users/me/subscription")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(body)
        .create_async()
        .await;

    let client = make_client(&server);
    let sub = client.get_my_subscription().await.unwrap();
    assert_eq!(sub.plan, PlanTier::Plus);
    assert_eq!(sub.credits.plan_credits, 10000);
    assert_eq!(sub.credits.used_credits, 2500);
    assert_eq!(sub.limits.concurrency_limit, 8);

    // Cover Debug/Clone/PartialEq for the new types.
    let _ = format!("{sub:?}");
    let cloned = sub.clone();
    assert_eq!(sub, cloned);
}

#[tokio::test]
async fn get_my_subscription_propagates_unauthorized() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v1/users/me/subscription")
        .with_status(401)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"bad key"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_my_subscription().await.unwrap_err();
    assert!(err.is_unauthorized());
}

#[tokio::test]
async fn get_my_subscription_propagates_rate_limited() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v1/users/me/subscription")
        .with_status(429)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"slow down"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_my_subscription().await.unwrap_err();
    assert!(err.is_rate_limited());
}

#[tokio::test]
async fn get_my_subscription_propagates_server_error() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v1/users/me/subscription")
        .with_status(500)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"boom"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_my_subscription().await.unwrap_err();
    assert!(err.is_server_error());
}

#[tokio::test]
async fn get_my_subscription_propagates_invalid_json_body() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v1/users/me/subscription")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("not json at all")
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client.get_my_subscription().await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[tokio::test]
async fn get_my_subscription_send_error_when_connection_refused() {
    let config = ClientConfig::new("k")
        .base_url(dead_base_url())
        .timeout(Duration::from_secs(2));
    let client = TypecastClient::new(config).unwrap();
    let err = client.get_my_subscription().await.unwrap_err();
    assert!(matches!(err, TypecastError::HttpError(_)));
}

#[test]
fn plan_tier_serializes_with_lowercase() {
    assert_eq!(serde_json::to_string(&PlanTier::Free).unwrap(), "\"free\"");
    assert_eq!(serde_json::to_string(&PlanTier::Lite).unwrap(), "\"lite\"");
    assert_eq!(serde_json::to_string(&PlanTier::Plus).unwrap(), "\"plus\"");
    assert_eq!(
        serde_json::to_string(&PlanTier::Custom).unwrap(),
        "\"custom\""
    );

    // Round-trip through Credits/Limits to cover their derives.
    let credits = Credits {
        plan_credits: 1,
        used_credits: 0,
    };
    let limits = Limits {
        concurrency_limit: 2,
    };
    let sub = SubscriptionResponse {
        plan: PlanTier::Free,
        credits: credits.clone(),
        limits: limits.clone(),
    };
    let json = serde_json::to_string(&sub).unwrap();
    let parsed: SubscriptionResponse = serde_json::from_str(&json).unwrap();
    assert_eq!(parsed, sub);
    let _ = format!("{credits:?}{limits:?}");
}

#[tokio::test]
async fn url_encoding_handles_special_characters_in_filter_values() {
    // Force the urlencoding helper's escape branch by going through a filter
    // value that contains characters outside the unreserved set. We can't pass
    // arbitrary strings to filters, but the helper is exercised by the
    // `use_cases=Tiktok/Reels` value (slash needs encoding) below.
    let mut server = Server::new_async().await;
    let _m = server
        .mock("GET", "/v2/voices")
        .match_query(mockito::Matcher::Regex("use_cases=Tiktok%2FReels".into()))
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("[]")
        .create_async()
        .await;

    let client = make_client(&server);
    let filter = VoicesV2Filter::new().use_cases(UseCase::TikTokReels);
    let voices = client.get_voices_v2(Some(filter)).await.unwrap();
    assert!(voices.is_empty());
}
