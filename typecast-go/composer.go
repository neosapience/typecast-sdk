package typecast

import (
	"bytes"
	"context"
	"encoding/binary"
	"fmt"
	"math"
	"strconv"
	"strings"
)

type SpeechPartKind string

const (
	SpeechPartText  SpeechPartKind = "text"
	SpeechPartPause SpeechPartKind = "pause"
)

type SpeechPart struct {
	Kind    SpeechPartKind
	Text    string
	Seconds float64
}

type ComposerSettings struct {
	VoiceID  string
	Model    TTSModel
	Language string
	Prompt   interface{}
	Output   *Output
	Seed     *int
}

type SpeechComposer struct {
	client   *Client
	defaults ComposerSettings
	parts    []composerPart
}

type composerPart struct {
	kind     SpeechPartKind
	text     string
	seconds  float64
	settings ComposerSettings
}

type wavSpec struct {
	sampleRate    uint32
	channels      uint16
	bitsPerSample uint16
}

type parsedWAV struct {
	spec    wavSpec
	samples []int16
}

func (c *Client) ComposeSpeech() *SpeechComposer {
	return &SpeechComposer{client: c}
}

func (c *SpeechComposer) Defaults(settings ComposerSettings) *SpeechComposer {
	c.defaults = mergeComposerSettings(c.defaults, settings)
	return c
}

func (c *SpeechComposer) Say(text string) *SpeechComposer {
	return c.SayWith(text, ComposerSettings{})
}

func (c *SpeechComposer) SayWith(text string, settings ComposerSettings) *SpeechComposer {
	c.parts = append(c.parts, composerPart{
		kind:     SpeechPartText,
		text:     text,
		settings: mergeComposerSettings(c.defaults, settings),
	})
	return c
}

// Pause inserts silence between speech segments.
//
// seconds is a duration in seconds. Use 0.3 for 300 ms, 3 for three seconds.
func (c *SpeechComposer) Pause(seconds float64) *SpeechComposer {
	c.parts = append(c.parts, composerPart{kind: SpeechPartPause, seconds: seconds})
	return c
}

func (c *SpeechComposer) Generate(ctx context.Context) (*TTSResponse, error) {
	plan, err := c.buildPlan()
	if err != nil {
		return nil, err
	}
	hasSpeech := false
	for _, part := range plan {
		if part.kind == SpeechPartText {
			hasSpeech = true
			break
		}
	}
	if !hasSpeech {
		return nil, fmt.Errorf("at least one speech segment is required")
	}

	outputFormat := AudioFormatWAV
	if c.defaults.Output != nil && c.defaults.Output.AudioFormat != "" {
		outputFormat = c.defaults.Output.AudioFormat
	}

	var spec *wavSpec
	var outputSamples []int16
	for _, part := range plan {
		if part.kind == SpeechPartPause {
			if !isValidPause(part.seconds) {
				return nil, fmt.Errorf("pause seconds must be greater than 0")
			}
			if spec == nil {
				return nil, fmt.Errorf("pause cannot be the first composed part")
			}
			outputSamples = append(outputSamples, make([]int16, secondsToSamples(part.seconds, spec.sampleRate))...)
			continue
		}

		response, err := c.client.TextToSpeech(ctx, requestFromComposerPart(part))
		if err != nil {
			return nil, err
		}
		wav, err := parseComposerWAV(response.AudioData)
		if err != nil {
			return nil, err
		}
		if spec != nil && *spec != wav.spec {
			return nil, fmt.Errorf("all composed WAV segments must use the same PCM format")
		}
		currentSpec := wav.spec
		spec = &currentSpec
		outputSamples = append(outputSamples, trimComposerSilence(wav.samples)...)
	}

	if outputFormat == AudioFormatMP3 {
		return nil, fmt.Errorf("ffmpeg is required to encode composed speech as mp3")
	}
	return &TTSResponse{
		AudioData: encodeComposerWAV(outputSamples, *spec),
		Duration:  float64(len(outputSamples)) / float64(spec.sampleRate),
		Format:    AudioFormatWAV,
	}, nil
}

