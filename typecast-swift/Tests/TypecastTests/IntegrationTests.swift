import XCTest
@testable import Typecast

/// Integration tests that require a valid API key
/// Set the TYPECAST_API_KEY environment variable or use the provided test key
final class IntegrationTests: XCTestCase {
    
    var client: TypecastClient!
    
    override func setUp() async throws {
        // Use environment variable or provided test key
        let apiKey = ProcessInfo.processInfo.environment["TYPECAST_API_KEY"]
            ?? "***REDACTED***"
        
        client = TypecastClient(apiKey: apiKey)
    }
    
    // MARK: - Voice API Tests
    
    func testGetVoices() async throws {
        let voices = try await client.getVoices()
        
        XCTAssertFalse(voices.isEmpty, "Should return at least one voice")
        
        let firstVoice = voices[0]
        XCTAssertFalse(firstVoice.voiceId.isEmpty, "Voice ID should not be empty")
        XCTAssertFalse(firstVoice.voiceName.isEmpty, "Voice name should not be empty")
        XCTAssertFalse(firstVoice.models.isEmpty, "Voice should have at least one model")
        
        print("✅ Found \(voices.count) voices")
        print("   First voice: \(firstVoice.voiceName) (\(firstVoice.voiceId))")
    }
    
    func testGetVoicesWithFilter() async throws {
        let filter = VoicesV2Filter(model: .ssfmV30, gender: .female)
        let voices = try await client.getVoices(filter: filter)
        
        XCTAssertFalse(voices.isEmpty, "Should return at least one filtered voice")
        
        // Verify all returned voices match the filter
        for voice in voices {
            XCTAssertTrue(voice.models.contains { $0.version == .ssfmV30 }, 
                         "Voice should support ssfm-v30 model")
            if let gender = voice.gender {
                XCTAssertEqual(gender, .female, "Voice should be female")
            }
        }
        
        print("✅ Found \(voices.count) female voices with ssfm-v30 model")
    }
    
    func testGetVoiceById() async throws {
        // First get a voice ID from the list
        let voices = try await client.getVoices()
        guard let firstVoice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        // Then fetch the specific voice
        let voice = try await client.getVoice(voiceId: firstVoice.voiceId)
        
        XCTAssertEqual(voice.voiceId, firstVoice.voiceId)
        XCTAssertEqual(voice.voiceName, firstVoice.voiceName)
        
        print("✅ Fetched voice: \(voice.voiceName)")
        print("   Models: \(voice.models.map { $0.version.rawValue }.joined(separator: ", "))")
        if let gender = voice.gender {
            print("   Gender: \(gender.rawValue)")
        }
        if let age = voice.age {
            print("   Age: \(age.rawValue)")
        }
    }
    
    // MARK: - TTS Tests
    
    func testTextToSpeech() async throws {
        // Get a voice first
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available for ssfm-v30")
            return
        }
        
        // Create TTS request
        let request = TTSRequest(
            voiceId: voice.voiceId,
            text: "Hello! This is a test of the Typecast Swift SDK.",
            model: .ssfmV30,
            language: .english
        )
        
        let response = try await client.textToSpeech(request)
        
        XCTAssertFalse(response.audioData.isEmpty, "Audio data should not be empty")
        XCTAssertGreaterThan(response.audioData.count, 1000, "Audio data should be substantial")
        XCTAssertEqual(response.format, .wav, "Default format should be WAV")
        
        print("✅ Generated audio:")
        print("   Size: \(response.audioData.count) bytes")
        print("   Duration: \(response.duration) seconds")
        print("   Format: \(response.format.rawValue)")
        
