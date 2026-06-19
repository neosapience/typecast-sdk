import XCTest
@testable import Typecast

/// Coverage for all enum raw values, Codable round-trips, and the
/// `TTSPrompt` discriminated union.
final class ModelsTests: XCTestCase {

    // MARK: - Enums

    func testTTSModelAllRawValues() {
        XCTAssertEqual(TTSModel.ssfmV30.rawValue, "ssfm-v30")
        XCTAssertEqual(TTSModel.ssfmV21.rawValue, "ssfm-v21")
        XCTAssertEqual(TTSModel(rawValue: "ssfm-v30"), .ssfmV30)
        XCTAssertEqual(TTSModel(rawValue: "ssfm-v21"), .ssfmV21)
        XCTAssertNil(TTSModel(rawValue: "nope"))
    }

    func testLanguageCodeRoundTrips() throws {
        let allCases: [LanguageCode] = [
            .english, .korean, .japanese, .spanish, .german, .french, .italian,
            .polish, .dutch, .russian, .greek, .tamil, .tagalog, .finnish,
            .chinese, .slovak, .arabic, .croatian, .ukrainian, .indonesian,
            .danish, .swedish, .malay, .czech, .portuguese, .bulgarian, .romanian,
            .bengali, .hindi, .hungarian, .minNan, .norwegian, .punjabi,
            .thai, .turkish, .vietnamese, .cantonese
        ]
        let encoder = JSONEncoder()
        let decoder = JSONDecoder()
        for code in allCases {
            let data = try encoder.encode([code])
            let decoded = try decoder.decode([LanguageCode].self, from: data)
            XCTAssertEqual(decoded.first, code)
        }
    }

    func testEmotionPresetAll() {
        XCTAssertEqual(EmotionPreset.normal.rawValue, "normal")
        XCTAssertEqual(EmotionPreset.happy.rawValue, "happy")
        XCTAssertEqual(EmotionPreset.sad.rawValue, "sad")
        XCTAssertEqual(EmotionPreset.angry.rawValue, "angry")
        XCTAssertEqual(EmotionPreset.whisper.rawValue, "whisper")
        XCTAssertEqual(EmotionPreset.toneup.rawValue, "toneup")
        XCTAssertEqual(EmotionPreset.tonedown.rawValue, "tonedown")
    }

    func testAudioFormatAll() {
        XCTAssertEqual(AudioFormat.wav.rawValue, "wav")
        XCTAssertEqual(AudioFormat.mp3.rawValue, "mp3")
    }

    func testGenderEnum() {
        XCTAssertEqual(GenderEnum.male.rawValue, "male")
        XCTAssertEqual(GenderEnum.female.rawValue, "female")
    }

    func testAgeEnumAll() {
        XCTAssertEqual(AgeEnum.child.rawValue, "child")
        XCTAssertEqual(AgeEnum.teenager.rawValue, "teenager")
        XCTAssertEqual(AgeEnum.youngAdult.rawValue, "young_adult")
        XCTAssertEqual(AgeEnum.middleAge.rawValue, "middle_age")
        XCTAssertEqual(AgeEnum.elder.rawValue, "elder")
    }

    func testUseCaseEnumAll() {
        let cases: [(UseCaseEnum, String)] = [
            (.announcer, "Announcer"),
            (.anime, "Anime"),
            (.audiobook, "Audiobook"),
            (.conversational, "Conversational"),
            (.documentary, "Documentary"),
            (.eLearning, "E-learning"),
            (.rapper, "Rapper"),
            (.game, "Game"),
            (.tiktokReels, "Tiktok/Reels"),
            (.news, "News"),
            (.podcast, "Podcast"),
            (.voicemail, "Voicemail"),
            (.ads, "Ads")
        ]
        for (value, expected) in cases {
            XCTAssertEqual(value.rawValue, expected)
        }
    }

    // MARK: - Prompt

    func testBasicPromptRoundTrip() throws {
        let prompt = Prompt(emotionPreset: .happy, emotionIntensity: 1.2)
        let data = try JSONEncoder().encode(prompt)
        let decoded = try JSONDecoder().decode(Prompt.self, from: data)
        XCTAssertEqual(decoded.emotionPreset, .happy)
        XCTAssertEqual(decoded.emotionIntensity, 1.2)
    }

