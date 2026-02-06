package com.neosapience.models;

/**
 * Enumeration of available TTS (Text-to-Speech) models.
 * 
 * <ul>
 *   <li>{@code SSFM_V21} - Supports 27 languages with 4 emotion presets</li>
 *   <li>{@code SSFM_V30} - Supports 37 languages with 7 emotion presets including smart emotion</li>
 * </ul>
 */
public enum TTSModel {
    /**
     * SSFM v2.1 model - 27 languages, 4 emotion presets (normal, happy, sad, angry)
     */
    SSFM_V21("ssfm-v21"),
    
    /**
     * SSFM v3.0 model - 37 languages, 7 emotion presets 
     * (normal, happy, sad, angry, whisper, toneup, tonedown)
     */
    SSFM_V30("ssfm-v30");

    private final String value;

    TTSModel(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the model.
     * 
     * @return the model string value (e.g., "ssfm-v21")
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a TTSModel from a string value.
     * 
     * @param value the string value (e.g., "ssfm-v21")
     * @return the corresponding TTSModel
     * @throws IllegalArgumentException if the value doesn't match any model
     */
    public static TTSModel fromValue(String value) {
        for (TTSModel model : values()) {
            if (model.value.equals(value)) {
                return model;
            }
        }
        throw new IllegalArgumentException("Unknown TTS model: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
