package com.neosapience.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Audio output configuration for TTS synthesis.
 *
 * @property volume Volume level (0-200, default 100)
 * @property audioPitch Audio pitch adjustment in semitones (-12 to 12, default 0)
 * @property audioTempo Audio tempo multiplier (0.5-2.0, default 1.0)
 * @property audioFormat Audio output format (default WAV)
 */
@Serializable
data class Output(
    @SerialName("volume")
    val volume: Int? = 100,
    
    @SerialName("audio_pitch")
    val audioPitch: Int? = 0,
    
    @SerialName("audio_tempo")
    val audioTempo: Double? = 1.0,
    
    @SerialName("audio_format")
    val audioFormat: AudioFormat? = AudioFormat.WAV
) {
    init {
        volume?.let {
            require(it in 0..200) { "Volume must be between 0 and 200" }
        }
        audioPitch?.let {
            require(it in -12..12) { "AudioPitch must be between -12 and 12" }
        }
        audioTempo?.let {
            require(it in 0.5..2.0) { "AudioTempo must be between 0.5 and 2.0" }
        }
    }
    
    companion object {
        fun builder() = Builder()
    }
    
    class Builder {
        private var volume: Int? = 100
        private var audioPitch: Int? = 0
        private var audioTempo: Double? = 1.0
        private var audioFormat: AudioFormat? = AudioFormat.WAV
        
        fun volume(value: Int?) = apply { volume = value }
        fun audioPitch(value: Int?) = apply { audioPitch = value }
        fun audioTempo(value: Double?) = apply { audioTempo = value }
        fun audioFormat(value: AudioFormat?) = apply { audioFormat = value }
        
        fun build() = Output(
            volume = volume,
            audioPitch = audioPitch,
            audioTempo = audioTempo,
            audioFormat = audioFormat
        )
    }
}
