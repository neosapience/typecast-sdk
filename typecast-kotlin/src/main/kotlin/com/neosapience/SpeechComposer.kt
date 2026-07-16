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

/**
 * Builder for composing multi-speaker speech and explicit pauses.
 *
 * Pause markup, when parsed manually, uses clear delimiters such as `<|3s|>`,
 * `<|0.3s|>`, and `<|0.34413s|>`. Invalid tokens are preserved as text.
 * Multi-speaker composition is exposed through chaining only: call [defaults],
 * [say], and [pause]. Generation is performed by the Typecast Compose API in
 * one request.
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
        val segments = mutableListOf<Any>()
        for (part in parts) {
            when (part) {
                is Double -> segments.add(part)
                is ComposerSpeechPart -> parsePauseMarkup(part.text).forEach { parsed ->
                    when {
                        parsed.isPause -> segments.add(parsed.pauseSeconds)
                        parsed.text.isNotBlank() -> segments.add(
                            buildRequest(ComposerSpeechPart(parsed.text, part.settings), outputFormat)
                        )
                    }
                }
            }
        }
        check(segments.any { it is TTSRequest }) { "At least one speech segment is required" }
        return client.composeTextToSpeech(segments)
    }

    private fun buildRequest(speech: ComposerSpeechPart, outputFormat: AudioFormat = AudioFormat.WAV): TTSRequest {
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
            output = output.copy(audioFormat = outputFormat),
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
            var merged = copyOutput(base) ?: Output(
                volume = null,
                targetLufs = null,
                audioPitch = null,
                audioTempo = null,
                audioFormat = null,
            )
            if (overrides == null) return merged
            if (overrides.volume != null) merged = merged.copy(volume = overrides.volume)
            if (overrides.targetLufs != null) merged = merged.copy(targetLufs = overrides.targetLufs)
            if (overrides.audioPitch != null) merged = merged.copy(audioPitch = overrides.audioPitch)
            if (overrides.audioTempo != null) merged = merged.copy(audioTempo = overrides.audioTempo)
            if (overrides.audioFormat != null) merged = merged.copy(audioFormat = overrides.audioFormat)
            return merged
        }

        private fun copyOutput(output: Output?): Output? {
            return output?.copy()
        }

        private fun parsePauseToken(token: String): Double? {
            if (!token.endsWith("s") || token.length < 2) return null
            val number = token.dropLast(1)
            if (number.any { !it.isDigit() && it != '.' }) return null
            return number.toDoubleOrNull()
        }

    }
}
