//! Integration tests for Typecast Rust SDK
//!
//! These tests require a valid TYPECAST_API_KEY environment variable.
//! Run with: TYPECAST_API_KEY=your_key cargo test --test integration_test

use typecast_rust::{
    AudioFormat, ClientConfig, EmotionPreset, Gender, Output, PresetPrompt, SmartPrompt,
    TTSModel, TTSRequest, TypecastClient, VoicesV2Filter,
};

fn get_client() -> TypecastClient {
    // Load .env file if present (for local development)
    let _ = dotenvy::dotenv();
    
    TypecastClient::from_env().expect("Failed to create client. Make sure TYPECAST_API_KEY is set")
}

fn get_test_voice_id() -> &'static str {
    // Using a well-known voice ID from the API
    "tc_60e5426de8b95f1d3000d7b5"
}

#[tokio::test]
async fn test_get_voices_v2() {
    let client = get_client();
    
    let voices = client.get_voices_v2(None).await.expect("Failed to get voices");
    
    assert!(!voices.is_empty(), "Should have at least one voice");
    
    // Check first voice has required fields
    let first_voice = &voices[0];
    assert!(!first_voice.voice_id.is_empty(), "voice_id should not be empty");
    assert!(!first_voice.voice_name.is_empty(), "voice_name should not be empty");
    assert!(!first_voice.models.is_empty(), "models should not be empty");
    
    println!("Found {} voices", voices.len());
    println!("First voice: {} ({})", first_voice.voice_name, first_voice.voice_id);
}

#[tokio::test]
async fn test_get_voices_v2_with_filter() {
    let client = get_client();
    
    let filter = VoicesV2Filter::new()
        .model(TTSModel::SsfmV30)
        .gender(Gender::Female);
    
    let voices = client.get_voices_v2(Some(filter)).await.expect("Failed to get filtered voices");
    
    assert!(!voices.is_empty(), "Should have at least one female voice with ssfm-v30");
    
    // Verify all returned voices are female
    for voice in &voices {
        if let Some(gender) = &voice.gender {
            assert_eq!(*gender, Gender::Female, "All voices should be female");
        }
    }
    
    println!("Found {} female voices with ssfm-v30", voices.len());
}

#[tokio::test]
async fn test_get_voice_v2_by_id() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let voice = client.get_voice_v2(voice_id).await.expect("Failed to get voice by ID");
    
    assert_eq!(voice.voice_id, voice_id, "Voice ID should match");
    assert!(!voice.voice_name.is_empty(), "voice_name should not be empty");
    assert!(!voice.models.is_empty(), "models should not be empty");
    
    println!("Voice: {} ({})", voice.voice_name, voice.voice_id);
    println!("Gender: {:?}, Age: {:?}", voice.gender, voice.age);
    println!("Use cases: {:?}", voice.use_cases);
    
    for model in &voice.models {
        println!("  Model {:?}: emotions = {:?}", model.version, model.emotions);
    }
}

#[tokio::test]
async fn test_get_voice_v2_not_found() {
    let client = get_client();
    
    let result = client.get_voice_v2("invalid_voice_id").await;
    
    assert!(result.is_err(), "Should return an error for invalid voice ID");
    
    let error = result.unwrap_err();
    // API may return different error codes for invalid voice IDs
    assert!(
        error.is_not_found() || error.is_validation_error() || error.is_bad_request(),
        "Should be NotFound, ValidationError, or BadRequest, got: {:?}", error
    );
    
    println!("Expected error: {}", error);
}

#[tokio::test]
async fn test_text_to_speech_basic() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "Hello, this is a test of the Typecast text to speech API.",
        TTSModel::SsfmV30,
    )
    .language("eng");
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    // Duration may be 0 if the header is not provided by the server
    assert!(response.duration >= 0.0, "Duration should be non-negative");
    assert_eq!(response.format, AudioFormat::Wav, "Default format should be WAV");
    
    println!("Generated audio: {} bytes, {:.2} seconds, format: {:?}", 
        response.audio_data.len(), 
        response.duration, 
        response.format
    );
    
    // Verify WAV header
    assert!(response.audio_data.len() > 44, "WAV file should have header");
    assert_eq!(&response.audio_data[0..4], b"RIFF", "Should start with RIFF");
}

