package typecast

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestLegacySeedIsNotSerialized(t *testing.T) {
	for _, seed := range []int{0, 42} {
		for _, request := range []interface{}{
			TTSRequest{VoiceID: "tc_test", Text: "hello", Seed: &seed},
			TTSRequestStream{VoiceID: "tc_test", Text: "hello", Seed: &seed},
			TTSRequestWithTimestamps{VoiceID: "tc_test", Text: "hello", Seed: &seed},
		} {
			body, err := json.Marshal(request)
			if err != nil {
				t.Fatal(err)
			}
			if strings.Contains(string(body), `"seed"`) {
				t.Fatalf("seed leaked: %s", body)
			}
		}
	}
}
