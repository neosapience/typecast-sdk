package com.neosapience.models;

import com.google.gson.annotations.SerializedName;
import com.neosapience.internal.CaptioningHelpers;
import com.neosapience.internal.CaptioningHelpers.Cue;
import com.neosapience.internal.CaptioningHelpers.Segment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Response from POST /v1/text-to-speech/with-timestamps.
 *
 * <p>Contains the synthesized audio (base64-encoded) together with word and/or
 * character-level alignment segments. Use {@link #toSrt()} / {@link #toVtt()} to
 * produce caption files.</p>
 */
public class TTSWithTimestampsResponse {

    private final String audio;

    @SerializedName("audio_format")
    private final String audioFormat;

    @SerializedName("audio_duration")
    private final double audioDuration;

    private final List<AlignmentSegmentWord> words;
    private final List<AlignmentSegmentCharacter> characters;

    /**
     * Creates a TTSWithTimestampsResponse.
     *
     * @param audio         base64-encoded audio data
     * @param audioFormat   format string (e.g., "wav" or "mp3")
     * @param audioDuration total audio duration in seconds
     * @param words         word-level alignment segments (may be null)
     * @param characters    character-level alignment segments (may be null)
     */
    public TTSWithTimestampsResponse(String audio, String audioFormat, double audioDuration,
                                     List<AlignmentSegmentWord> words,
                                     List<AlignmentSegmentCharacter> characters) {
        this.audio = audio;
        this.audioFormat = audioFormat;
        this.audioDuration = audioDuration;
        this.words = words;
        this.characters = characters;
    }

    /** Returns the base64-encoded audio field. */
    public String getAudio() {
        return audio;
    }

    /** Returns the audio format string. */
    public String getAudioFormat() {
        return audioFormat;
    }

    /** Returns the total audio duration in seconds. */
    public double getAudioDuration() {
        return audioDuration;
    }

    /** Returns the word-level alignment segments, or null if not present. */
    public List<AlignmentSegmentWord> getWords() {
        return words;
    }

    /** Returns the character-level alignment segments, or null if not present. */
    public List<AlignmentSegmentCharacter> getCharacters() {
        return characters;
    }

    /**
     * Decodes and returns the audio data.
     *
     * @return decoded audio bytes
     */
    public byte[] audioBytes() {
        return Base64.getDecoder().decode(audio);
    }

    /**
     * Writes the decoded audio bytes to the given path.
     *
     * @param path target file path
     * @throws IOException if an I/O error occurs
     */
    public void saveAudio(Path path) throws IOException {
        Files.write(path, audioBytes());
    }

    /**
     * Returns SRT-formatted caption text.
     *
     * <p>Cue indices start at 1. Timestamps use {@code HH:MM:SS,mmm} format.
     * Lines are separated by LF.</p>
     *
     * @return SRT content as a string
     * @throws IllegalStateException if there are no usable alignment segments
     */
    public String toSrt() {
        return formatCaptions(true);
    }

    /**
     * Returns WebVTT-formatted caption text.
     *
     * <p>Starts with {@code WEBVTT\n\n}. Timestamps use {@code HH:MM:SS.mmm} format.
     * Lines are separated by LF.</p>
     *
     * @return VTT content as a string
     * @throws IllegalStateException if there are no usable alignment segments
     */
    public String toVtt() {
        return formatCaptions(false);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String formatCaptions(boolean srt) {
        List<Segment> segs;
        boolean wordMode;
        if (words != null && words.size() >= 2) {
            segs = words.stream()
                    .map(w -> new Segment(w.getText(), w.getStart(), w.getEnd()))
                    .collect(Collectors.toList());
            wordMode = true;
        } else if (characters != null && characters.size() >= 1) {
            segs = characters.stream()
                    .map(c -> new Segment(c.getText(), c.getStart(), c.getEnd()))
                    .collect(Collectors.toList());
            wordMode = false;
        } else if (words != null && words.size() == 1) {
            segs = words.stream()
                    .map(w -> new Segment(w.getText(), w.getStart(), w.getEnd()))
                    .collect(Collectors.toList());
            wordMode = true;
        } else {
            throw new IllegalStateException("no alignment segments to caption from");
        }

        List<Cue> cues = CaptioningHelpers.groupIntoCues(segs, wordMode);
        if (cues.isEmpty()) {
            throw new IllegalStateException("no alignment segments to caption from");
        }

        StringBuilder sb = new StringBuilder();
        if (!srt) {
            sb.append("WEBVTT\n\n");
        }
        for (int i = 0; i < cues.size(); i++) {
            Cue c = cues.get(i);
            if (srt) {
                sb.append(i + 1).append('\n');
            }
            sb.append(srt ? CaptioningHelpers.formatSrtTime(c.start)
                          : CaptioningHelpers.formatVttTime(c.start))
              .append(" --> ")
              .append(srt ? CaptioningHelpers.formatSrtTime(c.end)
                          : CaptioningHelpers.formatVttTime(c.end))
              .append('\n');
            sb.append(c.text).append('\n').append('\n');
        }
        return sb.toString();
    }
}
