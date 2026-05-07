package com.neosapience.models;

/**
 * Response of POST /v1/voices/clone — quick-cloned custom voice metadata.
 *
 * <p>The {@code voiceId} field has the {@code "uc_"} prefix and can be used
 * directly as {@code voice_id} in {@code textToSpeech} calls.</p>
 *
 * <p>Gson deserializes this class using
 * {@link com.google.gson.FieldNamingPolicy#LOWER_CASE_WITH_UNDERSCORES}, so the
 * Java field {@code voiceId} maps to the JSON key {@code "voice_id"} without
 * any annotation.</p>
 */
public class CustomVoice {

    /** Maximum audio file size accepted by cloneVoice (25 MB). Matches typecast-api. */
    public static final long CLONING_MAX_FILE_SIZE = 25L * 1024 * 1024;

    /** Minimum allowed length for the voice name passed to cloneVoice. */
    public static final int NAME_MIN_LENGTH = 1;

    /** Maximum allowed length for the voice name passed to cloneVoice. */
    public static final int NAME_MAX_LENGTH = 30;

    // Field names follow LOWER_CASE_WITH_UNDERSCORES mapping used by Gson:
    //   voiceId  → voice_id
    //   name     → name
    //   model    → model
    private String voiceId;
    private String name;
    private String model;

    /** No-arg constructor required by Gson. */
    public CustomVoice() {
    }

    /**
     * Creates a new CustomVoice with all fields.
     *
     * @param voiceId the unique voice identifier (has "uc_" prefix)
     * @param name    the display name of the cloned voice
     * @param model   the synthesis model used for this voice
     */
    public CustomVoice(String voiceId, String name, String model) {
        this.voiceId = voiceId;
        this.name = name;
        this.model = model;
    }

    /**
     * Returns the unique voice identifier.
     *
     * @return the voice ID (has "uc_" prefix)
     */
    public String getVoiceId() {
        return voiceId;
    }

    /**
     * Sets the unique voice identifier.
     *
     * @param voiceId the voice ID
     */
    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    /**
     * Returns the display name of the cloned voice.
     *
     * @return the voice name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the display name of the cloned voice.
     *
     * @param name the voice name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the synthesis model used for this voice.
     *
     * @return the model identifier
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the synthesis model.
     *
     * @param model the model identifier
     */
    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public String toString() {
        return "CustomVoice{"
                + "voiceId='" + voiceId + '\''
                + ", name='" + name + '\''
                + ", model='" + model + '\''
                + '}';
    }
}
