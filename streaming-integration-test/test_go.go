package main

import (
	"context"
	"fmt"
	"io"
	"os"

	typecast "github.com/neosapience/typecast-sdk/typecast-go"
)

const (
	apiKey     = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3"
	host       = "https://api.icepeak.in"
	voiceID    = "tc_68d259f809700d8ac76e8567"
	outputFile = "/tmp/streaming_test_go.wav"
)

func main() {
	client := typecast.NewClient(&typecast.ClientConfig{
		APIKey:  apiKey,
		BaseURL: host,
	})

	request := typecast.TTSRequestStream{
		VoiceID:  voiceID,
		Text:     "Hello, this is a streaming integration test from the Go SDK.",
		Model:    "ssfm-v30",
		Language: "eng",
		Output: &typecast.OutputStream{
			AudioFormat: typecast.AudioFormatWAV,
		},
	}

	fmt.Println("[Go] Calling TextToSpeechStream...")
	reader, err := client.TextToSpeechStream(context.Background(), request)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[Go] FAILED: %v\n", err)
		os.Exit(1)
	}
	defer reader.Close()

	f, err := os.Create(outputFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[Go] FAILED to create file: %v\n", err)
		os.Exit(1)
	}
	defer f.Close()

	n, err := io.Copy(f, reader)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[Go] FAILED to read stream: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("[Go] SUCCESS - %d bytes -> %s\n", n, outputFile)
	if n == 0 {
		fmt.Fprintln(os.Stderr, "[Go] FAILED: No audio data received")
		os.Exit(1)
	}
}
