package com.neosapience.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * V1 Voices response (deprecated, use VoiceV2Response instead)
 */
@Serializable
data class VoicesResponse(
    @SerialName("voice_id")
    val voiceId: String,
    
    @SerialName("voice_name")
    val voiceName: String,
    
    @SerialName("model")
    val model: TTSModel,
    
    @SerialName("emotions")
    val emotions: List<String>
)

/**
 * Model information with supported emotions.
 */
@Serializable
data class ModelInfo(
    @SerialName("version")
    val version: String,
    
    @SerialName("emotions")
    val emotions: List<String>
)

/**
 * V2 Voice response with enhanced metadata.
 */
@Serializable
data class VoiceV2Response(
    @SerialName("voice_id")
    val voiceId: String,
    
    @SerialName("voice_name")
    val voiceName: String,
    
    @SerialName("models")
    val models: List<ModelInfo>,
    
    @SerialName("gender")
    val gender: GenderEnum? = null,
    
    @SerialName("age")
    val age: AgeEnum? = null,
    
    @SerialName("use_cases")
    val useCases: List<String>? = null
)

/**
 * Filter options for V2 voices endpoint.
 */
data class VoicesV2Filter(
    val model: TTSModel? = null,
    val gender: GenderEnum? = null,
    val age: AgeEnum? = null,
    val useCases: UseCaseEnum? = null
) {
    companion object {
        fun builder() = Builder()
    }
    
    class Builder {
        private var model: TTSModel? = null
        private var gender: GenderEnum? = null
        private var age: AgeEnum? = null
        private var useCases: UseCaseEnum? = null
        
        fun model(value: TTSModel?) = apply { model = value }
        fun gender(value: GenderEnum?) = apply { gender = value }
        fun age(value: AgeEnum?) = apply { age = value }
        fun useCases(value: UseCaseEnum?) = apply { useCases = value }
        
        fun build() = VoicesV2Filter(model, gender, age, useCases)
    }
}
