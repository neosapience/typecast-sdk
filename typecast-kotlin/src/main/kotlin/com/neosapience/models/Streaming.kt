package com.neosapience.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Audio output configuration for streaming TTS synthesis.
 *
 * Mirrors [Output] but omits `volume` and `target_lufs`, which the
 * streaming endpoint does not support.
 *
 * @property audioPitch Audio pitch adjustment in semitones (-12 to 12, default 0)
 * @property audioTempo Audio tempo multiplier (0.5-2.0, default 1.0)
 * @property audioFormat Audio output format string ("wav" or "mp3", default "wav")
 */
@Serializable
data class OutputStream(
    @SerialName("audio_pitch")
    val audioPitch: Int? = 0,

    @SerialName("audio_tempo")
    val audioTempo: Double? = 1.0,

    @SerialName("audio_format")
    val audioFormat: String? = "wav"
) {
    init {
        audioPitch?.let {
            require(it in -12..12) { "AudioPitch must be between -12 and 12" }
        }
        audioTempo?.let {
            require(it in 0.5..2.0) { "AudioTempo must be between 0.5 and 2.0" }
        }
        audioFormat?.let {
            require(it == "wav" || it == "mp3") { "AudioFormat must be 'wav' or 'mp3'" }
        }
    }

    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var audioPitch: Int? = 0
        private var audioTempo: Double? = 1.0
        private var audioFormat: String? = "wav"

        fun audioPitch(value: Int?) = apply { audioPitch = value }
        fun audioTempo(value: Double?) = apply { audioTempo = value }
        fun audioFormat(value: String?) = apply { audioFormat = value }

        fun build(): OutputStream {
            return OutputStream(
                audioPitch = audioPitch,
                audioTempo = audioTempo,
                audioFormat = audioFormat
            )
        }
    }
}

/**
 * Request object for the streaming Text-to-Speech endpoint.
 *
 * Mirrors [TTSRequest] but uses [OutputStream] in place of [Output].
 *
 * @property voiceId Voice ID in format 'tc_' followed by a unique identifier
 * @property text Text to convert to speech (max 2000 characters)
 * @property model TTS model to use for speech synthesis
 * @property language Language code (ISO 639-3). If not provided, will be auto-detected
 * @property prompt Emotion and style settings for the generated speech
 * @property output Streaming audio output settings
 * @property seed Random seed for reproducible results
 */
@Serializable
data class TTSRequestStream(
    @SerialName("voice_id")
    val voiceId: String,

    @SerialName("text")
    val text: String,

    @SerialName("model")
    val model: TTSModel,

    @SerialName("language")
    val language: LanguageCode? = null,

    @SerialName("prompt")
    val prompt: @Serializable TTSPromptSerializer? = null,

    @SerialName("output")
    val output: OutputStream? = null,

    @SerialName("seed")
    val seed: Int? = null
) {
    init {
        require(voiceId.isNotBlank()) { "voiceId is required" }
        require(text.isNotBlank()) { "text is required" }
        require(text.length <= 2000) { "text must not exceed 2000 characters" }
    }

    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var voiceId: String = ""
        private var text: String = ""
        private var model: TTSModel = TTSModel.SSFM_V30
        private var language: LanguageCode? = null
        private var prompt: TTSPrompt? = null
        private var output: OutputStream? = null
        private var seed: Int? = null

        fun voiceId(value: String) = apply { voiceId = value }
        fun text(value: String) = apply { text = value }
        fun model(value: TTSModel) = apply { model = value }
        fun language(value: LanguageCode?) = apply { language = value }
        fun prompt(value: Prompt) = apply { prompt = value }
        fun prompt(value: PresetPrompt) = apply { prompt = value }
        fun prompt(value: SmartPrompt) = apply { prompt = value }
        fun output(value: OutputStream) = apply { output = value }
        fun seed(value: Int?) = apply { seed = value }

        fun build(): TTSRequestStream {
            require(voiceId.isNotBlank()) { "voiceId is required" }
            require(text.isNotBlank()) { "text is required" }

            return TTSRequestStream(
                voiceId = voiceId,
                text = text,
                model = model,
                language = language,
                prompt = prompt?.let { TTSPromptSerializer(it) },
                output = output,
                seed = seed
            )
        }
    }
}