    func testPresetPromptRoundTrip() throws {
        let prompt = PresetPrompt(emotionPreset: .sad, emotionIntensity: 0.5)
        let data = try JSONEncoder().encode(prompt)
        let decoded = try JSONDecoder().decode(PresetPrompt.self, from: data)
        XCTAssertEqual(decoded.emotionType, "preset")
        XCTAssertEqual(decoded.emotionPreset, .sad)
        XCTAssertEqual(decoded.emotionIntensity, 0.5)
    }

    func testSmartPromptRoundTrip() throws {
        let prompt = SmartPrompt(previousText: "before", nextText: "after")
        let data = try JSONEncoder().encode(prompt)
        let decoded = try JSONDecoder().decode(SmartPrompt.self, from: data)
        XCTAssertEqual(decoded.emotionType, "smart")
        XCTAssertEqual(decoded.previousText, "before")
        XCTAssertEqual(decoded.nextText, "after")
    }

    // MARK: - TTSPrompt union

    func testTTSPromptBasicEncodeDecode() throws {
        let union = TTSPrompt.basic(Prompt(emotionPreset: .normal, emotionIntensity: 1.0))
        let data = try JSONEncoder().encode(union)
        let decoded = try JSONDecoder().decode(TTSPrompt.self, from: data)
        guard case .basic(let prompt) = decoded else {
            XCTFail("expected basic")
            return
        }
        XCTAssertEqual(prompt.emotionPreset, .normal)
    }

    func testTTSPromptPresetEncodeDecode() throws {
        let union = TTSPrompt.preset(PresetPrompt(emotionPreset: .happy, emotionIntensity: 1.5))
        let data = try JSONEncoder().encode(union)
        let decoded = try JSONDecoder().decode(TTSPrompt.self, from: data)
        guard case .preset(let prompt) = decoded else {
            XCTFail("expected preset")
            return
        }
        XCTAssertEqual(prompt.emotionPreset, .happy)
        XCTAssertEqual(prompt.emotionIntensity, 1.5)
    }

    func testTTSPromptSmartEncodeDecode() throws {
        let union = TTSPrompt.smart(SmartPrompt(previousText: "p", nextText: "n"))
        let data = try JSONEncoder().encode(union)
        let decoded = try JSONDecoder().decode(TTSPrompt.self, from: data)
        guard case .smart(let prompt) = decoded else {
            XCTFail("expected smart")
            return
        }
        XCTAssertEqual(prompt.previousText, "p")
        XCTAssertEqual(prompt.nextText, "n")
    }

    // MARK: - OutputSettings

    func testOutputSettingsRoundTripFull() throws {
        let output = OutputSettings(
            volume: 100,
            targetLufs: -16.0,
            audioPitch: -3,
            audioTempo: 1.5,
            audioFormat: .mp3
        )
        let data = try JSONEncoder().encode(output)
        let decoded = try JSONDecoder().decode(OutputSettings.self, from: data)
        XCTAssertEqual(decoded.volume, 100)
        XCTAssertEqual(decoded.targetLufs, -16.0)
        XCTAssertEqual(decoded.audioPitch, -3)
        XCTAssertEqual(decoded.audioTempo, 1.5)
        XCTAssertEqual(decoded.audioFormat, .mp3)
    }

    // MARK: - Streaming models

    func testOutputStreamRoundTripFull() throws {
        let output = Typecast.OutputStream(targetLufs: -14.0, audioPitch: 2, audioTempo: 1.25, audioFormat: .mp3)
        let data = try JSONEncoder().encode(output)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        // Streaming output must NOT include volume, but accepts target_lufs.
        XCTAssertNil(json?["volume"])
        XCTAssertEqual(json?["target_lufs"] as? Double, -14.0)
        XCTAssertEqual(json?["audio_pitch"] as? Int, 2)
        XCTAssertEqual(json?["audio_tempo"] as? Double, 1.25)
        XCTAssertEqual(json?["audio_format"] as? String, "mp3")

        let decoded = try JSONDecoder().decode(Typecast.OutputStream.self, from: data)
        XCTAssertEqual(decoded.audioPitch, 2)
        XCTAssertEqual(decoded.targetLufs, -14.0)
        XCTAssertEqual(decoded.audioTempo, 1.25)
        XCTAssertEqual(decoded.audioFormat, .mp3)
    }

    func testOutputStreamDefaultsAreNil() {
        let output = Typecast.OutputStream()
        XCTAssertNil(output.targetLufs)
        XCTAssertNil(output.audioPitch)
        XCTAssertNil(output.audioTempo)
        XCTAssertNil(output.audioFormat)
    }

