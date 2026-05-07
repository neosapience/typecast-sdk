<div align="center">

# Typecast SDK for Zig

**The official Zig SDK for the Typecast Text-to-Speech API**

Convert text to lifelike speech using AI-powered voices

[![Zig Version](https://img.shields.io/badge/Zig-%3E%3D0.14-F7A41D.svg?style=flat-square&logo=zig&logoColor=white)](https://ziglang.org/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg?style=flat-square)](LICENSE)

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
  - [Streaming](#streaming)
  - [Text to Speech with Timestamps](#text-to-speech-with-timestamps)
  - [Voice Discovery](#voice-discovery)
  - [Emotion Control](#emotion-control)
  - [Quick Voice Cloning](#quick-voice-cloning)
- [Supported Languages](#supported-languages)
- [Error Handling](#error-handling)
- [License](#license)

---

## Installation

Add the SDK as a dependency in your `build.zig.zon`:

```zig
.dependencies = .{
    .typecast = .{
        .url = "https://github.com/neosapience/typecast-sdk/archive/refs/heads/main.tar.gz",
        // Replace with the actual hash after first fetch
        .hash = "...",
    },
},
```

Then import it in your `build.zig`:

```zig
const typecast_dep = b.dependency("typecast", .{
    .target = target,
    .optimize = optimize,
});
exe.root_module.addImport("typecast", typecast_dep.module("typecast"));
```

Or fetch it directly:

```bash
zig fetch --save https://github.com/neosapience/typecast-sdk/archive/refs/heads/main.tar.gz
```

---

## Quick Start

```zig
const std = @import("std");
const typecast = @import("typecast");

pub fn main() !void {
    var gpa = std.heap.GeneralPurposeAllocator(.{}){};
    defer _ = gpa.deinit();
    const allocator = gpa.allocator();

    var client = typecast.Client.init(allocator, .{
        .api_key = "YOUR_API_KEY",
    });
    defer client.deinit();

    const response = try client.textToSpeech(.{
        .voice_id = "tc_672c5f5ce59fac2a48faeaee",
        .text = "Hello! I'm your friendly text-to-speech assistant.",
        .model = .ssfm_v30,
    });
    defer allocator.free(response.audio_data);

    const file = try std.fs.cwd().createFile("output.wav", .{});
    defer file.close();
    try file.writeAll(response.audio_data);
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
| **Streaming** | Chunked audio delivery via callback for low-latency playback |
| **Timestamp TTS** | Word/character alignment data with SRT/VTT subtitle generation |
| **Voice Discovery** | Filter voices by model, gender, age, and use cases |
| **Quick Voice Cloning** | Upload audio to create a custom voice; delete when no longer needed |
| **Zero Dependencies** | Uses only the Zig standard library |

---

## Usage

### Configuration

```zig
const typecast = @import("typecast");

// Pass API key directly
var client = typecast.Client.init(allocator, .{
    .api_key = "your-api-key",
});
defer client.deinit();
```

Or read from an environment variable (recommended):

```zig
// std.posix.getenv is POSIX-only; on Windows use std.process.EnvMap instead.
const api_key = std.posix.getenv("TYPECAST_API_KEY") orelse return error.MissingApiKey;
var client = typecast.Client.init(allocator, .{
    .api_key = api_key,
    .base_url = "https://api.typecast.ai", // optional, this is the default
});
defer client.deinit();
```

### Text to Speech

#### Basic Usage

```zig
const response = try client.textToSpeech(.{
    .voice_id = "tc_672c5f5ce59fac2a48faeaee",
    .text = "Hello, world!",
    .model = .ssfm_v30,
});
defer allocator.free(response.audio_data);

std.debug.print("Audio: {d} bytes, {d:.2}s\n", .{
    response.audio_data.len,
    response.duration,
});
```

#### With Audio Options

```zig
const response = try client.textToSpeech(.{
    .voice_id = "tc_672c5f5ce59fac2a48faeaee",
    .text = "Hello, world!",
    .model = .ssfm_v30,
    .language = "eng",
    .output = .{
        .volume = 120,          // 0-200 (default: 100)
        .audio_pitch = 2,       // -12 to +12 semitones
        .audio_tempo = 1.2,     // 0.5x to 2.0x
        .audio_format = .mp3,   // .wav or .mp3
    },
    .seed = 42, // for reproducible results
});
defer allocator.free(response.audio_data);
```

### Streaming

Stream audio chunks as they are generated for lower latency:

```zig
try client.textToSpeechStream(.{
    .voice_id = "tc_672c5f5ce59fac2a48faeaee",
    .text = "This text is streamed chunk by chunk.",
    .model = .ssfm_v30,
}, struct {
    fn onChunk(chunk: []const u8) anyerror!void {
        // Write each chunk to a file, pipe, or audio player
        _ = chunk;
    }
}.onChunk);
```

### Text to Speech with Timestamps

Get word- or character-level alignment data together with audio, and generate
SRT or WebVTT subtitle files:

```zig
// Returns audio + alignment segments in one call.
var resp = try client.textToSpeechWithTimestamps(
    .{
        .voice_id = "tc_672c5f5ce59fac2a48faeaee",
        .text = "Hello. How are you?",
        .model = .ssfm_v30,
    },
    null, // granularity: null or "" = both words and chars, "word", or "char"
);
defer resp.deinit();

// Inspect word-level alignment.
if (resp.words) |words| {
    for (words) |w| {
        std.debug.print("{s}  {d:.3}s – {d:.3}s\n", .{ w.text, w.start, w.end });
    }
}

// Generate SRT captions (caller owns the returned slice).
const srt = try resp.toSrt(allocator);
defer allocator.free(srt);
try std.fs.cwd().writeFile(.{ .sub_path = "output.srt", .data = srt });

// Generate WebVTT captions.
const vtt = try resp.toVtt(allocator);
defer allocator.free(vtt);
try std.fs.cwd().writeFile(.{ .sub_path = "output.vtt", .data = vtt });

// Decode the raw audio bytes.
const audio = try resp.audioBytes(allocator);
defer allocator.free(audio);

// Or save audio directly to a file.
try resp.saveAudio("output.wav", allocator);
```

Captioning rules applied automatically:

- Cue boundary on sentence terminators (`.` `?` `!` `。` `？` `！`).
- Max 7 seconds or 42 characters per cue.
- For non-whitespace languages (Japanese, Chinese) where word segments
  collapse to a single entry, the helper automatically falls back to
  character granularity.

### Voice Discovery

```zig
// Get all voices (V2 API - recommended)
const voices = try client.getVoicesV2(null);
defer allocator.free(voices);

// Filter by criteria
const filtered = try client.getVoicesV2(.{
    .model = .ssfm_v30,
    .gender = .female,
    .age = .young_adult,
});
defer allocator.free(filtered);

// Get a single voice by ID
const voice = try client.getVoiceV2("tc_672c5f5ce59fac2a48faeaee", null);
```

### Emotion Control

#### ssfm-v21: Basic Emotion

```zig
const response = try client.textToSpeech(.{
    .voice_id = "tc_62a8975e695ad26f7fb514d1",
    .text = "I'm so excited!",
    .model = .ssfm_v21,
    .prompt = .{ .basic = .{
        .emotion_preset = .happy, // normal, happy, sad, angry
        .emotion_intensity = 1.5, // 0.0 to 2.0
    }},
});
defer allocator.free(response.audio_data);
```

#### ssfm-v30: Preset Mode

```zig
const response = try client.textToSpeech(.{
    .voice_id = "tc_672c5f5ce59fac2a48faeaee",
    .text = "I'm so excited!",
    .model = .ssfm_v30,
    .prompt = .{ .preset = .{
        .emotion_preset = .happy, // + whisper, toneup, tonedown
        .emotion_intensity = 1.5,
    }},
});
defer allocator.free(response.audio_data);
```

#### ssfm-v30: Smart Mode (Context-Aware)

```zig
const response = try client.textToSpeech(.{
    .voice_id = "tc_672c5f5ce59fac2a48faeaee",
    .text = "Everything is perfect.",
    .model = .ssfm_v30,
    .prompt = .{ .smart = .{
        .previous_text = "I just got the best news!",
        .next_text = "I can't wait to celebrate!",
    }},
});
defer allocator.free(response.audio_data);
```

### Quick Voice Cloning

Upload a WAV or MP3 recording to create a custom voice, use it for synthesis,
and delete it when you no longer need it.

```zig
const typecast = @import("typecast");

// Load audio from disk (max 25 MB)
const audio_file = try std.fs.cwd().openFile("recording.wav", .{});
defer audio_file.close();
const audio_bytes = try audio_file.readToEndAlloc(allocator, typecast.CLONING_MAX_FILE_SIZE);
defer allocator.free(audio_bytes);

// Clone the voice
const custom_voice = try client.cloneVoice(
    allocator,
    audio_bytes,
    "recording.wav", // filename used for MIME detection
    "My Voice",      // display name (1–30 characters)
    "ssfm-v30",      // model
);
defer {
    allocator.free(custom_voice.voice_id);
    allocator.free(custom_voice.name);
    allocator.free(custom_voice.model);
}

std.debug.print("Cloned: {s}\n", .{custom_voice.voice_id});

// Synthesise with the custom voice
const response = try client.textToSpeech(.{
    .voice_id = custom_voice.voice_id,
    .text = "Hello from my cloned voice!",
    .model = .ssfm_v30,
});
defer allocator.free(response.audio_data);

// Delete the custom voice when done
try client.deleteVoice(custom_voice.voice_id);
```

**Constraints enforced client-side (before any HTTP call):**

- Audio must be ≤ 25 MB (`error.AudioTooLarge`).
- Name must be 1–30 characters (`error.InvalidName`).

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

```zig
// Auto-detect language (recommended)
const response = try client.textToSpeech(.{
    .voice_id = "...",
    .text = "こんにちは",
    .model = .ssfm_v30,
});

// Explicit language code
const response = try client.textToSpeech(.{
    .voice_id = "...",
    .text = "안녕하세요",
    .model = .ssfm_v30,
    .language = "kor",
});
```

---

## Error Handling

The client returns Zig errors that map to HTTP status codes:

```zig
const response = client.textToSpeech(request) catch |err| switch (err) {
    error.Unauthorized => {
        // Invalid API key (401)
        std.debug.print("Invalid API key\n", .{});
        return err;
    },
    error.PaymentRequired => {
        // Insufficient credits (402)
        std.debug.print("Insufficient credits\n", .{});
        return err;
    },
    error.NotFound => {
        // Voice not found (404)
        std.debug.print("Voice not found\n", .{});
        return err;
    },
    error.UnprocessableEntity => {
        // Validation error (422)
        std.debug.print("Invalid request parameters\n", .{});
        return err;
    },
    error.RateLimited => {
        // Too many requests (429)
        std.debug.print("Rate limited, try again later\n", .{});
        return err;
    },
    error.InternalServerError => {
        // Server error (500)
        std.debug.print("Server error\n", .{});
        return err;
    },
    else => return err,
};
```

---

## API Reference

### Client Methods

| Method | Description |
|--------|-------------|
| `textToSpeech(request)` | Convert text to speech (full response) |
| `textToSpeechStream(request, callback)` | Stream audio chunks via callback |
| `textToSpeechWithTimestamps(request, granularity)` | TTS with word/character alignment data |
| `getVoicesV2(filter)` | List available voices with filtering |
| `getVoiceV2(voice_id, model)` | Get specific voice details |
| `getVoices(model)` | List voices (V1 API) |
| `getMySubscription()` | Get current subscription info |
| `cloneVoice(allocator, audio, filename, name, model)` | Clone a voice from an audio file |
| `deleteVoice(voice_id)` | Delete a custom cloned voice |

### Models

| Enum Value | API Value | Description |
|------------|-----------|-------------|
| `.ssfm_v30` | `ssfm-v30` | Latest model with improved prosody |
| `.ssfm_v21` | `ssfm-v21` | Stable production model |

### Emotion Presets

| Enum Value | ssfm-v21 | ssfm-v30 |
|------------|----------|----------|
| `.normal` | Yes | Yes |
| `.happy` | Yes | Yes |
| `.sad` | Yes | Yes |
| `.angry` | Yes | Yes |
| `.whisper` | No | Yes |
| `.toneup` | No | Yes |
| `.tonedown` | No | Yes |

---

## License

[MIT](LICENSE) - [Neosapience](https://typecast.ai/?lang=en)
