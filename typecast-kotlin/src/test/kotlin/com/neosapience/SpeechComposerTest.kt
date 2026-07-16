package com.neosapience

import com.neosapience.exceptions.TypecastException
import com.neosapience.models.AudioFormat
import com.neosapience.models.EmotionPreset
import com.neosapience.models.LanguageCode
import com.neosapience.models.Output
import com.neosapience.models.PresetPrompt
import com.neosapience.models.Prompt
import com.neosapience.models.TTSModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Field

class SpeechComposerTest {
    @Test
    fun generateUsesComposeApiAndMergesOverrides() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "audio/mpeg")
                    .setHeader("X-Audio-Duration", "1.25")
                    .setBody("composed-audio")
            )
            server.start()
            val client = TypecastClient.builder().apiKey("test-key")
                .baseUrl(server.url("/").toString()).build()

            val response = client.composeSpeech()
                .defaults(
                    ComposerSettings(
                        voiceId = "voice-a",
                        model = TTSModel.SSFM_V30,
                        output = Output(audioPitch = 1, audioFormat = AudioFormat.MP3),
                    )
                )
                .say(
                    "Hello<|0.3s|>world",
                    ComposerSettings(
                        voiceId = "voice-b",
                        output = Output(volume = null, audioPitch = null, audioTempo = 1.1, audioFormat = null),
                    )
                )
                .generate(AudioFormat.MP3)

            val request = server.takeRequest()
            assertEquals("/v1/text-to-speech/compose", request.path)
            val segments = Json.parseToJsonElement(request.body.readUtf8()).jsonObject["segments"]!!.jsonArray
            assertEquals(listOf("tts", "pause", "tts"), segments.map { it.jsonObject["type"]!!.jsonPrimitive.content })
            assertEquals("Hello", segments[0].jsonObject["text"]!!.jsonPrimitive.content)
            assertEquals("voice-b", segments[0].jsonObject["voice_id"]!!.jsonPrimitive.content)
            val output = segments[0].jsonObject["output"]!!.jsonObject
            assertEquals("mp3", output["audio_format"]!!.jsonPrimitive.content)
            assertEquals(1, output["audio_pitch"]!!.jsonPrimitive.content.toInt())
            assertEquals(1.1, output["audio_tempo"]!!.jsonPrimitive.content.toDouble(), 0.0001)
            assertEquals(0.3, segments[1].jsonObject["duration_seconds"]!!.jsonPrimitive.content.toDouble(), 0.0001)
            assertEquals("world", segments[2].jsonObject["text"]!!.jsonPrimitive.content)
            assertArrayEquals("composed-audio".toByteArray(), response.audioData)
            assertEquals("mp3", response.format)
            assertEquals(1.25, response.duration, 0.0001)
            assertEquals(1, server.requestCount)
            client.close()
        }
    }

    @Test
    fun validatesBeforeNetwork() {
        val client = TypecastClient.builder().apiKey("test-key").baseUrl("http://localhost:1").build()
        assertThrows(IllegalStateException::class.java) { client.composeSpeech().say("Hello").generate() }
        assertThrows(IllegalStateException::class.java) { client.composeSpeech().generate() }
        client.close()
    }

    @Test
    fun parsePauseMarkupPreservesInvalidTokens() {
        val parts = SpeechComposer.parsePauseMarkup("a<|0.3s|>b<|abc|>c<|s|><|xs|><|.s|><|1.2.3s|><|3s|>")
        assertEquals(5, parts.size)
        assertEquals("a", parts[0].text)
        assertTrue(parts[1].isPause)
        assertEquals("b<|abc|>c<|s|><|xs|><|.s|><|1.2.3s|>", parts[2].text)
        assertTrue(parts[3].isPause)
    }

    @Test
    fun segmentRequestsKeepCompatibilityAndMergeOptions() {
        val client = TypecastClient.builder().apiKey("test-key").baseUrl("http://localhost:1").build()
        val requests = client.composeSpeech()
            .defaults(
                ComposerSettings(
                    voiceId = "voice-a",
                    model = TTSModel.SSFM_V30,
                    language = LanguageCode.ENG,
                    prompt = Prompt(EmotionPreset.HAPPY, 1.0),
                    output = Output(audioPitch = 1, audioTempo = 0.9, audioFormat = AudioFormat.MP3),
                )
            )
            .say("First")
            .pause(0.25)
            .say(
                "Second",
                ComposerSettings(
                    voiceId = "voice-b",
                    prompt = PresetPrompt(emotionPreset = EmotionPreset.SAD),
                    output = Output(volume = 80, audioPitch = -2, audioTempo = 1.1),
                    seed = 77,
                )
            )
            .segmentRequests()

        assertEquals(2, requests.size)
        assertEquals(AudioFormat.WAV, requests[0].output?.audioFormat)
        assertEquals("voice-b", requests[1].voiceId)
        assertEquals(80, requests[1].output?.volume)

        val defaults = client.composeSpeech()
            .defaults(ComposerSettings(voiceId = "voice"))
            .say("Hello")
            .segmentRequests()
        assertEquals(TTSModel.SSFM_V30, defaults[0].model)
        assertEquals(AudioFormat.WAV, defaults[0].output?.audioFormat)

        assertThrows(IllegalArgumentException::class.java) { client.composeSpeech().pause(-0.1) }
        assertThrows(IllegalStateException::class.java) { client.composeSpeech().say("Hello").segmentRequests() }
        assertThrows(IllegalStateException::class.java) {
            client.composeSpeech().defaults(ComposerSettings(voiceId = "   ")).say("Hello").segmentRequests()
        }
        client.close()
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun generateSkipsUnknownInternalParts() {
        val client = TypecastClient.builder().apiKey("test-key").baseUrl("http://localhost:1").build()
        val composer = client.composeSpeech()
        val partsField: Field = composer.javaClass.getDeclaredField("parts")
        partsField.isAccessible = true
        (partsField.get(composer) as MutableList<Any>).add("ignored")
        assertThrows(IllegalStateException::class.java) { composer.generate() }
        client.close()
    }

    @Test
    fun defaultGenerateIncludesExplicitPauseAndBlankText() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/wav").setBody("audio"))
            server.start()
            val client = TypecastClient.builder().apiKey("test-key").baseUrl(server.url("/").toString()).build()
            val response = client.composeSpeech()
                .defaults(ComposerSettings(voiceId = "voice"))
                .pause(0.1)
                .say("Hello<|0.1s|>   ")
                .generate()
            assertEquals("wav", response.format)
            client.close()
        }
    }

    @Test
    fun composeResponseCoversErrorsAndHeaderFallbacks() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(422).setBody("{\"message\":\"invalid\"}"))
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "audio/mp3")
                    .setHeader("X-Audio-Duration", "invalid")
                    .setBody("audio")
            )
            server.enqueue(MockResponse().setResponseCode(200).setBody("audio"))
            server.start()
            val client = TypecastClient.builder().apiKey("test-key")
                .baseUrl(server.url("/").toString()).build()

            assertThrows(TypecastException::class.java) {
                client.composeSpeech().defaults(ComposerSettings(voiceId = "voice")).say("Hello").generate()
            }
            val mp3 = client.composeSpeech().defaults(ComposerSettings(voiceId = "voice"))
                .say("Hello").generate(AudioFormat.MP3)
            assertEquals("mp3", mp3.format)
            assertEquals(0.0, mp3.duration)
            val noHeaders = client.composeSpeech().defaults(ComposerSettings(voiceId = "voice"))
                .say("Hello").generate()
            assertEquals("wav", noHeaders.format)
            assertEquals(0.0, noHeaders.duration)
            client.close()
        }
    }
}