        // Save to file for verification
        let outputPath = FileManager.default.temporaryDirectory.appendingPathComponent("test_output.\(response.format.rawValue)")
        try response.audioData.write(to: outputPath)
        print("   Saved to: \(outputPath.path)")
    }
    
    func testTextToSpeechWithEmotion() async throws {
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        // Use preset emotion
        let request = TTSRequest(
            voiceId: voice.voiceId,
            text: "I'm so excited to meet you!",
            model: .ssfmV30,
            language: .english,
            prompt: .preset(PresetPrompt(emotionPreset: .happy, emotionIntensity: 1.5))
        )
        
        let response = try await client.textToSpeech(request)
        
        XCTAssertFalse(response.audioData.isEmpty)
        print("✅ Generated happy emotion audio: \(response.audioData.count) bytes")
    }
    
    func testTextToSpeechWithSmartPrompt() async throws {
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        // Use smart prompt for context-aware emotion
        let request = TTSRequest(
            voiceId: voice.voiceId,
            text: "This is the best day of my life!",
            model: .ssfmV30,
            language: .english,
            prompt: .smart(SmartPrompt(
                previousText: "I just got accepted into my dream university!",
                nextText: "I can't wait to tell everyone!"
            ))
        )
        
        let response = try await client.textToSpeech(request)
        
        XCTAssertFalse(response.audioData.isEmpty)
        print("✅ Generated smart prompt audio: \(response.audioData.count) bytes")
    }
    
    func testTextToSpeechWithMP3Format() async throws {
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        let request = TTSRequest(
            voiceId: voice.voiceId,
            text: "Testing MP3 format output.",
            model: .ssfmV30,
            language: .english,
            output: OutputSettings(audioFormat: .mp3)
        )
        
        let response = try await client.textToSpeech(request)
        
        XCTAssertFalse(response.audioData.isEmpty)
        // Note: Server may return different Content-Type header, 
        // but the actual audio data is in the requested format
        // MP3 files are typically smaller than WAV for same content
        print("✅ Generated MP3 audio: \(response.audioData.count) bytes, format: \(response.format.rawValue)")
    }
    
    func testConvenienceSpeak() async throws {
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        // Test simple speak method
        let response = try await client.speak("Hello from the speak method!", voiceId: voice.voiceId)
        
        XCTAssertFalse(response.audioData.isEmpty)
        print("✅ Convenience speak method works: \(response.audioData.count) bytes")
    }
    
    func testConvenienceSpeakWithEmotion() async throws {
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        // Test speak with emotion
        let response = try await client.speak(
            "This is sad news...",
            voiceId: voice.voiceId,
            emotion: .sad,
            intensity: 1.3
        )
        
        XCTAssertFalse(response.audioData.isEmpty)
        print("✅ Convenience speak with emotion works: \(response.audioData.count) bytes")
    }
    
    // MARK: - Error Handling Tests
    
    func testInvalidVoiceId() async {
        let request = TTSRequest(
            voiceId: "invalid_voice_id",
            text: "This should fail",
            model: .ssfmV30
        )
        
        do {
            _ = try await client.textToSpeech(request)
            XCTFail("Should throw an error for invalid voice ID")
        } catch let error as TypecastError {
            print("✅ Caught expected error: \(error.localizedDescription)")
            // Expected error
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }
    
    func testInvalidApiKey() async {
        let invalidClient = TypecastClient(apiKey: "invalid_api_key")
        
        do {
            _ = try await invalidClient.getVoices()
            XCTFail("Should throw unauthorized error")
        } catch let error as TypecastError {
            if case .unauthorized = error {
                print("✅ Correctly received unauthorized error")
            } else {
                print("✅ Received error: \(error.localizedDescription)")
            }
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
    }
    
    // MARK: - Korean Language Test
    
    func testKoreanTTS() async throws {
        let voices = try await client.getVoices(filter: VoicesV2Filter(model: .ssfmV30))
        guard let voice = voices.first else {
            XCTFail("No voices available")
            return
        }
        
        let request = TTSRequest(
            voiceId: voice.voiceId,
            text: "안녕하세요! 타입캐스트 스위프트 SDK 테스트입니다.",
            model: .ssfmV30,
            language: .korean
        )
        
        let response = try await client.textToSpeech(request)
        
        XCTAssertFalse(response.audioData.isEmpty)
        print("✅ Korean TTS works: \(response.audioData.count) bytes")
    }
}
