// Typecast Swift SDK
// Official Swift SDK for the Typecast Text-to-Speech API
//
// Copyright (c) 2025 Neosapience, Inc.
// Licensed under the Apache License, Version 2.0

/// Typecast Swift SDK - Convert text to lifelike speech using AI-powered voices
///
/// ## Quick Start
///
/// ```swift
/// import Typecast
///
/// let client = TypecastClient(apiKey: "YOUR_API_KEY")
///
/// // Simple usage
/// let audio = try await client.speak("Hello, world!", voiceId: "tc_672c5f5ce59fac2a48faeaee")
///
/// // Save to file
/// try audio.audioData.write(to: URL(fileURLWithPath: "output.\(audio.format.rawValue)"))
/// ```
///
/// ## Features
///
/// - **Multiple Models**: Support for `ssfm-v21` and `ssfm-v30` AI voice models
/// - **37 Languages**: English, Korean, Japanese, Chinese, Spanish, and 32 more
/// - **Emotion Control**: Preset emotions or smart context-aware inference
/// - **Audio Customization**: Volume, pitch, tempo, and format (WAV/MP3)
/// - **Voice Discovery**: Filter voices by model, gender, age, and use cases
/// - **Swift Concurrency**: Full async/await support
/// - **Cross-Platform**: iOS, macOS, tvOS, watchOS, and visionOS

// Re-export all public types
@_exported import Foundation
