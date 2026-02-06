//! Typecast API client
//!
//! This module contains the main client for interacting with the Typecast API.

use crate::errors::{Result, TypecastError};
use crate::models::{
    AudioFormat, ErrorResponse, TTSRequest, TTSResponse, VoiceV2, VoicesV2Filter,
};
use reqwest::header::{HeaderMap, HeaderValue, CONTENT_TYPE};
use std::env;
use std::time::Duration;

/// Default API base URL
pub const DEFAULT_BASE_URL: &str = "https://api.typecast.ai";

/// Default request timeout in seconds
pub const DEFAULT_TIMEOUT_SECS: u64 = 60;

/// Configuration for the Typecast client
#[derive(Debug, Clone)]
pub struct ClientConfig {
    /// API key for authentication
    pub api_key: String,
    /// Base URL for the API (defaults to <https://api.typecast.ai>)
    pub base_url: String,
    /// Request timeout duration
    pub timeout: Duration,
}

impl Default for ClientConfig {
    fn default() -> Self {
        Self {
            api_key: env::var("TYPECAST_API_KEY").unwrap_or_default(),
            base_url: env::var("TYPECAST_API_HOST").unwrap_or_else(|_| DEFAULT_BASE_URL.to_string()),
            timeout: Duration::from_secs(DEFAULT_TIMEOUT_SECS),
        }
    }
}

impl ClientConfig {
    /// Create a new configuration with an API key
    pub fn new(api_key: impl Into<String>) -> Self {
        Self {
            api_key: api_key.into(),
            ..Default::default()
        }
    }

    /// Set a custom base URL
    pub fn base_url(mut self, base_url: impl Into<String>) -> Self {
        self.base_url = base_url.into();
        self
    }

    /// Set a custom timeout
    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = timeout;
        self
    }
}

/// The main Typecast API client
#[derive(Debug, Clone)]
pub struct TypecastClient {
    client: reqwest::Client,
    base_url: String,
    api_key: String,
}

