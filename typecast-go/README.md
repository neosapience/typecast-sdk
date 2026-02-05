<div align="center">

# Typecast SDK for Go

**The official Go SDK for the Typecast Text-to-Speech API**

Convert text to lifelike speech using AI-powered voices

[![Go Version](https://img.shields.io/badge/Go-%3E%3D1.21-00ADD8.svg?style=flat-square&logo=go&logoColor=white)](https://go.dev/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)
[![Go Reference](https://img.shields.io/badge/Go-Reference-00ADD8.svg?style=flat-square&logo=go&logoColor=white)](https://pkg.go.dev/github.com/neosapience/typecast-go)

[Documentation](https://typecast.ai/docs) | [API Reference](https://typecast.ai/docs/api-reference) | [Get API Key](https://typecast.ai/developers/api/api-key)

</div>

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Usage](#usage)
  - [Configuration](#configuration)
  - [Text to Speech](#text-to-speech)
  - [Voice Discovery](#voice-discovery)
  - [Emotion Control](#emotion-control)
- [Supported Languages](#supported-languages)
- [Error Handling](#error-handling)
- [License](#license)

---

## Installation

```bash
go get github.com/neosapience/typecast-go
```

---

## Quick Start

```go
package main

import (
    "context"
    "os"

    typecast "github.com/neosapience/typecast-go"
)

func main() {
    client := typecast.NewClient(&typecast.ClientConfig{
        APIKey: "YOUR_API_KEY",
    })

    ctx := context.Background()

    audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
        Text:    "Hello! I'm your friendly text-to-speech assistant.",
        Model:   typecast.ModelSSFMV30,
        VoiceID: "tc_672c5f5ce59fac2a48faeaee",
    })
    if err != nil {
        panic(err)
    }

    os.WriteFile("output.wav", audio.AudioData, 0644)
}
```

---

## Features

| Feature | Description |
|---------|-------------|
| **Multiple Models** | Support for `ssfm-v21` and `ssfm-v30` AI voice models |
| **37 Languages** | English, Korean, Japanese, Chinese, Spanish, and 32 more |
| **Emotion Control** | Preset emotions or smart context-aware inference |
| **Audio Customization** | Volume, pitch, tempo, and format (WAV/MP3) |
| **Voice Discovery** | Filter voices by model, gender, age, and use cases |
| **Context Support** | Full context.Context support for cancellation and timeouts |
| **Zero Dependencies** | Uses only Go standard library |

---

## Usage

### Configuration

```go
import typecast "github.com/neosapience/typecast-go"

// Using environment variable (recommended)
// export TYPECAST_API_KEY="your-api-key"
client := typecast.NewClient(nil)

// Or pass directly
client := typecast.NewClient(&typecast.ClientConfig{
    APIKey:  "your-api-key",
    BaseURL: "https://api.typecast.ai",  // optional
    Timeout: 60 * time.Second,           // optional
})
```

### Text to Speech

#### Basic Usage

```go
audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:    "Hello, world!",
    VoiceID: "tc_672c5f5ce59fac2a48faeaee",
    Model:   typecast.ModelSSFMV30,
})
```

#### With Audio Options

```go
volume := 120
pitch := 2
tempo := 1.2

audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:     "Hello, world!",
    VoiceID:  "tc_672c5f5ce59fac2a48faeaee",
    Model:    typecast.ModelSSFMV30,
    Language: "eng",
    Output: &typecast.Output{
        Volume:      &volume,       // 0-200 (default: 100)
        AudioPitch:  &pitch,        // -12 to +12 semitones
        AudioTempo:  &tempo,        // 0.5x to 2.0x
        AudioFormat: typecast.AudioFormatMP3,  // WAV or MP3
    },
    Seed: intPtr(42),  // for reproducible results
})
```

### Voice Discovery

```go
// Get all voices (V2 API - recommended)
voices, err := client.GetVoicesV2(ctx, nil)

// Filter by criteria
voices, err := client.GetVoicesV2(ctx, &typecast.VoicesV2Filter{
    Model:  typecast.ModelSSFMV30,
    Gender: typecast.GenderFemale,
    Age:    typecast.AgeYoungAdult,
})

// Display voice info
voice := voices[0]
fmt.Printf("Name: %s\n", voice.VoiceName)
fmt.Printf("Gender: %s, Age: %s\n", *voice.Gender, *voice.Age)
for _, m := range voice.Models {
    fmt.Printf("Model: %s, Emotions: %v\n", m.Version, m.Emotions)
}
```

### Emotion Control

#### ssfm-v21: Basic Emotion

```go
intensity := 1.5

audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:    "I'm so excited!",
    VoiceID: "tc_62a8975e695ad26f7fb514d1",
    Model:   typecast.ModelSSFMV21,
    Prompt: &typecast.Prompt{
        EmotionPreset:    typecast.EmotionHappy,  // normal, happy, sad, angry
        EmotionIntensity: &intensity,             // 0.0 to 2.0
    },
})
```

#### ssfm-v30: Preset Mode

```go
intensity := 1.5

audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:    "I'm so excited!",
    VoiceID: "tc_672c5f5ce59fac2a48faeaee",
    Model:   typecast.ModelSSFMV30,
    Prompt: &typecast.PresetPrompt{
        EmotionType:      "preset",
        EmotionPreset:    typecast.EmotionHappy,  // + whisper, toneup, tonedown
        EmotionIntensity: &intensity,
    },
})
```

#### ssfm-v30: Smart Mode (Context-Aware)

```go
audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:    "Everything is perfect.",
    VoiceID: "tc_672c5f5ce59fac2a48faeaee",
    Model:   typecast.ModelSSFMV30,
    Prompt: &typecast.SmartPrompt{
        EmotionType:  "smart",
        PreviousText: "I just got the best news!",
        NextText:     "I can't wait to celebrate!",
    },
})
```

---

## Supported Languages

<details>
<summary><strong>View all 37 supported languages</strong></summary>

| Code | Language | Code | Language | Code | Language |
|------|----------|------|----------|------|----------|
| `eng` | English | `jpn` | Japanese | `ukr` | Ukrainian |
| `kor` | Korean | `ell` | Greek | `ind` | Indonesian |
| `spa` | Spanish | `tam` | Tamil | `dan` | Danish |
| `deu` | German | `tgl` | Tagalog | `swe` | Swedish |
| `fra` | French | `fin` | Finnish | `msa` | Malay |
| `ita` | Italian | `zho` | Chinese | `ces` | Czech |
| `pol` | Polish | `slk` | Slovak | `por` | Portuguese |
| `nld` | Dutch | `ara` | Arabic | `bul` | Bulgarian |
| `rus` | Russian | `hrv` | Croatian | `ron` | Romanian |
| `ben` | Bengali | `hin` | Hindi | `hun` | Hungarian |
| `nan` | Hokkien | `nor` | Norwegian | `pan` | Punjabi |
| `tha` | Thai | `tur` | Turkish | `vie` | Vietnamese |
| `yue` | Cantonese | | | | |

</details>

```go
// Auto-detect (recommended)
audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:    "こんにちは",
    VoiceID: "...",
    Model:   typecast.ModelSSFMV30,
})

// Explicit language
audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
    Text:     "안녕하세요",
    VoiceID:  "...",
    Model:    typecast.ModelSSFMV30,
    Language: "kor",
})
```

---

## Error Handling

```go
audio, err := client.TextToSpeech(ctx, request)
if err != nil {
    if apiErr, ok := err.(*typecast.APIError); ok {
        fmt.Printf("Error %d: %s\n", apiErr.StatusCode, apiErr.Message)

        // Handle specific errors
        switch {
        case apiErr.IsUnauthorized():
            // Invalid API key (401)
        case apiErr.IsForbidden():
            // Access denied (403)
        case apiErr.IsPaymentRequired():
            // Insufficient credits (402)
        case apiErr.IsNotFound():
            // Resource not found (404)
        case apiErr.IsValidationError():
            // Validation error (422)
        case apiErr.IsRateLimited():
            // Rate limit exceeded (429)
        case apiErr.IsServerError():
            // Server error (5xx)
        }
    }
}
```

---

## API Reference

### Client Methods

| Method | Description |
|--------|-------------|
| `TextToSpeech(ctx, request)` | Convert text to speech |
| `GetVoicesV2(ctx, filter)` | List available voices with filtering |
| `GetVoiceV2(ctx, voiceID)` | Get specific voice details |
| `GetVoices(ctx, model)` | List voices (V1 API, deprecated) |
| `GetVoice(ctx, voiceID, model)` | Get voice (V1 API, deprecated) |

### Models

| Constant | Value | Description |
|----------|-------|-------------|
| `ModelSSFMV30` | `ssfm-v30` | Latest model with improved prosody |
| `ModelSSFMV21` | `ssfm-v21` | Stable production model |

### Emotion Presets

| Constant | ssfm-v21 | ssfm-v30 |
|----------|----------|----------|
| `EmotionNormal` | ✓ | ✓ |
| `EmotionHappy` | ✓ | ✓ |
| `EmotionSad` | ✓ | ✓ |
| `EmotionAngry` | ✓ | ✓ |
| `EmotionWhisper` | ✗ | ✓ |
| `EmotionToneUp` | ✗ | ✓ |
| `EmotionToneDown` | ✗ | ✓ |

---

## License

[MIT](LICENSE) © [Neosapience](https://typecast.ai/?lang=en)
