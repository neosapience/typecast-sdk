package typecast

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func loadFixture(t *testing.T, name string) []byte {
	t.Helper()
	repoRoot := repoRootFor(t)
	path := filepath.Join(repoRoot, "test-fixtures", "with-timestamps", name)
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read fixture %s: %v", name, err)
	}
	return b
}

func loadExpected(t *testing.T, name string) string {
	t.Helper()
	repoRoot := repoRootFor(t)
	path := filepath.Join(repoRoot, "test-fixtures", "with-timestamps", "expected", name)
	b, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read expected %s: %v", name, err)
	}
	return string(b)
}

// repoRootFor walks up from the test file location until it finds the
// test-fixtures directory.
func repoRootFor(t *testing.T) string {
	t.Helper()
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("getwd: %v", err)
	}
	dir := cwd
	for i := 0; i < 6; i++ {
		if _, err := os.Stat(filepath.Join(dir, "test-fixtures", "with-timestamps")); err == nil {
			return dir
		}
		dir = filepath.Dir(dir)
	}
	t.Fatalf("test-fixtures/with-timestamps not found from %s", cwd)
	return ""
}

func mustParseResp(t *testing.T, fixtureName string) *TTSWithTimestampsResponse {
	t.Helper()
	var resp TTSWithTimestampsResponse
	if err := json.Unmarshal(loadFixture(t, fixtureName), &resp); err != nil {
		t.Fatalf("unmarshal %s: %v", fixtureName, err)
	}
	return &resp
}

func TestToSRT_BothFixture(t *testing.T) {
	resp := mustParseResp(t, "both.json")
	got, err := resp.ToSRT()
	if err != nil {
		t.Fatalf("ToSRT: %v", err)
	}
	want := loadExpected(t, "both.srt")
	if got != want {
		t.Errorf("byte mismatch\n--- want ---\n%s\n--- got ---\n%s", want, got)
	}
}

func TestToSRT_AllFixtures(t *testing.T) {
	cases := []string{"both", "word_only", "char_only", "jpn_char"}
	for _, name := range cases {
		t.Run(name, func(t *testing.T) {
			resp := mustParseResp(t, name+".json")
			got, err := resp.ToSRT()
			if err != nil {
				t.Fatalf("ToSRT(%s): %v", name, err)
			}
			want := loadExpected(t, name+".srt")
			if got != want {
				t.Errorf("byte mismatch for %s\n--- want ---\n%q\n--- got ---\n%q", name, want, got)
			}
		})
	}
}

func TestToVTT_AllFixtures(t *testing.T) {
	cases := []string{"both", "word_only", "char_only", "jpn_char"}
	for _, name := range cases {
		t.Run(name, func(t *testing.T) {
			resp := mustParseResp(t, name+".json")
			got, err := resp.ToVTT()
			if err != nil {
				t.Fatalf("ToVTT(%s): %v", name, err)
			}
			want := loadExpected(t, name+".vtt")
			if got != want {
				t.Errorf("byte mismatch for %s\n--- want ---\n%q\n--- got ---\n%q", name, want, got)
			}
		})
	}
}

func TestAudioBytes(t *testing.T) {
	resp := mustParseResp(t, "both.json")
	b, err := resp.AudioBytes()
	if err != nil {
		t.Fatalf("AudioBytes: %v", err)
	}
	if len(b) == 0 {
		t.Fatal("expected non-empty bytes")
	}
	if resp.AudioFormat == AudioFormatWAV {
		if len(b) < 4 || string(b[:4]) != "RIFF" {
			t.Errorf("expected RIFF header, got %q (len=%d)", string(b[:min(4, len(b))]), len(b))
		}
	}
}

func TestSaveAudio(t *testing.T) {
	resp := mustParseResp(t, "both.json")
	dir := t.TempDir()
	path := filepath.Join(dir, "out.wav")
	if err := resp.SaveAudio(path); err != nil {
		t.Fatalf("SaveAudio: %v", err)
	}
	stat, err := os.Stat(path)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	if stat.Size() == 0 {
		t.Fatal("expected non-empty file")
	}
}

func TestToSRT_NoAlignmentSegments(t *testing.T) {
	resp := &TTSWithTimestampsResponse{
		Audio:         "UklGRgAAAA==",
		AudioFormat:   AudioFormatWAV,
		AudioDuration: 0,
	}
	if _, err := resp.ToSRT(); err == nil {
		t.Fatal("expected error for missing arrays")
	}
}

