package typecast

import (
	"context"
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

type composeTTSSegment struct {
	Type string `json:"type"`
	*TTSRequest
}

type composePauseSegment struct {
	Type            string  `json:"type"`
	DurationSeconds float64 `json:"duration_seconds"`
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

	segments := make([]interface{}, 0, len(plan))
	for _, part := range plan {
		if part.kind == SpeechPartPause {
			if !isValidPause(part.seconds) {
				return nil, fmt.Errorf("pause seconds must be greater than 0")
			}
			segments = append(segments, composePauseSegment{Type: "pause", DurationSeconds: part.seconds})
			continue
		}
		segments = append(segments, composeTTSSegment{Type: "tts", TTSRequest: requestFromComposerPart(part, outputFormat)})
	}
	return c.client.composeTextToSpeech(ctx, struct {
		Segments []interface{} `json:"segments"`
	}{segments})
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
		if strings.HasSuffix(tokenBody, "s") && validSecondsLiteral(tokenBody[:len(tokenBody)-1]) {
			secondsText := tokenBody[:len(tokenBody)-1]
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

func requestFromComposerPart(part composerPart, format AudioFormat) *TTSRequest {
	output := mergeComposerOutput(part.settings.Output, &Output{AudioFormat: format})
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
