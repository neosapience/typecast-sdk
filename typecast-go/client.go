package typecast

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"time"
)

const (
	// DefaultBaseURL is the default Typecast API base URL
	DefaultBaseURL = "https://api.typecast.ai"
	// DefaultTimeout is the default HTTP client timeout
	DefaultTimeout = 60 * time.Second
)

// ClientConfig holds configuration options for the TypecastClient
type ClientConfig struct {
	// APIKey is the Typecast API key (required)
	APIKey string
	// BaseURL is the API base URL (optional, defaults to https://api.typecast.ai)
	BaseURL string
	// HTTPClient is the HTTP client to use (optional)
	HTTPClient *http.Client
	// Timeout is the HTTP request timeout (optional, defaults to 60s)
	Timeout time.Duration
}

// Client is the Typecast API client
type Client struct {
	apiKey     string
	baseURL    string
	httpClient *http.Client
}

// NewClient creates a new Typecast API client
func NewClient(config *ClientConfig) *Client {
	// Use environment variables as defaults
	apiKey := os.Getenv("TYPECAST_API_KEY")
	baseURL := os.Getenv("TYPECAST_API_HOST")

	if baseURL == "" {
		baseURL = DefaultBaseURL
	}

	timeout := DefaultTimeout

	// Override with provided config
	if config != nil {
		if config.APIKey != "" {
			apiKey = config.APIKey
		}
		if config.BaseURL != "" {
			baseURL = config.BaseURL
		}
		if config.Timeout > 0 {
			timeout = config.Timeout
		}
	}

	httpClient := &http.Client{Timeout: timeout}
	if config != nil && config.HTTPClient != nil {
		httpClient = config.HTTPClient
	}

	return &Client{
		apiKey:     apiKey,
		baseURL:    baseURL,
		httpClient: httpClient,
	}
}

// doRequest performs an HTTP request with the appropriate headers
func (c *Client) doRequest(ctx context.Context, method, path string, body interface{}) (*http.Response, error) {
	var bodyReader io.Reader
	if body != nil {
		jsonBody, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		bodyReader = bytes.NewReader(jsonBody)
	}

	reqURL := c.baseURL + path

	req, err := http.NewRequestWithContext(ctx, method, reqURL, bodyReader)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("X-API-KEY", c.apiKey)
	req.Header.Set("Content-Type", "application/json")

	return c.httpClient.Do(req)
}

// handleErrorResponse parses an error response and returns an APIError
func (c *Client) handleErrorResponse(resp *http.Response) error {
	var errResp ErrorResponse
	if err := json.NewDecoder(resp.Body).Decode(&errResp); err != nil {
		// If we can't decode the error response, just use the status code
		return NewAPIError(resp.StatusCode, "")
	}
	return NewAPIError(resp.StatusCode, errResp.Detail)
}

// TextToSpeech converts text to speech using the Typecast API
func (c *Client) TextToSpeech(ctx context.Context, request *TTSRequest) (*TTSResponse, error) {
	resp, err := c.doRequest(ctx, http.MethodPost, "/v1/text-to-speech", request)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, c.handleErrorResponse(resp)
	}

	// Read audio data
	audioData, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read audio data: %w", err)
	}

	// Parse content type for format
	contentType := resp.Header.Get("Content-Type")
	format := AudioFormatWAV
	if contentType == "audio/mpeg" || contentType == "audio/mp3" {
		format = AudioFormatMP3
	}

	// Parse duration from header
	var duration float64
	if durationStr := resp.Header.Get("X-Audio-Duration"); durationStr != "" {
		duration, _ = strconv.ParseFloat(durationStr, 64)
	}

	return &TTSResponse{
		AudioData: audioData,
		Duration:  duration,
		Format:    format,
	}, nil
}

// GetVoicesV2 retrieves the list of available voices with enhanced metadata (V2 API)
func (c *Client) GetVoicesV2(ctx context.Context, filter *VoicesV2Filter) ([]VoiceV2, error) {
	path := "/v2/voices"

	// Build query parameters
	if filter != nil {
		params := url.Values{}
		if filter.Model != "" {
			params.Set("model", string(filter.Model))
		}
		if filter.Gender != "" {
			params.Set("gender", string(filter.Gender))
		}
		if filter.Age != "" {
			params.Set("age", string(filter.Age))
		}
		if filter.UseCases != "" {
			params.Set("use_cases", string(filter.UseCases))
		}
		if len(params) > 0 {
			path = path + "?" + params.Encode()
		}
	}

	resp, err := c.doRequest(ctx, http.MethodGet, path, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, c.handleErrorResponse(resp)
	}

	var voices []VoiceV2
	if err := json.NewDecoder(resp.Body).Decode(&voices); err != nil {
		return nil, fmt.Errorf("failed to decode voices response: %w", err)
	}

	return voices, nil
}

// GetVoiceV2 retrieves a specific voice by ID with enhanced metadata (V2 API)
func (c *Client) GetVoiceV2(ctx context.Context, voiceID string) (*VoiceV2, error) {
	path := fmt.Sprintf("/v2/voices/%s", voiceID)

	resp, err := c.doRequest(ctx, http.MethodGet, path, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, c.handleErrorResponse(resp)
	}

	var voice VoiceV2
	if err := json.NewDecoder(resp.Body).Decode(&voice); err != nil {
		return nil, fmt.Errorf("failed to decode voice response: %w", err)
	}

	return &voice, nil
}

// GetVoices retrieves the list of available voices (V1 API - deprecated)
// Deprecated: Use GetVoicesV2 for enhanced metadata and filtering options
func (c *Client) GetVoices(ctx context.Context, model TTSModel) ([]VoiceV1, error) {
	path := "/v1/voices"
	if model != "" {
		path = path + "?model=" + string(model)
	}

	resp, err := c.doRequest(ctx, http.MethodGet, path, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, c.handleErrorResponse(resp)
	}

	var voices []VoiceV1
	if err := json.NewDecoder(resp.Body).Decode(&voices); err != nil {
		return nil, fmt.Errorf("failed to decode voices response: %w", err)
	}

	return voices, nil
}

// GetVoice retrieves a specific voice by ID (V1 API - deprecated)
// Deprecated: Use GetVoiceV2 for enhanced metadata
func (c *Client) GetVoice(ctx context.Context, voiceID string, model TTSModel) ([]VoiceV1, error) {
	path := fmt.Sprintf("/v1/voices/%s", voiceID)
	if model != "" {
		path = path + "?model=" + string(model)
	}

	resp, err := c.doRequest(ctx, http.MethodGet, path, nil)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, c.handleErrorResponse(resp)
	}

	var voices []VoiceV1
	if err := json.NewDecoder(resp.Body).Decode(&voices); err != nil {
		return nil, fmt.Errorf("failed to decode voice response: %w", err)
	}

	return voices, nil
}
