package com.neosapience.models;

/**
 * Voice recommendation result.
 *
 * <p>Recommendation results only include the matched voice ID, voice name, and
 * similarity score. Use {@code getVoiceV2} or {@code getVoicesV2} to fetch
 * detailed voice metadata for a returned voice ID.</p>
 */
public class RecommendedVoice {
    private String voiceId;
    private String voiceName;
    private double score;

    public RecommendedVoice() {
    }

    public RecommendedVoice(String voiceId, String voiceName, double score) {
        this.voiceId = voiceId;
        this.voiceName = voiceName;
        this.score = score;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
