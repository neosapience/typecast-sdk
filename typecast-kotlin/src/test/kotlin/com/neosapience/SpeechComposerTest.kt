package com.neosapience

import com.neosapience.exceptions.TypecastException
import com.neosapience.models.AudioFormat
import com.neosapience.models.LanguageCode
import com.neosapience.models.Output
import com.neosapience.models.TTSModel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

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
        val parts = SpeechComposer.parsePauseMarkup("Hello <|0.3s|>world <|bad|> <|3000s|>")

        assertEquals(5, parts.size)
        assertEquals("Hello ", parts[0].text)
        assertEquals(0.3, parts[1].pauseSeconds, 0.0001)
        assertEquals("world <|bad|> ", parts[2].text)
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
                    output = Output(audioPitch = 1, audioTempo = 0.9, audioFormat = AudioFormat.MP3),
                )
            )
            .say("First")
            .pause(0.25)
            .say(
                "Second",
                ComposerSettings(
                    voiceId = "voice-b",
                    output = Output(volume = null, audioPitch = -2, audioTempo = 1.1, audioFormat = null),
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
        assertEquals(-2, requests[1].output?.audioPitch)
        assertEquals(1.1, requests[1].output?.audioTempo)
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
    fun generateRejectsMp3() {
        assertThrows(TypecastException::class.java) {
            client.composeSpeech()
                .defaults(ComposerSettings(voiceId = "voice-a"))
                .say("Hello")
                .generate(AudioFormat.MP3)
        }
    }

    private fun wavResponse(samples: ShortArray, sampleRate: Int): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "audio/wav")
            .setBody(Buffer().write(testWav(samples, sampleRate)))
    }

    private fun testWav(samples: ShortArray, sampleRate: Int): ByteArray {
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
        putShort(20, 1)
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

    private fun readPcm(wav: ByteArray): ShortArray {
        val dataLength = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return ShortArray(dataLength / 2) { index ->
            ByteBuffer.wrap(wav, 44 + index * 2, 2).order(ByteOrder.LITTLE_ENDIAN).short
        }
    }
}
