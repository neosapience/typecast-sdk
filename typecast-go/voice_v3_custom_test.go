package typecast

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

var v3TestVoice = VoiceV3{
	VoiceID:   "tc_v3",
	VoiceName: LocalizedVoiceName{Eng: "Voice", Kor: "보이스"},
	Models:    []ModelInfo{{Version: ModelSSFMV30, Emotions: []string{"normal"}}},
	VoiceType: "original",
}

var professionalTestVoice = CustomVoice{
	VoiceID: "uc_professional", Name: "Narrator", Model: "ssfm-v30", Source: "professional", Status: "processing",
}

func TestV3AndCustomVoiceSuccess(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case "/v3/voices":
			if r.URL.Query().Get("model") != "ssfm-v30" || r.URL.Query().Get("gender") != "female" || r.URL.Query().Get("age") != "young_adult" || r.URL.Query().Get("use_cases") != "Audiobook" {
				t.Fatalf("unexpected V3 filter: %s", r.URL.RawQuery)
			}
			_ = json.NewEncoder(w).Encode([]VoiceV3{v3TestVoice})
		case "/v3/voices/tc_v3":
			_ = json.NewEncoder(w).Encode(v3TestVoice)
		case "/v1/custom-voices/professional-clone":
			if err := r.ParseMultipartForm(32 << 20); err != nil || r.FormValue("language") != "en" || r.FormValue("model") != "ssfm-v30" {
				t.Fatalf("invalid professional-clone request: %v", err)
			}
			if _, _, err := r.FormFile("files"); err != nil {
				t.Fatalf("professional clone files field: %v", err)
			}
			w.WriteHeader(http.StatusAccepted)
			_ = json.NewEncoder(w).Encode(professionalTestVoice)
		case "/v1/custom-voices":
			_ = json.NewEncoder(w).Encode([]CustomVoice{professionalTestVoice})
		case "/v1/custom-voices/uc_professional":
			_ = json.NewEncoder(w).Encode(professionalTestVoice)
		default:
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
	}))
	defer srv.Close()
	c := newTestClient(srv, "key")
	ctx := context.Background()

	voices, err := c.GetVoicesV3(ctx, &VoicesV2Filter{Model: ModelSSFMV30, Gender: GenderFemale, Age: AgeYoungAdult, UseCases: UseCaseAudiobook})
	if err != nil || voices[0].VoiceName.Kor != "보이스" {
		t.Fatalf("V3 voices: %v, %#v", err, voices)
	}
	if voice, err := c.GetVoiceV3(ctx, "tc_v3"); err != nil || voice.VoiceName.Eng != "Voice" {
		t.Fatalf("V3 voice: %v, %#v", err, voice)
	}
	if voice, err := c.CreateProfessionalVoice(ctx, []byte("audio"), "voice.wav", "Narrator", "en", "ssfm-v30"); err != nil || voice.Status != "processing" {
		t.Fatalf("professional clone: %v, %#v", err, voice)
	}
	if voices, err := c.GetCustomVoices(ctx); err != nil || voices[0].Source != "professional" {
		t.Fatalf("custom voices: %v, %#v", err, voices)
	}
	if voice, err := c.GetCustomVoice(ctx, "uc_professional"); err != nil || voice.VoiceID != "uc_professional" {
		t.Fatalf("custom voice: %v, %#v", err, voice)
	}
}

func TestV3AndCustomVoiceErrors(t *testing.T) {
	for _, body := range []string{"{", `{"detail":"boom"}`} {
		t.Run(body, func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if body != "{" {
					w.WriteHeader(http.StatusInternalServerError)
				} else if r.URL.Path == "/v1/custom-voices/professional-clone" {
					w.WriteHeader(http.StatusAccepted)
				}
				_, _ = w.Write([]byte(body))
			}))
			defer srv.Close()
			c, ctx := newTestClient(srv, "key"), context.Background()
			if _, err := c.GetVoicesV3(ctx, nil); err == nil {
				t.Fatal("expected V3 list error")
			}
			if _, err := c.GetVoiceV3(ctx, "tc_v3"); err == nil {
				t.Fatal("expected V3 detail error")
			}
			if _, err := c.CreateProfessionalVoice(ctx, []byte("a"), "a.wav", "name", "en", "ssfm-v30"); err == nil {
				t.Fatal("expected clone error")
			}
			if _, err := c.GetCustomVoices(ctx); err == nil {
				t.Fatal("expected custom list error")
			}
			if _, err := c.GetCustomVoice(ctx, "uc_voice"); err == nil {
				t.Fatal("expected custom detail error")
			}
		})
	}
}

func TestV3AndCustomVoiceRequestErrors(t *testing.T) {
	c := &Client{apiKey: "key", baseURL: "http://[::1", httpClient: http.DefaultClient}
	ctx := context.Background()
	if _, err := c.GetVoicesV3(ctx, nil); err == nil {
		t.Fatal("expected V3 list request error")
	}
	if _, err := c.GetVoiceV3(ctx, "tc_v3"); err == nil {
		t.Fatal("expected V3 detail request error")
	}
	if _, err := c.CreateProfessionalVoice(ctx, []byte("a"), "a.wav", "name", "en", "ssfm-v30"); err == nil {
		t.Fatal("expected clone request error")
	}
	if _, err := c.GetCustomVoices(ctx); err == nil {
		t.Fatal("expected custom list request error")
	}
	if _, err := c.GetCustomVoice(ctx, "uc_voice"); err == nil {
		t.Fatal("expected custom detail request error")
	}
}

func TestProfessionalCloneValidationAndTransportErrors(t *testing.T) {
	ctx := context.Background()
	c := &Client{apiKey: "key", baseURL: "https://api.example.test", httpClient: &http.Client{Transport: roundTripFunc(func(*http.Request) (*http.Response, error) { return nil, context.DeadlineExceeded })}}
	if _, err := c.CreateProfessionalVoice(ctx, []byte("a"), "a.wav", "", "en", "ssfm-v30"); err == nil {
		t.Fatal("expected name validation error")
	}
	if _, err := c.CreateProfessionalVoice(ctx, make([]byte, CloningMaxFileSize+1), "a.wav", "name", "en", "ssfm-v30"); err == nil {
		t.Fatal("expected size validation error")
	}
	if _, err := c.CreateProfessionalVoice(ctx, []byte("a"), "a.wav", "name", "en", "ssfm-v30"); err == nil {
		t.Fatal("expected transport error")
	}
	c = &Client{baseURL: DefaultBaseURL, httpClient: http.DefaultClient}
	if _, err := c.CreateProfessionalVoice(ctx, []byte("a"), "a.wav", "name", "en", "ssfm-v30"); err == nil {
		t.Fatal("expected missing API key error")
	}
}
