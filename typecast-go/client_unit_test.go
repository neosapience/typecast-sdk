package typecast

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// helper to build a client pointed at the given test server
func newTestClient(server *httptest.Server, apiKey string) *Client {
	return NewClient(&ClientConfig{
		APIKey:  apiKey,
		BaseURL: server.URL,
	})
}

// ---------- NewClient ----------

func TestNewClient_NilConfig_UsesEnvDefaults(t *testing.T) {
	t.Setenv("TYPECAST_API_KEY", "env-key")
	t.Setenv("TYPECAST_API_HOST", "")

	c := NewClient(nil)
	if c.apiKey != "env-key" {
		t.Errorf("expected apiKey from env, got %q", c.apiKey)
	}
	if c.baseURL != DefaultBaseURL {
		t.Errorf("expected default base url, got %q", c.baseURL)
	}
	if c.httpClient.Timeout != DefaultTimeout {
		t.Errorf("expected default timeout, got %v", c.httpClient.Timeout)
	}
}

func TestNewClient_EnvBaseURL(t *testing.T) {
	t.Setenv("TYPECAST_API_KEY", "k")
	t.Setenv("TYPECAST_API_HOST", "https://example.test")

	c := NewClient(nil)
	if c.baseURL != "https://example.test" {
		t.Errorf("expected env base url, got %q", c.baseURL)
	}
}

func TestNewClient_ConfigOverrides(t *testing.T) {
	t.Setenv("TYPECAST_API_KEY", "env-key")
	t.Setenv("TYPECAST_API_HOST", "https://env.host")

	custom := &http.Client{Timeout: 5 * time.Second}
	c := NewClient(&ClientConfig{
		APIKey:     "cfg-key",
		BaseURL:    "https://cfg.host",
		HTTPClient: custom,
		Timeout:    10 * time.Second, // overridden by HTTPClient assignment
	})
	if c.apiKey != "cfg-key" {
		t.Errorf("expected cfg key, got %q", c.apiKey)
	}
	if c.baseURL != "https://cfg.host" {
		t.Errorf("expected cfg base url, got %q", c.baseURL)
	}
	if c.httpClient != custom {
		t.Error("expected custom http client to be used")
	}
}

func TestNewClient_TimeoutOnly(t *testing.T) {
	t.Setenv("TYPECAST_API_KEY", "")
	t.Setenv("TYPECAST_API_HOST", "")
	c := NewClient(&ClientConfig{
		APIKey:  "k",
		Timeout: 7 * time.Second,
	})
	if c.httpClient.Timeout != 7*time.Second {
		t.Errorf("expected 7s timeout, got %v", c.httpClient.Timeout)
	}
}

func TestNewClient_EmptyConfig(t *testing.T) {
	t.Setenv("TYPECAST_API_KEY", "")
	t.Setenv("TYPECAST_API_HOST", "")
	c := NewClient(&ClientConfig{})
	if c.baseURL != DefaultBaseURL {
		t.Errorf("expected default base url, got %q", c.baseURL)
	}
	if c.apiKey != "" {
		t.Errorf("expected empty api key, got %q", c.apiKey)
	}
}

func TestSetAuthHeader_DefaultHostRequiresAPIKey(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "   ", BaseURL: DefaultBaseURL + "/"})
	headers := http.Header{}
	err := c.setAuthHeader(headers)
	if err == nil || !strings.Contains(err.Error(), "API key is required") {
		t.Fatalf("expected missing api key error, got %v", err)
	}
}

func TestSetAuthHeader_ProxyHostAllowsMissingAPIKey(t *testing.T) {
	c := NewClient(&ClientConfig{BaseURL: "https://proxy.example"})
	headers := http.Header{}
	if err := c.setAuthHeader(headers); err != nil {
		t.Fatalf("expected proxy host to allow missing api key, got %v", err)
	}
	if headers.Get("X-API-KEY") != "" {
		t.Fatalf("expected no api key header, got %q", headers.Get("X-API-KEY"))
	}
}

