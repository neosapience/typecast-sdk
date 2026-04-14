// Streaming TTS integration test for typecast-kotlin SDK.
package streamingtest

import com.neosapience.TypecastClient
import com.neosapience.models.*
import java.io.File

fun main() {
    val apiKey = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3"
    val host = "https://api.icepeak.in"
    val voiceId = "tc_68d259f809700d8ac76e8567"
    val outputFile = "/tmp/streaming_test_kotlin.wav"

    val client = TypecastClient.Builder()
        .apiKey(apiKey)
        .baseUrl(host)
        .build()

    val request = TTSRequestStream(
        voiceId = voiceId,
        text = "Hello, this is a streaming integration test from the Kotlin SDK.",
        model = "ssfm-v30",
        language = "eng",
        output = OutputStream(audioFormat = AudioFormat.WAV),
    )

    println("[Kotlin] Calling textToSpeechStream...")
    val stream = client.textToSpeechStream(request)
    val file = File(outputFile)

    var totalBytes = 0
    var chunkCount = 0
    file.outputStream().use { fos ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            fos.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
            chunkCount++
        }
    }
    stream.close()

    println("[Kotlin] SUCCESS - $chunkCount chunks, $totalBytes bytes -> $outputFile")
    require(totalBytes > 0) { "No audio data received" }
}
