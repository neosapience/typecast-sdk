package typecast

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// TestCloneVoiceReturnsCustomVoice checks that a 200 response with voice JSON
// is decoded correctly into a *CustomVoice.
func TestCloneVoiceReturnsCustomVoice(t *testing.T) {
	want := CustomVoice{
		VoiceID: "uc_abc123",
		Name:    "MyVoice",
		Model:   "ssfm-v30",
	}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(want)
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	got, err := c.CloneVoice(context.Background(), make([]byte, 100), "sample.wav", "MyVoice", "ssfm-v30")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got.VoiceID != want.VoiceID {
		t.Errorf("VoiceID: got %q, want %q", got.VoiceID, want.VoiceID)
	}
	if got.Name != want.Name {
		t.Errorf("Name: got %q, want %q", got.Name, want.Name)
	}
	if got.Model != want.Model {
		t.Errorf("Model: got %q, want %q", got.Model, want.Model)
	}
}

// TestCloneVoiceSendsMultipartBody verifies the HTTP request shape:
// correct URL, POST method, multipart/form-data Content-Type with boundary,
// and body parts named "name", "model", "file" with correct values.
func TestCloneVoiceSendsMultipartBody(t *testing.T) {
	audioData := []byte("fakeaudiobytes")
	const voiceName = "TestVoice"
	const voiceModel = "ssfm-v21"
	const filename = "sample.wav"

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Check URL and method
		if !strings.HasSuffix(r.URL.Path, "/v1/voices/clone") {
			t.Errorf("URL path: got %q, want to end with /v1/voices/clone", r.URL.Path)
		}
		if r.Method != http.MethodPost {
			t.Errorf("method: got %q, want POST", r.Method)
		}

		// Check Content-Type starts with multipart/form-data; boundary=
		ct := r.Header.Get("Content-Type")
		if !strings.HasPrefix(ct, "multipart/form-data; boundary=") {
			t.Errorf("Content-Type: got %q, want prefix multipart/form-data; boundary=", ct)
		}

		// Parse multipart body
		if err := r.ParseMultipartForm(32 << 20); err != nil {
			t.Fatalf("ParseMultipartForm: %v", err)
		}

		// Check field "name"
		if got := r.FormValue("name"); got != voiceName {
			t.Errorf("name field: got %q, want %q", got, voiceName)
		}

		// Check field "model"
		if got := r.FormValue("model"); got != voiceModel {
			t.Errorf("model field: got %q, want %q", got, voiceModel)
		}

		// Check file part
		f, fh, err := r.FormFile("file")
		if err != nil {
			t.Fatalf("FormFile: %v", err)
		}
		defer f.Close()
		if fh.Filename != filename {
			t.Errorf("filename: got %q, want %q", fh.Filename, filename)
		}
		buf := make([]byte, len(audioData))
		if n, err := f.Read(buf); err != nil || n != len(audioData) {
			t.Errorf("file content mismatch: n=%d err=%v", n, err)
		}
		for i, b := range buf {
			if b != audioData[i] {
				t.Errorf("file byte %d: got %x, want %x", i, b, audioData[i])
			}
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(CustomVoice{VoiceID: "uc_test", Name: voiceName, Model: voiceModel})
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	_, err := c.CloneVoice(context.Background(), audioData, filename, voiceName, voiceModel)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
}

// TestCloneVoicePreValidatesSize checks that audio bytes exceeding 25 MB
// return an error before the server is ever called.
func TestCloneVoicePreValidatesSize(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("server should not be called when file is too large")
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	// 25 MB + 1 byte
	big := make([]byte, CloningMaxFileSize+1)
	_, err := c.CloneVoice(context.Background(), big, "big.wav", "MyVoice", "ssfm-v30")
	if err == nil {
		t.Fatal("expected error for oversized audio")
	}
	if !strings.Contains(err.Error(), "25MB") {
		t.Errorf("expected 25MB mention in error, got %q", err.Error())
	}
}

// TestCloneVoicePreValidatesNameLength checks that empty and over-30-char names
// return an error before the server is ever called.
func TestCloneVoicePreValidatesNameLength(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("server should not be called when name is invalid")
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	audio := make([]byte, 100)

	// Empty name
	_, err := c.CloneVoice(context.Background(), audio, "a.wav", "", "ssfm-v30")
	if err == nil {
		t.Fatal("expected error for empty name")
	}

	// 31-char name
	longName := strings.Repeat("a", NameMaxLength+1)
	_, err = c.CloneVoice(context.Background(), audio, "a.wav", longName, "ssfm-v30")
	if err == nil {
		t.Fatal("expected error for 31-char name")
	}
}

// TestCloneVoicePropagatesHTTPError checks that non-200 clone responses are
// converted into APIError values.
func TestCloneVoicePropagatesHTTPError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
		json.NewEncoder(w).Encode(ErrorResponse{Detail: "Voice cloning is not available on your plan"})
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	_, err := c.CloneVoice(context.Background(), []byte("audio"), "sample.wav", "MyVoice", "ssfm-v30")
	if err == nil {
		t.Fatal("expected non-nil error for 403")
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected *APIError, got %T", err)
	}
	if !apiErr.IsForbidden() {
		t.Errorf("expected IsForbidden(), got status %d", apiErr.StatusCode)
	}
}