func (c *SpeechComposer) buildPlan() ([]composerPart, error) {
	var plan []composerPart
	for _, part := range c.parts {
		if part.kind == SpeechPartPause {
			if !isValidPause(part.seconds) {
				return nil, fmt.Errorf("pause seconds must be greater than 0")
			}
			plan = append(plan, part)
			continue
		}
		for _, parsed := range ParsePauseMarkup(part.text) {
			if parsed.Kind == SpeechPartPause {
				plan = append(plan, composerPart{kind: SpeechPartPause, seconds: parsed.Seconds})
				continue
			}
			if strings.TrimSpace(parsed.Text) == "" {
				continue
			}
			if strings.TrimSpace(part.settings.VoiceID) == "" {
				return nil, fmt.Errorf("voice_id is required for composed speech segments")
			}
			if part.settings.Model == "" {
				return nil, fmt.Errorf("model is required for composed speech segments")
			}
			plan = append(plan, composerPart{
				kind:     SpeechPartText,
				text:     parsed.Text,
				settings: part.settings,
			})
		}
	}
	return plan, nil
}

func ParsePauseMarkup(text string) []SpeechPart {
	var parts []SpeechPart
	lastEmit := 0
	searchFrom := 0
	for {
		relativeStart := strings.Index(text[searchFrom:], "<|")
		if relativeStart < 0 {
			break
		}
		tokenStart := searchFrom + relativeStart
		bodyStart := tokenStart + 2
		relativeEnd := strings.Index(text[bodyStart:], "|>")
		if relativeEnd < 0 {
			break
		}
		bodyEnd := bodyStart + relativeEnd
		tokenEnd := bodyEnd + 2
		tokenBody := text[bodyStart:bodyEnd]
		if secondsText, ok := strings.CutSuffix(tokenBody, "s"); ok && validSecondsLiteral(secondsText) {
			seconds, err := strconv.ParseFloat(secondsText, 64)
			if err == nil {
				if tokenStart > lastEmit {
					parts = append(parts, SpeechPart{Kind: SpeechPartText, Text: text[lastEmit:tokenStart]})
				}
				parts = append(parts, SpeechPart{Kind: SpeechPartPause, Seconds: seconds})
				lastEmit = tokenEnd
				searchFrom = tokenEnd
				continue
			}
		}
		searchFrom = bodyStart
	}
	if lastEmit < len(text) {
		parts = append(parts, SpeechPart{Kind: SpeechPartText, Text: text[lastEmit:]})
	}
	return parts
}

func mergeComposerSettings(base, override ComposerSettings) ComposerSettings {
	merged := base
	if override.VoiceID != "" {
		merged.VoiceID = override.VoiceID
	}
	if override.Model != "" {
		merged.Model = override.Model
	}
	if override.Language != "" {
		merged.Language = override.Language
	}
	if override.Prompt != nil {
		merged.Prompt = override.Prompt
	}
	merged.Output = mergeComposerOutput(base.Output, override.Output)
	if override.Seed != nil {
		merged.Seed = override.Seed
	}
	return merged
}

func mergeComposerOutput(base, override *Output) *Output {
	if base == nil && override == nil {
		return nil
	}
	var merged Output
	if base != nil {
		merged = *base
	}
	if override != nil {
		if override.Volume != nil {
			merged.Volume = override.Volume
		}
		if override.TargetLUFS != nil {
			merged.TargetLUFS = override.TargetLUFS
		}
		if override.AudioPitch != nil {
			merged.AudioPitch = override.AudioPitch
		}
		if override.AudioTempo != nil {
			merged.AudioTempo = override.AudioTempo
		}
		if override.AudioFormat != "" {
			merged.AudioFormat = override.AudioFormat
		}
	}
	return &merged
}

func requestFromComposerPart(part composerPart) *TTSRequest {
	output := mergeComposerOutput(part.settings.Output, &Output{AudioFormat: AudioFormatWAV})
	return &TTSRequest{
		VoiceID:  part.settings.VoiceID,
		Text:     part.text,
		Model:    part.settings.Model,
		Language: part.settings.Language,
		Prompt:   part.settings.Prompt,
		Output:   output,
		Seed:     part.settings.Seed,
	}
}

