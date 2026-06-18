package com.neosapience

import com.neosapience.exceptions.TypecastException
import com.neosapience.models.AudioFormat
import com.neosapience.models.LanguageCode
import com.neosapience.models.Output
import com.neosapience.models.TTSModel
import com.neosapience.models.TTSPrompt
import com.neosapience.models.TTSPromptSerializer
import com.neosapience.models.TTSRequest
import com.neosapience.models.TTSResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/**
 * Defaults or per-segment overrides for [SpeechComposer].
 *
 * Use [SpeechComposer.defaults] for shared settings, and pass another
 * [ComposerSettings] to [SpeechComposer.say] when a segment needs its own
 * voice, pitch, tempo, prompt, seed, or output settings.
 */
data class ComposerSettings(
    /** Voice ID. Browse available API voices at https://typecast.ai/developers/api/voices. */
    val voiceId: String? = null,
    /** Typecast TTS model for this segment. Defaults to [TTSModel.SSFM_V30]. */
    val model: TTSModel? = null,
    /** Optional ISO 639-3 language code, such as [LanguageCode.KOR] or [LanguageCode.ENG]. */
    val language: LanguageCode? = null,
    /** Optional emotion/style prompt for this segment. */
    val prompt: TTSPrompt? = null,
    /** Optional output controls such as pitch, tempo, volume, target LUFS, and requested format. */
    val output: Output? = null,
    /** Optional deterministic generation seed passed through to the Typecast API. */
    val seed: Int? = null,
)

/**
 * A parsed text or pause part returned by [SpeechComposer.parsePauseMarkup].
 */
data class SpeechPart(
    /** Text content for speech parts, or an empty string for pause parts. */
    val text: String,
    /** Pause duration in seconds for pause parts. */
    val pauseSeconds: Double,
    /** Whether this part represents a pause token. */
    val isPause: Boolean,
)

private data class ComposerSpeechPart(val text: String, val settings: ComposerSettings)
private data class WavInfo(val sampleRate: Int, val pcmStart: Int, val pcmLength: Int)

/**
 * Builder for composing multi-speaker speech and explicit pauses.
 *
 * Pause markup, when parsed manually, uses clear delimiters such as `<|3s|>`,
 * `<|0.3s|>`, and `<|0.34413s|>`. Invalid tokens are preserved as text.
 * Multi-speaker composition is exposed through chaining only: call [defaults],
 * [say], and [pause]. Internal segment requests force WAV so the SDK can trim
 * leading/trailing silence and concatenate PCM.
 */
class SpeechComposer internal constructor(private val client: TypecastClient) {
    private val parts = mutableListOf<Any>()
    private var defaultSettings = ComposerSettings()

    /**
     * Sets defaults shared by following speech segments.
     */
    fun defaults(settings: ComposerSettings): SpeechComposer = apply {
        defaultSettings = merge(defaultSettings, settings)
    }

    /**
     * Adds one speech segment. Overrides apply only to this segment.
     */
    fun say(text: String, overrides: ComposerSettings = ComposerSettings()): SpeechComposer = apply {
        parts.add(ComposerSpeechPart(text, overrides))
    }

    /**
     * Adds an explicit silent pause in seconds.
     */
    fun pause(seconds: Double): SpeechComposer = apply {
        require(seconds >= 0) { "Pause must be non-negative" }
        parts.add(seconds)
    }

    /**
     * Builds the individual TTS requests, excluding pause-only parts.
     */
    fun segmentRequests(): List<TTSRequest> {
        return parts.filterIsInstance<ComposerSpeechPart>().map(::buildRequest)
    }

    /**
     * Generates composed speech as WAV.
     */
    fun generate(outputFormat: AudioFormat = AudioFormat.WAV): TTSResponse {
        if (outputFormat == AudioFormat.MP3) {
            throw TypecastException("MP3 conversion is not available for composed speech in the Kotlin SDK.")
        }

        val wavs = mutableListOf<ByteArray>()
        val pauses = mutableListOf<Double>()
        var pendingPause = 0.0
        var hasAudio = false
        for (part in parts) {
            when (part) {
                is Double -> pendingPause += part
                is ComposerSpeechPart -> {
                    if (hasAudio) pauses.add(pendingPause)
                    pendingPause = 0.0
                    wavs.add(client.textToSpeech(buildRequest(part)).audioData)
                    hasAudio = true
                }
            }
        }
        check(wavs.isNotEmpty()) { "At least one speech segment is required" }
        val audio = composeWav(wavs, pauses)
        val info = parseWav(audio)
        return TTSResponse(audio, (info.pcmLength / 2.0) / info.sampleRate, "wav")
    }

    private fun buildRequest(speech: ComposerSpeechPart): TTSRequest {
        val settings = merge(defaultSettings, speech.settings)
        val voiceId = settings.voiceId
        check(!voiceId.isNullOrBlank()) { "voiceId is required for composed speech segments" }
        val output = copyOutput(settings.output) ?: Output(
            volume = null,
            audioPitch = null,
            audioTempo = null,
            audioFormat = null,
        )
        return TTSRequest(
            voiceId = voiceId,
            text = speech.text,
            model = settings.model ?: TTSModel.SSFM_V30,
            language = settings.language,
            prompt = settings.prompt?.let { TTSPromptSerializer(it) },
            output = output.copy(audioFormat = AudioFormat.WAV),
            seed = settings.seed,
        )
    }

