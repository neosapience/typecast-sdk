package io.typecast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request object for Text-to-Speech synthesis.
 *
 * @property voiceId Voice ID in format 'tc_' followed by a unique identifier
 * @property text Text to convert to speech (max 2000 characters)
 * @property model TTS model to use for speech synthesis
 * @property language Language code (ISO 639-3). If not provided, will be auto-detected
 * @property prompt Emotion and style settings for the generated speech
 * @property output Audio output settings
 * @property seed Random seed for reproducible results
 */
@Serializable
data class TTSRequest(
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
    val output: Output? = null,
    
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
        private var output: Output? = null
        private var seed: Int? = null
        
        fun voiceId(value: String) = apply { voiceId = value }
        fun text(value: String) = apply { text = value }
        fun model(value: TTSModel) = apply { model = value }
        fun language(value: LanguageCode?) = apply { language = value }
        fun prompt(value: Prompt) = apply { prompt = value }
        fun prompt(value: PresetPrompt) = apply { prompt = value }
        fun prompt(value: SmartPrompt) = apply { prompt = value }
        fun output(value: Output) = apply { output = value }
        fun seed(value: Int?) = apply { seed = value }
        
        fun build(): TTSRequest {
            require(voiceId.isNotBlank()) { "voiceId is required" }
            require(text.isNotBlank()) { "text is required" }
            
            return TTSRequest(
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

/**
 * Wrapper class for serializing different prompt types.
 */
@Serializable
data class TTSPromptSerializer(
    @SerialName("emotion_type")
    val emotionType: String? = null,
    
    @SerialName("emotion_preset")
    val emotionPreset: EmotionPreset? = null,
    
    @SerialName("emotion_intensity")
    val emotionIntensity: Double? = null,
    
    @SerialName("previous_text")
    val previousText: String? = null,
    
    @SerialName("next_text")
    val nextText: String? = null
) {
    constructor(prompt: TTSPrompt) : this(
        emotionType = when (prompt) {
            is PresetPrompt -> "preset"
            is SmartPrompt -> "smart"
            is Prompt -> null
        },
        emotionPreset = when (prompt) {
            is Prompt -> prompt.emotionPreset
            is PresetPrompt -> prompt.emotionPreset
            else -> null
        },
        emotionIntensity = when (prompt) {
            is Prompt -> prompt.emotionIntensity
            is PresetPrompt -> prompt.emotionIntensity
            else -> null
        },
        previousText = (prompt as? SmartPrompt)?.previousText,
        nextText = (prompt as? SmartPrompt)?.nextText
    )
}
