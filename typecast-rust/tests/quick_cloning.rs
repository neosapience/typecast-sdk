//! Integration tests for clone_voice and delete_voice.
//!
//! All tests use `mockito` for in-process HTTP mocking — no real API key
//! is required.

use mockito::Server;
use std::time::Duration;
use typecast_rust::{
    ClientConfig, TypecastClient, TypecastError, CLONING_MAX_FILE_SIZE, NAME_MAX_LENGTH,
    NAME_MIN_LENGTH,
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

fn small_wav() -> Vec<u8> {
    // Minimal 44-byte WAV header so the bytes look plausible.
    let mut buf = Vec::new();
    buf.extend_from_slice(b"RIFF");
    buf.extend_from_slice(&36u32.to_le_bytes()); // chunk size
    buf.extend_from_slice(b"WAVE");
    buf.extend_from_slice(b"fmt ");
    buf.extend_from_slice(&16u32.to_le_bytes()); // subchunk1 size
    buf.extend_from_slice(&1u16.to_le_bytes()); // PCM
    buf.extend_from_slice(&1u16.to_le_bytes()); // mono
    buf.extend_from_slice(&44100u32.to_le_bytes()); // sample rate
    buf.extend_from_slice(&88200u32.to_le_bytes()); // byte rate
    buf.extend_from_slice(&2u16.to_le_bytes()); // block align
    buf.extend_from_slice(&16u16.to_le_bytes()); // bits per sample
    buf.extend_from_slice(b"data");
    buf.extend_from_slice(&0u32.to_le_bytes()); // data size
    buf
}

fn clone_response_json() -> &'static str {
    r#"{"voice_id":"cv_abc123","name":"My Voice","model":"ssfm-v30"}"#
}

// ---------------------------------------------------------------------------
// Test 1: clone_voice returns CustomVoice on 200
// ---------------------------------------------------------------------------

#[tokio::test]
async fn clone_voice_returns_custom_voice() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/voices/clone")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(clone_response_json())
        .create_async()
        .await;

    let client = make_client(&server);
    let result = client
        .clone_voice(small_wav(), "sample.wav", "My Voice", "ssfm-v30")
        .await;

    let voice = result.expect("clone_voice should succeed");
    assert_eq!(voice.voice_id, "cv_abc123");
    assert_eq!(voice.name, "My Voice");
    assert_eq!(voice.model, "ssfm-v30");

    // Cover Debug + Clone + PartialEq for CustomVoice.
    let _ = format!("{voice:?}");
    let cloned = voice.clone();
    assert_eq!(voice, cloned);
}

// ---------------------------------------------------------------------------
// Test 2: clone_voice sends multipart body with correct parts
// ---------------------------------------------------------------------------

#[tokio::test]
async fn clone_voice_sends_multipart_body() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/voices/clone")
        // Content-Type header must start with "multipart/form-data".
        .match_header(
            "content-type",
            mockito::Matcher::Regex("^multipart/form-data".into()),
        )
        // The raw body must contain the text part values and the filename.
        .match_body(mockito::Matcher::AllOf(vec![
            mockito::Matcher::Regex("My Clone".into()),
            mockito::Matcher::Regex("ssfm-v21".into()),
            mockito::Matcher::Regex("sample.wav".into()),
        ]))
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(r#"{"voice_id":"cv_xyz","name":"My Clone","model":"ssfm-v21"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let result = client
        .clone_voice(small_wav(), "sample.wav", "My Clone", "ssfm-v21")
        .await;
    assert!(result.is_ok(), "expected Ok, got {result:?}");

    // The mock was configured to match the multipart content-type and body;
    // if the request did not match, mockito would have returned 501 and the
    // json() parse would have failed, not the assert above.
    _m.assert_async().await;
}

#[tokio::test]
async fn clone_voice_sets_supported_mime_types() {
    let cases = [
        ("sample.mp3", "audio/mpeg"),
        ("sample.ogg", "audio/ogg"),
        ("sample.flac", "audio/flac"),
        ("sample.m4a", "audio/mp4"),
        ("sample.bin", "application/octet-stream"),
    ];

    for (filename, expected_mime) in cases {
        let mut server = Server::new_async().await;
        let _m = server
            .mock("POST", "/v1/voices/clone")
            .match_body(mockito::Matcher::Regex(expected_mime.into()))
            .with_status(200)
            .with_header("content-type", "application/json")
            .with_body(clone_response_json())
            .create_async()
            .await;

        let client = make_client(&server);
        let result = client
            .clone_voice(small_wav(), filename, "My Voice", "ssfm-v30")
            .await;
        assert!(result.is_ok(), "expected Ok for {filename}, got {result:?}");
        _m.assert_async().await;
    }
}

#[tokio::test]
async fn clone_voice_returns_err_on_http_error() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/voices/clone")
        .with_status(422)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"invalid audio format"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client
        .clone_voice(small_wav(), "sample.wav", "My Voice", "ssfm-v30")
        .await
        .expect_err("should fail on 422");
    assert!(
        matches!(err, TypecastError::ValidationError { .. }),
        "expected ValidationError, got {err:?}"
    );
}

