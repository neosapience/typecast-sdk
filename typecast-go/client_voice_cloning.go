package typecast

import "strings"

// guessAudioMime returns a MIME type based on the audio filename extension.
func guessAudioMime(filename string) string {
	lower := strings.ToLower(filename)
	if strings.HasSuffix(lower, ".wav") {
		return "audio/wav"
	}
	if strings.HasSuffix(lower, ".mp3") {
		return "audio/mpeg"
	}
	return "application/octet-stream"
}
