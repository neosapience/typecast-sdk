package com.neosapience.models;

import java.util.List;

/** Response from the current V3 Voice API. */
public class VoiceV3Response {
    public static class LocalizedName { public String eng; public String kor; }
    private String voiceId;
    private LocalizedName voiceName;
    private List<ModelInfo> models;
    private String voiceType;
    private GenderEnum gender;
    private AgeEnum age;
    private List<String> useCases;
    private String previewUrl;
    public String getVoiceId() { return voiceId; }
    public LocalizedName getVoiceName() { return voiceName; }
    public List<ModelInfo> getModels() { return models; }
    public String getVoiceType() { return voiceType; }
    public GenderEnum getGender() { return gender; }
    public AgeEnum getAge() { return age; }
    public List<String> getUseCases() { return useCases; }
    public String getPreviewUrl() { return previewUrl; }
}
