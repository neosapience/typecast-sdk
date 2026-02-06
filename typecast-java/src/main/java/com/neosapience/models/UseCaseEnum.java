package com.neosapience.models;

/**
 * Use case enumeration for voice filtering.
 */
public enum UseCaseEnum {
    /** Announcer voice */
    ANNOUNCER("announcer"),
    /** Anime voice */
    ANIME("anime"),
    /** Audiobook voice */
    AUDIOBOOK("audiobook"),
    /** Conversational voice */
    CONVERSATIONAL("conversational"),
    /** Documentary voice */
    DOCUMENTARY("documentary"),
    /** E-Learning voice */
    E_LEARNING("e_learning"),
    /** Rapper voice */
    RAPPER("rapper"),
    /** Game voice */
    GAME("game"),
    /** TikTok/Reels voice */
    TIKTOK_REELS("tiktok_reels"),
    /** News voice */
    NEWS("news"),
    /** Podcast voice */
    PODCAST("podcast"),
    /** Voicemail voice */
    VOICEMAIL("voicemail"),
    /** Ads voice */
    ADS("ads");

    private final String value;

    UseCaseEnum(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the use case.
     * 
     * @return the use case string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a UseCaseEnum from a string value.
     * 
     * @param value the use case string
     * @return the corresponding UseCaseEnum
     * @throws IllegalArgumentException if the value doesn't match any use case
     */
    public static UseCaseEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (UseCaseEnum useCase : values()) {
            if (useCase.value.equalsIgnoreCase(value)) {
                return useCase;
            }
        }
        throw new IllegalArgumentException("Unknown use case: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
