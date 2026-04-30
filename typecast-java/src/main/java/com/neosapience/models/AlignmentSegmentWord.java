package com.neosapience.models;

/**
 * Represents a word-level alignment segment from the TTS with-timestamps response.
 */
public class AlignmentSegmentWord {
    private String text;
    private double start;
    private double end;

    /** No-arg constructor for Gson deserialization. */
    public AlignmentSegmentWord() {}

    /**
     * Creates an AlignmentSegmentWord with all fields.
     *
     * @param text  the word text
     * @param start start time in seconds
     * @param end   end time in seconds
     */
    public AlignmentSegmentWord(String text, double start, double end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    /** Returns the word text. */
    public String getText() {
        return text;
    }

    /** Returns the start time in seconds. */
    public double getStart() {
        return start;
    }

    /** Returns the end time in seconds. */
    public double getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return "AlignmentSegmentWord{text='" + text + "', start=" + start + ", end=" + end + '}';
    }
}
