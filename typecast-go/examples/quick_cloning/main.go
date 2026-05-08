// Instant cloning: clone -> speak -> delete in one flow.
//
// Usage:
//
//	export TYPECAST_API_KEY="your-api-key"
//	export TYPECAST_API_HOST="https://api.icepeak.in"  # dev only, omit for prod
//	go run ./examples/quick_cloning <audio.wav>
package main

import (
	"context"
	"fmt"
	"os"
	"path/filepath"

	typecast "github.com/neosapience/typecast-sdk/typecast-go"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: quick_cloning <audio.wav>")
		fmt.Fprintln(os.Stderr, "")
		fmt.Fprintln(os.Stderr, "Limits:")
		fmt.Fprintf(os.Stderr, "  - File size: <= %d MB\n", typecast.CloningMaxFileSize/(1024*1024))
		fmt.Fprintf(os.Stderr, "  - Voice name: %d-%d characters\n", typecast.NameMinLength, typecast.NameMaxLength)
		os.Exit(1)
	}

	audioPath := os.Args[1]
	audioBytes, err := os.ReadFile(audioPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "error reading audio file: %v\n", err)
		os.Exit(1)
	}

	client := typecast.NewClient(nil) // reads TYPECAST_API_KEY + TYPECAST_API_HOST from env
	ctx := context.Background()

	voiceName := "QuickCloningDemo"
	filename := filepath.Base(audioPath)

	fmt.Printf("Cloning voice from %q (name=%q, model=ssfm-v30)...\n", filename, voiceName)
	custom, err := client.CloneVoice(ctx, audioBytes, filename, voiceName, "ssfm-v30")
	if err != nil {
		fmt.Fprintf(os.Stderr, "clone error: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("Created custom voice: id=%q name=%q model=%q\n", custom.VoiceID, custom.Name, custom.Model)

	// Synthesize speech with the cloned voice
	text := "Hello! This is my cloned voice speaking with the Typecast API."
	fmt.Printf("Synthesizing %q...\n", text)
	audio, err := client.TextToSpeech(ctx, &typecast.TTSRequest{
		VoiceID: custom.VoiceID,
		Text:    text,
		Model:   typecast.ModelSSFMV30,
	})
	if err != nil {
		// Attempt cleanup even on synthesis error
		_ = client.DeleteVoice(ctx, custom.VoiceID)
		fmt.Fprintf(os.Stderr, "tts error: %v\n", err)
		os.Exit(1)
	}

	outPath := "/tmp/quick_cloning_go.wav"
	if err := os.WriteFile(outPath, audio.AudioData, 0644); err != nil {
		_ = client.DeleteVoice(ctx, custom.VoiceID)
		fmt.Fprintf(os.Stderr, "write error: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("Audio saved to %s (%.2fs)\n", outPath, audio.Duration)

	// Clean up: delete the custom voice
	fmt.Printf("Deleting custom voice %q...\n", custom.VoiceID)
	if err := client.DeleteVoice(ctx, custom.VoiceID); err != nil {
		fmt.Fprintf(os.Stderr, "delete error: %v\n", err)
		os.Exit(1)
	}
	fmt.Println("Done. Custom voice deleted.")
}
