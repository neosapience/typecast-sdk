import XCTest
@testable import Typecast

final class TypecastClientTests: XCTestCase {
    
    // MARK: - Model Tests
    
    func testTTSModelRawValues() {
        XCTAssertEqual(TTSModel.ssfmV30.rawValue, "ssfm-v30")
        XCTAssertEqual(TTSModel.ssfmV21.rawValue, "ssfm-v21")
    }
    
    func testLanguageCodeRawValues() {
        XCTAssertEqual(LanguageCode.english.rawValue, "eng")
        XCTAssertEqual(LanguageCode.korean.rawValue, "kor")
        XCTAssertEqual(LanguageCode.japanese.rawValue, "jpn")
    }
    
    func testEmotionPresetRawValues() {
        XCTAssertEqual(EmotionPreset.normal.rawValue, "normal")
        XCTAssertEqual(EmotionPreset.happy.rawValue, "happy")
        XCTAssertEqual(EmotionPreset.sad.rawValue, "sad")
        XCTAssertEqual(EmotionPreset.angry.rawValue, "angry")
        XCTAssertEqual(EmotionPreset.whisper.rawValue, "whisper")
    }
    
    func testAudioFormatRawValues() {
        XCTAssertEqual(AudioFormat.wav.rawValue, "wav")
        XCTAssertEqual(AudioFormat.mp3.rawValue, "mp3")
    }
    
    // MARK: - Request Encoding Tests
    
    func testTTSRequestEncoding() throws {
        let request = TTSRequest(
            voiceId: "tc_test123",
            text: "Hello, world!",
            model: .ssfmV30,
            language: .english
        )
        
        let encoder = JSONEncoder()
        let data = try encoder.encode(request)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        
        XCTAssertEqual(json?["voice_id"] as? String, "tc_test123")
        XCTAssertEqual(json?["text"] as? String, "Hello, world!")
        XCTAssertEqual(json?["model"] as? String, "ssfm-v30")
        XCTAssertEqual(json?["language"] as? String, "eng")
    }
    
    func testPresetPromptEncoding() throws {
        let prompt = PresetPrompt(emotionPreset: .happy, emotionIntensity: 1.5)
        
        let encoder = JSONEncoder()
        let data = try encoder.encode(prompt)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        
        XCTAssertEqual(json?["emotion_type"] as? String, "preset")
        XCTAssertEqual(json?["emotion_preset"] as? String, "happy")
        XCTAssertEqual(json?["emotion_intensity"] as? Double, 1.5)
    }
    
    func testSmartPromptEncoding() throws {
        let prompt = SmartPrompt(previousText: "I'm so excited!", nextText: "This is amazing!")
        
        let encoder = JSONEncoder()
        let data = try encoder.encode(prompt)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        
        XCTAssertEqual(json?["emotion_type"] as? String, "smart")
        XCTAssertEqual(json?["previous_text"] as? String, "I'm so excited!")
        XCTAssertEqual(json?["next_text"] as? String, "This is amazing!")
    }
    
    func testOutputSettingsEncoding() throws {
        let output = OutputSettings(volume: 150, audioPitch: 2, audioTempo: 1.2, audioFormat: .mp3)
        
        let encoder = JSONEncoder()
        let data = try encoder.encode(output)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        
        XCTAssertEqual(json?["volume"] as? Int, 150)
        XCTAssertEqual(json?["audio_pitch"] as? Int, 2)
        XCTAssertEqual(json?["audio_tempo"] as? Double, 1.2)
        XCTAssertEqual(json?["audio_format"] as? String, "mp3")
    }
    
    // MARK: - Voice Filter Tests
    
    func testVoicesV2FilterToQueryParams() {
        let filter = VoicesV2Filter(
            model: .ssfmV30,
            gender: .female,
            age: .youngAdult,
            useCases: .audiobook
        )
        
        let params = filter.toQueryParams()
        
        XCTAssertEqual(params["model"], "ssfm-v30")
        XCTAssertEqual(params["gender"], "female")
        XCTAssertEqual(params["age"], "young_adult")
        XCTAssertEqual(params["use_cases"], "Audiobook")
    }
    
    func testEmptyFilterToQueryParams() {
        let filter = VoicesV2Filter()
        let params = filter.toQueryParams()
        XCTAssertTrue(params.isEmpty)
    }
    
    // MARK: - Error Tests
    
    func testTypecastErrorStatusCodes() {
        XCTAssertEqual(TypecastError.badRequest("test").statusCode, 400)
        XCTAssertEqual(TypecastError.unauthorized("test").statusCode, 401)
        XCTAssertEqual(TypecastError.paymentRequired("test").statusCode, 402)
        XCTAssertEqual(TypecastError.notFound("test").statusCode, 404)
        XCTAssertEqual(TypecastError.validationError("test").statusCode, 422)
        XCTAssertEqual(TypecastError.rateLimitExceeded("test").statusCode, 429)
        XCTAssertEqual(TypecastError.serverError("test").statusCode, 500)
    }
    
    func testTypecastErrorFromResponse() {
        let jsonData = """
        {"detail": "Invalid API key"}
        """.data(using: .utf8)
        
        let error = TypecastError.fromResponse(statusCode: 401, data: jsonData)
        
        if case .unauthorized(let message) = error {
            XCTAssertEqual(message, "Invalid API key")
        } else {
            XCTFail("Expected unauthorized error")
        }
    }
    
    // MARK: - Client Configuration Tests
    
    func testClientConfiguration() {
        let config = TypecastConfiguration(apiKey: "test-key", baseURL: "https://custom.api.com")
        let client = TypecastClient(configuration: config)
        
        XCTAssertNotNil(client)
    }
    
    func testClientConvenienceInit() {
        let client = TypecastClient(apiKey: "test-key")
        XCTAssertNotNil(client)
    }
    
    // MARK: - Voice Response Decoding Tests
    
    func testVoiceV2Decoding() throws {
        let json = """
        {
            "voice_id": "tc_test123",
            "voice_name": "Test Voice",
            "models": [
                {
                    "version": "ssfm-v30",
                    "emotions": ["normal", "happy", "sad"]
                }
            ],
            "gender": "female",
            "age": "young_adult",
            "use_cases": ["Audiobook", "Podcast"]
        }
        """.data(using: .utf8)!
        
        let decoder = JSONDecoder()
        let voice = try decoder.decode(VoiceV2.self, from: json)
        
        XCTAssertEqual(voice.voiceId, "tc_test123")
        XCTAssertEqual(voice.voiceName, "Test Voice")
        XCTAssertEqual(voice.models.count, 1)
        XCTAssertEqual(voice.models[0].version, .ssfmV30)
        XCTAssertEqual(voice.models[0].emotions, ["normal", "happy", "sad"])
        XCTAssertEqual(voice.gender, .female)
        XCTAssertEqual(voice.age, .youngAdult)
        XCTAssertEqual(voice.useCases, ["Audiobook", "Podcast"])
    }
}
