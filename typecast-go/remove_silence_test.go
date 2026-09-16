package typecast

import (
	"context"
	"encoding/json"
	"strings"
	"testing"
)

func TestRemoveSilence(t *testing.T) {
	invalid := 1001
	_, err := NewClient(&ClientConfig{APIKey: "test", BaseURL: "http://localhost:1"}).ComposeSpeech().Defaults(ComposerSettings{
		VoiceID: "voice", Model: ModelSSFMV30, Output: &Output{RemoveSilenceMS: &invalid},
	}).Say("Hello").Generate(context.Background())
	if err == nil || !strings.Contains(err.Error(), "remove_silence_ms") {
		t.Fatal("invalid composed silence accepted")
	}
	for _, value := range []int{0, 300, 1000, -1, 1001} {
		output := &Output{RemoveSilenceMS: &value}
		stream := &OutputStream{RemoveSilenceMS: &value}
		valid := value >= 0 && value <= 1000
		if (output.Validate() == nil) != valid || (stream.Validate() == nil) != valid {
			t.Fatal(value)
		}
		for _, obj := range []interface{}{output, stream} {
			data, err := json.Marshal(obj)
			if err != nil || !strings.Contains(string(data), `"remove_silence_ms":`) {
				t.Fatal(string(data), err)
			}
		}
	}
	for _, obj := range []interface{}{Output{}, OutputStream{}} {
		data, _ := json.Marshal(obj)
		if strings.Contains(string(data), "remove_silence_ms") {
			t.Fatal(string(data))
		}
	}
	base, zero := 300, 0
	merged := mergeComposerOutput(&Output{RemoveSilenceMS: &base}, &Output{RemoveSilenceMS: &zero})
	if merged.RemoveSilenceMS == nil || *merged.RemoveSilenceMS != 0 {
		t.Fatal(merged)
	}
}