func TestCloneVoice_DefaultHostWithoutAPIKeyReturnsError(t *testing.T) {
	c := NewClient(&ClientConfig{BaseURL: DefaultBaseURL})
	_, err := c.CloneVoice(context.Background(), []byte("audio"), "sample.wav", "voice", string(ModelSSFMV30))
	if err == nil || !strings.Contains(err.Error(), "API key is required") {
		t.Fatalf("expected missing api key error, got %v", err)
	}
}

func TestDeleteVoice_DefaultHostWithoutAPIKeyReturnsError(t *testing.T) {
	c := NewClient(&ClientConfig{BaseURL: DefaultBaseURL})
	err := c.DeleteVoice(context.Background(), "uc_test")
	if err == nil || !strings.Contains(err.Error(), "API key is required") {
		t.Fatalf("expected missing api key error, got %v", err)
	}
}

// ---------- TextToSpeech ----------

func TestTextToSpeech_NilRequest(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://x"})
	_, err := c.TextToSpeech(context.Background(), nil)
	if err == nil || !strings.Contains(err.Error(), "request cannot be nil") {
		t.Errorf("expected nil request error, got %v", err)
	}
}

func TestTextToSpeech_ValidationError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://x"})
	bad := 999
	_, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v",
		Text:    "hi",
		Model:   ModelSSFMV21,
		Output:  &Output{Volume: &bad},
	})
	if err == nil {
		t.Fatal("expected validation error")
	}
}

func TestTextToSpeech_DefaultHostWithoutAPIKeyReturnsError(t *testing.T) {
	c := NewClient(&ClientConfig{BaseURL: DefaultBaseURL})
	_, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v",
		Text:    "hi",
		Model:   ModelSSFMV30,
		Output:  &Output{},
	})
	if err == nil || !strings.Contains(err.Error(), "API key is required") {
		t.Fatalf("expected missing api key error, got %v", err)
	}
}

func TestTextToSpeech_HappyPathWAV(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/text-to-speech" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST")
		}
		if r.Header.Get("X-API-KEY") != "key" {
			t.Errorf("missing api key header")
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("missing content type")
		}
		var body TTSRequest
		_ = json.NewDecoder(r.Body).Decode(&body)
		w.Header().Set("Content-Type", "audio/wav")
		w.Header().Set("X-Audio-Duration", "1.23")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("WAVDATA"))
	}))
	defer srv.Close()

	c := newTestClient(srv, "key")
	vol := 100
	tempo := 1.0
	pitch := 2
	lufs := -16.0
	resp, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID:  "v1",
		Text:     "hello",
		Model:    ModelSSFMV21,
		Language: "eng",
		Prompt:   &Prompt{EmotionPreset: EmotionNormal},
		Output: &Output{
			Volume:      &vol,
			AudioPitch:  &pitch,
			AudioTempo:  &tempo,
			AudioFormat: AudioFormatWAV,
		},
	})
	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	if string(resp.AudioData) != "WAVDATA" {
		t.Errorf("unexpected audio data %s", resp.AudioData)
	}
	if resp.Format != AudioFormatWAV {
		t.Errorf("expected wav, got %s", resp.Format)
	}
	if resp.Duration != 1.23 {
		t.Errorf("expected duration 1.23, got %v", resp.Duration)
	}

	// Output with TargetLUFS instead of Volume
	resp2, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v1",
		Text:    "hi",
		Model:   ModelSSFMV21,
		Output:  &Output{TargetLUFS: &lufs},
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	_ = resp2
}

func TestTextToSpeech_MP3ContentType(t *testing.T) {
	for _, ct := range []string{"audio/mpeg", "audio/mp3"} {
		ct := ct
		t.Run(ct, func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Content-Type", ct)
				w.WriteHeader(http.StatusOK)
				w.Write([]byte("MP3"))
			}))
			defer srv.Close()
			c := newTestClient(srv, "k")
			resp, err := c.TextToSpeech(context.Background(), &TTSRequest{
				VoiceID: "v",
				Text:    "t",
				Model:   ModelSSFMV30,
				Prompt: &PresetPrompt{
					EmotionType:   "preset",
					EmotionPreset: EmotionHappy,
				},
				Output: &Output{AudioFormat: AudioFormatMP3},
			})
			if err != nil {
				t.Fatalf("err: %v", err)
			}
			if resp.Format != AudioFormatMP3 {
				t.Errorf("expected mp3, got %s", resp.Format)
			}
		})
	}
}

