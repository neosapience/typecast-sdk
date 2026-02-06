package com.neosapience.models;

/**
 * Audio output configuration for TTS synthesis.
 */
public class Output {
    private Integer volume;
    private Integer audioPitch;
    private Double audioTempo;
    private AudioFormat audioFormat;

    /**
     * Creates a new Output with default values.
     */
    public Output() {
        this.volume = 100;
        this.audioPitch = 0;
        this.audioTempo = 1.0;
        this.audioFormat = AudioFormat.WAV;
    }

    /**
     * Gets the volume level.
     * 
     * @return the volume (0-200, default 100)
     */
    public Integer getVolume() {
        return volume;
    }

    /**
     * Sets the volume level.
     * 
     * @param volume the volume level (0-200)
     * @return this Output for chaining
     * @throws IllegalArgumentException if volume is out of range
     */
    public Output setVolume(Integer volume) {
        if (volume != null && (volume < 0 || volume > 200)) {
            throw new IllegalArgumentException("Volume must be between 0 and 200");
        }
        this.volume = volume;
        return this;
    }

    /**
     * Gets the audio pitch adjustment.
     * 
     * @return the pitch adjustment (-12 to 12 semitones, default 0)
     */
    public Integer getAudioPitch() {
        return audioPitch;
    }

    /**
     * Sets the audio pitch adjustment.
     * 
     * @param audioPitch the pitch adjustment in semitones (-12 to 12)
     * @return this Output for chaining
     * @throws IllegalArgumentException if audioPitch is out of range
     */
    public Output setAudioPitch(Integer audioPitch) {
        if (audioPitch != null && (audioPitch < -12 || audioPitch > 12)) {
            throw new IllegalArgumentException("Audio pitch must be between -12 and 12");
        }
        this.audioPitch = audioPitch;
        return this;
    }

    /**
     * Gets the audio tempo multiplier.
     * 
     * @return the tempo multiplier (0.5-2.0, default 1.0)
     */
    public Double getAudioTempo() {
        return audioTempo;
    }

    /**
     * Sets the audio tempo multiplier.
     * 
     * @param audioTempo the tempo multiplier (0.5-2.0)
     * @return this Output for chaining
     * @throws IllegalArgumentException if audioTempo is out of range
     */
    public Output setAudioTempo(Double audioTempo) {
        if (audioTempo != null && (audioTempo < 0.5 || audioTempo > 2.0)) {
            throw new IllegalArgumentException("Audio tempo must be between 0.5 and 2.0");
        }
        this.audioTempo = audioTempo;
        return this;
    }

    /**
     * Gets the audio output format.
     * 
     * @return the audio format (default WAV)
     */
    public AudioFormat getAudioFormat() {
        return audioFormat;
    }

    /**
     * Sets the audio output format.
     * 
     * @param audioFormat the audio format
     * @return this Output for chaining
     */
    public Output setAudioFormat(AudioFormat audioFormat) {
        this.audioFormat = audioFormat;
        return this;
    }

    /**
     * Creates a builder for Output.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for Output.
     */
    public static class Builder {
        private Integer volume = 100;
        private Integer audioPitch = 0;
        private Double audioTempo = 1.0;
        private AudioFormat audioFormat = AudioFormat.WAV;

        /**
         * Sets the volume level.
         * 
         * @param volume the volume level (0-200)
         * @return this Builder for chaining
         */
        public Builder volume(Integer volume) {
            this.volume = volume;
            return this;
        }

        /**
         * Sets the audio pitch adjustment.
         * 
         * @param audioPitch the pitch adjustment in semitones (-12 to 12)
         * @return this Builder for chaining
         */
        public Builder audioPitch(Integer audioPitch) {
            this.audioPitch = audioPitch;
            return this;
        }

        /**
         * Sets the audio tempo multiplier.
         * 
         * @param audioTempo the tempo multiplier (0.5-2.0)
         * @return this Builder for chaining
         */
        public Builder audioTempo(Double audioTempo) {
            this.audioTempo = audioTempo;
            return this;
        }

        /**
         * Sets the audio output format.
         * 
         * @param audioFormat the audio format
         * @return this Builder for chaining
         */
        public Builder audioFormat(AudioFormat audioFormat) {
            this.audioFormat = audioFormat;
            return this;
        }

        /**
         * Builds the Output instance.
         * 
         * @return the configured Output
         */
        public Output build() {
            Output output = new Output();
            output.setVolume(volume);
            output.setAudioPitch(audioPitch);
            output.setAudioTempo(audioTempo);
            output.setAudioFormat(audioFormat);
            return output;
        }
    }

    @Override
    public String toString() {
        return "Output{" +
                "volume=" + volume +
                ", audioPitch=" + audioPitch +
                ", audioTempo=" + audioTempo +
                ", audioFormat=" + audioFormat +
                '}';
    }
}
