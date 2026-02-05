package io.typecast.models;

import java.util.List;

/**
 * Response object for V1 voices API.
 * 
 * @deprecated Use {@link VoiceV2Response} with the V2 API instead.
 */
@Deprecated
public class VoicesResponse {
    private String voiceId;
    private String voiceName;
    private String model;
    private List<String> emotions;

    /**
     * Creates a new VoicesResponse.
     */
    public VoicesResponse() {
    }

    /**
     * Creates a new VoicesResponse with all fields.
     * 
     * @param voiceId   the voice ID
     * @param voiceName the voice name
     * @param model     the model version
     * @param emotions  list of available emotions
     */
    public VoicesResponse(String voiceId, String voiceName, String model, List<String> emotions) {
        this.voiceId = voiceId;
        this.voiceName = voiceName;
        this.model = model;
        this.emotions = emotions;
    }

    /**
     * Gets the voice ID.
     * 
     * @return the voice ID
     */
    public String getVoiceId() {
        return voiceId;
    }

    /**
     * Sets the voice ID.
     * 
     * @param voiceId the voice ID
     */
    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    /**
     * Gets the voice name.
     * 
     * @return the voice name
     */
    public String getVoiceName() {
        return voiceName;
    }

    /**
     * Sets the voice name.
     * 
     * @param voiceName the voice name
     */
    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    /**
     * Gets the model version.
     * 
     * @return the model version string
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the model version.
     * 
     * @param model the model version
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Gets the available emotions.
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
        return "VoicesResponse{" +
                "voiceId='" + voiceId + '\'' +
                ", voiceName='" + voiceName + '\'' +
                ", model='" + model + '\'' +
                ", emotions=" + emotions +
                '}';
    }
}
