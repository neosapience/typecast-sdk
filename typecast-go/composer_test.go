package typecast

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
)

func TestComposeSpeech_ComposesWAVAndMergesOverrides(t *testing.T) {
	responses := [][]byte{
		makeComposerTestWAV([]int16{0, 1000, 2000, 0}, 1000),
		makeComposerTestWAV([]int16{0, -1000, -2000, 0}, 1000),
	}
	var bodies []TTSRequest
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body TTSRequest
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			t.Fatalf("decode body: %v", err)
		}
		bodies = append(bodies, body)
		w.Header().Set("Content-Type", "audio/wav")
		w.WriteHeader(http.StatusOK)
		w.Write(responses[len(bodies)-1])
	}))
	defer srv.Close()

	pitch := 1
	tempo := 1.1
	resp, err := newTestClient(srv, "key").
		ComposeSpeech().
		Defaults(ComposerSettings{
			VoiceID:  "voice-a",
			Model:    ModelSSFMV30,
			Language: "eng",
			Output:   &Output{AudioPitch: &pitch, AudioFormat: AudioFormatWAV},
		}).
		SayWith("Hello<|0.001s|>world", ComposerSettings{
			VoiceID: "voice-b",
			Output:  &Output{AudioTempo: &tempo},
		}).
		Generate(context.Background())
	if err != nil {
		t.Fatalf("Generate() error = %v", err)
	}

	if len(bodies) != 2 {
		t.Fatalf("expected 2 requests, got %d", len(bodies))
	}
	if bodies[0].Text != "Hello" || bodies[1].Text != "world" {
		t.Fatalf("unexpected texts: %#v", bodies)
	}
	if bodies[0].VoiceID != "voice-b" {
		t.Fatalf("expected override voice, got %s", bodies[0].VoiceID)
	}
	if bodies[0].Output == nil || bodies[0].Output.AudioFormat != AudioFormatWAV {
		t.Fatalf("expected internal wav output: %#v", bodies[0].Output)
	}
	if bodies[0].Output.AudioPitch == nil || *bodies[0].Output.AudioPitch != 1 {
		t.Fatalf("expected merged pitch: %#v", bodies[0].Output)
	}
	if bodies[0].Output.AudioTempo == nil || *bodies[0].Output.AudioTempo != 1.1 {
		t.Fatalf("expected override tempo: %#v", bodies[0].Output)
	}
	if resp.Format != AudioFormatWAV {
		t.Fatalf("expected wav, got %s", resp.Format)
	}
	if got := samplesFromComposerTestWAV(resp.AudioData); !reflect.DeepEqual(got, []int16{1000, 2000, 0, -1000, -2000}) {
		t.Fatalf("unexpected samples: %v", got)
	}
	if resp.Duration != 0.005 {
		t.Fatalf("unexpected duration: %v", resp.Duration)
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

func TestComposeSpeech_DefensiveErrors(t *testing.T) {
	badWAV := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/wav")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("not wav"))
	}))
	defer badWAV.Close()
	_, err := newTestClient(badWAV, "key").
		ComposeSpeech().
		Defaults(ComposerSettings{VoiceID: "voice-a", Model: ModelSSFMV30}).
		Say("Hello").
		Generate(context.Background())
	if err == nil || !strings.Contains(err.Error(), "unsupported WAV data") {
		t.Fatalf("expected bad wav error, got %v", err)
	}

	responses := [][]byte{
		makeComposerTestWAV([]int16{1000}, 1000),
		makeComposerTestWAV([]int16{1000}, 2000),
	}
	mismatch := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "audio/wav")
		w.WriteHeader(http.StatusOK)
		w.Write(responses[0])
		responses = responses[1:]
	}))
	defer mismatch.Close()
	_, err = newTestClient(mismatch, "key").
		ComposeSpeech().
		Defaults(ComposerSettings{VoiceID: "voice-a", Model: ModelSSFMV30}).
		Say("one<|0.001s|>two").
		Generate(context.Background())
	if err == nil || !strings.Contains(err.Error(), "same PCM format") {
		t.Fatalf("expected mismatch error, got %v", err)
	}

	var request TTSRequest
	mp3 := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Fatalf("decode body: %v", err)
		}
		w.Header().Set("Content-Type", "audio/wav")
		w.WriteHeader(http.StatusOK)
		w.Write(makeComposerTestWAV([]int16{1000}, 1000))
	}))
	defer mp3.Close()
	_, err = newTestClient(mp3, "key").
		ComposeSpeech().
		Defaults(ComposerSettings{VoiceID: "voice-a", Model: ModelSSFMV30, Output: &Output{AudioFormat: AudioFormatMP3}}).
		Say("Hello").
		Generate(context.Background())
	if err == nil || !strings.Contains(err.Error(), "ffmpeg is required") {
		t.Fatalf("expected mp3 error, got %v", err)
	}
	if request.Output == nil || request.Output.AudioFormat != AudioFormatWAV {
		t.Fatalf("expected internal wav request, got %#v", request.Output)
	}

	if _, err = newTestClient(mp3, "key").ComposeSpeech().Generate(context.Background()); err == nil || !strings.Contains(err.Error(), "at least one speech segment") {
		t.Fatalf("expected empty composer error, got %v", err)
	}
	if _, err = newTestClient(mp3, "key").ComposeSpeech().Defaults(ComposerSettings{VoiceID: "voice-a", Model: ModelSSFMV30}).Pause(0.1).Say("Hello").Generate(context.Background()); err == nil || !strings.Contains(err.Error(), "pause cannot be the first") {
		t.Fatalf("expected leading pause error, got %v", err)
	}
	if _, err = newTestClient(mp3, "key").ComposeSpeech().Defaults(ComposerSettings{VoiceID: "voice-a"}).Say("Hello").Generate(context.Background()); err == nil || !strings.Contains(err.Error(), "model is required") {
		t.Fatalf("expected model error, got %v", err)
	}
}

func makeComposerTestWAV(samples []int16, sampleRate uint32) []byte {
	var out bytes.Buffer
	out.WriteString("RIFF")
	binary.Write(&out, binary.LittleEndian, uint32(36+len(samples)*2))
	out.WriteString("WAVE")
	out.WriteString("fmt ")
	binary.Write(&out, binary.LittleEndian, uint32(16))
	binary.Write(&out, binary.LittleEndian, uint16(1))
	binary.Write(&out, binary.LittleEndian, uint16(1))
	binary.Write(&out, binary.LittleEndian, sampleRate)
	binary.Write(&out, binary.LittleEndian, sampleRate*2)
	binary.Write(&out, binary.LittleEndian, uint16(2))
	binary.Write(&out, binary.LittleEndian, uint16(16))
	out.WriteString("data")
	binary.Write(&out, binary.LittleEndian, uint32(len(samples)*2))
	for _, sample := range samples {
		binary.Write(&out, binary.LittleEndian, sample)
	}
	return out.Bytes()
}

func samplesFromComposerTestWAV(data []byte) []int16 {
	index := bytes.Index(data, []byte("data"))
	if index < 0 {
		panic(errors.New("missing data chunk"))
	}
	payload := data[index+8:]
	samples := make([]int16, 0, len(payload)/2)
	for offset := 0; offset+1 < len(payload); offset += 2 {
		samples = append(samples, int16(binary.LittleEndian.Uint16(payload[offset:])))
	}
	return samples
}
