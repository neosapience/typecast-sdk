package com.neosapience.examples

import com.neosapience.TypecastClient
import com.neosapience.models.*
import java.nio.file.Paths
import java.nio.file.Files

/**
 * Example: text-to-speech with word/character timestamps, SRT and VTT export.
 *
 * Run: ./gradlew run -PmainClass=com.neosapience.examples.WithTimestampsKt
 */
fun main() {
    val apiKey = System.getenv("TYPECAST_API_KEY")
        ?: error("TYPECAST_API_KEY not set")

    val client = TypecastClient.create(apiKey)

    val request = TTSRequestWithTimestamps(
        voiceId = "tc_60e5426de8b95f1d3000d7b5",
        text = "Hello. How are you?",
        model = TTSModel.SSFM_V30,
        language = LanguageCode.ENG,
    )

    val resp = client.textToSpeechWithTimestamps(request, null)

    resp.saveAudio(Paths.get("/tmp/with_timestamps_kotlin.wav"))
    Files.writeString(Paths.get("/tmp/with_timestamps_kotlin.srt"), resp.toSrt())
    Files.writeString(Paths.get("/tmp/with_timestamps_kotlin.vtt"), resp.toVtt())

    println("audio: /tmp/with_timestamps_kotlin.wav (%.2fs, format=%s)".format(
        resp.audioDuration, resp.audioFormat))
    val wordCount = resp.words?.size ?: 0
    val charCount = resp.characters?.size ?: 0
    println("words: $wordCount, characters: $charCount")
    val firstCue = resp.toSrt().lines().take(4).joinToString("\n")
    println("SRT first cue:\n$firstCue")

    client.close()
}
