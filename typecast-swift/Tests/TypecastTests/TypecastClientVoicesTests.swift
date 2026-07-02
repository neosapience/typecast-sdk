import XCTest
@testable import Typecast

final class TypecastClientVoicesTests: TypecastClientMockTestCase {

    // MARK: - getVoices V2

    func testGetVoicesNoFilter() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v2/voices")
            XCTAssertNil(req.url?.query)
            XCTAssertTrue(req.value(forHTTPHeaderField: "User-Agent")?.contains("typecast-swift/") ?? false)
            XCTAssertTrue(req.value(forHTTPHeaderField: "User-Agent")?.contains("sdk_env=swift") ?? false)
            let body = """
            [
              {
                "voice_id": "tc_1",
                "voice_name": "Alice",
                "models": [{"version":"ssfm-v30","emotions":["normal"]}]
              }
            ]
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let voices = try await client.getVoices()
        XCTAssertEqual(voices.count, 1)
        XCTAssertEqual(voices[0].voiceId, "tc_1")
        XCTAssertNil(voices[0].gender)
        XCTAssertNil(voices[0].age)
        XCTAssertNil(voices[0].useCases)
    }

    func testGetVoicesWithFilter() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v2/voices")
            let query = req.url?.query ?? ""
            XCTAssertTrue(query.contains("model=ssfm-v30"))
            XCTAssertTrue(query.contains("gender=female"))
            XCTAssertTrue(query.contains("age=young_adult"))
            XCTAssertTrue(query.contains("use_cases=Audiobook"))
            return (self.httpResponse(url: req.url!, status: 200), "[]".data(using: .utf8))
        }

        let filter = VoicesV2Filter(
            model: .ssfmV30,
            gender: .female,
            age: .youngAdult,
            useCases: .audiobook
        )
        let voices = try await client.getVoices(filter: filter)
        XCTAssertTrue(voices.isEmpty)
    }

    func testGetVoiceByIdSuccess() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v2/voices/tc_42")
            let body = """
            {
              "voice_id":"tc_42",
              "voice_name":"Bob",
              "models":[{"version":"ssfm-v21","emotions":["normal","happy"]}],
              "gender":"male",
              "age":"middle_age",
              "use_cases":["News"]
            }
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let voice = try await client.getVoice(voiceId: "tc_42")
        XCTAssertEqual(voice.voiceId, "tc_42")
        XCTAssertEqual(voice.gender, .male)
        XCTAssertEqual(voice.age, .middleAge)
        XCTAssertEqual(voice.useCases, ["News"])
    }

    func testGetVoiceEncodesVoiceIdPathComponent() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertTrue(req.url?.absoluteString.contains("/v2/voices/tc%2F42%20%23x") ?? false)
            let body = """
            {
              "voice_id":"tc/42 #x",
              "voice_name":"Bob",
              "models":[{"version":"ssfm-v30","emotions":["normal"]}],
              "use_cases":[]
            }
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let voice = try await client.getVoice(voiceId: "tc/42 #x")
        XCTAssertEqual(voice.voiceId, "tc/42 #x")
    }

    func testGetVoiceRejectsEmptyVoiceIdBeforeRequest() async {
        MockURLProtocol.requestHandler = { _ in
            XCTFail("getVoice should validate voiceId before requesting")
            return (self.httpResponse(url: URL(string: self.baseURL)!, status: 200), Data())
        }

        do {
            _ = try await client.getVoice(voiceId: "")
            XCTFail("expected voiceId validation error")
        } catch let error as TypecastError {
            guard case .badRequest(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "voiceId must be a valid path component")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testGetVoiceNotFound() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 404),
                #"{"detail":"missing"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.getVoice(voiceId: "tc_404")
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .notFound(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "missing")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    // MARK: - recommendVoices

    func testRecommendVoicesSuccess() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/voices/recommendations")
            let components = URLComponents(url: req.url!, resolvingAgainstBaseURL: false)
            let queryItems = components?.queryItems ?? []
            XCTAssertEqual(queryItems.first(where: { $0.name == "query" })?.value, "warm narrator")
            XCTAssertEqual(queryItems.first(where: { $0.name == "count" })?.value, "2")
            let body = """
            [
              {"voice_id":"tc_1","voice_name":"Warm Narrator","score":0.97}
            ]
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let voices = try await client.recommendVoices(query: "warm narrator", count: 2)
        XCTAssertEqual(voices.count, 1)
        XCTAssertEqual(voices[0].voiceId, "tc_1")
        XCTAssertEqual(voices[0].voiceName, "Warm Narrator")
        XCTAssertEqual(voices[0].score, 0.97)
    }

    func testRecommendVoicesDefaultsCountToFive() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/voices/recommendations")
            let components = URLComponents(url: req.url!, resolvingAgainstBaseURL: false)
            let queryItems = components?.queryItems ?? []
            XCTAssertEqual(queryItems.first(where: { $0.name == "count" })?.value, "5")
            return (self.httpResponse(url: req.url!, status: 200), "[]".data(using: .utf8))
        }

        let voices = try await client.recommendVoices(query: "calm voice")
        XCTAssertTrue(voices.isEmpty)
    }

    func testRecommendVoicesRejectsInvalidCountBeforeRequest() async {
        MockURLProtocol.requestHandler = { _ in
            XCTFail("recommendVoices should validate count before requesting")
            return (self.httpResponse(url: URL(string: self.baseURL)!, status: 200), Data())
        }

        do {
            _ = try await client.recommendVoices(query: "voice", count: 0)
            XCTFail("expected count validation error")
        } catch let error as TypecastError {
            guard case .badRequest(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "count must be between 1 and 10")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    // MARK: - getVoicesV1 (deprecated)

    @available(*, deprecated)
    func testGetVoicesV1NoModel() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/voices")
            XCTAssertNil(req.url?.query)
            let body = """
            [{"voice_id":"tc_v1","voice_name":"V1","model":"ssfm-v21","emotions":["normal"]}]
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }
        let voices = try await client.getVoicesV1()
        XCTAssertEqual(voices.count, 1)
        XCTAssertEqual(voices[0].voiceId, "tc_v1")
        XCTAssertEqual(voices[0].model, .ssfmV21)
    }

    @available(*, deprecated)
    func testGetVoicesV1WithModel() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/voices")
            XCTAssertEqual(req.url?.query, "model=ssfm-v30")
            return (self.httpResponse(url: req.url!, status: 200), "[]".data(using: .utf8))
        }
        let voices = try await client.getVoicesV1(model: .ssfmV30)
        XCTAssertTrue(voices.isEmpty)
    }

    // MARK: - Convenience speak

    func testConvenienceSpeak() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav", "X-Audio-Duration": "1.0"]
                ),
                Data([0x01])
            )
        }
        let response = try await client.speak("hi", voiceId: "tc_1")
        XCTAssertEqual(response.duration, 1.0)
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        XCTAssertEqual(json?["model"] as? String, "ssfm-v30")
    }

    func testConvenienceSpeakWithEmotionV30UsesPresetPrompt() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav"]
                ),
                Data([0x01])
            )
        }
        _ = try await client.speak(
            "hi",
            voiceId: "tc_1",
            model: .ssfmV30,
            emotion: .happy,
            intensity: 1.4
        )
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        let prompt = json?["prompt"] as? [String: Any]
        XCTAssertEqual(prompt?["emotion_type"] as? String, "preset")
        XCTAssertEqual(prompt?["emotion_preset"] as? String, "happy")
        XCTAssertEqual(prompt?["emotion_intensity"] as? Double, 1.4)
    }

    func testConvenienceSpeakWithEmotionV21UsesBasicPrompt() async throws {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(
                    url: req.url!,
                    status: 200,
                    headers: ["Content-Type": "audio/wav"]
                ),
                Data([0x01])
            )
        }
        _ = try await client.speak(
            "hi",
            voiceId: "tc_1",
            model: .ssfmV21,
            emotion: .sad,
            intensity: 0.5
        )
        let body = try XCTUnwrap(MockURLProtocol.lastBody)
        let json = try JSONSerialization.jsonObject(with: body) as? [String: Any]
        let prompt = json?["prompt"] as? [String: Any]
        XCTAssertNil(prompt?["emotion_type"])
        XCTAssertEqual(prompt?["emotion_preset"] as? String, "sad")
        XCTAssertEqual(prompt?["emotion_intensity"] as? Double, 0.5)
    }
}