#[tokio::test]
async fn clone_voice_returns_err_on_malformed_success_json() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("POST", "/v1/voices/clone")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body("{")
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client
        .clone_voice(small_wav(), "sample.wav", "My Voice", "ssfm-v30")
        .await
        .expect_err("should fail on malformed JSON");
    assert!(matches!(err, TypecastError::HttpError(_)), "expected HttpError, got {err:?}");
}

#[tokio::test]
async fn clone_voice_returns_err_on_transport_failure() {
    let config = ClientConfig::new("test-api-key")
        .base_url("http://127.0.0.1:9")
        .timeout(Duration::from_millis(100));
    let client = TypecastClient::new(config).expect("client builds");

    let err = client
        .clone_voice(small_wav(), "sample.wav", "My Voice", "ssfm-v30")
        .await
        .expect_err("should fail when transport cannot connect");
    assert!(matches!(err, TypecastError::HttpError(_)), "expected HttpError, got {err:?}");
}

// ---------------------------------------------------------------------------
// Test 3: clone_voice rejects audio that exceeds 25 MB
// ---------------------------------------------------------------------------

#[tokio::test]
async fn clone_voice_rejects_oversized_audio() {
    // Allocate just over the 25 MB limit without making a real request.
    let oversized = vec![0u8; CLONING_MAX_FILE_SIZE + 1];

    // Server is created but no mock is registered — any real request would
    // yield a 501; the validation error must fire before the network call.
    let server = Server::new_async().await;
    let client = make_client(&server);

    let err = client
        .clone_voice(oversized, "big.wav", "Voice", "ssfm-v30")
        .await
        .expect_err("should reject oversized audio");

    assert!(
        matches!(err, TypecastError::ValidationError { .. }),
        "expected ValidationError, got {err:?}"
    );
    assert!(
        err.to_string().contains("25MB"),
        "error message should mention '25MB', got: {err}"
    );
}

// ---------------------------------------------------------------------------
// Test 4: clone_voice rejects names outside 1–30 characters
// ---------------------------------------------------------------------------

#[tokio::test]
async fn clone_voice_rejects_bad_name_length() {
    let server = Server::new_async().await;
    let client = make_client(&server);
    let audio = small_wav();

    // Empty name (below minimum).
    let err_empty = client
        .clone_voice(audio.clone(), "sample.wav", "", "ssfm-v30")
        .await
        .expect_err("empty name should fail");
    assert!(
        matches!(err_empty, TypecastError::ValidationError { .. }),
        "expected ValidationError for empty name, got {err_empty:?}"
    );
    assert!(
        err_empty.to_string().contains(&format!("{}-{}", NAME_MIN_LENGTH, NAME_MAX_LENGTH)),
        "error should mention valid range, got: {err_empty}"
    );

    // Name that is 31 characters (above maximum).
    let long_name = "a".repeat(NAME_MAX_LENGTH + 1);
    let err_long = client
        .clone_voice(audio.clone(), "sample.wav", &long_name, "ssfm-v30")
        .await
        .expect_err("31-char name should fail");
    assert!(
        matches!(err_long, TypecastError::ValidationError { .. }),
        "expected ValidationError for long name, got {err_long:?}"
    );
    assert!(
        err_long.to_string().contains(&format!("{}-{}", NAME_MIN_LENGTH, NAME_MAX_LENGTH)),
        "error should mention valid range, got: {err_long}"
    );
}

// ---------------------------------------------------------------------------
// Test 5: delete_voice returns Ok(()) on 204
// ---------------------------------------------------------------------------

#[tokio::test]
async fn delete_voice_returns_ok_on_204() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("DELETE", "/v1/voices/cv_abc123")
        .with_status(204)
        .create_async()
        .await;

    let client = make_client(&server);
    let result = client.delete_voice("cv_abc123").await;
    assert!(result.is_ok(), "expected Ok(()), got {result:?}");
}

// ---------------------------------------------------------------------------
// Test 6: delete_voice returns Err on 404
// ---------------------------------------------------------------------------

#[tokio::test]
async fn delete_voice_returns_err_on_404() {
    let mut server = Server::new_async().await;
    let _m = server
        .mock("DELETE", "/v1/voices/missing")
        .with_status(404)
        .with_header("content-type", "application/json")
        .with_body(r#"{"detail":"voice not found"}"#)
        .create_async()
        .await;

    let client = make_client(&server);
    let err = client
        .delete_voice("missing")
        .await
        .expect_err("should fail on 404");
    assert!(err.is_not_found(), "expected NotFound, got {err:?}");
}

#[tokio::test]
async fn delete_voice_returns_err_on_transport_failure() {
    let config = ClientConfig::new("test-api-key")
        .base_url("http://127.0.0.1:9")
        .timeout(Duration::from_millis(100));
    let client = TypecastClient::new(config).expect("client builds");

    let err = client
        .delete_voice("uc_xxx")
        .await
        .expect_err("should fail when transport cannot connect");
    assert!(matches!(err, TypecastError::HttpError(_)), "expected HttpError, got {err:?}");
}