func TestTextToSpeechWithTimestamps_NoGranularity(t *testing.T) {
	fixture := loadFixture(t, "both.json")
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/text-to-speech/with-timestamps" {
			t.Errorf("unexpected path %q", r.URL.Path)
		}
		if got := r.URL.Query().Get("granularity"); got != "" {
			t.Errorf("expected no granularity param, got %q", got)
		}
		if got := r.Header.Get("X-API-KEY"); got != "k" {
			t.Errorf("expected api key header, got %q", got)
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write(fixture)
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
	resp, err := c.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err != nil {
		t.Fatalf("call failed: %v", err)
	}
	if resp.AudioFormat != AudioFormatWAV {
		t.Errorf("expected wav, got %s", resp.AudioFormat)
	}
}

func TestTextToSpeechWithTimestamps_Granularity(t *testing.T) {
	for _, granularity := range []string{"word", "char"} {
		granularity := granularity // capture loop variable (Go < 1.22)
		t.Run(granularity, func(t *testing.T) {
			fixture := loadFixture(t, "word_only.json")
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if got := r.URL.Query().Get("granularity"); got != granularity {
					t.Errorf("expected granularity=%s, got %q", granularity, got)
				}
				w.Header().Set("Content-Type", "application/json")
				w.Write(fixture)
			}))
			defer server.Close()

			c := newTestClient(server, "k")
			req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
			if _, err := c.TextToSpeechWithTimestamps(context.Background(), req, granularity); err != nil {
				t.Fatalf("call failed: %v", err)
			}
		})
	}
}

func TestTextToSpeechWithTimestamps_RejectsInvalidGranularity(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("server should not be called")
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
	_, err := c.TextToSpeechWithTimestamps(context.Background(), req, "words")
	if err == nil {
		t.Fatal("expected validation error")
	}
	if !strings.Contains(err.Error(), "granularity") {
		t.Errorf("expected granularity in error, got %v", err)
	}
}

func TestTextToSpeechWithTimestamps_402Error(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusPaymentRequired)
		w.Write([]byte(`{"detail":"Insufficient credit"}`))
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
	_, err := c.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err == nil {
		t.Fatal("expected error on 402")
	}
}

func TestTextToSpeechWithTimestamps_NilRequest(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("server should not be called for nil request")
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	_, err := c.TextToSpeechWithTimestamps(context.Background(), nil, "")
	if err == nil {
		t.Fatal("expected error for nil request")
	}
	if !strings.Contains(err.Error(), "nil") {
		t.Errorf("expected 'nil' in error message, got: %v", err)
	}
}

