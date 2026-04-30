//go:build e2e
// +build e2e

package typecast

import (
	"context"
	"testing"
)

const (
	e2eVoice = "tc_60e5426de8b95f1d3000d7b5"
)

func makeTimestampRequest(text, language string) *TTSRequestWithTimestamps {
	seed := 42
	intensity := 1.0
	return &TTSRequestWithTimestamps{
		VoiceID:  e2eVoice,
		Text:     text,
		Model:    ModelSSFMV30,
		Language: language,
		Prompt: &PresetPrompt{
			EmotionType:      "preset",
			EmotionPreset:    EmotionNormal,
			EmotionIntensity: &intensity,
		},
		Seed: &seed,
	}
}

func TestWithTimestamps_NoGranularity(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	req := makeTimestampRequest("Hello.", "eng")
	resp, err := client.TextToSpeechWithTimestamps(ctx, req, "")
	if err != nil {
		t.Fatalf("TextToSpeechWithTimestamps failed: %v", err)
	}

	if resp.AudioDuration <= 0 {
		t.Errorf("Expected audio_duration > 0, got %f", resp.AudioDuration)
	}
	if len(resp.Words) == 0 {
		t.Error("Expected words to be non-empty for no_granularity")
	}
	if len(resp.Characters) == 0 {
		t.Error("Expected characters to be non-empty for no_granularity")
	}
	t.Logf("no_granularity: duration=%.2fs words=%d chars=%d", resp.AudioDuration, len(resp.Words), len(resp.Characters))
}

func TestWithTimestamps_WordGranularity(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	req := makeTimestampRequest("Hello.", "eng")
	resp, err := client.TextToSpeechWithTimestamps(ctx, req, "word")
	if err != nil {
		t.Fatalf("TextToSpeechWithTimestamps (word) failed: %v", err)
	}

	if len(resp.Words) == 0 {
		t.Error("Expected words to be non-empty for word granularity")
	}
	if len(resp.Characters) != 0 {
		t.Errorf("Expected characters to be nil/empty for word granularity, got %d", len(resp.Characters))
	}
	t.Logf("word granularity: words=%d", len(resp.Words))
}

func TestWithTimestamps_CharGranularity(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	req := makeTimestampRequest("Hello.", "eng")
	resp, err := client.TextToSpeechWithTimestamps(ctx, req, "char")
	if err != nil {
		t.Fatalf("TextToSpeechWithTimestamps (char) failed: %v", err)
	}

	if len(resp.Characters) == 0 {
		t.Error("Expected characters to be non-empty for char granularity")
	}
	if len(resp.Words) != 0 {
		t.Errorf("Expected words to be nil/empty for char granularity, got %d", len(resp.Words))
	}
	t.Logf("char granularity: chars=%d", len(resp.Characters))
}

func TestWithTimestamps_JpnChar(t *testing.T) {
	client := getTestClient(t)
	ctx := context.Background()

	req := makeTimestampRequest("こんにちは。お元気ですか?", "jpn")
	resp, err := client.TextToSpeechWithTimestamps(ctx, req, "char")
	if err != nil {
		t.Fatalf("TextToSpeechWithTimestamps (jpn+char) failed: %v", err)
	}

	if len(resp.Characters) < 5 {
		t.Errorf("Expected characters length >= 5 for Japanese, got %d", len(resp.Characters))
	}
	t.Logf("jpn+char: chars=%d", len(resp.Characters))
}
