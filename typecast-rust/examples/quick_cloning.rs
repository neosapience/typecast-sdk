//! Quick Voice Cloning example
//!
//! Clones a voice from a local audio file and optionally synthesizes speech
//! with the new voice.
//!
//! # Usage
//!
//! ```bash
//! # Clone from a WAV file with default model (ssfm-v30)
//! cargo run --example quick_cloning -- sample.wav "My Voice"
//!
//! # Specify model explicitly
//! cargo run --example quick_cloning -- sample.wav "My Voice" ssfm-v21
//! ```
//!
//! Set `TYPECAST_API_KEY` in your environment before running.

use std::env;
use typecast_rust::{TypecastClient, TypecastError};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = env::args().collect();
    if args.len() < 3 {
        eprintln!("Usage: {} <audio_file> <voice_name> [model]", args[0]);
        eprintln!("       model defaults to ssfm-v30");
        std::process::exit(1);
    }

    let audio_path = &args[1];
    let voice_name = &args[2];
    let model = args.get(3).map(String::as_str).unwrap_or("ssfm-v30");

    let client = TypecastClient::from_env().map_err(|e| {
        eprintln!("Failed to create client: {e}");
        eprintln!("Make sure TYPECAST_API_KEY is set.");
        e
    })?;

    // Read the audio file.
    let audio = std::fs::read(audio_path).map_err(|e| {
        eprintln!("Could not read audio file '{audio_path}': {e}");
        e
    })?;

    let filename = std::path::Path::new(audio_path)
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or(audio_path.as_str());

    println!("Cloning voice from '{audio_path}' ({} bytes) ...", audio.len());

    let voice = match client
        .clone_voice(audio, filename, voice_name, model)
        .await
    {
        Ok(v) => v,
        Err(TypecastError::ValidationError { detail }) => {
            eprintln!("Validation error: {detail}");
            std::process::exit(1);
        }
        Err(e) => return Err(e.into()),
    };

    println!("Voice cloned successfully!");
    println!("  voice_id : {}", voice.voice_id);
    println!("  name     : {}", voice.name);
    println!("  model    : {}", voice.model);
    println!();
    println!("To delete this voice later, run:");
    println!(
        "  TYPECAST_API_KEY=... cargo run --example quick_cloning -- delete {}",
        voice.voice_id
    );

    Ok(())
}