    func testOutputStreamRejectsInvalidDecodedTargetLufs() {
        let data = #"{"target_lufs":0.1}"#.data(using: .utf8)!
        XCTAssertThrowsError(try JSONDecoder().decode(Typecast.OutputStream.self, from: data))
    }

    func testTTSRequestStreamFullRoundTrip() throws {
        let request = TTSRequestStream(
            voiceId: "tc_s",
            text: "stream this",
            model: .ssfmV30,
            language: .korean,
            prompt: .smart(SmartPrompt(previousText: "p", nextText: "n")),
            output: Typecast.OutputStream(audioPitch: -1, audioTempo: 0.9, audioFormat: .wav),
            seed: 99
        )
        let data = try JSONEncoder().encode(request)
        let decoded = try JSONDecoder().decode(TTSRequestStream.self, from: data)
        XCTAssertEqual(decoded.voiceId, "tc_s")
        XCTAssertEqual(decoded.text, "stream this")
        XCTAssertEqual(decoded.model, .ssfmV30)
        XCTAssertEqual(decoded.language, .korean)
        XCTAssertEqual(decoded.seed, 99)
        XCTAssertEqual(decoded.output?.audioPitch, -1)
        XCTAssertEqual(decoded.output?.audioTempo, 0.9)
        XCTAssertEqual(decoded.output?.audioFormat, .wav)
        guard case .smart(let smart) = decoded.prompt else {
            XCTFail("expected smart prompt"); return
        }
        XCTAssertEqual(smart.previousText, "p")
        XCTAssertEqual(smart.nextText, "n")
    }

    func testTTSRequestStreamMinimalDefaults() {
        let request = TTSRequestStream(voiceId: "tc_s", text: "hi", model: .ssfmV21)
        XCTAssertNil(request.language)
        XCTAssertNil(request.prompt)
        XCTAssertNil(request.output)
        XCTAssertNil(request.seed)
    }

    // MARK: - TTSRequest / TTSResponse

    func testTTSRequestFullRoundTrip() throws {
        let request = TTSRequest(
            voiceId: "tc_x",
            text: "hello",
            model: .ssfmV30,
            language: .english,
            prompt: .preset(PresetPrompt(emotionPreset: .happy, emotionIntensity: 1.0)),
            output: OutputSettings(volume: 90, audioFormat: .wav),
            seed: 7
        )
        let data = try JSONEncoder().encode(request)
        let decoded = try JSONDecoder().decode(TTSRequest.self, from: data)
        XCTAssertEqual(decoded.voiceId, "tc_x")
        XCTAssertEqual(decoded.text, "hello")
        XCTAssertEqual(decoded.model, .ssfmV30)
        XCTAssertEqual(decoded.language, .english)
        XCTAssertEqual(decoded.seed, 7)
        XCTAssertEqual(decoded.output?.volume, 90)
    }

    func testTTSResponseRoundTrip() throws {
        let response = TTSResponse(audioData: Data([1, 2, 3]), duration: 4.5, format: .mp3)
        let data = try JSONEncoder().encode(response)
        let decoded = try JSONDecoder().decode(TTSResponse.self, from: data)
        XCTAssertEqual(decoded.audioData, Data([1, 2, 3]))
        XCTAssertEqual(decoded.duration, 4.5)
        XCTAssertEqual(decoded.format, .mp3)
    }

    // MARK: - Voice / VoiceV2

    func testVoiceRoundTrip() throws {
        let json = """
        {"voice_id":"tc_v1","voice_name":"V1","model":"ssfm-v21","emotions":["normal"]}
        """.data(using: .utf8)!
        let voice = try JSONDecoder().decode(Voice.self, from: json)
        XCTAssertEqual(voice.voiceId, "tc_v1")
        XCTAssertEqual(voice.model, .ssfmV21)
        let reEncoded = try JSONEncoder().encode(voice)
        let reDecoded = try JSONDecoder().decode(Voice.self, from: reEncoded)
        XCTAssertEqual(reDecoded.voiceName, "V1")
    }

    func testModelInfoRoundTrip() throws {
        let info = ModelInfo(version: .ssfmV30, emotions: ["normal", "happy"])
        let data = try JSONEncoder().encode(info)
        let decoded = try JSONDecoder().decode(ModelInfo.self, from: data)
        XCTAssertEqual(decoded.version, .ssfmV30)
        XCTAssertEqual(decoded.emotions, ["normal", "happy"])
    }

