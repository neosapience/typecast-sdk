package typecast

import (
	"context"
	"fmt"
	"os"
	"strings"
)

// GenerateToFile converts text to speech and writes the audio bytes to a file.
//
// Model defaults to ssfm-v30. If Output.AudioFormat is omitted, the format is
// inferred from a .mp3 or .wav file extension.
func (c *Client) GenerateToFile(ctx context.Context, path string, request GenerateToFileRequest) (*TTSResponse, error) {
	if path == "" {
		return nil, fmt.Errorf("path cannot be empty")
	}
	if err := request.Validate(); err != nil {
		return nil, err
	}
	ttsRequest := request.toTTSRequest()
	if ttsRequest.Output == nil {
		if format := inferAudioFormatFromPath(path); format != "" {
			ttsRequest.Output = &Output{AudioFormat: format}
		}
	} else if ttsRequest.Output.AudioFormat == "" {
		if format := inferAudioFormatFromPath(path); format != "" {
			output := *ttsRequest.Output
			output.AudioFormat = format
			ttsRequest.Output = &output
		}
	}
	response, err := c.TextToSpeech(ctx, ttsRequest)
	if err != nil {
		return nil, err
	}
	if err := os.WriteFile(path, response.AudioData, 0644); err != nil {
		return nil, fmt.Errorf("failed to write audio file: %w", err)
	}
	return response, nil
}

func inferAudioFormatFromPath(path string) AudioFormat {
	lower := strings.ToLower(path)
	switch {
	case strings.HasSuffix(lower, ".mp3"):
		return AudioFormatMP3
	case strings.HasSuffix(lower, ".wav"):
		return AudioFormatWAV
	default:
		return ""
	}
}
