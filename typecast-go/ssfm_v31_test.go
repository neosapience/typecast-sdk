package typecast

import (
	"encoding/json"
	"testing"
)

func TestSSFMV31WireValue(t *testing.T) {
	for _, model := range []TTSModel{ModelSSFMV21, ModelSSFMV30, ModelSSFMV31} {
		data, err := json.Marshal(model)
		if err != nil || string(data) != `"`+string(model)+`"` {
			t.Fatalf("model serialization: %s, %v", data, err)
		}
	}
}
