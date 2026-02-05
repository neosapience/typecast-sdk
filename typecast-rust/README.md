# Typecast Rust SDK

Official Rust SDK for the [Typecast](https://typecast.ai) Text-to-Speech API.

## Installation

Add this to your `Cargo.toml`:

```toml
[dependencies]
typecast = "0.1.0"
tokio = { version = "1", features = ["full"] }
```

## Quick Start

```rust
use typecast::{TypecastClient, TTSRequest, TTSModel};

#[tokio::main]
async fn main() -> typecast::Result<()> {
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
use typecast::{TypecastClient, ClientConfig};
use std::time::Duration;

let config = ClientConfig::new("your-api-key")
    .base_url("https://api.typecast.ai")
    .timeout(Duration::from_secs(120));

let client = TypecastClient::new(config)?;
```

## Features

### Text-to-Speech

```rust
use typecast::{TTSRequest, TTSModel, Output, AudioFormat};

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
use typecast::{PresetPrompt, EmotionPreset};

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
use typecast::SmartPrompt;

let request = TTSRequest::new(voice_id, text, TTSModel::SsfmV30)
    .prompt(SmartPrompt::new()
        .previous_text("I'm so excited about this!")
        .next_text("This is the best day ever!"));
```

### Voice Discovery

```rust
use typecast::{VoicesV2Filter, TTSModel, Gender, Age};

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
use typecast::TypecastError;

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

## Running Tests

```bash
# Set your API key
export TYPECAST_API_KEY=your_api_key

# Run all tests
cargo test

# Run integration tests only
cargo test --test integration_test
```

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [Typecast API Documentation](https://docs.typecast.ai)
- [Typecast Dashboard](https://typecast.ai)
- [API Reference](https://docs.typecast.ai/api-reference)