impl TypecastClient {
    /// Create a new TypecastClient with the given configuration
    pub fn new(config: ClientConfig) -> Result<Self> {
        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));
        headers.insert(
            "X-API-KEY",
            HeaderValue::from_str(&config.api_key)
                .map_err(|_| TypecastError::BadRequest { 
                    detail: "Invalid API key format".to_string() 
                })?,
        );

        let client = reqwest::Client::builder()
            .default_headers(headers)
            .timeout(config.timeout)
            .build()?;

        Ok(Self {
            client,
            base_url: config.base_url,
            api_key: config.api_key,
        })
    }

    /// Create a new TypecastClient from environment variables
    ///
    /// Reads TYPECAST_API_KEY and optionally TYPECAST_API_HOST
    pub fn from_env() -> Result<Self> {
        Self::new(ClientConfig::default())
    }

    /// Create a new TypecastClient with just an API key
    pub fn with_api_key(api_key: impl Into<String>) -> Result<Self> {
        Self::new(ClientConfig::new(api_key))
    }

    /// Get the base URL
    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    /// Get the API key (masked)
    pub fn api_key_masked(&self) -> String {
        if self.api_key.len() > 8 {
            format!("{}...{}", &self.api_key[..4], &self.api_key[self.api_key.len()-4..])
        } else {
            "****".to_string()
        }
    }

    /// Build a URL with optional query parameters
    fn build_url(&self, path: &str, params: Option<Vec<(&str, String)>>) -> String {
        let mut url = format!("{}{}", self.base_url, path);
        if let Some(params) = params {
            let query: Vec<String> = params
                .into_iter()
                .map(|(k, v)| format!("{}={}", k, urlencoding::encode(&v)))
                .collect();
            if !query.is_empty() {
                url = format!("{}?{}", url, query.join("&"));
            }
        }
        url
    }

    /// Handle an error response
    async fn handle_error_response(&self, response: reqwest::Response) -> TypecastError {
        let status_code = response.status().as_u16();
        let error_response: Option<ErrorResponse> = response.json().await.ok();
        TypecastError::from_response(status_code, error_response)
    }

    /// Convert text to speech
    ///
    /// # Arguments
    ///
    /// * `request` - The TTS request containing text, voice_id, model, and optional settings
    ///
    /// # Returns
    ///
    /// Returns a `TTSResponse` containing the audio data, duration, and format
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::{TypecastClient, TTSRequest, TTSModel, ClientConfig};
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// let request = TTSRequest::new(
    ///     "tc_60e5426de8b95f1d3000d7b5",
    ///     "Hello, world!",
    ///     TTSModel::SsfmV30,
    /// );
    /// let response = client.text_to_speech(&request).await?;
    /// println!("Audio duration: {} seconds", response.duration);
    /// # Ok(())
    /// # }
    /// ```
    pub async fn text_to_speech(&self, request: &TTSRequest) -> Result<TTSResponse> {
        let url = self.build_url("/v1/text-to-speech", None);
        
        let response = self.client
            .post(&url)
            .json(request)
            .send()
            .await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        // Parse content type for format
        let content_type = response
            .headers()
            .get(CONTENT_TYPE)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("audio/wav");
        
        let format = if content_type.contains("mp3") || content_type.contains("mpeg") {
            AudioFormat::Mp3
        } else {
            AudioFormat::Wav
        };

        // Parse duration from header
        let duration = response
            .headers()
            .get("X-Audio-Duration")
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.parse::<f64>().ok())
            .unwrap_or(0.0);

        let audio_data = response.bytes().await?.to_vec();

        Ok(TTSResponse {
            audio_data,
            duration,
            format,
        })
    }

    /// Get voices with enhanced metadata (V2 API)
    ///
    /// # Arguments
    ///
    /// * `filter` - Optional filter for voices (model, gender, age, use_cases)
    ///
    /// # Returns
    ///
    /// Returns a list of `VoiceV2` with enhanced metadata
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::{TypecastClient, VoicesV2Filter, TTSModel, Gender, ClientConfig};
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// 
    /// // Get all voices
    /// let voices = client.get_voices_v2(None).await?;
    /// 
    /// // Get filtered voices
    /// let filter = VoicesV2Filter::new()
    ///     .model(TTSModel::SsfmV30)
    ///     .gender(Gender::Female);
    /// let filtered_voices = client.get_voices_v2(Some(filter)).await?;
    /// # Ok(())
    /// # }
    /// ```
    pub async fn get_voices_v2(&self, filter: Option<VoicesV2Filter>) -> Result<Vec<VoiceV2>> {
        let mut params = Vec::new();

        if let Some(f) = filter {
            if let Some(model) = f.model {
                params.push(("model", serde_json::to_string(&model)?.trim_matches('"').to_string()));
            }
            if let Some(gender) = f.gender {
                params.push(("gender", serde_json::to_string(&gender)?.trim_matches('"').to_string()));
            }
            if let Some(age) = f.age {
                params.push(("age", serde_json::to_string(&age)?.trim_matches('"').to_string()));
            }
            if let Some(use_cases) = f.use_cases {
                params.push(("use_cases", serde_json::to_string(&use_cases)?.trim_matches('"').to_string()));
            }
        }

        let url = self.build_url("/v2/voices", if params.is_empty() { None } else { Some(params) });

        let response = self.client
            .get(&url)
            .send()
            .await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let voices: Vec<VoiceV2> = response.json().await?;
        Ok(voices)
    }

    /// Get a specific voice by ID with enhanced metadata (V2 API)
    ///
    /// # Arguments
    ///
    /// * `voice_id` - The voice ID (e.g., 'tc_60e5426de8b95f1d3000d7b5')
    ///
    /// # Returns
    ///
    /// Returns a `VoiceV2` with enhanced metadata
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::{TypecastClient, ClientConfig};
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// let voice = client.get_voice_v2("tc_60e5426de8b95f1d3000d7b5").await?;
    /// println!("Voice: {} ({})", voice.voice_name, voice.voice_id);
    /// # Ok(())
    /// # }
    /// ```
    pub async fn get_voice_v2(&self, voice_id: &str) -> Result<VoiceV2> {
        let url = self.build_url(&format!("/v2/voices/{}", voice_id), None);

        let response = self.client
            .get(&url)
            .send()
            .await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let voice: VoiceV2 = response.json().await?;
        Ok(voice)
    }
}

/// URL encoding helper
mod urlencoding {
    pub fn encode(s: &str) -> String {
        url_encode(s)
    }

    fn url_encode(s: &str) -> String {
        let mut result = String::new();
        for c in s.chars() {
            match c {
                'a'..='z' | 'A'..='Z' | '0'..='9' | '-' | '_' | '.' | '~' => {
                    result.push(c);
                }
                _ => {
                    for b in c.to_string().as_bytes() {
                        result.push_str(&format!("%{:02X}", b));
                    }
                }
            }
        }
        result
    }
}