    companion object {
        /**
         * Parses clear pause markup tokens such as `<|3s|>`, `<|0.3s|>`, or `<|0.34413s|>`.
         */
        @JvmStatic
        fun parsePauseMarkup(text: String): List<SpeechPart> {
            val parts = mutableListOf<SpeechPart>()
            var textStart = 0
            var searchStart = 0
            while (searchStart < text.length) {
                val start = text.indexOf("<|", searchStart)
                if (start < 0) break
                val valueStart = start + 2
                val end = text.indexOf("|>", valueStart)
                if (end < 0) break
                val seconds = parsePauseToken(text.substring(valueStart, end))
                if (seconds != null) {
                    parts.add(SpeechPart(text.substring(textStart, start), 0.0, false))
                    parts.add(SpeechPart("", seconds, true))
                    textStart = end + 2
                    searchStart = textStart
                } else {
                    searchStart = valueStart
                }
            }
            parts.add(SpeechPart(text.substring(textStart), 0.0, false))
            return parts
        }

        private fun merge(base: ComposerSettings, overrides: ComposerSettings): ComposerSettings {
            return ComposerSettings(
                voiceId = overrides.voiceId ?: base.voiceId,
                model = overrides.model ?: base.model,
                language = overrides.language ?: base.language,
                prompt = overrides.prompt ?: base.prompt,
                output = mergeOutput(base.output, overrides.output),
                seed = overrides.seed ?: base.seed,
            )
        }

        private fun mergeOutput(base: Output?, overrides: Output?): Output? {
            if (base == null && overrides == null) return null
            return Output(
                volume = overrides?.volume ?: base?.volume,
                targetLufs = overrides?.targetLufs ?: base?.targetLufs,
                audioPitch = overrides?.audioPitch ?: base?.audioPitch,
                audioTempo = overrides?.audioTempo ?: base?.audioTempo,
                audioFormat = overrides?.audioFormat ?: base?.audioFormat,
            )
        }

        private fun copyOutput(output: Output?): Output? {
            return output?.copy()
        }

        private fun parsePauseToken(token: String): Double? {
            if (!token.endsWith("s") || token.length < 2) return null
            val number = token.dropLast(1)
            if (number.isEmpty() || number.any { !it.isDigit() && it != '.' }) return null
            return number.toDoubleOrNull()
        }

        private fun composeWav(wavs: List<ByteArray>, pauses: List<Double>): ByteArray {
            check(wavs.isNotEmpty() && pauses.size + 1 == wavs.size) { "Invalid composed speech parts" }
            val infos = wavs.map(::parseWav)
            val sampleRate = infos.first().sampleRate
            check(infos.all { it.sampleRate == sampleRate }) { "WAV segment sample rates must match" }

            val ranges = mutableListOf<IntRange>()
            var totalSamples = 0
            for (index in wavs.indices) {
                val wav = wavs[index]
                val info = infos[index]
                var start = 0
                var end = info.pcmLength / 2
                while (start < end && readShort(wav, info.pcmStart + start * 2).toInt() == 0) start++
                while (end > start && readShort(wav, info.pcmStart + (end - 1) * 2).toInt() == 0) end--
                ranges.add(start until end)
                totalSamples += end - start
                if (index < pauses.size) totalSamples += (pauses[index] * sampleRate).roundToInt()
            }

            val output = ByteArray(44 + totalSamples * 2)
            writeWavHeader(output, sampleRate, totalSamples * 2)
            var cursor = 44
            for (index in wavs.indices) {
                val info = infos[index]
                val range = ranges[index]
                val bytes = (range.last - range.first + 1).coerceAtLeast(0) * 2
                System.arraycopy(wavs[index], info.pcmStart + range.first * 2, output, cursor, bytes)
                cursor += bytes
                if (index < pauses.size) {
                    cursor += (pauses[index] * sampleRate).roundToInt() * 2
                }
            }
            return output
        }

        private fun parseWav(wav: ByteArray): WavInfo {
            check(wav.size >= 44 && ascii(wav, 0, 4) == "RIFF" && ascii(wav, 8, 4) == "WAVE") {
                "Composed speech requires WAV audio"
            }
            check(readShort(wav, 20).toInt() == 1 && readShort(wav, 22).toInt() == 1 && readShort(wav, 34).toInt() == 16) {
                "Composed speech requires mono 16-bit PCM WAV segments"
            }
            var cursor = 36
            while (cursor + 8 <= wav.size) {
                val id = ascii(wav, cursor, 4)
                val size = readInt(wav, cursor + 4)
                check(cursor + 8 + size <= wav.size) { "Invalid WAV data" }
                if (id == "data") return WavInfo(readInt(wav, 24), cursor + 8, size)
                cursor += 8 + size
            }
            error("WAV data chunk is missing")
        }

        private fun readShort(data: ByteArray, offset: Int): Short {
            return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short
        }

        private fun readInt(data: ByteArray, offset: Int): Int {
            return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
        }

        private fun ascii(data: ByteArray, offset: Int, length: Int): String {
            return String(data, offset, length, StandardCharsets.US_ASCII)
        }

        private fun writeWavHeader(output: ByteArray, sampleRate: Int, dataLength: Int) {
            fun putAscii(offset: Int, value: String) {
                value.toByteArray(StandardCharsets.US_ASCII).copyInto(output, offset)
            }
            fun putInt(offset: Int, value: Int) {
                ByteBuffer.wrap(output, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
            }
            fun putShort(offset: Int, value: Int) {
                ByteBuffer.wrap(output, offset, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort())
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
        }
    }
}
