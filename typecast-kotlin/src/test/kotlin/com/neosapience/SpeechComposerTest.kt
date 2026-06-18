package com.neosapience

import com.neosapience.exceptions.TypecastException
import com.neosapience.models.AudioFormat
import com.neosapience.models.EmotionPreset
import com.neosapience.models.LanguageCode
import com.neosapience.models.Output
import com.neosapience.models.PresetPrompt
import com.neosapience.models.Prompt
import com.neosapience.models.TTSModel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Field

class SpeechComposerTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var client: TypecastClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        client = TypecastClient.builder()
            .apiKey("test-api-key")
            .baseUrl(mockServer.url("/").toString())
            .build()
    }

    @AfterEach
    fun tearDown() {
        client.close()
        mockServer.shutdown()
    }

    @Test
    fun parsePauseMarkupPreservesInvalidTokens() {
        val parts = SpeechComposer.parsePauseMarkup("Hello <|0.3s|>world <|bad|> <|s|> <|3|> <||> <|xs|> <|1.2.3s|> <|3000s|>")

        assertEquals(5, parts.size)
        assertEquals("Hello ", parts[0].text)
        assertTrue(parts[1].isPause)
        assertEquals(0.3, parts[1].pauseSeconds, 0.0001)
        assertEquals("world <|bad|> <|s|> <|3|> <||> <|xs|> <|1.2.3s|> ", parts[2].text)
        assertEquals(3000.0, parts[3].pauseSeconds, 0.0001)
        assertEquals("", parts[4].text)
    }

    @Test
    fun segmentRequestsMergeDefaultsAndForceWav() {
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
                    prompt = PresetPrompt(emotionPreset = EmotionPreset.SAD, emotionIntensity = 0.5),
                    output = Output(volume = 80, audioPitch = -2, audioTempo = 1.1, audioFormat = AudioFormat.MP3),
                    seed = 77,
                )
            )
            .segmentRequests()

        assertEquals(2, requests.size)
        assertEquals("voice-a", requests[0].voiceId)
        assertEquals("First", requests[0].text)
        assertEquals(AudioFormat.WAV, requests[0].output?.audioFormat)
        assertEquals(1, requests[0].output?.audioPitch)
        assertEquals(0.9, requests[0].output?.audioTempo)
        assertEquals("voice-b", requests[1].voiceId)
        assertEquals("Second", requests[1].text)
        assertEquals(AudioFormat.WAV, requests[1].output?.audioFormat)
        assertEquals(80, requests[1].output?.volume)
        assertEquals(-2, requests[1].output?.audioPitch)
        assertEquals(1.1, requests[1].output?.audioTempo)
        assertEquals(77, requests[1].seed)
    }

    @Test
    fun segmentRequestsDefaultsModelAndPreservesBaseOutput() {
        val requests = client.composeSpeech()
            .defaults(
                ComposerSettings(
                    voiceId = "voice-a",
                    prompt = PresetPrompt(emotionPreset = EmotionPreset.SAD),
                    output = Output(volume = 70, audioPitch = 2, audioTempo = 0.95, audioFormat = AudioFormat.MP3),
                )
            )
            .say("Hello")
            .segmentRequests()

        assertEquals(TTSModel.SSFM_V30, requests[0].model)
        assertEquals(70, requests[0].output?.volume)
        assertEquals(2, requests[0].output?.audioPitch)
        assertEquals(0.95, requests[0].output?.audioTempo)
        assertEquals(AudioFormat.WAV, requests[0].output?.audioFormat)
    }

    @Test
    fun pauseRejectsNegativeDurations() {
        assertThrows(IllegalArgumentException::class.java) {
            client.composeSpeech().pause(-0.1)
        }
    }

    @Test
    fun segmentRequestsRequireVoiceId() {
        assertThrows(IllegalStateException::class.java) {
            client.composeSpeech().say("Hello").segmentRequests()
        }
        assertThrows(IllegalStateException::class.java) {
            client.composeSpeech()
                .defaults(ComposerSettings(voiceId = "   "))
                .say("Hello")
                .segmentRequests()
        }
    }

    @Test
    fun generateRequiresAtLeastOneSpeechSegment() {
        assertThrows(IllegalStateException::class.java) {
            client.composeSpeech().generate()
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun generateSkipsUnknownInternalParts() {
        val composer = client.composeSpeech()
        val partsField: Field = composer.javaClass.getDeclaredField("parts")
        partsField.isAccessible = true
        (partsField.get(composer) as MutableList<Any>).add("ignored")

        assertThrows(IllegalStateException::class.java) {
            composer.generate()
        }
    }

    @Test
    fun generateIgnoresLeadingPauseBeforeFirstSpeech() {
        mockServer.enqueue(wavResponse(shortArrayOf(0, 100, 0), 10))

        val response = client.composeSpeech()
            .defaults(ComposerSettings(voiceId = "voice-a", model = TTSModel.SSFM_V30))
            .pause(0.1)
            .say("Hello")
            .generate()

        assertArrayEquals(shortArrayOf(100), readPcm(response.audioData))
    }

    @Test
    fun generateTrimsSegmentsAndInsertsSilence() {
        mockServer.enqueue(wavResponse(shortArrayOf(0, 100, 0), 10))
        mockServer.enqueue(wavResponse(shortArrayOf(0, -200, 0), 10))

        val response = client.composeSpeech()
            .defaults(ComposerSettings(voiceId = "voice-a", model = TTSModel.SSFM_V30))
            .say("First")
            .pause(0.2)
            .say("Second")
            .generate()

        assertArrayEquals(shortArrayOf(100, 0, 0, -200), readPcm(response.audioData))
        assertEquals(0.4, response.duration, 0.0001)
    }

    @Test
    fun generateTrimsAllZeroSegmentsToEmptyAudio() {
        mockServer.enqueue(wavResponse(shortArrayOf(0, 0), 10))

        val response = client.composeSpeech()
            .defaults(ComposerSettings(voiceId = "voice-a", model = TTSModel.SSFM_V30))
            .say("Silence")
            .generate()

        assertArrayEquals(shortArrayOf(), readPcm(response.audioData))
        assertEquals(0.0, response.duration, 0.0001)
    }

    @Test
    fun generateRejectsMp3() {
        assertThrows(TypecastException::class.java) {
            client.composeSpeech()
                .defaults(ComposerSettings(voiceId = "voice-a"))
                .say("Hello")
                .generate(AudioFormat.MP3)
        }
    }

    @Test
    fun generateRejectsMalformedWavResponses() {
        val cases = listOf(
            byteArrayOf(1, 2, 3),
            testWav(shortArrayOf(100), 10, audioFormat = 2),
            testWavWithInvalidChunkSize(),
            testWavWithoutData(extraChunk = true),
            testWavWithoutData(extraChunk = false),
        )

        cases.forEach { wav ->
            mockServer.enqueue(wavResponse(wav))
            assertThrows(IllegalStateException::class.java) {
                client.composeSpeech()
                    .defaults(ComposerSettings(voiceId = "voice-a", model = TTSModel.SSFM_V30))
                    .say("Hello")
                    .generate()
            }
        }
    }

    @Test
    fun generateRejectsMismatchedSampleRates() {
        mockServer.enqueue(wavResponse(shortArrayOf(100), 10))
        mockServer.enqueue(wavResponse(shortArrayOf(200), 20))

        assertThrows(IllegalStateException::class.java) {
            client.composeSpeech()
                .defaults(ComposerSettings(voiceId = "voice-a", model = TTSModel.SSFM_V30))
                .say("First")
                .say("Second")
                .generate()
        }
    }

    @Test
    fun composeWavRejectsInvalidInternalPartShape() {
        val companion = SpeechComposer.Companion::class.java
        val method = companion.getDeclaredMethod("composeWav", List::class.java, List::class.java)
        method.isAccessible = true

        val emptyError = assertThrows(InvocationTargetException::class.java) {
            method.invoke(SpeechComposer, emptyList<ByteArray>(), emptyList<Double>())
        }
        assertTrue(emptyError.cause is IllegalStateException)

        val sizeError = assertThrows(InvocationTargetException::class.java) {
            method.invoke(SpeechComposer, listOf(testWav(shortArrayOf(100), 10), testWav(shortArrayOf(200), 10)), emptyList<Double>())
        }
        assertTrue(sizeError.cause is IllegalStateException)
    }

    @Test
    fun mergeOutputSupportsTargetLufsOverride() {
        val companion = SpeechComposer.Companion::class.java
        val method = companion.getDeclaredMethod("mergeOutput", Output::class.java, Output::class.java)
        method.isAccessible = true

        val override = Output(
            volume = null,
            targetLufs = -18.0,
            audioPitch = -1,
            audioTempo = 1.2,
            audioFormat = AudioFormat.MP3,
        )
        val merged = method.invoke(SpeechComposer, null, override) as Output

        assertEquals(-18.0, merged.targetLufs)
        assertEquals(-1, merged.audioPitch)
        assertEquals(1.2, merged.audioTempo)
        assertEquals(AudioFormat.MP3, merged.audioFormat)
    }

    private fun wavResponse(samples: ShortArray, sampleRate: Int): MockResponse {
        return wavResponse(testWav(samples, sampleRate))
    }

    private fun wavResponse(wav: ByteArray): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "audio/wav")
            .setBody(Buffer().write(wav))
    }

    private fun testWav(samples: ShortArray, sampleRate: Int, audioFormat: Int = 1): ByteArray {
        val dataLength = samples.size * 2
        val bytes = ByteArray(44 + dataLength)
        fun putAscii(offset: Int, value: String) {
            value.toByteArray(StandardCharsets.US_ASCII).copyInto(bytes, offset)
        }
        fun putInt(offset: Int, value: Int) {
            ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        }
        fun putShort(offset: Int, value: Int) {
            ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
        }

        putAscii(0, "RIFF")
        putInt(4, 36 + dataLength)
        putAscii(8, "WAVEfmt ")
        putInt(16, 16)
        putShort(20, audioFormat)
        putShort(22, 1)
        putInt(24, sampleRate)
        putInt(28, sampleRate * 2)
        putShort(32, 2)
        putShort(34, 16)
        putAscii(36, "data")
        putInt(40, dataLength)
        samples.forEachIndexed { index, sample ->
            putShort(44 + index * 2, sample.toInt())
        }
        return bytes
    }

    private fun testWavWithoutData(extraChunk: Boolean): ByteArray {
        val payloadLength = if (extraChunk) 12 else 8
        val bytes = ByteArray(36 + payloadLength)
        fun putAscii(offset: Int, value: String) {
            value.toByteArray(StandardCharsets.US_ASCII).copyInto(bytes, offset)
        }
        fun putInt(offset: Int, value: Int) {
            ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
        }
        fun putShort(offset: Int, value: Int) {
            ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
        }

        putAscii(0, "RIFF")
        putInt(4, 28 + payloadLength)
        putAscii(8, "WAVEfmt ")
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, 1)
        putInt(24, 10)
        putInt(28, 20)
        putShort(32, 2)
        putShort(34, 16)
        if (extraChunk) {
            putAscii(36, "JUNK")
            putInt(40, 4)
            putInt(44, 123)
        }
        return bytes
    }

    private fun testWavWithInvalidChunkSize(): ByteArray {
        val bytes = testWav(shortArrayOf(), 10)
        "JUNK".toByteArray(StandardCharsets.US_ASCII).copyInto(bytes, 36)
        ByteBuffer.wrap(bytes, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(1000)
        return bytes
    }

    private fun readPcm(wav: ByteArray): ShortArray {
        val dataLength = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return ShortArray(dataLength / 2) { index ->
            ByteBuffer.wrap(wav, 44 + index * 2, 2).order(ByteOrder.LITTLE_ENDIAN).short
        }
    }
}