// TestCloneVoicePropagatesRequestCreationError checks invalid base URL handling.
func TestCloneVoicePropagatesRequestCreationError(t *testing.T) {
	c := &Client{apiKey: "k", baseURL: "http://[::1", httpClient: http.DefaultClient}
	_, err := c.CloneVoice(context.Background(), []byte("audio"), "sample.wav", "MyVoice", "ssfm-v30")
	if err == nil || !strings.Contains(err.Error(), "failed to create request") {
		t.Fatalf("expected request creation error, got %v", err)
	}
}

// TestCloneVoicePropagatesDoError checks transport failures from the injected
// HTTP client.
func TestCloneVoicePropagatesDoError(t *testing.T) {
	c := &Client{
		apiKey:  "k",
		baseURL: "https://api.example.test",
		httpClient: &http.Client{
			Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
				return nil, io.ErrUnexpectedEOF
			}),
		},
	}

	_, err := c.CloneVoice(context.Background(), []byte("audio"), "sample.wav", "MyVoice", "ssfm-v30")
	if !errors.Is(err, io.ErrUnexpectedEOF) {
		t.Fatalf("expected io.ErrUnexpectedEOF, got %v", err)
	}
}

// TestCloneVoicePropagatesDecodeError checks malformed success JSON handling.
func TestCloneVoicePropagatesDecodeError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("{"))
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	_, err := c.CloneVoice(context.Background(), []byte("audio"), "sample.wav", "MyVoice", "ssfm-v30")
	if err == nil || !strings.Contains(err.Error(), "failed to decode clone voice response") {
		t.Fatalf("expected decode error, got %v", err)
	}
}

// TestDeleteVoiceReturnsNilOn204 checks that a 204 No Content response
// results in a nil error and the URL ends with the given voice ID.
func TestDeleteVoiceReturnsNilOn204(t *testing.T) {
	const voiceID = "uc_xxx"

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/v1/voices/"+voiceID) {
			t.Errorf("URL path: got %q, want to end with /v1/voices/%s", r.URL.Path, voiceID)
		}
		if r.Method != http.MethodDelete {
			t.Errorf("method: got %q, want DELETE", r.Method)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	err := c.DeleteVoice(context.Background(), voiceID)
	if err != nil {
		t.Fatalf("expected nil error for 204, got: %v", err)
	}
}

// TestDeleteVoiceReturnsNilOn200 checks that 200 OK is also accepted.
func TestDeleteVoiceReturnsNilOn200(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	if err := c.DeleteVoice(context.Background(), "uc_xxx"); err != nil {
		t.Fatalf("expected nil error for 200, got: %v", err)
	}
}

