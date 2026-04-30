// Example: text-to-speech with word/character timestamps, SRT and VTT export.
import Foundation
import Typecast

guard let apiKey = ProcessInfo.processInfo.environment["TYPECAST_API_KEY"] else {
    print("Error: TYPECAST_API_KEY not set")
    exit(1)
}

let client = TypecastClient(apiKey: apiKey)

let request = TTSRequestWithTimestamps(
    voiceId: "tc_60e5426de8b95f1d3000d7b5",
    text: "Hello. How are you?",
    model: .ssfmV30,
    language: .english
)

// Run async code in a synchronous main using a semaphore
let semaphore = DispatchSemaphore(value: 0)
var exitCode: Int32 = 0

Task {
    do {
        let resp = try await client.textToSpeechWithTimestamps(request)

        let audioURL = URL(fileURLWithPath: "/tmp/with_timestamps_swift.wav")
        try resp.saveAudio(to: audioURL)

        let srt = try resp.toSrt()
        try srt.write(toFile: "/tmp/with_timestamps_swift.srt", atomically: true, encoding: .utf8)

        let vtt = try resp.toVtt()
        try vtt.write(toFile: "/tmp/with_timestamps_swift.vtt", atomically: true, encoding: .utf8)

        print("audio: /tmp/with_timestamps_swift.wav (\(String(format: "%.2f", resp.audioDuration))s, format=\(resp.audioFormat))")
        let wordCount = resp.words?.count ?? 0
        let charCount = resp.characters?.count ?? 0
        print("words: \(wordCount), characters: \(charCount)")
        let firstCue = srt.components(separatedBy: "\n").prefix(4).joined(separator: "\n")
        print("SRT first cue:\n\(firstCue)")
    } catch {
        print("Error: \(error)")
        exitCode = 1
    }
    semaphore.signal()
}

semaphore.wait()
exit(exitCode)
