package com.neosapience.models;

/**
 * Represents a character-level alignment segment from the TTS with-timestamps response.
 */
public class AlignmentSegmentCharacter {
    private String text;
    private double start;
    private double end;

    /** No-arg constructor for Gson deserialization. */
    public AlignmentSegmentCharacter() {}

    /**
     * Creates an AlignmentSegmentCharacter with all fields.
     *
     * @param text  the character text
     * @param start start time in seconds
     * @param end   end time in seconds
     */
    public AlignmentSegmentCharacter(String text, double start, double end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    /** Returns the character text. */
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
        return "AlignmentSegmentCharacter{text='" + text + "', start=" + start + ", end=" + end + '}';
    }
}
