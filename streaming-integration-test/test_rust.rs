// Streaming TTS integration test for typecast-rust SDK.
use futures_util::StreamExt;
use std::io::Write;
use typecast_rust::{ClientConfig, TypecastClient, TTSRequestStream, TTSModel, OutputStream, AudioFormat};

const API_KEY: &str = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3";
const HOST: &str = "https://api.icepeak.in";
const VOICE_ID: &str = "tc_68d259f809700d8ac76e8567";
const OUTPUT_FILE: &str = "/tmp/streaming_test_rust.wav";

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let config = ClientConfig::new(API_KEY).base_url(HOST);
    let client = TypecastClient::new(config)?;

    let request = TTSRequestStream::new(VOICE_ID, "Hello, this is a streaming integration test from the Rust SDK.", TTSModel::SsfmV30)
        .language("eng")
        .output(OutputStream::new().audio_format(AudioFormat::Wav));

    println!("[Rust] Calling text_to_speech_stream...");
    let mut stream = client.text_to_speech_stream(&request).await?;
    let mut file = std::fs::File::create(OUTPUT_FILE)?;
    let mut total_bytes: usize = 0;
    let mut chunk_count: usize = 0;

    while let Some(chunk) = stream.next().await {
        let bytes = chunk?;
        file.write_all(&bytes)?;
        total_bytes += bytes.len();
        chunk_count += 1;
    }

    println!("[Rust] SUCCESS - {} chunks, {} bytes -> {}", chunk_count, total_bytes, OUTPUT_FILE);
    assert!(total_bytes > 0, "No audio data received");
    Ok(())
}
