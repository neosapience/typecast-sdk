package com.neosapience.models;

/**
 * Audio output configuration for streaming TTS synthesis.
 *
 * <p>Mirrors {@link Output} but omits {@code volume}, which is not supported
 * by the streaming endpoint. Streaming supports {@code targetLufs}.</p>
 */
public class OutputStream {
    private Double targetLufs;
    private Integer audioPitch;
    private Double audioTempo;
    private AudioFormat audioFormat;

    /**
     * Creates a new OutputStream with default values.
     */
    public OutputStream() {
        this.audioPitch = 0;
        this.audioTempo = 1.0;
        this.audioFormat = AudioFormat.WAV;
        this.targetLufs = null;
    }

    public Double getTargetLufs() {
        return targetLufs;
    }

    public OutputStream setTargetLufs(Double targetLufs) {
        if (targetLufs != null && (targetLufs.isNaN() || targetLufs.isInfinite() || targetLufs < -70.0 || targetLufs > 0.0)) {
            throw new IllegalArgumentException("Target LUFS must be between -70 and 0");
        }
        this.targetLufs = targetLufs;
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
     * @return this OutputStream for chaining
     * @throws IllegalArgumentException if audioPitch is out of range
     */
    public OutputStream setAudioPitch(Integer audioPitch) {
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
     * @return this OutputStream for chaining
     * @throws IllegalArgumentException if audioTempo is out of range
     */
    public OutputStream setAudioTempo(Double audioTempo) {
        if (audioTempo != null && (audioTempo.isNaN() || audioTempo.isInfinite() || audioTempo < 0.5 || audioTempo > 2.0)) {
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
     * @return this OutputStream for chaining
     */
    public OutputStream setAudioFormat(AudioFormat audioFormat) {
        this.audioFormat = audioFormat;
        return this;
    }

    /**
     * Creates a builder for OutputStream.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for OutputStream.
     */
    public static class Builder {
        private Integer audioPitch = 0;
        private Double audioTempo = 1.0;
        private AudioFormat audioFormat = AudioFormat.WAV;
        private Double targetLufs = null;

        public Builder targetLufs(Double targetLufs) {
            this.targetLufs = targetLufs;
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
         * Builds the OutputStream instance.
         *
         * @return the configured OutputStream
         */
        public OutputStream build() {
            OutputStream output = new OutputStream();
            output.setAudioPitch(audioPitch);
            output.setAudioTempo(audioTempo);
            output.setAudioFormat(audioFormat);
            output.setTargetLufs(targetLufs);
            return output;
        }
    }

    @Override
    public String toString() {
        return "OutputStream{" +
                "targetLufs=" + targetLufs +
                ", " +
                "audioPitch=" + audioPitch +
                ", audioTempo=" + audioTempo +
                ", audioFormat=" + audioFormat +
                '}';
    }
}