    func testVoiceV2RoundTrip() throws {
        let info = ModelInfo(version: .ssfmV30, emotions: ["normal"])
        let v = VoiceV2(
            voiceId: "tc_id",
            voiceName: "Name",
            models: [info],
            gender: .male,
            age: .elder,
            useCases: ["News"]
        )
        let data = try JSONEncoder().encode(v)
        let decoded = try JSONDecoder().decode(VoiceV2.self, from: data)
        XCTAssertEqual(decoded.voiceId, "tc_id")
        XCTAssertEqual(decoded.gender, .male)
        XCTAssertEqual(decoded.age, .elder)
        XCTAssertEqual(decoded.useCases, ["News"])
    }

    // MARK: - VoicesV2Filter

    func testVoicesV2FilterPartial() {
        let filter = VoicesV2Filter(model: .ssfmV21)
        let params = filter.toQueryParams()
        XCTAssertEqual(params, ["model": "ssfm-v21"])
    }

    func testVoicesV2FilterEmpty() {
        XCTAssertTrue(VoicesV2Filter().toQueryParams().isEmpty)
    }

    func testVoicesV2FilterFull() {
        let filter = VoicesV2Filter(
            model: .ssfmV30,
            gender: .female,
            age: .child,
            useCases: .anime
        )
        let params = filter.toQueryParams()
        XCTAssertEqual(params["model"], "ssfm-v30")
        XCTAssertEqual(params["gender"], "female")
        XCTAssertEqual(params["age"], "child")
        XCTAssertEqual(params["use_cases"], "Anime")
    }

    // MARK: - Subscription

    func testPlanTierAllRawValues() {
        XCTAssertEqual(PlanTier.free.rawValue, "free")
        XCTAssertEqual(PlanTier.lite.rawValue, "lite")
        XCTAssertEqual(PlanTier.plus.rawValue, "plus")
        XCTAssertEqual(PlanTier.custom.rawValue, "custom")
        XCTAssertEqual(PlanTier(rawValue: "free"), .free)
        XCTAssertEqual(PlanTier(rawValue: "lite"), .lite)
        XCTAssertEqual(PlanTier(rawValue: "plus"), .plus)
        XCTAssertEqual(PlanTier(rawValue: "custom"), .custom)
    }

    func testCreditsRoundTrip() throws {
        let json = """
        {"plan_credits": 500, "used_credits": 100}
        """.data(using: .utf8)!
        let credits = try JSONDecoder().decode(Credits.self, from: json)
        XCTAssertEqual(credits.planCredits, 500)
        XCTAssertEqual(credits.usedCredits, 100)
        let reEncoded = try JSONEncoder().encode(credits)
        let reDecoded = try JSONDecoder().decode(Credits.self, from: reEncoded)
        XCTAssertEqual(reDecoded.planCredits, 500)
        XCTAssertEqual(reDecoded.usedCredits, 100)
    }

    func testLimitsRoundTrip() throws {
        let json = """
        {"concurrency_limit": 8}
        """.data(using: .utf8)!
        let limits = try JSONDecoder().decode(Limits.self, from: json)
        XCTAssertEqual(limits.concurrencyLimit, 8)
        let reEncoded = try JSONEncoder().encode(limits)
        let reDecoded = try JSONDecoder().decode(Limits.self, from: reEncoded)
        XCTAssertEqual(reDecoded.concurrencyLimit, 8)
    }

    func testSubscriptionResponseRoundTrip() throws {
        let json = """
        {
          "plan": "free",
          "credits": {"plan_credits": 100, "used_credits": 0},
          "limits": {"concurrency_limit": 1}
        }
        """.data(using: .utf8)!
        let subscription = try JSONDecoder().decode(SubscriptionResponse.self, from: json)
        XCTAssertEqual(subscription.plan, .free)
        XCTAssertEqual(subscription.credits.planCredits, 100)
        XCTAssertEqual(subscription.credits.usedCredits, 0)
        XCTAssertEqual(subscription.limits.concurrencyLimit, 1)
        let reEncoded = try JSONEncoder().encode(subscription)
        let reDecoded = try JSONDecoder().decode(SubscriptionResponse.self, from: reEncoded)
        XCTAssertEqual(reDecoded.plan, .free)
    }

    // MARK: - Configuration

    func testConfigurationDefaults() {
        let config = TypecastConfiguration(apiKey: "k")
        XCTAssertEqual(config.apiKey, "k")
        XCTAssertEqual(config.baseURL, "https://api.typecast.ai")
    }

    func testConfigurationCustomBaseURL() {
        let config = TypecastConfiguration(apiKey: "k", baseURL: "https://x")
        XCTAssertEqual(config.baseURL, "https://x")
    }
}
