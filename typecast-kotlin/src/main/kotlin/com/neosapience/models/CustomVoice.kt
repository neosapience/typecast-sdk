package com.neosapience.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of POST /v1/voices/clone — custom voice (created via instant cloning) metadata.
 *
 * The [voiceId] field has the `"uc_"` prefix and can be used directly as
 * `voice_id` in [com.neosapience.TypecastClient.textToSpeech] calls.
 */
@Serializable
data class CustomVoice(
    @SerialName("voice_id") val voiceId: String,
    @SerialName("name") val name: String,
    @SerialName("model") val model: String,
) {
    companion object {
        /** Maximum audio file size accepted by cloneVoice (25 MB). Matches typecast-api. */
        const val CLONING_MAX_FILE_SIZE: Long = 25L * 1024 * 1024

        /** Minimum allowed length for the voice name passed to cloneVoice. */
        const val NAME_MIN_LENGTH: Int = 1

        /** Maximum allowed length for the voice name passed to cloneVoice. */
        const val NAME_MAX_LENGTH: Int = 30
    }
}
