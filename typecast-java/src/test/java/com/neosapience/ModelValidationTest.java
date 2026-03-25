package com.neosapience;

import com.neosapience.models.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assertions.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for model validation (Output, Prompt, SmartPrompt, Enums).
 */
class ModelValidationTest {

    // ==================== Output Validation ====================

    @Test
    @DisplayName("Output should validate volume range")
    void output_validatesVolumeRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setVolume(-1));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setVolume(201));

        assertDoesNotThrow(() -> new Output().setVolume(0));
        assertDoesNotThrow(() -> new Output().setVolume(200));
    }

    @Test
    @DisplayName("Output should validate audio pitch range")
    void output_validatesAudioPitchRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioPitch(-13));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioPitch(13));

        assertDoesNotThrow(() -> new Output().setAudioPitch(-12));
        assertDoesNotThrow(() -> new Output().setAudioPitch(12));
    }

    @Test
    @DisplayName("Output should validate target LUFS range")
    void output_validatesTargetLufsRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setVolume(null).setTargetLufs(-71.0));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setVolume(null).setTargetLufs(1.0));

        assertDoesNotThrow(() -> new Output().setVolume(null).setTargetLufs(-70.0));
        assertDoesNotThrow(() -> new Output().setVolume(null).setTargetLufs(0.0));
    }

    @Test
    @DisplayName("Output builder should auto-clear volume when targetLufs is set")
    void output_builderAutoClearsVolumeForTargetLufs() {
        Output output = Output.builder()
                .targetLufs(-14.0)
                .build();
        assertNull(output.getVolume());
        assertEquals(-14.0, output.getTargetLufs());
    }

    @Test
    @DisplayName("Output should validate audio tempo range")
    void output_validatesAudioTempoRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioTempo(0.4));

        assertThrows(IllegalArgumentException.class, () ->
                new Output().setAudioTempo(2.1));

        assertDoesNotThrow(() -> new Output().setAudioTempo(0.5));
        assertDoesNotThrow(() -> new Output().setAudioTempo(2.0));
    }

    // ==================== Prompt Validation ====================

    @Test
    @DisplayName("Prompt should validate emotion intensity range")
    void prompt_validatesEmotionIntensityRange() {
        assertThrows(IllegalArgumentException.class, () ->
                new Prompt().setEmotionIntensity(-0.1));

        assertThrows(IllegalArgumentException.class, () ->
                new Prompt().setEmotionIntensity(2.1));

        assertDoesNotThrow(() -> new Prompt().setEmotionIntensity(0.0));
        assertDoesNotThrow(() -> new Prompt().setEmotionIntensity(2.0));
    }

    @Test
    @DisplayName("SmartPrompt should validate text length")
    void smartPrompt_validatesTextLength() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 2001; i++) {
            longText.append("a");
        }

        assertThrows(IllegalArgumentException.class, () ->
                new SmartPrompt().setPreviousText(longText.toString()));

        assertThrows(IllegalArgumentException.class, () ->
                new SmartPrompt().setNextText(longText.toString()));
    }

    // ==================== Enum Tests ====================

    @Test
    @DisplayName("TTSModel should convert to and from string")
    void ttsModel_conversion() {
        assertEquals("ssfm-v21", TTSModel.SSFM_V21.getValue());
        assertEquals("ssfm-v30", TTSModel.SSFM_V30.getValue());

        assertEquals(TTSModel.SSFM_V21, TTSModel.fromValue("ssfm-v21"));
        assertEquals(TTSModel.SSFM_V30, TTSModel.fromValue("ssfm-v30"));

        assertThrows(IllegalArgumentException.class, () ->
                TTSModel.fromValue("invalid"));
    }

    @Test
    @DisplayName("EmotionPreset should convert to and from string")
    void emotionPreset_conversion() {
        assertEquals("normal", EmotionPreset.NORMAL.getValue());
        assertEquals("happy", EmotionPreset.HAPPY.getValue());
        assertEquals("whisper", EmotionPreset.WHISPER.getValue());

        assertEquals(EmotionPreset.NORMAL, EmotionPreset.fromValue("normal"));
        assertEquals(EmotionPreset.HAPPY, EmotionPreset.fromValue("HAPPY"));
    }

    @Test
    @DisplayName("LanguageCode should convert to and from string")
    void languageCode_conversion() {
        assertEquals("eng", LanguageCode.ENG.getValue());
        assertEquals("kor", LanguageCode.KOR.getValue());

        assertEquals(LanguageCode.ENG, LanguageCode.fromValue("eng"));
        assertEquals(LanguageCode.KOR, LanguageCode.fromValue("KOR"));
    }

    @Test
    @DisplayName("AudioFormat should return correct MIME type")
    void audioFormat_mimeType() {
        assertEquals("audio/wav", AudioFormat.WAV.getMimeType());
        assertEquals("audio/mp3", AudioFormat.MP3.getMimeType());
    }
}
