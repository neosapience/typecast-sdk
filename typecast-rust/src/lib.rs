//! Typecast Rust SDK
//!
//! Official Rust SDK for the Typecast Text-to-Speech API.
//!
//! # Quick Start
//!
//! ```no_run
//! use typecast::{TypecastClient, TTSRequest, TTSModel, ClientConfig};
//!
//! #[tokio::main]
//! async fn main() -> typecast::Result<()> {
//!     // Create a client from environment variables
//!     let client = TypecastClient::from_env()?;
//!
//!     // Or create with explicit API key
//!     // let client = TypecastClient::with_api_key("your-api-key")?;
//!
//!     // Create a TTS request
//!     let request = TTSRequest::new(
//!         "tc_60e5426de8b95f1d3000d7b5",
//!         "Hello, world!",
//!         TTSModel::SsfmV30,
//!     );
//!
//!     // Generate speech
//!     let response = client.text_to_speech(&request).await?;
//!     
//!     // Save audio to file
//!     std::fs::write("output.wav", &response.audio_data)?;
//!     println!("Audio saved! Duration: {} seconds", response.duration);
//!
//!     Ok(())
//! }
//! ```
//!
//! # Features
//!
//! - **Text-to-Speech**: Convert text to natural-sounding speech
//! - **Multiple Models**: Support for ssfm-v21 and ssfm-v30 models
//! - **Emotion Control**: Preset emotions and smart context-aware inference
//! - **Voice Discovery**: List and filter available voices
//! - **Audio Customization**: Control volume, pitch, tempo, and format
//!
//! # Configuration
//!
//! The SDK can be configured using environment variables:
//!
//! - `TYPECAST_API_KEY`: Your Typecast API key (required)
//! - `TYPECAST_API_HOST`: Custom API host (optional, defaults to https://api.typecast.ai)
//!
//! # Error Handling
//!
//! All operations return a `Result<T, TypecastError>`. The error type provides
//! detailed information about what went wrong:
//!
//! ```no_run
//! use typecast::{TypecastClient, TTSRequest, TTSModel, TypecastError};
//!
//! # async fn example() -> typecast::Result<()> {
//! let client = TypecastClient::from_env()?;
//! let request = TTSRequest::new("invalid_voice", "Hello", TTSModel::SsfmV30);
//!
//! match client.text_to_speech(&request).await {
//!     Ok(response) => println!("Success!"),
//!     Err(TypecastError::NotFound { detail }) => {
//!         println!("Voice not found: {}", detail);
//!     }
//!     Err(TypecastError::Unauthorized { .. }) => {
//!         println!("Invalid API key");
//!     }
//!     Err(e) => println!("Other error: {}", e),
//! }
//! # Ok(())
//! # }
//! ```

pub mod client;
pub mod errors;
pub mod models;

// Re-export main types for convenience
pub use client::{ClientConfig, TypecastClient, DEFAULT_BASE_URL, DEFAULT_TIMEOUT_SECS};
pub use errors::{Result, TypecastError};
pub use models::{
    Age, AudioFormat, EmotionPreset, ErrorResponse, Gender, ModelInfo, Output, PresetPrompt,
    Prompt, SmartPrompt, TTSModel, TTSPrompt, TTSRequest, TTSResponse, UseCase, VoiceV2,
    VoicesV2Filter,
};
