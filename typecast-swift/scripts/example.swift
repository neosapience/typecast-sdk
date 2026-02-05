#!/usr/bin/env swift

// Simple example script to test the Typecast Swift SDK
// Usage: TYPECAST_API_KEY=your_key swift scripts/example.swift

import Foundation

// Since we can't import the module directly in a script,
// this is a standalone example that demonstrates the API usage

struct TypecastExample {
    let apiKey: String
    let baseURL = "https://api.typecast.ai"
    
    init(apiKey: String) {
        self.apiKey = apiKey
    }
    
    // Get voices
    func getVoices() async throws -> [[String: Any]] {
        var request = URLRequest(url: URL(string: "\(baseURL)/v2/voices")!)
        request.httpMethod = "GET"
        request.setValue(apiKey, forHTTPHeaderField: "X-API-KEY")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw NSError(domain: "TypecastError", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to fetch voices"])
        }
        
        let json = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] ?? []
        return json
    }
    
    // Text to speech
    func textToSpeech(text: String, voiceId: String) async throws -> Data {
        var request = URLRequest(url: URL(string: "\(baseURL)/v1/text-to-speech")!)
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "X-API-KEY")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "voice_id": voiceId,
            "text": text,
            "model": "ssfm-v30",
            "language": "eng"
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            let errorText = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw NSError(domain: "TypecastError", code: -1, userInfo: [NSLocalizedDescriptionKey: errorText])
        }
        
        return data
    }
}

// Main
@main
struct Main {
    static func main() async {
        print("========================================")
        print("  Typecast Swift SDK Example")
        print("========================================")
        print("")
        
        guard let apiKey = ProcessInfo.processInfo.environment["TYPECAST_API_KEY"] else {
            print("❌ Error: TYPECAST_API_KEY environment variable is not set")
            print("   Usage: TYPECAST_API_KEY=your_key swift scripts/example.swift")
            return
        }
        
        let client = TypecastExample(apiKey: apiKey)
        
        do {
            // 1. Get voices
            print("📋 Fetching available voices...")
            let voices = try await client.getVoices()
            print("✅ Found \(voices.count) voices")
            
            if let firstVoice = voices.first,
               let voiceId = firstVoice["voice_id"] as? String,
               let voiceName = firstVoice["voice_name"] as? String {
                print("   Using voice: \(voiceName) (\(voiceId))")
                print("")
                
                // 2. Generate speech
                print("🎤 Generating speech...")
                let audioData = try await client.textToSpeech(
                    text: "Hello! This is a test of the Typecast Swift SDK. The audio quality is amazing!",
                    voiceId: voiceId
                )
                print("✅ Generated \(audioData.count) bytes of audio")
                
                // 3. Save to file
                let outputPath = FileManager.default.currentDirectoryPath + "/output.wav"
                try audioData.write(to: URL(fileURLWithPath: outputPath))
                print("💾 Saved to: \(outputPath)")
            }
            
            print("")
            print("========================================")
            print("  Example completed successfully!")
            print("========================================")
            
        } catch {
            print("❌ Error: \(error.localizedDescription)")
        }
    }
}