func TestTextToSpeech_SmartPrompt(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/wav")
		w.Write([]byte("X"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v",
		Text:    "t",
		Model:   ModelSSFMV30,
		Prompt:  &SmartPrompt{EmotionType: "smart", PreviousText: "p", NextText: "n"},
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
}

func TestTextToSpeech_NoDurationHeader(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/wav")
		w.Write([]byte("ok"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	resp, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v", Text: "t", Model: ModelSSFMV21,
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if resp.Duration != 0 {
		t.Errorf("expected zero duration, got %v", resp.Duration)
	}
}

func TestTextToSpeech_ErrorStatuses(t *testing.T) {
	for _, code := range []int{400, 401, 402, 403, 404, 422, 429, 500, 418} {
		code := code
		t.Run(http.StatusText(code), func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(code)
				json.NewEncoder(w).Encode(ErrorResponse{Detail: "boom"})
			}))
			defer srv.Close()
			c := newTestClient(srv, "k")
			_, err := c.TextToSpeech(context.Background(), &TTSRequest{
				VoiceID: "v", Text: "t", Model: ModelSSFMV21,
			})
			var apiErr *APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("expected APIError, got %T", err)
			}
			if apiErr.StatusCode != code {
				t.Errorf("expected status %d, got %d", code, apiErr.StatusCode)
			}
			if apiErr.Detail != "boom" {
				t.Errorf("expected detail boom, got %q", apiErr.Detail)
			}
		})
	}
}

func TestTextToSpeech_MalformedErrorBody(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadRequest)
		w.Write([]byte("not json"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v", Text: "t", Model: ModelSSFMV21,
	})
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected APIError, got %T", err)
	}
	if apiErr.StatusCode != 400 {
		t.Errorf("expected 400, got %d", apiErr.StatusCode)
	}
	if apiErr.Detail != "" {
		t.Errorf("expected empty detail, got %q", apiErr.Detail)
	}
}

// errReader always fails reads.
type errReader struct{}

func (errReader) Read(p []byte) (int, error) { return 0, errors.New("read boom") }
func (errReader) Close() error                { return nil }

// roundTripperFunc lets us inject a custom response.
type roundTripperFunc func(*http.Request) (*http.Response, error)

func (f roundTripperFunc) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

func TestTextToSpeech_ReadBodyError(t *testing.T) {
	rt := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: http.StatusOK,
			Header:     http.Header{"Content-Type": []string{"audio/wav"}},
			Body:       errReader{},
		}, nil
	})
	c := NewClient(&ClientConfig{
		APIKey:     "k",
		BaseURL:    "http://example.invalid",
		HTTPClient: &http.Client{Transport: rt},
	})
	_, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v", Text: "t", Model: ModelSSFMV21,
	})
	if err == nil || !strings.Contains(err.Error(), "failed to read audio data") {
		t.Fatalf("expected read error, got %v", err)
	}
}

func TestTextToSpeech_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://127.0.0.1:1"})
	// invalid url path with control char to fail NewRequestWithContext
	c.baseURL = "http://[::1]:bad"
	_, err := c.TextToSpeech(context.Background(), &TTSRequest{
		VoiceID: "v", Text: "t", Model: ModelSSFMV21,
	})
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- GetVoicesV2 ----------

