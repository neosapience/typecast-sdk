package typecast

import (
	"context"
	"os"
	"testing"
	"time"
)

func skipIfNoAPIKey(t *testing.T) string {
	t.Helper()
	apiKey := os.Getenv("TYPECAST_API_KEY")
	if apiKey == "" {
		t.Skip("TYPECAST_API_KEY environment variable is required for integration tests")
	}
	return apiKey
}

func getTestClient(t *testing.T) *Client {
	apiKey := skipIfNoAPIKey(t)
	return NewClient(&ClientConfig{
		APIKey:  apiKey,
		Timeout: 60 * time.Second,
	})
}

func TestGetVoicesV2(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	voices, err := client.GetVoicesV2(ctx, nil)
	if err != nil {
		t.Fatalf("GetVoicesV2 failed: %v", err)
	}

	if len(voices) == 0 {
		t.Fatal("Expected at least one voice, got none")
	}

	// Check that the first voice has required fields
	voice := voices[0]
	if voice.VoiceID == "" {
		t.Error("Voice ID should not be empty")
	}
	if voice.VoiceName == "" {
		t.Error("Voice name should not be empty")
	}
	if len(voice.Models) == 0 {
		t.Error("Voice should have at least one model")
	}

	t.Logf("Found %d voices", len(voices))
	t.Logf("First voice: ID=%s, Name=%s, Models=%d", voice.VoiceID, voice.VoiceName, len(voice.Models))
}

func TestGetVoicesV2WithFilter(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	// Filter by model
	filter := &VoicesV2Filter{
		Model: ModelSSFMV30,
	}

	voices, err := client.GetVoicesV2(ctx, filter)
	if err != nil {
		t.Fatalf("GetVoicesV2 with filter failed: %v", err)
	}

	if len(voices) == 0 {
		t.Fatal("Expected at least one voice with ssfm-v30 model, got none")
	}

	// Verify all voices support ssfm-v30
	for _, voice := range voices {
		hasV30 := false
		for _, model := range voice.Models {
			if model.Version == ModelSSFMV30 {
				hasV30 = true
				break
			}
		}
		if !hasV30 {
			t.Errorf("Voice %s should support ssfm-v30", voice.VoiceID)
		}
	}

	t.Logf("Found %d voices with ssfm-v30 support", len(voices))
}

func TestGetVoiceV2(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	// First, get a voice ID from the list
	voices, err := client.GetVoicesV2(ctx, nil)
	if err != nil {
		t.Fatalf("GetVoicesV2 failed: %v", err)
	}

	if len(voices) == 0 {
		t.Fatal("No voices available for testing")
	}

	voiceID := voices[0].VoiceID

	// Now get the specific voice
	voice, err := client.GetVoiceV2(ctx, voiceID)
	if err != nil {
		t.Fatalf("GetVoiceV2 failed: %v", err)
	}

	if voice.VoiceID != voiceID {
		t.Errorf("Expected voice ID %s, got %s", voiceID, voice.VoiceID)
	}

	t.Logf("Voice details: ID=%s, Name=%s, Gender=%v, Age=%v", 
		voice.VoiceID, voice.VoiceName, voice.Gender, voice.Age)
}

func TestTextToSpeech(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	// First, get a voice that supports ssfm-v21
	voices, err := client.GetVoicesV2(ctx, &VoicesV2Filter{Model: ModelSSFMV21})
	if err != nil {
		t.Fatalf("GetVoicesV2 failed: %v", err)
	}

	if len(voices) == 0 {
		t.Fatal("No voices available for testing")
	}

	voiceID := voices[0].VoiceID
	t.Logf("Using voice: %s (%s)", voices[0].VoiceName, voiceID)

	// Create TTS request
	volume := 100
	tempo := 1.0
	intensity := 1.0

	request := &TTSRequest{
		VoiceID:  voiceID,
		Text:     "Hello, this is a test of the Typecast Go SDK.",
		Model:    ModelSSFMV21,
		Language: "eng",
		Prompt: &Prompt{
			EmotionPreset:    EmotionNormal,
			EmotionIntensity: &intensity,
		},
		Output: &Output{
			Volume:      &volume,
			AudioTempo:  &tempo,
			AudioFormat: AudioFormatWAV,
		},
	}

	response, err := client.TextToSpeech(ctx, request)
	if err != nil {
		t.Fatalf("TextToSpeech failed: %v", err)
	}

	// Verify response
	if len(response.AudioData) == 0 {
		t.Error("Audio data should not be empty")
	}

	if response.Format != AudioFormatWAV {
		t.Errorf("Expected WAV format, got %s", response.Format)
	}

	t.Logf("Generated audio: %d bytes, format=%s, duration=%.2fs", 
		len(response.AudioData), response.Format, response.Duration)

	// Save audio file for verification
	outputPath := "test_output.wav"
	if err := os.WriteFile(outputPath, response.AudioData, 0644); err != nil {
		t.Fatalf("Failed to save audio file: %v", err)
	}

	t.Logf("Audio saved to %s", outputPath)
}

