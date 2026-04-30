<div align="center">

# Typecast SDK for Swift

**The official Swift SDK for the Typecast Text-to-Speech API**

Convert text to lifelike speech using AI-powered voices

[![Swift](https://img.shields.io/badge/Swift-5.9+-F05138.svg?style=flat-square&logo=swift&logoColor=white)](https://swift.org/)
[![Platforms](https://img.shields.io/badge/Platforms-iOS%20|%20macOS%20|%20tvOS%20|%20watchOS%20|%20visionOS-blue.svg?style=flat-square)](https://developer.apple.com/)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)
[![Swift Package Manager](https://img.shields.io/badge/SPM-compatible-4BC51D.svg?style=flat-square)](https://swift.org/package-manager/)
[![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)](./Makefile)

[Documentation](https://typecast.ai/docs) | [API Reference](https://typecast.ai/docs/api-reference) | [Get API Key](https://typecast.ai/developers/api/api-key)

</div>

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
  - [Swift Package Manager](#swift-package-manager)
  - [Xcode](#xcode)
- [Quick Start](#quick-start)
- [Features](#features)
- [Usage](#usage)
  - [Configuration](#configuration)
  - [Text to Speech](#text-to-speech)
  - [Text to Speech with Timestamps](#text-to-speech-with-timestamps)
  - [Voice Discovery](#voice-discovery)
  - [Emotion Control](#emotion-control)
  - [Audio Output Settings](#audio-output-settings)
- [Platform-Specific Usage](#platform-specific-usage)
  - [iOS](#ios)
  - [macOS](#macos)
  - [tvOS](#tvos)
  - [watchOS](#watchos)
  - [visionOS](#visionos)
- [Supported Languages](#supported-languages)
- [Error Handling](#error-handling)
- [License](#license)

---

## Requirements

| Platform | Minimum Version |
|----------|-----------------|
| iOS | 13.0+ |
| macOS | 10.15+ |
| tvOS | 13.0+ |
| watchOS | 6.0+ |
| visionOS | 1.0+ |
| Swift | 5.9+ |

---

## Installation

### Swift Package Manager

Add the following to your `Package.swift` file:

```swift
dependencies: [
    .package(url: "https://github.com/neosapience/typecast-sdk.git", from: "1.0.0")
]
```

Then add `Typecast` to your target dependencies:

```swift
targets: [
    .target(
        name: "YourTarget",
        dependencies: [
            .product(name: "Typecast", package: "typecast-sdk")
        ]
    )
]
```

### Xcode

1. Open your project in Xcode
2. Go to **File** → **Add Package Dependencies...**
3. Enter the repository URL: `https://github.com/neosapience/typecast-sdk.git`
4. Select version rules and click **Add Package**
5. Select the `Typecast` library and add it to your target

---

## Quick Start

```swift
import Typecast

let client = TypecastClient(apiKey: "YOUR_API_KEY")

// Simple usage
let audio = try await client.speak(
    "Hello! I'm your friendly text-to-speech assistant.",
    voiceId: "tc_672c5f5ce59fac2a48faeaee"
)

// Save to file
let url = URL(fileURLWithPath: "output.\(audio.format.rawValue)")
try audio.audioData.write(to: url)
print("Saved: \(url.path) (\(audio.duration)s)")
```

---

## Features

| Feature | Description |
|---------|-------------|
| **Multiple Models** | Support for `ssfm-v21` and `ssfm-v30` AI voice models |
| **37 Languages** | English, Korean, Japanese, Chinese, Spanish, and 32 more |
| **Emotion Control** | Preset emotions or smart context-aware inference |
| **Audio Customization** | Volume, pitch, tempo, and format (WAV/MP3) |
| **Timestamps** | Word/character alignment for SRT and WebVTT caption generation |
| **Voice Discovery** | Filter voices by model, gender, age, and use cases |
| **Swift Concurrency** | Full async/await support |
| **Cross-Platform** | iOS, macOS, tvOS, watchOS, and visionOS |
| **Sendable** | Thread-safe types for concurrent usage |

---

## Usage

### Configuration

```swift
import Typecast

// Direct initialization
let client = TypecastClient(apiKey: "your-api-key")

// With custom base URL
let client = TypecastClient(
    apiKey: "your-api-key",
    baseURL: "https://api.typecast.ai"
)

// Using configuration struct
let config = TypecastConfiguration(apiKey: "your-api-key")
let client = TypecastClient(configuration: config)
```

### Text to Speech

#### Basic Usage

```swift
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "tc_672c5f5ce59fac2a48faeaee",
    text: "Hello, world!",
    model: .ssfmV30
))

// Access response
print("Duration: \(audio.duration) seconds")
print("Format: \(audio.format.rawValue)")
print("Size: \(audio.audioData.count) bytes")
```

#### Convenience Method

```swift
// Simple speak method
let audio = try await client.speak(
    "Hello, world!",
    voiceId: "tc_672c5f5ce59fac2a48faeaee"
)

// With emotion
let audio = try await client.speak(
    "I'm so excited!",
    voiceId: "tc_672c5f5ce59fac2a48faeaee",
    emotion: .happy,
    intensity: 1.5
)
```

### Text to Speech with Timestamps

The `textToSpeechWithTimestamps` method calls `POST /v1/text-to-speech/with-timestamps` and returns the audio (base64-encoded) together with word- and/or character-level alignment segments. Use these segments to generate SRT or WebVTT captions that are byte-for-byte identical across all Typecast SDKs.

#### Basic usage

```swift
let request = TTSRequestWithTimestamps(
    voiceId: "tc_672c5f5ce59fac2a48faeaee",
    text: "Hello. How are you?",
    model: .ssfmV30
)
let response = try await client.textToSpeechWithTimestamps(request)

// Save audio
let audioURL = URL(fileURLWithPath: "output.wav")
try response.saveAudio(to: audioURL)

// Generate captions
let srt = try response.toSrt()  // SRT format
let vtt = try response.toVtt()  // WebVTT format
print(srt)
```

#### Granularity control

Pass `granularity` to request specific alignment data: `"word"`, `"character"`, or `"both"` (default).

```swift
// Word-level only
let wordResponse = try await client.textToSpeechWithTimestamps(request, granularity: "word")

// Character-level only (recommended for languages without whitespace, e.g. Japanese, Chinese)
let charResponse = try await client.textToSpeechWithTimestamps(request, granularity: "character")
```

#### Working with alignment segments

```swift
let response = try await client.textToSpeechWithTimestamps(request)

// Access word segments
if let words = response.words {
    for word in words {
        print("\(word.text): \(word.start)s – \(word.end)s")
    }
}

// Access character segments
if let characters = response.characters {
    for char in characters {
        print("\(char.text): \(char.start)s – \(char.end)s")
    }
}

// Write captions to files
let srtURL = URL(fileURLWithPath: "captions.srt")
try response.toSrt().write(to: srtURL, atomically: true, encoding: .utf8)

let vttURL = URL(fileURLWithPath: "captions.vtt")
try response.toVtt().write(to: vttURL, atomically: true, encoding: .utf8)
```

#### Caption formatting rules

The `toSrt()` and `toVtt()` methods apply the same rules used by all Typecast SDKs:

- **Hard cap — time**: split before a segment if the cue would exceed 7 seconds.
- **Hard cap — length**: split before a segment if the joined text would exceed 42 Unicode codepoints.
- **Sentence boundary**: flush the cue immediately after a segment ending with `.` `?` `!` `。` `？` `！`.
- **Word mode**: segments are joined with a space (used when `words` has ≥ 2 entries).
- **Character mode**: segments are concatenated without a separator (used when only `characters` is present, or `words` has exactly 1 entry).

### Voice Discovery

```swift
// Get all voices
let voices = try await client.getVoices()

// Filter by criteria
let filteredVoices = try await client.getVoices(filter: VoicesV2Filter(
    model: .ssfmV30,
    gender: .female,
    age: .youngAdult
))

// Get specific voice
let voice = try await client.getVoice(voiceId: "tc_672c5f5ce59fac2a48faeaee")

// Display voice info
print("Name: \(voice.voiceName)")
print("Gender: \(voice.gender?.rawValue ?? "N/A")")
print("Age: \(voice.age?.rawValue ?? "N/A")")
print("Models: \(voice.models.map { $0.version.rawValue }.joined(separator: ", "))")
```

### Emotion Control

#### ssfm-v21: Basic Emotion

```swift
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "tc_62a8975e695ad26f7fb514d1",
    text: "I'm so excited!",
    model: .ssfmV21,
    prompt: .basic(Prompt(
        emotionPreset: .happy,
        emotionIntensity: 1.5
    ))
))
```

#### ssfm-v30: Preset Mode

```swift
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "tc_672c5f5ce59fac2a48faeaee",
    text: "I'm so excited!",
    model: .ssfmV30,
    prompt: .preset(PresetPrompt(
        emotionPreset: .happy,  // normal, happy, sad, angry, whisper, toneup, tonedown
        emotionIntensity: 1.5
    ))
))
```

#### ssfm-v30: Smart Mode (Context-Aware)

```swift
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "tc_672c5f5ce59fac2a48faeaee",
    text: "Everything is perfect.",
    model: .ssfmV30,
    prompt: .smart(SmartPrompt(
        previousText: "I just got the best news!",
        nextText: "I can't wait to celebrate!"
    ))
))
```

### Audio Output Settings

```swift
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "tc_672c5f5ce59fac2a48faeaee",
    text: "Hello, world!",
    model: .ssfmV30,
    language: .english,
    output: OutputSettings(
        volume: 120,        // 0-200 (default: 100)
        audioPitch: 2,      // -12 to +12 semitones
        audioTempo: 1.2,    // 0.5x to 2.0x
        audioFormat: .mp3   // .wav or .mp3
    ),
    seed: 42  // for reproducible results
))
```

---

## Platform-Specific Usage

### iOS

```swift
import Typecast
import AVFoundation

class TTSManager {
    private let client = TypecastClient(apiKey: "YOUR_API_KEY")
    private var audioPlayer: AVAudioPlayer?
    
    func speak(_ text: String) async throws {
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        // Play audio
        audioPlayer = try AVAudioPlayer(data: audio.audioData)
        audioPlayer?.play()
    }
    
    func saveToDocuments(_ text: String) async throws -> URL {
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let fileURL = documentsPath.appendingPathComponent("speech.\(audio.format.rawValue)")
        
        try audio.audioData.write(to: fileURL)
        return fileURL
    }
}
```

### macOS

```swift
import Typecast
import AppKit
import AVFoundation

class MacTTSManager {
    private let client = TypecastClient(apiKey: "YOUR_API_KEY")
    private var audioPlayer: AVAudioPlayer?
    
    func speak(_ text: String) async throws {
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        audioPlayer = try AVAudioPlayer(data: audio.audioData)
        audioPlayer?.play()
    }
    
    func saveWithPanel(_ text: String) async throws {
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        let savePanel = NSSavePanel()
        savePanel.allowedContentTypes = [.audio]
        savePanel.nameFieldStringValue = "speech.\(audio.format.rawValue)"
        
        if savePanel.runModal() == .OK, let url = savePanel.url {
            try audio.audioData.write(to: url)
        }
    }
}
```

### tvOS

```swift
import Typecast
import AVFoundation

class TVTTSManager {
    private let client = TypecastClient(apiKey: "YOUR_API_KEY")
    private var audioPlayer: AVAudioPlayer?
    
    func playNarration(_ text: String) async throws {
        // Configure audio session for TV
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playback, mode: .spokenAudio)
        try audioSession.setActive(true)
        
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        audioPlayer = try AVAudioPlayer(data: audio.audioData)
        audioPlayer?.play()
    }
}
```

### watchOS

```swift
import Typecast
import AVFoundation
import WatchKit

class WatchTTSManager {
    private let client = TypecastClient(apiKey: "YOUR_API_KEY")
    
    func speak(_ text: String) async throws {
        let audio = try await client.speak(
            text,
            voiceId: "tc_672c5f5ce59fac2a48faeaee",
            model: .ssfmV30
        )
        
        // Save to temporary file and play
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("speech.\(audio.format.rawValue)")
        try audio.audioData.write(to: tempURL)
        
        // Use WKAudioFilePlayer for watchOS
        let asset = WKAudioFileAsset(url: tempURL)
        let playerItem = WKAudioFilePlayerItem(asset: asset)
        let player = WKAudioFilePlayer(playerItem: playerItem)
        player.play()
    }
}
```

### visionOS

```swift
import Typecast
import AVFoundation
import RealityKit

class VisionTTSManager {
    private let client = TypecastClient(apiKey: "YOUR_API_KEY")
    private var audioPlayer: AVAudioPlayer?
    
    func speak(_ text: String) async throws {
        // Configure spatial audio for immersive experience
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.playback, mode: .spokenAudio)
        try audioSession.setActive(true)
        
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        audioPlayer = try AVAudioPlayer(data: audio.audioData)
        audioPlayer?.play()
    }
    
    // For RealityKit integration
    func createAudioEntity(_ text: String, in scene: RealityKit.Scene) async throws {
        let audio = try await client.speak(text, voiceId: "tc_672c5f5ce59fac2a48faeaee")
        
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("speech.\(audio.format.rawValue)")
        try audio.audioData.write(to: tempURL)
        
        // Create audio resource for spatial audio
        let audioResource = try AudioFileResource.load(contentsOf: tempURL)
        // Use audioResource with RealityKit entities
    }
}
```

---

## Supported Languages

<details>
<summary><strong>View all 37 supported languages</strong></summary>

| Code | Language | Code | Language | Code | Language |
|------|----------|------|----------|------|----------|
| `.english` | English | `.japanese` | Japanese | `.ukrainian` | Ukrainian |
| `.korean` | Korean | `.greek` | Greek | `.indonesian` | Indonesian |
| `.spanish` | Spanish | `.tamil` | Tamil | `.danish` | Danish |
| `.german` | German | `.tagalog` | Tagalog | `.swedish` | Swedish |
| `.french` | French | `.finnish` | Finnish | `.malay` | Malay |
| `.italian` | Italian | `.chinese` | Chinese | `.czech` | Czech |
| `.polish` | Polish | `.slovak` | Slovak | `.portuguese` | Portuguese |
| `.dutch` | Dutch | `.arabic` | Arabic | `.bulgarian` | Bulgarian |
| `.russian` | Russian | `.croatian` | Croatian | `.romanian` | Romanian |
| `.bengali` | Bengali | `.hindi` | Hindi | `.hungarian` | Hungarian |
| `.minNan` | Hokkien | `.norwegian` | Norwegian | `.punjabi` | Punjabi |
| `.thai` | Thai | `.turkish` | Turkish | `.vietnamese` | Vietnamese |
| `.cantonese` | Cantonese | | | | |

</details>

```swift
// Auto-detect (recommended)
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "...",
    text: "こんにちは",
    model: .ssfmV30
))

// Explicit language
let audio = try await client.textToSpeech(TTSRequest(
    voiceId: "...",
    text: "안녕하세요",
    model: .ssfmV30,
    language: .korean
))
```

---

## Error Handling

```swift
import Typecast

do {
    let audio = try await client.textToSpeech(request)
} catch let error as TypecastError {
    switch error {
    case .unauthorized(let message):
        print("Invalid API key: \(message)")
    case .paymentRequired(let message):
        print("Insufficient credits: \(message)")
    case .notFound(let message):
        print("Voice not found: \(message)")
    case .validationError(let message):
        print("Invalid request: \(message)")
    case .rateLimitExceeded(let message):
        print("Rate limit exceeded: \(message)")
    case .serverError(let message):
        print("Server error: \(message)")
    case .networkError(let underlyingError):
        print("Network error: \(underlyingError.localizedDescription)")
    case .invalidResponse(let message):
        print("Invalid response: \(message)")
    default:
        print("Error: \(error.localizedDescription)")
    }
    
    // Access status code if available
    if let statusCode = error.statusCode {
        print("HTTP Status: \(statusCode)")
    }
}
```

---

## License

[Apache-2.0](LICENSE) © [Neosapience](https://typecast.ai/?lang=en)
