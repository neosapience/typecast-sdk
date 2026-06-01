package com.neosapience.examples

import com.neosapience.TypecastClient
import com.neosapience.models.TTSModel
import com.neosapience.models.TTSRequest
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Example: instant cloning — clone a voice from an audio sample, synthesize
 * speech with it, then delete the clone when done.
 *
 * Requirements:
 *   - TYPECAST_API_KEY environment variable (or .env file)
 *   - A short audio sample (WAV or MP3, ≤ 25 MB) at the path below
 *
 * Run:
 *   TYPECAST_API_KEY=your-key ./gradlew run -PmainClass=com.neosapience.examples.QuickCloningKt
 */
fun main() {
    val apiKey = System.getenv("TYPECAST_API_KEY")
        ?: error("TYPECAST_API_KEY environment variable is not set")

    val client = TypecastClient.create(apiKey)

    // ------------------------------------------------------------------
    // 1. Clone a voice from an audio file
    // ------------------------------------------------------------------
    val audioFile = File("my_voice_sample.wav")
    if (!audioFile.exists()) {
        error("Audio file not found: ${audioFile.absolutePath}. " +
            "Please provide a WAV or MP3 sample (≤ 25 MB).")
    }

    println("Uploading audio sample: ${audioFile.name} (${audioFile.length()} bytes)")

    val cloned = client.cloneVoice(
        audioFile = audioFile,
        name = "My Cloned Voice",
        model = "ssfm-v30",
    )

    println("Cloned voice created:")
    println("  Voice ID : ${cloned.voiceId}")
    println("  Name     : ${cloned.name}")
    println("  Model    : ${cloned.model}")

    // ------------------------------------------------------------------
    // 2. Synthesize speech using the cloned voice
    // ------------------------------------------------------------------
    val request = TTSRequest.builder()
        .voiceId(cloned.voiceId)
        .text("Hello! This is my cloned voice speaking.")
        .model(TTSModel.SSFM_V30)
        .build()

    val ttsResponse = client.textToSpeech(request)

    val outputPath = Paths.get("/tmp/quick_cloning_kotlin.wav")
    Files.write(outputPath, ttsResponse.audioData)
    println("Audio saved to $outputPath (duration: %.2fs)".format(ttsResponse.duration))

    // ------------------------------------------------------------------
    // 3. Delete the cloned voice when it is no longer needed
    // ------------------------------------------------------------------
    client.deleteVoice(cloned.voiceId)
    println("Cloned voice ${cloned.voiceId} deleted.")

    client.close()
}
