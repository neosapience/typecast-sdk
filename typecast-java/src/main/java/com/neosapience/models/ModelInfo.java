package com.neosapience.models;

import java.util.List;

/**
 * Model information for V2 voice responses.
 * 
 * <p>Contains the model version and its available emotions.</p>
 */
public class ModelInfo {
    private String version;
    private List<String> emotions;

    /**
     * Creates a new ModelInfo.
     */
    public ModelInfo() {
    }

    /**
     * Creates a new ModelInfo with specified values.
     * 
     * @param version  the model version
     * @param emotions list of available emotions
     */
    public ModelInfo(String version, List<String> emotions) {
        this.version = version;
        this.emotions = emotions;
    }

    /**
     * Gets the model version.
     * 
     * @return the version string (e.g., "ssfm-v21")
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the model version.
     * 
     * @param version the version string
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Gets the available emotions for this model.
     * 
     * @return list of emotion names
     */
    public List<String> getEmotions() {
        return emotions;
    }

    /**
     * Sets the available emotions.
     * 
     * @param emotions list of emotion names
     */
    public void setEmotions(List<String> emotions) {
        this.emotions = emotions;
    }

    @Override
    public String toString() {
        return "ModelInfo{" +
                "version='" + version + '\'' +
                ", emotions=" + emotions +
                '}';
    }
}