func TestTextToSpeechWithTimestamps_5xxError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"detail":"boom"}`))
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
	_, err := c.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err == nil {
		t.Fatal("expected error on 500")
	}
}

func TestTextToSpeechWithTimestamps_BadJSON(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`not-valid-json`))
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
	_, err := c.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err == nil {
		t.Fatal("expected decode error for invalid JSON")
	}
}

// --- Validate() coverage ---

func TestValidate_NilRequest(t *testing.T) {
	var r *TTSRequestWithTimestamps
	err := r.Validate()
	if err == nil {
		t.Fatal("expected error for nil receiver")
	}
}

func TestValidate_MissingModel(t *testing.T) {
	r := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "hi"}
	if err := r.Validate(); err == nil {
		t.Fatal("expected error for missing model")
	}
}

func TestValidate_InvalidOutput(t *testing.T) {
	vol := 300 // out of range 0-200
	r := &TTSRequestWithTimestamps{
		VoiceID: "tc_x",
		Text:    "hi",
		Model:   ModelSSFMV30,
		Output:  &Output{Volume: &vol},
	}
	if err := r.Validate(); err == nil {
		t.Fatal("expected error for invalid output volume")
	}
}

func TestValidate_EmptyText(t *testing.T) {
	r := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "", Model: ModelSSFMV30}
	if err := r.Validate(); err == nil {
		t.Fatal("expected error for empty text")
	}
}

func TestTextToSpeechWithTimestamps_ValidationError(t *testing.T) {
	// Request is non-nil but fails Validate() (empty VoiceID)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("server should not be called when validation fails")
	}))
	defer server.Close()

	c := newTestClient(server, "k")
	req := &TTSRequestWithTimestamps{VoiceID: "", Text: "Hi", Model: ModelSSFMV30}
	_, err := c.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err == nil {
		t.Fatal("expected validation error")
	}
}

func TestTextToSpeechWithTimestamps_NetworkError(t *testing.T) {
	// Use a closed server to trigger a network-level error
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	url := server.URL
	server.Close() // Close before request

	c := newTestClient(server, "k")
	// Override the base URL to point to the closed server
	c = NewClient(&ClientConfig{APIKey: "k", BaseURL: url})
	req := &TTSRequestWithTimestamps{VoiceID: "tc_x", Text: "Hi", Model: ModelSSFMV30}
	_, err := c.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err == nil {
		t.Fatal("expected network error")
	}
}

// --- SaveAudio error path ---

func TestSaveAudio_BadBase64(t *testing.T) {
	resp := &TTSWithTimestampsResponse{
		Audio:       "!!!not-valid-base64!!!",
		AudioFormat: AudioFormatWAV,
	}
	if err := resp.SaveAudio(t.TempDir() + "/out.wav"); err == nil {
		t.Fatal("expected error for invalid base64")
	}
}

// --- ToSRT / ToVTT error paths ---

func TestToVTT_NoAlignmentSegments(t *testing.T) {
	resp := &TTSWithTimestampsResponse{
		Audio:         "UklGRgAAAA==",
		AudioFormat:   AudioFormatWAV,
		AudioDuration: 0,
	}
	if _, err := resp.ToVTT(); err == nil {
		t.Fatal("expected error for missing arrays")
	}
}

// --- pickSegments: single-word fallback ---

func TestPickSegments_SingleWordFallback(t *testing.T) {
	resp := &TTSWithTimestampsResponse{
		Audio:       "AAAA",
		AudioFormat: AudioFormatWAV,
		Words:       []AlignmentSegmentWord{{Text: "Hello.", Start: 0.0, End: 0.5}},
	}
	srt, err := resp.ToSRT()
	if err != nil {
		t.Fatalf("ToSRT with single word: %v", err)
	}
	if !strings.Contains(srt, "Hello.") {
		t.Errorf("expected 'Hello.' in SRT, got: %s", srt)
	}
}

// --- groupIntoCues: hard-cap flush (time-based) ---

func TestGroupIntoCues_HardCapByTime(t *testing.T) {
	// Segments spanning > 7.0s in total — should produce >= 2 cues
	resp := &TTSWithTimestampsResponse{
		Audio:       "AAAA",
		AudioFormat: AudioFormatWAV,
		Words: []AlignmentSegmentWord{
			{Text: "Word1", Start: 0.0, End: 2.0},
			{Text: "Word2", Start: 2.0, End: 4.0},
			{Text: "Word3", Start: 4.0, End: 6.0},
			// Adding Word4 at end=8.0 makes span 8.0 > maxCaptionSeconds=7.0
			{Text: "Word4", Start: 6.0, End: 8.0},
		},
	}
	srt, err := resp.ToSRT()
	if err != nil {
		t.Fatalf("ToSRT hard-cap: %v", err)
	}
	// With a hard-cap flush, should produce cue index "2"
	if !strings.Contains(srt, "2\n") {
		t.Errorf("expected at least 2 cues (time hard-cap), got:\n%s", srt)
	}
}

// --- groupIntoCues: hard-cap flush (char-based) ---

func TestGroupIntoCues_HardCapByChars(t *testing.T) {
	// 4 * "AAAAAAAAAA" joined with spaces = 43 chars > 42 limit
	resp := &TTSWithTimestampsResponse{
		Audio:       "AAAA",
		AudioFormat: AudioFormatWAV,
		Words: []AlignmentSegmentWord{
			{Text: "AAAAAAAAAA", Start: 0.0, End: 0.5},
			{Text: "AAAAAAAAAA", Start: 0.5, End: 1.0},
			{Text: "AAAAAAAAAA", Start: 1.0, End: 1.5},
			{Text: "AAAAAAAAAA", Start: 1.5, End: 2.0},
		},
	}
	srt, err := resp.ToSRT()
	if err != nil {
		t.Fatalf("ToSRT char hard-cap: %v", err)
	}
	if !strings.Contains(srt, "2\n") {
		t.Errorf("expected at least 2 cues (char hard-cap), got:\n%s", srt)
	}
}

// --- ToSRT / ToVTT: empty cues after grouping ---

func TestToSRT_AllEmptyTextSegments(t *testing.T) {
	// All word text is empty; groupIntoCues produces zero non-empty cues
	resp := &TTSWithTimestampsResponse{
		Audio:       "AAAA",
		AudioFormat: AudioFormatWAV,
		Words:       []AlignmentSegmentWord{{Text: "", Start: 0.0, End: 0.5}, {Text: "", Start: 0.5, End: 1.0}},
	}
	_, err := resp.ToSRT()
	if err == nil {
		t.Fatal("expected error when all segments produce empty text")
	}
}

func TestToVTT_AllEmptyTextSegments(t *testing.T) {
	resp := &TTSWithTimestampsResponse{
		Audio:       "AAAA",
		AudioFormat: AudioFormatWAV,
		Words:       []AlignmentSegmentWord{{Text: "", Start: 0.0, End: 0.5}, {Text: "", Start: 0.5, End: 1.0}},
	}
	_, err := resp.ToVTT()
	if err == nil {
		t.Fatal("expected error when all segments produce empty text")
	}
}
