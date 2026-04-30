package com.neosapience.models

import com.neosapience.internal.CaptioningHelpers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Word-level alignment segment returned by the with-timestamps endpoint.
 *
 * @property text  the word text
 * @property start start time in seconds
 * @property end   end time in seconds
 */
@Serializable
data class AlignmentSegmentWord(
    val text: String,
    val start: Double,
    val end: Double,
)

/**
 * Character-level alignment segment returned by the with-timestamps endpoint.
 *
 * @property text  the character text
 * @property start start time in seconds
 * @property end   end time in seconds
 */
@Serializable
data class AlignmentSegmentCharacter(
    val text: String,
    val start: Double,
    val end: Double,
)

/**
 * Request object for Text-to-Speech synthesis with word/character timestamps.
 *
 * @property voiceId  Voice ID in format 'tc_' followed by a unique identifier
 * @property text     Text to convert to speech (max 2000 characters)
 * @property model    TTS model to use for speech synthesis
 * @property language Language code (ISO 639-3). If not provided, will be auto-detected
 * @property prompt   Emotion and style settings for the generated speech
 * @property output   Audio output settings
 * @property seed     Random seed for reproducible results
 */
@Serializable
data class TTSRequestWithTimestamps(
    @SerialName("voice_id") val voiceId: String,
    val text: String,
    val model: TTSModel,
    val language: LanguageCode? = null,
    val prompt: TTSPromptSerializer? = null,
    val output: Output? = null,
    val seed: Int? = null,
)

/**
 * Response from POST /v1/text-to-speech/with-timestamps.
 *
 * Contains the synthesized audio (base64-encoded) together with word and/or
 * character-level alignment segments. Use [toSrt] / [toVtt] to produce caption files.
 *
 * @property audio          base64-encoded audio data
 * @property audioFormat    format string (e.g., "wav" or "mp3")
 * @property audioDuration  total audio duration in seconds
 * @property words          word-level alignment segments (may be null)
 * @property characters     character-level alignment segments (may be null)
 */
@Serializable
data class TTSWithTimestampsResponse(
    val audio: String,
    @SerialName("audio_format") val audioFormat: String,
    @SerialName("audio_duration") val audioDuration: Double,
    val words: List<AlignmentSegmentWord>? = null,
    val characters: List<AlignmentSegmentCharacter>? = null,
) {
    /**
     * Decodes and returns the audio bytes.
     *
     * @return decoded audio bytes
     */
    fun audioBytes(): ByteArray = Base64.getDecoder().decode(audio)

    /**
     * Writes the decoded audio bytes to the given path.
     *
     * @param path target file path
     */
    fun saveAudio(path: Path) {
        Files.write(path, audioBytes())
    }

    /**
     * Returns SRT-formatted caption text.
     *
     * Cue indices start at 1. Timestamps use HH:MM:SS,mmm format.
     * Lines are separated by LF.
     *
     * @return SRT content as a string
     * @throws IllegalStateException if there are no usable alignment segments
     */
    fun toSrt(): String = formatCaptions(srt = true)

    /**
     * Returns WebVTT-formatted caption text.
     *
     * Starts with WEBVTT followed by a blank line. Timestamps use HH:MM:SS.mmm format.
     * Lines are separated by LF.
     *
     * @return VTT content as a string
     * @throws IllegalStateException if there are no usable alignment segments
     */
    fun toVtt(): String = formatCaptions(srt = false)

    private fun formatCaptions(srt: Boolean): String {
        val (segs, wordMode) = pickSegments()
        val cues = CaptioningHelpers.groupIntoCues(segs, wordMode)
        if (cues.isEmpty()) error("no alignment segments to caption from")
        val sb = StringBuilder()
        if (!srt) sb.append("WEBVTT\n\n")
        cues.forEachIndexed { i, c ->
            if (srt) sb.append(i + 1).append('\n')
            val tStart = if (srt) CaptioningHelpers.formatSrtTime(c.start)
                         else CaptioningHelpers.formatVttTime(c.start)
            val tEnd = if (srt) CaptioningHelpers.formatSrtTime(c.end)
                       else CaptioningHelpers.formatVttTime(c.end)
            sb.append("$tStart --> $tEnd\n")
            sb.append(c.text).append('\n').append('\n')
        }
        return sb.toString()
    }

    private fun pickSegments(): Pair<List<CaptioningHelpers.Segment>, Boolean> {
        val w = words
        val c = characters
        if (w != null && w.size >= 2) {
            return w.map { CaptioningHelpers.Segment(it.text, it.start, it.end) } to true
        }
        if (c != null && c.isNotEmpty()) {
            return c.map { CaptioningHelpers.Segment(it.text, it.start, it.end) } to false
        }
        if (w != null && w.size == 1) {
            return w.map { CaptioningHelpers.Segment(it.text, it.start, it.end) } to true
        }
        error("no alignment segments to caption from")
    }
}
