package com.neosapience.models

/**
 * Response from Text-to-Speech synthesis.
 *
 * @property audioData Generated audio data as ByteArray
 * @property duration Audio duration in seconds
 * @property format Audio format (wav or mp3)
 */
data class TTSResponse(
    val audioData: ByteArray,
    val duration: Double,
    val format: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TTSResponse

        if (!audioData.contentEquals(other.audioData)) return false
        if (duration != other.duration) return false
        if (format != other.format) return false

        return true
    }

    override fun hashCode(): Int {
        var result = audioData.contentHashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + format.hashCode()
        return result
    }

    override fun toString(): String {
        return "TTSResponse(audioData=${audioData.size} bytes, duration=$duration, format='$format')"
    }
}