func TestTextToSpeechV30(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	// Get a voice that supports ssfm-v30
	voices, err := client.GetVoicesV2(ctx, &VoicesV2Filter{Model: ModelSSFMV30})
	if err != nil {
		t.Fatalf("GetVoicesV2 failed: %v", err)
	}

	if len(voices) == 0 {
		t.Fatal("No voices available for testing")
	}

	voiceID := voices[0].VoiceID
	t.Logf("Using voice: %s (%s)", voices[0].VoiceName, voiceID)

	// Create TTS request with smart prompt
	request := &TTSRequest{
		VoiceID:  voiceID,
		Text:     "Everything is so incredibly perfect that I feel like I'm dreaming.",
		Model:    ModelSSFMV30,
		Language: "eng",
		Prompt: &SmartPrompt{
			EmotionType:  "smart",
			PreviousText: "I feel like I'm walking on air and I just want to scream with joy!",
			NextText:     "I am literally bursting with happiness and I never want this feeling to end!",
		},
		Output: &Output{
			AudioFormat: AudioFormatWAV,
		},
	}

	response, err := client.TextToSpeech(ctx, request)
	if err != nil {
		t.Fatalf("TextToSpeech with ssfm-v30 failed: %v", err)
	}

	if len(response.AudioData) == 0 {
		t.Error("Audio data should not be empty")
	}

	t.Logf("Generated audio (ssfm-v30): %d bytes, format=%s", 
		len(response.AudioData), response.Format)

	// Save audio file
	outputPath := "test_output_v30.wav"
	if err := os.WriteFile(outputPath, response.AudioData, 0644); err != nil {
		t.Fatalf("Failed to save audio file: %v", err)
	}

	t.Logf("Audio saved to %s", outputPath)
}

func TestTextToSpeechPresetPrompt(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	// Get a voice that supports ssfm-v30
	voices, err := client.GetVoicesV2(ctx, &VoicesV2Filter{Model: ModelSSFMV30})
	if err != nil {
		t.Fatalf("GetVoicesV2 failed: %v", err)
	}

	if len(voices) == 0 {
		t.Fatal("No voices available for testing")
	}

	voiceID := voices[0].VoiceID
	intensity := 1.5

	// Create TTS request with preset prompt
	request := &TTSRequest{
		VoiceID:  voiceID,
		Text:     "I am so happy today!",
		Model:    ModelSSFMV30,
		Language: "eng",
		Prompt: &PresetPrompt{
			EmotionType:      "preset",
			EmotionPreset:    EmotionHappy,
			EmotionIntensity: &intensity,
		},
		Output: &Output{
			AudioFormat: AudioFormatMP3,
		},
	}

	response, err := client.TextToSpeech(ctx, request)
	if err != nil {
		t.Fatalf("TextToSpeech with preset prompt failed: %v", err)
	}

	if len(response.AudioData) == 0 {
		t.Error("Audio data should not be empty")
	}

	if response.Format != AudioFormatMP3 {
		t.Errorf("Expected MP3 format, got %s", response.Format)
	}

	t.Logf("Generated audio (preset happy): %d bytes, format=%s", 
		len(response.AudioData), response.Format)

	// Save audio file
	outputPath := "test_output_happy.mp3"
	if err := os.WriteFile(outputPath, response.AudioData, 0644); err != nil {
		t.Fatalf("Failed to save audio file: %v", err)
	}

	t.Logf("Audio saved to %s", outputPath)
}

func TestAPIErrorHandling(t *testing.T) {
	// Create client with invalid API key
	client := NewClient(&ClientConfig{
		APIKey: "invalid_api_key",
	})

	ctx := context.Background()

	_, err := client.GetVoicesV2(ctx, nil)
	if err == nil {
		t.Fatal("Expected error with invalid API key, got none")
	}

	apiErr, ok := err.(*APIError)
	if !ok {
		t.Fatalf("Expected APIError, got %T", err)
	}

	// API returns 401 or 403 for invalid API key
	if !apiErr.IsUnauthorized() && !apiErr.IsForbidden() {
		t.Errorf("Expected 401 or 403 error, got status %d", apiErr.StatusCode)
	}

	t.Logf("Got expected error: %s", apiErr.Error())
}

func TestTextToSpeechInvalidVoice(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	request := &TTSRequest{
		VoiceID: "invalid_voice_id",
		Text:    "Test",
		Model:   ModelSSFMV21,
	}

	_, err := client.TextToSpeech(ctx, request)
	if err == nil {
		t.Fatal("Expected error with invalid voice ID, got none")
	}

	apiErr, ok := err.(*APIError)
	if !ok {
		t.Fatalf("Expected APIError, got %T", err)
	}

	// Should be 400, 404 or 422 for invalid voice
	if apiErr.StatusCode != 400 && apiErr.StatusCode != 404 && apiErr.StatusCode != 422 {
		t.Errorf("Expected 400, 404 or 422 error, got status %d", apiErr.StatusCode)
	}

	t.Logf("Got expected error: %s", apiErr.Error())
}