func parseComposerWAV(data []byte) (*parsedWAV, error) {
	if len(data) < 12 || string(data[0:4]) != "RIFF" || string(data[8:12]) != "WAVE" {
		return nil, fmt.Errorf("unsupported WAV data")
	}
	offset := 12
	var spec *wavSpec
	var samples []int16
	for offset+8 <= len(data) {
		chunkID := string(data[offset : offset+4])
		chunkSize := int(binary.LittleEndian.Uint32(data[offset+4 : offset+8]))
		chunkDataOffset := offset + 8
		chunkEnd := chunkDataOffset + chunkSize
		if chunkEnd > len(data) {
			return nil, fmt.Errorf("unsupported WAV data")
		}
		switch chunkID {
		case "fmt ":
			if chunkSize < 16 {
				return nil, fmt.Errorf("unsupported WAV data")
			}
			audioFormat := binary.LittleEndian.Uint16(data[chunkDataOffset:])
			channels := binary.LittleEndian.Uint16(data[chunkDataOffset+2:])
			sampleRate := binary.LittleEndian.Uint32(data[chunkDataOffset+4:])
			bitsPerSample := binary.LittleEndian.Uint16(data[chunkDataOffset+14:])
			if audioFormat != 1 || channels != 1 || bitsPerSample != 16 {
				return nil, fmt.Errorf("only mono 16-bit PCM WAV is supported for composed speech")
			}
			spec = &wavSpec{sampleRate: sampleRate, channels: channels, bitsPerSample: bitsPerSample}
		case "data":
			samples = make([]int16, 0, chunkSize/2)
			for index := chunkDataOffset; index+1 < chunkEnd; index += 2 {
				samples = append(samples, int16(binary.LittleEndian.Uint16(data[index:])))
			}
		}
		offset = chunkEnd + chunkSize%2
	}
	if spec == nil || samples == nil {
		return nil, fmt.Errorf("unsupported WAV data")
	}
	return &parsedWAV{spec: *spec, samples: samples}, nil
}

func encodeComposerWAV(samples []int16, spec wavSpec) []byte {
	var out bytes.Buffer
	out.WriteString("RIFF")
	_ = binary.Write(&out, binary.LittleEndian, uint32(36+len(samples)*2))
	out.WriteString("WAVE")
	out.WriteString("fmt ")
	_ = binary.Write(&out, binary.LittleEndian, uint32(16))
	_ = binary.Write(&out, binary.LittleEndian, uint16(1))
	_ = binary.Write(&out, binary.LittleEndian, spec.channels)
	_ = binary.Write(&out, binary.LittleEndian, spec.sampleRate)
	_ = binary.Write(&out, binary.LittleEndian, spec.sampleRate*uint32(spec.channels)*2)
	_ = binary.Write(&out, binary.LittleEndian, spec.channels*2)
	_ = binary.Write(&out, binary.LittleEndian, spec.bitsPerSample)
	out.WriteString("data")
	_ = binary.Write(&out, binary.LittleEndian, uint32(len(samples)*2))
	for _, sample := range samples {
		_ = binary.Write(&out, binary.LittleEndian, sample)
	}
	return out.Bytes()
}

func trimComposerSilence(samples []int16) []int16 {
	start := 0
	end := len(samples)
	for start < end && samples[start] == 0 {
		start++
	}
	for end > start && samples[end-1] == 0 {
		end--
	}
	return samples[start:end]
}

func secondsToSamples(seconds float64, sampleRate uint32) int {
	return int(math.Round(seconds * float64(sampleRate)))
}

func isValidPause(seconds float64) bool {
	return !math.IsNaN(seconds) && !math.IsInf(seconds, 0) && seconds > 0
}

func validSecondsLiteral(value string) bool {
	if value == "" {
		return false
	}
	parts := strings.Split(value, ".")
	if len(parts) > 2 || parts[0] == "" {
		return false
	}
	for _, part := range parts {
		if part == "" {
			return false
		}
		for _, r := range part {
			if r < '0' || r > '9' {
				return false
			}
		}
	}
	return true
}