#[tokio::test]
async fn test_text_to_speech_with_mp3_format() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "This is a test with MP3 output format.",
        TTSModel::SsfmV30,
    )
    .language("eng")
    .output(Output::new().audio_format(AudioFormat::Mp3));
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate MP3 speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    assert_eq!(response.format, AudioFormat::Mp3, "Format should be MP3");
    
    println!("Generated MP3: {} bytes, {:.2} seconds", 
        response.audio_data.len(), 
        response.duration
    );
}

#[tokio::test]
async fn test_text_to_speech_with_preset_emotion() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "I am so happy today! Everything is wonderful!",
        TTSModel::SsfmV30,
    )
    .language("eng")
    .prompt(PresetPrompt::new()
        .emotion_preset(EmotionPreset::Happy)
        .emotion_intensity(1.5));
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate happy speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    
    println!("Generated happy speech: {} bytes, {:.2} seconds", 
        response.audio_data.len(), 
        response.duration
    );
}

#[tokio::test]
async fn test_text_to_speech_with_smart_prompt() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "Everything is so incredibly perfect that I feel like I'm dreaming.",
        TTSModel::SsfmV30,
    )
    .language("eng")
    .prompt(SmartPrompt::new()
        .previous_text("I feel like I'm walking on air and I just want to scream with joy!")
        .next_text("I am literally bursting with happiness and I never want this feeling to end!"));
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate smart speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    
    println!("Generated smart speech: {} bytes, {:.2} seconds", 
        response.audio_data.len(), 
        response.duration
    );
}

#[tokio::test]
async fn test_text_to_speech_with_output_settings() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "Testing audio output settings with custom volume and tempo.",
        TTSModel::SsfmV30,
    )
    .language("eng")
    .output(Output::new()
        .volume(120)
        .audio_pitch(2)
        .audio_tempo(1.2));
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate customized speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    
    println!("Generated customized speech: {} bytes, {:.2} seconds", 
        response.audio_data.len(), 
        response.duration
    );
}

#[tokio::test]
async fn test_text_to_speech_with_seed() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "Testing reproducible output with a seed value.",
        TTSModel::SsfmV30,
    )
    .language("eng")
    .seed(42);
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate seeded speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    
    println!("Generated seeded speech: {} bytes, {:.2} seconds", 
        response.audio_data.len(), 
        response.duration
    );
}

#[tokio::test]
async fn test_text_to_speech_korean() {
    let client = get_client();
    let voice_id = get_test_voice_id();
    
    let request = TTSRequest::new(
        voice_id,
        "안녕하세요, 타입캐스트 텍스트 투 스피치 API 테스트입니다.",
        TTSModel::SsfmV30,
    )
    .language("kor");
    
    let response = client.text_to_speech(&request).await.expect("Failed to generate Korean speech");
    
    assert!(!response.audio_data.is_empty(), "Audio data should not be empty");
    
    println!("Generated Korean speech: {} bytes, {:.2} seconds", 
        response.audio_data.len(), 
        response.duration
    );
}

#[tokio::test]
async fn test_text_to_speech_invalid_voice() {
    let client = get_client();
    
    let request = TTSRequest::new(
        "invalid_voice_id",
        "This should fail.",
        TTSModel::SsfmV30,
    );
    
    let result = client.text_to_speech(&request).await;
    
    assert!(result.is_err(), "Should return an error for invalid voice ID");
    
    let error = result.unwrap_err();
    println!("Expected error: {}", error);
}

#[tokio::test]
async fn test_client_with_custom_config() {
    let config = ClientConfig::new(std::env::var("TYPECAST_API_KEY").expect("TYPECAST_API_KEY not set"))
        .timeout(std::time::Duration::from_secs(120));
    
    let client = TypecastClient::new(config).expect("Failed to create client with custom config");
    
    let voices = client.get_voices_v2(None).await.expect("Failed to get voices");
    
    assert!(!voices.is_empty(), "Should have at least one voice");
    println!("Client with custom config works! Found {} voices", voices.len());
}