func TestGetVoicesV2_NoFilter(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v2/voices" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.URL.RawQuery != "" {
			t.Errorf("expected no query, got %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV2{{VoiceID: "v1", VoiceName: "Voice 1"}})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	voices, err := c.GetVoicesV2(context.Background(), nil)
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if len(voices) != 1 || voices[0].VoiceID != "v1" {
		t.Errorf("unexpected voices: %+v", voices)
	}
}

func TestGetVoicesV2_AllFilters(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		if q.Get("model") != string(ModelSSFMV30) ||
			q.Get("gender") != string(GenderFemale) ||
			q.Get("age") != string(AgeYoungAdult) ||
			q.Get("use_cases") != string(UseCaseAudiobook) {
			t.Errorf("unexpected query: %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV2{})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoicesV2(context.Background(), &VoicesV2Filter{
		Model:    ModelSSFMV30,
		Gender:   GenderFemale,
		Age:      AgeYoungAdult,
		UseCases: UseCaseAudiobook,
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
}

func TestGetVoicesV2_EmptyFilter(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.RawQuery != "" {
			t.Errorf("expected no query, got %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV2{})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoicesV2(context.Background(), &VoicesV2Filter{})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
}

func TestGetVoicesV2_Error(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		json.NewEncoder(w).Encode(ErrorResponse{Detail: "oops"})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoicesV2(context.Background(), nil)
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.StatusCode != 500 {
		t.Fatalf("expected 500 APIError, got %v", err)
	}
}

func TestGetVoicesV2_DecodeError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("not json"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoicesV2(context.Background(), nil)
	if err == nil {
		t.Fatal("expected decode error")
	}
}

func TestGetVoicesV2_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://[::1]:bad"})
	_, err := c.GetVoicesV2(context.Background(), nil)
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- GetVoiceV2 ----------

func TestGetVoiceV2_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v2/voices/abc" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		json.NewEncoder(w).Encode(VoiceV2{VoiceID: "abc", VoiceName: "A"})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	v, err := c.GetVoiceV2(context.Background(), "abc")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if v.VoiceID != "abc" {
		t.Errorf("unexpected: %+v", v)
	}
}

func TestGetVoiceV2_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(ErrorResponse{Detail: "no"})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoiceV2(context.Background(), "x")
	var apiErr *APIError
	if !errors.As(err, &apiErr) || !apiErr.IsNotFound() {
		t.Fatalf("expected 404, got %v", err)
	}
}

func TestGetVoiceV2_DecodeError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("not json"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoiceV2(context.Background(), "x")
	if err == nil {
		t.Fatal("expected decode error")
	}
}

func TestGetVoiceV2_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://[::1]:bad"})
	_, err := c.GetVoiceV2(context.Background(), "x")
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- GetVoices (V1) ----------

func TestGetVoices_NoModel(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/voices" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.URL.RawQuery != "" {
			t.Errorf("expected no query, got %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV1{{VoiceID: "v1"}})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	voices, err := c.GetVoices(context.Background(), "")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if len(voices) != 1 {
		t.Errorf("unexpected: %+v", voices)
	}
}

func TestGetVoices_WithModel(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("model") != string(ModelSSFMV21) {
			t.Errorf("unexpected query %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV1{})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoices(context.Background(), ModelSSFMV21)
	if err != nil {
		t.Fatalf("err: %v", err)
	}
}

func TestGetVoices_Error(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		json.NewEncoder(w).Encode(ErrorResponse{Detail: "no"})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoices(context.Background(), "")
	var apiErr *APIError
	if !errors.As(err, &apiErr) || !apiErr.IsUnauthorized() {
		t.Fatalf("expected 401, got %v", err)
	}
}

func TestGetVoices_DecodeError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("not json"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoices(context.Background(), "")
	if err == nil {
		t.Fatal("expected decode error")
	}
}

func TestGetVoices_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://[::1]:bad"})
	_, err := c.GetVoices(context.Background(), "")
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- GetVoice (V1) ----------

func TestGetVoice_NoModel(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/voices/abc" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.URL.RawQuery != "" {
			t.Errorf("expected no query, got %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV1{{VoiceID: "abc"}})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	voices, err := c.GetVoice(context.Background(), "abc", "")
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if len(voices) != 1 || voices[0].VoiceID != "abc" {
		t.Errorf("unexpected: %+v", voices)
	}
}

func TestGetVoice_WithModel(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("model") != string(ModelSSFMV30) {
			t.Errorf("unexpected query %s", r.URL.RawQuery)
		}
		json.NewEncoder(w).Encode([]VoiceV1{})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoice(context.Background(), "x", ModelSSFMV30)
	if err != nil {
		t.Fatalf("err: %v", err)
	}
}

func TestGetVoice_NotFound(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(ErrorResponse{Detail: "no"})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoice(context.Background(), "x", "")
	var apiErr *APIError
	if !errors.As(err, &apiErr) || !apiErr.IsNotFound() {
		t.Fatalf("expected 404, got %v", err)
	}
}

func TestGetVoice_DecodeError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("not json"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetVoice(context.Background(), "x", "")
	if err == nil {
		t.Fatal("expected decode error")
	}
}

func TestGetVoice_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://[::1]:bad"})
	_, err := c.GetVoice(context.Background(), "x", "")
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- GetMySubscription ----------

func TestGetMySubscription_Success(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/users/me/subscription" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.Method != http.MethodGet {
			t.Errorf("expected GET")
		}
		if r.Header.Get("X-API-KEY") != "k" {
			t.Errorf("missing api key header")
		}
		json.NewEncoder(w).Encode(SubscriptionResponse{
			Plan:    PlanTierPlus,
			Credits: Credits{PlanCredits: 1000, UsedCredits: 250},
			Limits:  Limits{ConcurrencyLimit: 5},
		})
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	sub, err := c.GetMySubscription(context.Background())
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	if sub.Plan != PlanTierPlus {
		t.Errorf("expected plus, got %s", sub.Plan)
	}
	if sub.Credits.PlanCredits != 1000 || sub.Credits.UsedCredits != 250 {
		t.Errorf("unexpected credits: %+v", sub.Credits)
	}
	if sub.Limits.ConcurrencyLimit != 5 {
		t.Errorf("unexpected limits: %+v", sub.Limits)
	}
}

func TestGetMySubscription_ErrorStatuses(t *testing.T) {
	cases := []struct {
		code int
		pred func(*APIError) bool
	}{
		{401, (*APIError).IsUnauthorized},
		{429, (*APIError).IsRateLimited},
		{500, (*APIError).IsServerError},
	}
	for _, tc := range cases {
		tc := tc
		t.Run(http.StatusText(tc.code), func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.code)
				json.NewEncoder(w).Encode(ErrorResponse{Detail: "boom"})
			}))
			defer srv.Close()
			c := newTestClient(srv, "k")
			_, err := c.GetMySubscription(context.Background())
			var apiErr *APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("expected APIError, got %T", err)
			}
			if apiErr.StatusCode != tc.code {
				t.Errorf("expected status %d, got %d", tc.code, apiErr.StatusCode)
			}
			if !tc.pred(apiErr) {
				t.Errorf("predicate failed for code %d", tc.code)
			}
		})
	}
}