// TestDeleteVoiceErrorOn404 checks that a 404 response returns a non-nil error.
func TestDeleteVoiceErrorOn404(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(ErrorResponse{Detail: "voice not found"})
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	err := c.DeleteVoice(context.Background(), "uc_does_not_exist")
	if err == nil {
		t.Fatal("expected non-nil error for 404")
	}
	var apiErr *APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("expected *APIError, got %T", err)
	}
	if !apiErr.IsNotFound() {
		t.Errorf("expected IsNotFound(), got status %d", apiErr.StatusCode)
	}
}

// TestDeleteVoicePropagatesRequestCreationError checks invalid base URL handling.
func TestDeleteVoicePropagatesRequestCreationError(t *testing.T) {
	c := &Client{apiKey: "k", baseURL: "http://[::1", httpClient: http.DefaultClient}
	err := c.DeleteVoice(context.Background(), "uc_xxx")
	if err == nil || !strings.Contains(err.Error(), "failed to create request") {
		t.Fatalf("expected request creation error, got %v", err)
	}
}

// TestDeleteVoicePropagatesDoError checks transport failures from the injected
// HTTP client.
func TestDeleteVoicePropagatesDoError(t *testing.T) {
	c := &Client{
		apiKey:  "k",
		baseURL: "https://api.example.test",
		httpClient: &http.Client{
			Transport: roundTripFunc(func(*http.Request) (*http.Response, error) {
				return nil, io.ErrUnexpectedEOF
			}),
		},
	}

	err := c.DeleteVoice(context.Background(), "uc_xxx")
	if !errors.Is(err, io.ErrUnexpectedEOF) {
		t.Fatalf("expected io.ErrUnexpectedEOF, got %v", err)
	}
}

func TestGuessAudioMime(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{name: "sample.wav", want: "audio/wav"},
		{name: "sample.WAV", want: "audio/wav"},
		{name: "sample.mp3", want: "audio/mpeg"},
		{name: "sample.bin", want: "application/octet-stream"},
	}

	for _, tc := range tests {
		if got := guessAudioMime(tc.name); got != tc.want {
			t.Errorf("guessAudioMime(%q): got %q, want %q", tc.name, got, tc.want)
		}
	}
}

// TestCloneVoiceMultipartReaderParsing is a low-level check that
// multipart.NewReader can parse what the implementation produces (the
// TestCloneVoiceSendsMultipartBody test uses r.ParseMultipartForm via httptest,
// but this test verifies the boundary parsing at the reader level).
func TestCloneVoiceMultipartReaderParsing(t *testing.T) {
	var capturedCT string
	var capturedBody []byte

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		capturedCT = r.Header.Get("Content-Type")
		var err error
		capturedBody, err = func() ([]byte, error) {
			buf := make([]byte, 1<<20)
			n, _ := r.Body.Read(buf)
			return buf[:n], nil
		}()
		_ = err
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(CustomVoice{VoiceID: "uc_r", Name: "R", Model: "ssfm-v30"})
	}))
	defer srv.Close()

	c := newTestClient(srv, "k")
	_, err := c.CloneVoice(context.Background(), []byte("pcmdata"), "hello.wav", "R", "ssfm-v30")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	// Extract boundary from captured Content-Type
	const prefix = "multipart/form-data; boundary="
	if !strings.HasPrefix(capturedCT, prefix) {
		t.Fatalf("unexpected Content-Type: %q", capturedCT)
	}
	boundary := capturedCT[len(prefix):]

	mr := multipart.NewReader(strings.NewReader(string(capturedBody)), boundary)
	parts := map[string]string{}
	for {
		p, err := mr.NextPart()
		if err != nil {
			break
		}
		buf := make([]byte, 4096)
		n, _ := p.Read(buf)
		parts[p.FormName()] = string(buf[:n])
	}

	if parts["name"] != "R" {
		t.Errorf("name part: got %q, want R", parts["name"])
	}
	if parts["model"] != "ssfm-v30" {
		t.Errorf("model part: got %q, want ssfm-v30", parts["model"])
	}
	if parts["file"] != "pcmdata" {
		t.Errorf("file part: got %q, want pcmdata", parts["file"])
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}
