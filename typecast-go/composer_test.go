package typecast

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"math"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
)

func TestComposeSpeech_UsesComposeAPIAndMergesOverrides(t *testing.T) {
	type segment struct {
		Type            string   `json:"type"`
		DurationSeconds float64  `json:"duration_seconds"`
		VoiceID         string   `json:"voice_id"`
		Text            string   `json:"text"`
		Model           TTSModel `json:"model"`
		Output          *Output  `json:"output"`
	}
	var body struct {
		Segments []segment `json:"segments"`
	}
	requests := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		if r.URL.Path != "/v1/text-to-speech/compose" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("decode body: %v", err)
		}
		w.Header().Set("Content-Type", "audio/mpeg")
		w.Header().Set("X-Audio-Duration", "1.25")
		_, _ = w.Write([]byte("composed-audio"))
	}))
	defer srv.Close()

	tempo := 1.1
	pitch := 1
	response, err := newTestClient(srv, "key").
		ComposeSpeech().
		Defaults(ComposerSettings{
			VoiceID: "voice-a",
			Model:   ModelSSFMV30,
			Output:  &Output{AudioFormat: AudioFormatMP3, AudioPitch: &pitch},
		}).
		SayWith("Hello<|0.3s|>world", ComposerSettings{
			VoiceID: "voice-b",
			Output:  &Output{AudioTempo: &tempo},
		}).
		Generate(context.Background())
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}
	if requests != 1 {
		t.Fatalf("expected one compose request, got %d", requests)
	}
	if len(body.Segments) != 3 {
		t.Fatalf("expected three segments, got %#v", body.Segments)
	}
	if body.Segments[0].Type != "tts" || body.Segments[0].Text != "Hello" || body.Segments[0].VoiceID != "voice-b" {
		t.Fatalf("unexpected first segment: %#v", body.Segments[0])
	}
	if body.Segments[0].Output == nil || body.Segments[0].Output.AudioFormat != AudioFormatMP3 || body.Segments[0].Output.AudioPitch == nil || body.Segments[0].Output.AudioTempo == nil {
		t.Fatalf("unexpected merged output: %#v", body.Segments[0].Output)
	}
	if body.Segments[1].Type != "pause" || body.Segments[1].DurationSeconds != 0.3 {
		t.Fatalf("unexpected pause segment: %#v", body.Segments[1])
	}
	if body.Segments[2].Type != "tts" || body.Segments[2].Text != "world" {
		t.Fatalf("unexpected final segment: %#v", body.Segments[2])
	}
	if string(response.AudioData) != "composed-audio" || response.Format != AudioFormatMP3 || response.Duration != 1.25 {
		t.Fatalf("unexpected response: %#v", response)
	}
}

func TestComposeSpeech_ValidatesBeforeNetwork(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "key", BaseURL: "http://127.0.0.1"})
	if _, err := c.ComposeSpeech().Say("Hello").Generate(context.Background()); err == nil || !strings.Contains(err.Error(), "voice_id is required") {
		t.Fatalf("expected voice_id error, got %v", err)
	}
	if _, err := c.ComposeSpeech().Pause(0).Generate(context.Background()); err == nil || !strings.Contains(err.Error(), "pause seconds must be greater than 0") {
		t.Fatalf("expected pause error, got %v", err)
	}
	if _, err := c.ComposeSpeech().Generate(context.Background()); err == nil || !strings.Contains(err.Error(), "at least one speech segment") {
		t.Fatalf("expected empty composer error, got %v", err)
	}
}

func TestParsePauseMarkup_LenientForInvalidTokens(t *testing.T) {
	parts := ParsePauseMarkup("a<|0.3s|>b<|abc|>c<|3s|>")
	want := []SpeechPart{
		{Kind: SpeechPartText, Text: "a"},
		{Kind: SpeechPartPause, Seconds: 0.3},
		{Kind: SpeechPartText, Text: "b<|abc|>c"},
		{Kind: SpeechPartPause, Seconds: 3},
	}
	if !reflect.DeepEqual(parts, want) {
		t.Fatalf("parts = %#v, want %#v", parts, want)
	}
}

func TestComposeSpeech_PropagatesServerErrors(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "nope", http.StatusInternalServerError)
	}))
	defer srv.Close()
	_, err := newTestClient(srv, "key").ComposeSpeech().
		Defaults(ComposerSettings{VoiceID: "voice-a", Model: ModelSSFMV30}).
		Say("Hello").Generate(context.Background())
	if err == nil {
		t.Fatal("expected server error")
	}
}

