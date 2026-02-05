package io.typecast.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base interface for all prompt types.
 */
sealed interface TTSPrompt

/**
 * Emotion and style settings for ssfm-v21 model.
 */
@Serializable
data class Prompt(
    @SerialName("emotion_preset")
    val emotionPreset: EmotionPreset = EmotionPreset.NORMAL,
    
    @SerialName("emotion_intensity")
    val emotionIntensity: Double = 1.0
) : TTSPrompt {
    init {
        require(emotionIntensity in 0.0..2.0) {
            "emotionIntensity must be between 0.0 and 2.0"
        }
    }
    
    companion object {
        fun builder() = Builder()
    }
    
    class Builder {
        private var emotionPreset: EmotionPreset = EmotionPreset.NORMAL
        private var emotionIntensity: Double = 1.0
        
        fun emotionPreset(value: EmotionPreset) = apply { emotionPreset = value }
        fun emotionIntensity(value: Double) = apply { emotionIntensity = value }
        
        fun build() = Prompt(emotionPreset, emotionIntensity)
    }
}

/**
 * Preset-based emotion control for ssfm-v30 model.
 */
@Serializable
data class PresetPrompt(
    @SerialName("emotion_type")
    val emotionType: String = "preset",
    
    @SerialName("emotion_preset")
    val emotionPreset: EmotionPreset = EmotionPreset.NORMAL,
    
    @SerialName("emotion_intensity")
    val emotionIntensity: Double = 1.0
) : TTSPrompt {
    init {
        require(emotionIntensity in 0.0..2.0) {
            "emotionIntensity must be between 0.0 and 2.0"
        }
    }
    
    companion object {
        fun builder() = Builder()
    }
    
    class Builder {
        private var emotionPreset: EmotionPreset = EmotionPreset.NORMAL
        private var emotionIntensity: Double = 1.0
        
        fun emotionPreset(value: EmotionPreset) = apply { emotionPreset = value }
        fun emotionIntensity(value: Double) = apply { emotionIntensity = value }
        
        fun build() = PresetPrompt(
            emotionType = "preset",
            emotionPreset = emotionPreset,
            emotionIntensity = emotionIntensity
        )
    }
}

/**
 * Context-aware emotion inference for ssfm-v30 model.
 */
@Serializable
data class SmartPrompt(
    @SerialName("emotion_type")
    val emotionType: String = "smart",
    
    @SerialName("previous_text")
    val previousText: String? = null,
    
    @SerialName("next_text")
    val nextText: String? = null
) : TTSPrompt {
    init {
        previousText?.let {
            require(it.length <= 2000) { "previousText must not exceed 2000 characters" }
        }
        nextText?.let {
            require(it.length <= 2000) { "nextText must not exceed 2000 characters" }
        }
    }
    
    companion object {
        fun builder() = Builder()
    }
    
    class Builder {
        private var previousText: String? = null
        private var nextText: String? = null
        
        fun previousText(value: String?) = apply { previousText = value }
        fun nextText(value: String?) = apply { nextText = value }
        
        fun build() = SmartPrompt(
            emotionType = "smart",
            previousText = previousText,
            nextText = nextText
        )
    }
}