func TestGetMySubscription_DecodeError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("not json"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	_, err := c.GetMySubscription(context.Background())
	if err == nil {
		t.Fatal("expected decode error")
	}
}

func TestGetMySubscription_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://[::1]:bad"})
	_, err := c.GetMySubscription(context.Background())
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- doRequest body marshal failure ----------

type badBody struct{}

func (badBody) MarshalJSON() ([]byte, error) {
	return nil, errors.New("nope")
}

func TestDoRequest_MarshalError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://x"})
	_, err := c.doRequest(context.Background(), http.MethodPost, "/p", badBody{})
	if err == nil || !strings.Contains(err.Error(), "marshal") {
		t.Fatalf("expected marshal error, got %v", err)
	}
}

// ---------- errors.go ----------

func TestNewAPIError_Messages(t *testing.T) {
	cases := map[int]string{
		400: "Bad Request",
		401: "Unauthorized",
		402: "Payment Required",
		403: "Forbidden",
		404: "Not Found",
		422: "Validation Error",
		429: "Too Many Requests",
		500: "Internal Server Error",
		418: "status 418",
	}
	for code, want := range cases {
		err := NewAPIError(code, "")
		if !strings.Contains(err.Message, want) {
			t.Errorf("for %d expected message to contain %q, got %q", code, want, err.Message)
		}
		if err.StatusCode != code {
			t.Errorf("status mismatch")
		}
	}
}

