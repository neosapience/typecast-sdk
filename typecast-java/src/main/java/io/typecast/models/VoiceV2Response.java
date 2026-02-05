package io.typecast.models;

import java.util.List;

/**
 * Response object for V2 voices API with enhanced metadata.
 */
public class VoiceV2Response {
    private String voiceId;
    private String voiceName;
    private List<ModelInfo> models;
    private GenderEnum gender;
    private AgeEnum age;
    private List<String> useCases;

    /**
     * Creates a new VoiceV2Response.
     */
    public VoiceV2Response() {
    }

    /**
     * Creates a new VoiceV2Response with all fields.
     * 
     * @param voiceId   the voice ID
     * @param voiceName the voice name
     * @param models    list of supported models with emotions
     * @param gender    the voice gender
     * @param age       the voice age category
     * @param useCases  list of recommended use cases
     */
    public VoiceV2Response(String voiceId, String voiceName, List<ModelInfo> models,
                           GenderEnum gender, AgeEnum age, List<String> useCases) {
        this.voiceId = voiceId;
        this.voiceName = voiceName;
        this.models = models;
        this.gender = gender;
        this.age = age;
        this.useCases = useCases;
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
     * Gets the supported models.
     * 
     * @return list of ModelInfo objects
     */
    public List<ModelInfo> getModels() {
        return models;
    }

    /**
     * Sets the supported models.
     * 
     * @param models list of ModelInfo objects
     */
    public void setModels(List<ModelInfo> models) {
        this.models = models;
    }

    /**
     * Gets the voice gender.
     * 
     * @return the gender, or null if not specified
     */
    public GenderEnum getGender() {
        return gender;
    }

    /**
     * Sets the voice gender.
     * 
     * @param gender the gender
     */
    public void setGender(GenderEnum gender) {
        this.gender = gender;
    }

    /**
     * Gets the voice age category.
     * 
     * @return the age category, or null if not specified
     */
    public AgeEnum getAge() {
        return age;
    }

    /**
     * Sets the voice age category.
     * 
     * @param age the age category
     */
    public void setAge(AgeEnum age) {
        this.age = age;
    }

    /**
     * Gets the recommended use cases.
     * 
     * @return list of use case strings, or null if not specified
     */
    public List<String> getUseCases() {
        return useCases;
    }

    /**
     * Sets the recommended use cases.
     * 
     * @param useCases list of use case strings
     */
    public void setUseCases(List<String> useCases) {
        this.useCases = useCases;
    }

    @Override
    public String toString() {
        return "VoiceV2Response{" +
                "voiceId='" + voiceId + '\'' +
                ", voiceName='" + voiceName + '\'' +
                ", models=" + models +
                ", gender=" + gender +
                ", age=" + age +
                ", useCases=" + useCases +
                '}';
    }
}