func TestComposeSpeech_CoversBuilderAndResponseBranches(t *testing.T) {
	volume, pitch, seed := 80, 2, 7
	target, tempo := -18.0, 1.2
	merged := mergeComposerSettings(
		ComposerSettings{VoiceID: "a", Model: ModelSSFMV30, Output: &Output{Volume: &volume}},
		ComposerSettings{
			VoiceID: "b", Model: ModelSSFMV21, Language: "eng", Prompt: "prompt", Seed: &seed,
			Output: &Output{Volume: &volume, TargetLUFS: &target, AudioPitch: &pitch, AudioTempo: &tempo, AudioFormat: AudioFormatMP3},
		},
	)
	if merged.VoiceID != "b" || merged.Language != "eng" || merged.Seed == nil ||
		merged.Output.TargetLUFS == nil || merged.Output.AudioPitch == nil ||
		merged.Output.AudioTempo == nil || merged.Output.AudioFormat != AudioFormatMP3 {
		t.Fatalf("unexpected merged settings: %#v", merged)
	}
	if mergeComposerOutput(nil, nil) != nil {
		t.Fatal("nil outputs should remain nil")
	}

	for _, text := range []string{"", "plain", "<|1s|>", "a<|s|>b", "a<|.3s|>b", "a<|3.s|>b", "a<|3..1s|>b", "a<|3xs|>b", "a<|1s"} {
		ParsePauseMarkup(text)
	}
	for _, seconds := range []float64{math.NaN(), math.Inf(1), 0} {
		if _, err := NewClient(&ClientConfig{APIKey: "key", BaseURL: "http://127.0.0.1"}).
			ComposeSpeech().Pause(seconds).Generate(context.Background()); err == nil {
			t.Fatalf("expected invalid pause for %v", seconds)
		}
	}
	if _, err := NewClient(&ClientConfig{APIKey: "key", BaseURL: "http://127.0.0.1"}).
		ComposeSpeech().Defaults(ComposerSettings{VoiceID: "voice"}).Say("Hello").Generate(context.Background()); err == nil {
		t.Fatal("expected model error")
	}
	if _, err := NewClient(&ClientConfig{APIKey: "key", BaseURL: "http://127.0.0.1"}).
		ComposeSpeech().Defaults(ComposerSettings{VoiceID: "voice", Model: ModelSSFMV30}).
		Say("Hello<|0s|>world").Generate(context.Background()); err == nil {
		t.Fatal("expected parsed pause error")
	}
}

func TestComposeSpeech_CoversHTTPResponseBranches(t *testing.T) {
	for _, contentType := range []string{"audio/mp3", "audio/wav"} {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", contentType)
			_, _ = w.Write([]byte("audio"))
		}))
		response, err := newTestClient(srv, "key").ComposeSpeech().
			Defaults(ComposerSettings{VoiceID: "voice", Model: ModelSSFMV30}).
			Say("Hello").Pause(0.1).Say("   ").Generate(context.Background())
		srv.Close()
		if err != nil || len(response.AudioData) == 0 {
			t.Fatalf("response error: %v", err)
		}
	}

	client := NewClient(&ClientConfig{APIKey: "key", BaseURL: "http://example.test"})
	client.httpClient.Transport = roundTripFunc(func(*http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: http.StatusOK,
			Header:     http.Header{},
			Body:       io.NopCloser(errorReader{}),
		}, nil
	})
	if _, err := client.ComposeSpeech().Defaults(ComposerSettings{VoiceID: "voice", Model: ModelSSFMV30}).
		Say("Hello").Generate(context.Background()); err == nil {
		t.Fatal("expected body read error")
	}

	client.httpClient.Transport = roundTripFunc(func(*http.Request) (*http.Response, error) {
		return nil, errors.New("network failed")
	})
	if _, err := client.ComposeSpeech().Defaults(ComposerSettings{VoiceID: "voice", Model: ModelSSFMV30}).
		Say("Hello").Generate(context.Background()); err == nil {
		t.Fatal("expected request error")
	}
}

type errorReader struct{}

func (errorReader) Read([]byte) (int, error) { return 0, errors.New("read failed") }
func (errorReader) Close() error             { return nil }
