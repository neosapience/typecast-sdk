package com.neosapience;

/**
 * Parsed part from pause markup. Pause tokens use {@code <|{seconds}s|>}.
 */
public class SpeechPart {
    private final String text;
    private final double pauseSeconds;
    private final boolean pause;

    public SpeechPart(String text, double pauseSeconds, boolean pause) {
        this.text = text;
        this.pauseSeconds = pauseSeconds;
        this.pause = pause;
    }

    public String getText() { return text; }
    public double getPauseSeconds() { return pauseSeconds; }
    public boolean isPause() { return pause; }
}
