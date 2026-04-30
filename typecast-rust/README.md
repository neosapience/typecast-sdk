# Typecast Rust SDK

[![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen.svg?style=flat-square)](../docs/coverage-policy.md)

Official Rust SDK for the [Typecast](https://typecast.ai/?lang=en) Text-to-Speech API.

## Installation

Add this to your `Cargo.toml`:

```toml
[dependencies]
typecast-rust = "0.1.0"
tokio = { version = "1", features = ["full"] }
```

## Quick Start

```rust
use typecast_rust::{TypecastClient, TTSRequest, TTSModel};

#[tokio::main]
async fn main() -> typecast_rust::Result<()> {
    // Create a client (reads TYPECAST_API_KEY from environment)
    let client = TypecastClient::from_env()?;

    // Create a TTS request
    let request = TTSRequest::new(
        "tc_60e5426de8b95f1d3000d7b5",  // voice_id
        "Hello, world!",                 // text
        TTSModel::SsfmV30,               // model
    );

    // Generate speech
    let response = client.text_to_speech(&request).await?;
    
    // Save to file
    std::fs::write("output.wav", &response.audio_data)?;
    println!("Audio saved! Duration: {:.2} seconds", response.duration);

    Ok(())
}
```

## Configuration

### Environment Variables

- `TYPECAST_API_KEY`: Your Typecast API key (required)
- `TYPECAST_API_HOST`: Custom API host (optional, defaults to `https://api.typecast.ai`)

### Custom Configuration

```rust
use typecast_rust::{TypecastClient, ClientConfig};
use std::time::Duration;

let config = ClientConfig::new("your-api-key")
    .base_url("https://api.typecast.ai")
    .timeout(Duration::from_secs(120));

let client = TypecastClient::new(config)?;
```

## Features

### Text-to-Speech

```rust
use typecast_rust::{TTSRequest, TTSModel, Output, AudioFormat};

// Basic request
let request = TTSRequest::new(
    "tc_60e5426de8b95f1d3000d7b5",
    "Hello, world!",
    TTSModel::SsfmV30,
);

// With language and output settings
let request = TTSRequest::new(
    "tc_60e5426de8b95f1d3000d7b5",
    "Hello, world!",
    TTSModel::SsfmV30,
)
.language("eng")
.output(Output::new()
    .volume(120)
    .audio_pitch(2)
    .audio_tempo(1.2)
    .audio_format(AudioFormat::Mp3))
.seed(42);
```

### Emotion Control

#### Preset Emotions (ssfm-v30)

```rust
use typecast_rust::{PresetPrompt, EmotionPreset};

let request = TTSRequest::new(voice_id, text, TTSModel::SsfmV30)
    .prompt(PresetPrompt::new()
        .emotion_preset(EmotionPreset::Happy)
        .emotion_intensity(1.5));
```

Available presets:
- `Normal` - Neutral, balanced tone
- `Happy` - Bright, cheerful expression
- `Sad` - Melancholic, subdued tone
- `Angry` - Strong, intense delivery
- `Whisper` - Soft, quiet speech (ssfm-v30 only)
- `ToneUp` - Higher tonal emphasis (ssfm-v30 only)
- `ToneDown` - Lower tonal emphasis (ssfm-v30 only)

#### Smart Context-Aware Emotion (ssfm-v30)

```rust
use typecast_rust::SmartPrompt;

let request = TTSRequest::new(voice_id, text, TTSModel::SsfmV30)
    .prompt(SmartPrompt::new()
        .previous_text("I'm so excited about this!")
        .next_text("This is the best day ever!"));
```

### Voice Discovery

```rust
use typecast_rust::{VoicesV2Filter, TTSModel, Gender, Age};

// Get all voices
let voices = client.get_voices_v2(None).await?;

// Filter voices
let filter = VoicesV2Filter::new()
    .model(TTSModel::SsfmV30)
    .gender(Gender::Female)
    .age(Age::YoungAdult);

let voices = client.get_voices_v2(Some(filter)).await?;

// Get specific voice
let voice = client.get_voice_v2("tc_60e5426de8b95f1d3000d7b5").await?;
println!("Voice: {} ({:?})", voice.voice_name, voice.gender);
```

## Error Handling

```rust
use typecast_rust::TypecastError;

match client.text_to_speech(&request).await {
    Ok(response) => {
        // Success
    }
    Err(TypecastError::Unauthorized { .. }) => {
        println!("Invalid API key");
    }
    Err(TypecastError::PaymentRequired { .. }) => {
        println!("Insufficient credits");
    }
    Err(TypecastError::RateLimited { .. }) => {
        println!("Rate limit exceeded, please retry later");
    }
    Err(TypecastError::NotFound { detail }) => {
        println!("Voice not found: {}", detail);
    }
    Err(e) => {
        println!("Error: {}", e);
    }
}
```

## Supported Languages

The API supports 37 languages with ssfm-v30 model:

| Code | Language | Code | Language | Code | Language |
|------|----------|------|----------|------|----------|
| eng | English | kor | Korean | jpn | Japanese |
| zho | Chinese | spa | Spanish | fra | French |
| deu | German | ita | Italian | por | Portuguese |
| rus | Russian | ara | Arabic | hin | Hindi |
| ... and more |

## Timestamp TTS

Generate speech with word- and/or character-level alignment data, then convert
it to SRT or WebVTT subtitles with a single method call.

```rust
use typecast_rust::{TypecastClient, TTSModel};
use typecast_rust::timestamps::TTSRequestWithTimestamps;

#[tokio::main]
async fn main() -> typecast_rust::Result<()> {
    let client = TypecastClient::from_env()?;

    let request = TTSRequestWithTimestamps::new(
        "tc_60e5426de8b95f1d3000d7b5",
        "Hello. How are you?",
        TTSModel::SsfmV30,
    );

    // Pass None for both word+char, "word" or "char" for a single granularity.
    let response = client.text_to_speech_with_timestamps(&request, None).await?;

    // Write audio to disk
    response.save_audio("output.wav")?;

    // Generate subtitle files
    let srt = response.to_srt()?;
    let vtt = response.to_vtt()?;

    std::fs::write("output.srt", srt)?;
    std::fs::write("output.vtt", vtt)?;

    println!("Audio duration: {:.2}s", response.audio_duration);
    Ok(())
}
```

### Captioning rules

- Word segments are used when two or more words are present; character segments
  are used otherwise (useful for languages without whitespace such as Japanese
  or Chinese).
- A cue is flushed when: a sentence-terminating punctuation mark is encountered
  (`. ? ! 。 ？ ！`), the cue duration would exceed 7 seconds, or the cue text
  would exceed 42 Unicode codepoints.

## Running Tests

```bash
# Set your API key
export TYPECAST_API_KEY=your_api_key

# Run all tests (unit + timestamp + doc tests)
cargo test

# Run timestamp tests only
cargo test --test timestamps_test

# Run integration tests only (requires TYPECAST_API_KEY)
cargo test --features e2e --test e2e_test
```

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [Typecast API Documentation](https://typecast.ai/docs)
- [Typecast Dashboard](https://typecast.ai/?lang=en)
- [API Reference](https://typecast.ai/docs/api-reference)
- [crates.io](https://crates.io/crates/typecast-rust)
- [docs.rs](https://docs.rs/typecast-rust)
