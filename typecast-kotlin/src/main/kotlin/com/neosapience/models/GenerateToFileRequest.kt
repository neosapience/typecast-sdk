package com.neosapience.models

/**
 * Convenience request for generating speech directly to a file.
 *
 * [model] defaults to [TTSModel.SSFM_V30].
 * Browse available API voices at https://typecast.ai/developers/api/voices.
 */
data class GenerateToFileRequest(
    val voiceId: String,
    val text: String,
    val model: TTSModel = TTSModel.SSFM_V30,
    val language: LanguageCode? = null,
    val prompt: TTSPrompt? = null,
    val output: Output? = null,
    val seed: Int? = null,
) {
    init {
        require(voiceId.isNotBlank()) { "voiceId is required" }
        require(text.isNotBlank()) { "text is required" }
        require(text.length <= 2000) { "text must not exceed 2000 characters" }
    }

    fun toTTSRequest(filePath: String): TTSRequest = TTSRequest(
        voiceId = voiceId,
        text = text,
        model = model,
        language = language,
        prompt = prompt?.let { TTSPromptSerializer(it) },
        output = output ?: inferOutput(filePath),
        seed = seed,
    )

    private fun inferOutput(filePath: String): Output? {
        val lower = filePath.lowercase()
        return when {
            lower.endsWith(".mp3") -> Output(audioFormat = AudioFormat.MP3)
            lower.endsWith(".wav") -> Output(audioFormat = AudioFormat.WAV)
            else -> null
        }
    }
}
