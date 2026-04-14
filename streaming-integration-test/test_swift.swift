// Streaming TTS integration test for typecast-swift SDK.
// Built as part of a Swift Package that depends on the local typecast-swift package.

import Foundation
import Typecast

let apiKey = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3"
let host = "https://api.icepeak.in"
let voiceID = "tc_68d259f809700d8ac76e8567"
let outputFile = "/tmp/streaming_test_swift.wav"

@main
struct StreamingTest {
    static func main() async throws {
        let client = TypecastClient(apiKey: apiKey, baseURL: host)

        let request = TTSRequestStream(
            voiceId: voiceID,
            text: "Hello, this is a streaming integration test from the Swift SDK.",
            model: "ssfm-v30",
            language: "eng",
            output: Typecast.OutputStream(audioFormat: .wav)
        )

        print("[Swift] Calling textToSpeechStream...")
        let stream = try await client.textToSpeechStream(request: request)

        var totalBytes = 0
        var chunkCount = 0
        var fileData = Data()

        for try await chunk in stream {
            fileData.append(chunk)
            totalBytes += chunk.count
            chunkCount += 1
        }

        try fileData.write(to: URL(fileURLWithPath: outputFile))
        print("[Swift] SUCCESS - \(chunkCount) chunks, \(totalBytes) bytes -> \(outputFile)")
        assert(totalBytes > 0, "No audio data received")
    }
}