func TestAPIError_ErrorString(t *testing.T) {
	e := NewAPIError(400, "")
	if !strings.Contains(e.Error(), "Bad Request") {
		t.Errorf("expected message only, got %q", e.Error())
	}
	e = NewAPIError(400, "details")
	if !strings.Contains(e.Error(), "details") {
		t.Errorf("expected detail in error, got %q", e.Error())
	}
}

func TestAPIError_Predicates(t *testing.T) {
	checks := []struct {
		code int
		fn   func(*APIError) bool
		name string
	}{
		{400, (*APIError).IsBadRequest, "BadRequest"},
		{401, (*APIError).IsUnauthorized, "Unauthorized"},
		{402, (*APIError).IsPaymentRequired, "PaymentRequired"},
		{403, (*APIError).IsForbidden, "Forbidden"},
		{404, (*APIError).IsNotFound, "NotFound"},
		{422, (*APIError).IsValidationError, "ValidationError"},
		{429, (*APIError).IsRateLimited, "RateLimited"},
		{500, (*APIError).IsServerError, "ServerError"},
		{503, (*APIError).IsServerError, "ServerError-503"},
	}
	for _, c := range checks {
		e := NewAPIError(c.code, "")
		if !c.fn(e) {
			t.Errorf("%s expected true for code %d", c.name, c.code)
		}
	}
	// Negative cases - non-matching status returns false
	e := NewAPIError(200, "")
	if e.IsBadRequest() || e.IsUnauthorized() || e.IsPaymentRequired() || e.IsForbidden() ||
		e.IsNotFound() || e.IsValidationError() || e.IsRateLimited() || e.IsServerError() {
		t.Error("predicates should be false for 200")
	}
}

// ---------- TextToSpeechStream ----------

func TestTextToSpeechStream_ValidationError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://x"})
	bad := 99
	_, err := c.TextToSpeechStream(context.Background(), TTSRequestStream{
		VoiceID: "v",
		Text:    "hi",
		Model:   ModelSSFMV21,
		Output:  &OutputStream{AudioPitch: &bad},
	})
	if err == nil {
		t.Fatal("expected validation error")
	}
}

func TestTextToSpeechStream_HappyPath(t *testing.T) {
	// WAV header bytes ("RIFF....WAVE") + a couple PCM samples.
	wavHeader := []byte{
		'R', 'I', 'F', 'F', 0x24, 0x00, 0x00, 0x00,
		'W', 'A', 'V', 'E', 'f', 'm', 't', ' ',
	}
	pcm := []byte{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08}
	wantBody := append(append([]byte{}, wavHeader...), pcm...)

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/text-to-speech/stream" {
			t.Errorf("unexpected path %s", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("expected POST")
		}
		if r.Header.Get("X-API-KEY") != "key" {
			t.Errorf("missing api key header")
		}
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("missing content type")
		}
		var body TTSRequestStream
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Errorf("failed to decode request: %v", err)
		}
		if body.VoiceID != "v1" || body.Text != "hello" || body.Model != ModelSSFMV21 {
			t.Errorf("unexpected body %+v", body)
		}
		if body.Output == nil || body.Output.AudioFormat != AudioFormatWAV {
			t.Errorf("unexpected output %+v", body.Output)
		}
		w.Header().Set("Content-Type", "audio/wav")
		w.WriteHeader(http.StatusOK)
		// Write in two chunks to exercise streaming reads.
		w.Write(wavHeader)
		if f, ok := w.(http.Flusher); ok {
			f.Flush()
		}
		w.Write(pcm)
	}))
	defer srv.Close()

	c := newTestClient(srv, "key")
	pitch := 1
	tempo := 1.0
	rc, err := c.TextToSpeechStream(context.Background(), TTSRequestStream{
		VoiceID:  "v1",
		Text:     "hello",
		Model:    ModelSSFMV21,
		Language: "eng",
		Prompt:   &Prompt{EmotionPreset: EmotionNormal},
		Output: &OutputStream{
			AudioPitch:  &pitch,
			AudioTempo:  &tempo,
			AudioFormat: AudioFormatWAV,
		},
	})
	if err != nil {
		t.Fatalf("unexpected err: %v", err)
	}
	defer rc.Close()
	got, err := io.ReadAll(rc)
	if err != nil {
		t.Fatalf("read all: %v", err)
	}
	if !bytes.Equal(got, wantBody) {
		t.Errorf("unexpected body bytes: got %x want %x", got, wantBody)
	}
}

