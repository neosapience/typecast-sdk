// Example: text-to-speech with word/character timestamps, SRT and VTT export.
package main

import (
	"context"
	"fmt"
	"os"

	typecast "github.com/neosapience/typecast-sdk/typecast-go"
)

func main() {
	client := typecast.NewClient(nil) // reads TYPECAST_API_KEY + TYPECAST_API_HOST from env

	req := &typecast.TTSRequestWithTimestamps{
		VoiceID:  "tc_60e5426de8b95f1d3000d7b5",
		Text:     "Hello. How are you?",
		Model:    typecast.ModelSSFMV30,
		Language: "eng",
	}

	resp, err := client.TextToSpeechWithTimestamps(context.Background(), req, "")
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}

	if err := resp.SaveAudio("/tmp/with_timestamps_go.wav"); err != nil {
		fmt.Fprintln(os.Stderr, "save audio error:", err)
		os.Exit(1)
	}

	srt, err := resp.ToSRT()
	if err != nil {
		fmt.Fprintln(os.Stderr, "srt error:", err)
		os.Exit(1)
	}
	if err := os.WriteFile("/tmp/with_timestamps_go.srt", []byte(srt), 0644); err != nil {
		fmt.Fprintln(os.Stderr, "write srt error:", err)
		os.Exit(1)
	}

	vtt, err := resp.ToVTT()
	if err != nil {
		fmt.Fprintln(os.Stderr, "vtt error:", err)
		os.Exit(1)
	}
	if err := os.WriteFile("/tmp/with_timestamps_go.vtt", []byte(vtt), 0644); err != nil {
		fmt.Fprintln(os.Stderr, "write vtt error:", err)
		os.Exit(1)
	}

	fmt.Printf("audio: /tmp/with_timestamps_go.wav (%.2fs, format=%s)\n", resp.AudioDuration, resp.AudioFormat)
	fmt.Printf("words: %d, characters: %d\n", len(resp.Words), len(resp.Characters))
	fmt.Printf("SRT first cue:\n%s", firstCue(srt))
}

func firstCue(srt string) string {
	for i, c := range srt {
		if c == '\n' {
			rest := srt[i+1:]
			for j, c2 := range rest {
				if c2 == '\n' && j > 0 && rest[j-1] == '\n' {
					return srt[:i+1+j+1]
				}
			}
		}
	}
	return srt
}