func TestTextToSpeechStream_NilOutputAllowed(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/wav")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	}))
	defer srv.Close()
	c := newTestClient(srv, "k")
	rc, err := c.TextToSpeechStream(context.Background(), TTSRequestStream{
		VoiceID: "v", Text: "t", Model: ModelSSFMV21,
	})
	if err != nil {
		t.Fatalf("err: %v", err)
	}
	defer rc.Close()
	b, _ := io.ReadAll(rc)
	if string(b) != "OK" {
		t.Errorf("unexpected body %q", b)
	}
}

// trackingBody records whether Close was called so the test can verify the
// streaming method closes the body on error paths.
type trackingBody struct {
	io.Reader
	closed bool
}

func (t *trackingBody) Close() error {
	t.closed = true
	return nil
}

func TestTextToSpeechStream_ErrorStatuses(t *testing.T) {
	for _, code := range []int{400, 401, 402, 404, 422, 429, 500} {
		code := code
		t.Run(http.StatusText(code), func(t *testing.T) {
			body := &trackingBody{Reader: bytes.NewReader([]byte(`{"detail":"boom"}`))}
			rt := roundTripperFunc(func(r *http.Request) (*http.Response, error) {
				return &http.Response{
					StatusCode: code,
					Header:     http.Header{"Content-Type": []string{"application/json"}},
					Body:       body,
				}, nil
			})
			c := NewClient(&ClientConfig{
				APIKey:     "k",
				BaseURL:    "http://example.invalid",
				HTTPClient: &http.Client{Transport: rt},
			})
			_, err := c.TextToSpeechStream(context.Background(), TTSRequestStream{
				VoiceID: "v", Text: "t", Model: ModelSSFMV21,
			})
			var apiErr *APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("expected APIError, got %T", err)
			}
			if apiErr.StatusCode != code {
				t.Errorf("expected status %d, got %d", code, apiErr.StatusCode)
			}
			if apiErr.Detail != "boom" {
				t.Errorf("expected detail boom, got %q", apiErr.Detail)
			}
			if !body.closed {
				t.Error("expected response body to be closed on error")
			}
		})
	}
}

func TestTextToSpeechStream_RequestError(t *testing.T) {
	c := NewClient(&ClientConfig{APIKey: "k", BaseURL: "http://[::1]:bad"})
	_, err := c.TextToSpeechStream(context.Background(), TTSRequestStream{
		VoiceID: "v", Text: "t", Model: ModelSSFMV21,
	})
	if err == nil {
		t.Fatal("expected request error")
	}
}

// ---------- OutputStream.Validate ----------

func TestTTSRequestStream_Validate(t *testing.T) {
	// missing voice_id
	r := TTSRequestStream{Text: "hi", Model: ModelSSFMV30}
	if err := r.Validate(); err == nil {
		t.Error("expected voice_id required error")
	}
	// missing text
	r = TTSRequestStream{VoiceID: "v", Model: ModelSSFMV30}
	if err := r.Validate(); err == nil {
		t.Error("expected text required error")
	}
	// text too long
	r = TTSRequestStream{VoiceID: "v", Text: string(make([]byte, 2001)), Model: ModelSSFMV30}
	if err := r.Validate(); err == nil {
		t.Error("expected text length error")
	}
	// missing model
	r = TTSRequestStream{VoiceID: "v", Text: "hi"}
	if err := r.Validate(); err == nil {
		t.Error("expected model required error")
	}
	// valid minimal
	r = TTSRequestStream{VoiceID: "v", Text: "hi", Model: ModelSSFMV30}
	if err := r.Validate(); err != nil {
		t.Errorf("expected valid, got %v", err)
	}
	// delegates to OutputStream.Validate
	bad := 99
	r = TTSRequestStream{VoiceID: "v", Text: "hi", Model: ModelSSFMV30, Output: &OutputStream{AudioPitch: &bad}}
	if err := r.Validate(); err == nil {
		t.Error("expected output validation error")
	}
}

func TestOutputStream_Validate(t *testing.T) {
	// nil
	var o *OutputStream
	if err := o.Validate(); err != nil {
		t.Errorf("nil should be valid, got %v", err)
	}
	// pitch out of range
	badP := -13
	if err := (&OutputStream{AudioPitch: &badP}).Validate(); err == nil {
		t.Error("expected pitch range error")
	}
	highP := 13
	if err := (&OutputStream{AudioPitch: &highP}).Validate(); err == nil {
		t.Error("expected pitch range error")
	}
	// tempo out of range
	badT := 0.4
	if err := (&OutputStream{AudioTempo: &badT}).Validate(); err == nil {
		t.Error("expected tempo range error")
	}
	highT := 2.1
	if err := (&OutputStream{AudioTempo: &highT}).Validate(); err == nil {
		t.Error("expected tempo range error")
	}
	// invalid format
	if err := (&OutputStream{AudioFormat: AudioFormat("flac")}).Validate(); err == nil {
		t.Error("expected format error")
	}
	// all valid
	t2 := 1.0
	p2 := 0
	if err := (&OutputStream{AudioTempo: &t2, AudioPitch: &p2, AudioFormat: AudioFormatMP3}).Validate(); err != nil {
		t.Errorf("expected valid, got %v", err)
	}
}

// ---------- Output.Validate ----------

func TestOutput_Validate(t *testing.T) {
	// nil
	var o *Output
	if err := o.Validate(); err != nil {
		t.Errorf("nil should be valid, got %v", err)
	}
	// volume + lufs mutually exclusive
	v := 50
	l := -10.0
	if err := (&Output{Volume: &v, TargetLUFS: &l}).Validate(); err == nil {
		t.Error("expected mutually exclusive error")
	}
	// volume out of range
	bad := -1
	if err := (&Output{Volume: &bad}).Validate(); err == nil {
		t.Error("expected volume range error")
	}
	high := 201
	if err := (&Output{Volume: &high}).Validate(); err == nil {
		t.Error("expected volume range error")
	}
	// lufs out of range
	badL := -71.0
	if err := (&Output{TargetLUFS: &badL}).Validate(); err == nil {
		t.Error("expected lufs range error")
	}
	highL := 1.0
	if err := (&Output{TargetLUFS: &highL}).Validate(); err == nil {
		t.Error("expected lufs range error")
	}
	// pitch out of range
	badP := -13
	if err := (&Output{AudioPitch: &badP}).Validate(); err == nil {
		t.Error("expected pitch range error")
	}
	highP := 13
	if err := (&Output{AudioPitch: &highP}).Validate(); err == nil {
		t.Error("expected pitch range error")
	}
	// tempo out of range
	badT := 0.4
	if err := (&Output{AudioTempo: &badT}).Validate(); err == nil {
		t.Error("expected tempo range error")
	}
	highT := 2.1
	if err := (&Output{AudioTempo: &highT}).Validate(); err == nil {
		t.Error("expected tempo range error")
	}
	// invalid format
	if err := (&Output{AudioFormat: AudioFormat("flac")}).Validate(); err == nil {
		t.Error("expected format error")
	}
	// all valid
	v2 := 100
	t2 := 1.0
	p2 := 0
	if err := (&Output{Volume: &v2, AudioTempo: &t2, AudioPitch: &p2, AudioFormat: AudioFormatMP3}).Validate(); err != nil {
		t.Errorf("expected valid, got %v", err)
	}
}
