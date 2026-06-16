//! Typecast API client
//!
//! This module contains the main client for interacting with the Typecast API.

use crate::errors::{Result, TypecastError};
use crate::models::{
    Age, AudioFormat, CustomVoice, ErrorResponse, Gender, GenerateToFileRequest,
    SubscriptionResponse, TTSModel, TTSRequest, TTSRequestStream, TTSResponse, UseCase, VoiceV2,
    VoicesV2Filter, CLONING_MAX_FILE_SIZE, NAME_MAX_LENGTH, NAME_MIN_LENGTH,
};
use bytes::Bytes;
use futures_util::stream::{Stream, StreamExt};
use reqwest::header::{HeaderMap, HeaderValue, CONTENT_TYPE};
use std::env;
use std::fs;
use std::path::Path;
use std::pin::Pin;
use std::time::Duration;

/// Boxed asynchronous stream of audio chunks returned by the streaming TTS endpoint.
pub type AudioByteStream = Pin<Box<dyn Stream<Item = Result<Bytes>> + Send>>;

/// Convert a [`TTSModel`] into the wire format string used in query parameters.
fn model_query_value(model: TTSModel) -> &'static str {
    match model {
        TTSModel::SsfmV30 => "ssfm-v30",
        TTSModel::SsfmV21 => "ssfm-v21",
    }
}

/// Convert a [`Gender`] into the wire format string used in query parameters.
fn gender_query_value(gender: Gender) -> &'static str {
    match gender {
        Gender::Male => "male",
        Gender::Female => "female",
    }
}

/// Convert an [`Age`] into the wire format string used in query parameters.
fn age_query_value(age: Age) -> &'static str {
    match age {
        Age::Child => "child",
        Age::Teenager => "teenager",
        Age::YoungAdult => "young_adult",
        Age::MiddleAge => "middle_age",
        Age::Elder => "elder",
    }
}

/// Convert a [`UseCase`] into the wire format string used in query parameters.
fn use_case_query_value(use_case: UseCase) -> &'static str {
    match use_case {
        UseCase::Announcer => "Announcer",
        UseCase::Anime => "Anime",
        UseCase::Audiobook => "Audiobook",
        UseCase::Conversational => "Conversational",
        UseCase::Documentary => "Documentary",
        UseCase::ELearning => "E-learning",
        UseCase::Rapper => "Rapper",
        UseCase::Game => "Game",
        UseCase::TikTokReels => "Tiktok/Reels",
        UseCase::News => "News",
        UseCase::Podcast => "Podcast",
        UseCase::Voicemail => "Voicemail",
        UseCase::Ads => "Ads",
    }
}

fn infer_audio_format_from_path(path: &Path) -> Option<AudioFormat> {
    match path.extension().and_then(|extension| extension.to_str()) {
        Some(extension) if extension.eq_ignore_ascii_case("mp3") => Some(AudioFormat::Mp3),
        Some(extension) if extension.eq_ignore_ascii_case("wav") => Some(AudioFormat::Wav),
        _ => None,
    }
}

/// Default API base URL
pub const DEFAULT_BASE_URL: &str = "https://api.typecast.ai";

/// Default request timeout in seconds
pub const DEFAULT_TIMEOUT_SECS: u64 = 60;

/// Configuration for the Typecast client
#[derive(Debug, Clone)]
pub struct ClientConfig {
    /// API key for authentication. Optional when using a proxy base URL.
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
            base_url: env::var("TYPECAST_API_HOST")
                .unwrap_or_else(|_| DEFAULT_BASE_URL.to_string()),
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
        let api_key = config.api_key.trim().to_string();
        let base_url = config.base_url.trim().trim_end_matches('/').to_string();
        if api_key.is_empty() && is_default_base_url(&base_url) {
            return Err(TypecastError::Unauthorized {
                detail: "API key is required for the default Typecast API host".to_string(),
            });
        }

        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));
        if !api_key.is_empty() {
            headers.insert(
                "X-API-KEY",
                HeaderValue::from_str(&api_key).map_err(|_| TypecastError::BadRequest {
                    detail: "Invalid API key format".to_string(),
                })?,
            );
        }

        // `reqwest::Client::builder().build()` only fails if TLS init fails,
        // which is not something we can usefully recover from at this layer.
        let client = reqwest::Client::builder()
            .default_headers(headers)
            .timeout(config.timeout)
            .build()
            .expect("reqwest client builder should not fail");

        Ok(Self {
            client,
            base_url,
            api_key,
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
            format!(
                "{}...{}",
                &self.api_key[..4],
                &self.api_key[self.api_key.len() - 4..]
            )
        } else {
            "****".to_string()
        }
    }

    fn with_auth_header(&self, request: reqwest::RequestBuilder) -> reqwest::RequestBuilder {
        if self.api_key.is_empty() {
            request
        } else {
            request.header("X-API-KEY", &self.api_key)
        }
    }

    /// Build a URL with optional query parameters.
    ///
    /// Callers must pass `None` when there are no query parameters; passing
    /// `Some(vec![])` is not supported and will produce a trailing `?`.
    fn build_url(&self, path: &str, params: Option<Vec<(&str, String)>>) -> String {
        let base = format!("{}{}", self.base_url, path);
        match params {
            Some(params) => {
                let query: Vec<String> = params
                    .into_iter()
                    .map(|(k, v)| format!("{}={}", k, urlencoding::encode(&v)))
                    .collect();
                format!("{}?{}", base, query.join("&"))
            }
            None => base,
        }
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

        let response = self.client.post(&url).json(request).send().await?;

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

    /// Convert text to speech and write the audio bytes to a file.
    ///
    /// If `request.output.audio_format` is omitted, the format is inferred from
    /// a `.mp3` or `.wav` file extension.
    pub async fn generate_to_file(
        &self,
        path: impl AsRef<Path>,
        request: GenerateToFileRequest,
    ) -> Result<TTSResponse> {
        let path_ref = path.as_ref();
        let mut tts_request = request.into_tts_request();
        let inferred = infer_audio_format_from_path(path_ref);
        match tts_request.output.as_mut() {
            Some(output) => {
                if output.audio_format.is_none() {
                    output.audio_format = inferred;
                }
            }
            None => {
                if let Some(format) = inferred {
                    tts_request.output = Some(crate::models::Output::new().audio_format(format));
                }
            }
        }

        let response = self.text_to_speech(&tts_request).await?;
        fs::write(path_ref, &response.audio_data)
            .map_err(|e| TypecastError::IoError(e.to_string()))?;
        Ok(response)
    }

    /// Convert text to speech as a streaming response
    ///
    /// Returns a stream of audio byte chunks. For `wav` output the first chunk
    /// contains the WAV header followed by PCM samples; for `mp3` output each
    /// chunk is independently decodable.
    ///
    /// # Arguments
    ///
    /// * `request` - The streaming TTS request
    ///
    /// # Returns
    ///
    /// A pinned boxed [`Stream`] yielding [`Result<Bytes>`] chunks.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use futures_util::StreamExt;
    /// use typecast_rust::{TypecastClient, TTSRequestStream, TTSModel};
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// let request = TTSRequestStream::new(
    ///     "tc_60e5426de8b95f1d3000d7b5",
    ///     "Hello, world!",
    ///     TTSModel::SsfmV30,
    /// );
    /// let mut stream = client.text_to_speech_stream(&request).await?;
    /// while let Some(chunk) = stream.next().await {
    ///     let bytes = chunk?;
    ///     // write bytes to file or audio sink
    ///     let _ = bytes;
    /// }
    /// # Ok(())
    /// # }
    /// ```
    pub async fn text_to_speech_stream(
        &self,
        request: &TTSRequestStream,
    ) -> Result<AudioByteStream> {
        let url = self.build_url("/v1/text-to-speech/stream", None);

        let response = self.client.post(&url).json(request).send().await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let stream = response
            .bytes_stream()
            .map(|item| item.map_err(TypecastError::from));
        Ok(Box::pin(stream))
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
                params.push(("model", model_query_value(model).to_string()));
            }
            if let Some(gender) = f.gender {
                params.push(("gender", gender_query_value(gender).to_string()));
            }
            if let Some(age) = f.age {
                params.push(("age", age_query_value(age).to_string()));
            }
            if let Some(use_cases) = f.use_cases {
                params.push(("use_cases", use_case_query_value(use_cases).to_string()));
            }
        }

        let url = self.build_url(
            "/v2/voices",
            if params.is_empty() {
                None
            } else {
                Some(params)
            },
        );

        let response = self.client.get(&url).send().await?;

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

        let response = self.client.get(&url).send().await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let voice: VoiceV2 = response.json().await?;
        Ok(voice)
    }

    /// Convert text to speech with word- and/or character-level timestamps.
    ///
    /// # Arguments
    ///
    /// * `request` - The TTS request with timestamps parameters.
    /// * `granularity` - Optional granularity: `None` (both), `"word"`, or `"char"`.
    ///
    /// # Returns
    ///
    /// A [`crate::timestamps::TTSWithTimestampsResponse`] containing the Base64-encoded audio
    /// and alignment segment arrays.  Use the response's `.to_srt()` / `.to_vtt()` methods to
    /// generate subtitle files.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::{TypecastClient, TTSModel};
    /// use typecast_rust::timestamps::TTSRequestWithTimestamps;
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// let request = TTSRequestWithTimestamps::new(
    ///     "tc_60e5426de8b95f1d3000d7b5",
    ///     "Hello, world!",
    ///     TTSModel::SsfmV30,
    /// );
    /// let response = client.text_to_speech_with_timestamps(&request, None).await?;
    /// let srt = response.to_srt()?;
    /// println!("{}", srt);
    /// # Ok(())
    /// # }
    /// ```
    pub async fn text_to_speech_with_timestamps(
        &self,
        request: &crate::timestamps::TTSRequestWithTimestamps,
        granularity: Option<&str>,
    ) -> Result<crate::timestamps::TTSWithTimestampsResponse> {
        if let Some(g) = granularity {
            if g != "word" && g != "char" {
                return Err(TypecastError::ValidationError {
                    detail: format!(
                        "granularity must be None, \"word\", or \"char\"; got {:?}",
                        g
                    ),
                });
            }
        }

        let url = match granularity {
            Some(g) => self.build_url(
                "/v1/text-to-speech/with-timestamps",
                Some(vec![("granularity", g.to_string())]),
            ),
            None => self.build_url("/v1/text-to-speech/with-timestamps", None),
        };

        let response = self.client.post(&url).json(request).send().await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let parsed: crate::timestamps::TTSWithTimestampsResponse = response
            .json()
            .await
            .map_err(|e| TypecastError::DecodeError(e.to_string()))?;
        Ok(parsed)
    }

    /// Get the authenticated user's subscription
    ///
    /// # Returns
    ///
    /// Returns a `SubscriptionResponse` containing the user's plan, credits,
    /// and usage limits.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::TypecastClient;
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// let subscription = client.get_my_subscription().await?;
    /// println!("Plan: {:?}", subscription.plan);
    /// println!(
    ///     "Credits: {}/{}",
    ///     subscription.credits.used_credits, subscription.credits.plan_credits
    /// );
    /// # Ok(())
    /// # }
    /// ```
    pub async fn get_my_subscription(&self) -> Result<SubscriptionResponse> {
        let url = self.build_url("/v1/users/me/subscription", None);

        let response = self.client.get(&url).send().await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let subscription: SubscriptionResponse = response.json().await?;
        Ok(subscription)
    }

    /// Clone a voice from an audio recording.
    ///
    /// Uploads the audio file as `multipart/form-data` to `POST /v1/voices/clone`
    /// and returns a [`CustomVoice`] representing the newly created voice.
    ///
    /// # Arguments
    ///
    /// * `audio` - Raw audio bytes (WAV or MP3). Must not exceed 25 MB.
    /// * `filename` - File name used in the multipart part (e.g. `"sample.wav"`).
    ///   The extension determines the MIME type sent to the API.
    /// * `name` - Display name for the custom voice (1–30 characters).
    /// * `model` - TTS model to use for cloning (e.g. `"ssfm-v30"`).
    ///
    /// # Errors
    ///
    /// Returns [`TypecastError::ValidationError`] if `name` is outside 1–30 characters
    /// or if `audio` exceeds the 25 MB limit before any network call is made.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::TypecastClient;
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// let audio = std::fs::read("sample.wav").unwrap();
    /// let voice = client.clone_voice(audio, "sample.wav", "My Voice", "ssfm-v30").await?;
    /// println!("Cloned voice ID: {}", voice.voice_id);
    /// # Ok(())
    /// # }
    /// ```
    pub async fn clone_voice(
        &self,
        audio: Vec<u8>,
        filename: &str,
        name: &str,
        model: &str,
    ) -> Result<CustomVoice> {
        let name_len = name.chars().count();
        if !(NAME_MIN_LENGTH..=NAME_MAX_LENGTH).contains(&name_len) {
            return Err(TypecastError::ValidationError {
                detail: format!(
                    "name must be {}-{} characters; got {}",
                    NAME_MIN_LENGTH, NAME_MAX_LENGTH, name_len
                ),
            });
        }
        if audio.len() > CLONING_MAX_FILE_SIZE {
            return Err(TypecastError::ValidationError {
                detail: format!("audio file exceeds 25MB limit; got {} bytes", audio.len()),
            });
        }

        let mime = guess_audio_mime(filename);
        let part = reqwest::multipart::Part::bytes(audio)
            .file_name(filename.to_string())
            .mime_str(mime)
            .expect("guess_audio_mime only returns valid MIME constants");
        let form = reqwest::multipart::Form::new()
            .text("name", name.to_string())
            .text("model", model.to_string())
            .part("file", part);

        let url = self.build_url("/v1/voices/clone", None);
        let response = self
            .with_auth_header(self.client.post(&url))
            .multipart(form)
            .send()
            .await?;

        if !response.status().is_success() {
            return Err(self.handle_error_response(response).await);
        }

        let voice: CustomVoice = response.json().await?;
        Ok(voice)
    }

    /// Delete a custom (cloned) voice by its ID.
    ///
    /// Sends `DELETE /v1/voices/{voice_id}`. A 204 No Content response is
    /// treated as success; any other non-2xx status is mapped to a
    /// [`TypecastError`].
    ///
    /// # Arguments
    ///
    /// * `voice_id` - The voice ID returned by [`TypecastClient::clone_voice`].
    ///
    /// # Example
    ///
    /// ```no_run
    /// use typecast_rust::TypecastClient;
    ///
    /// # async fn example() -> typecast_rust::Result<()> {
    /// let client = TypecastClient::from_env()?;
    /// client.delete_voice("cv_abc123").await?;
    /// println!("Voice deleted.");
    /// # Ok(())
    /// # }
    /// ```
    pub async fn delete_voice(&self, voice_id: &str) -> Result<()> {
        let url = self.build_url(&format!("/v1/voices/{}", voice_id), None);
        let response = self
            .with_auth_header(self.client.delete(&url))
            .send()
            .await?;

        let status = response.status();
        if !status.is_success() {
            return Err(self.handle_error_response(response).await);
        }
        Ok(())
    }
}

/// Infer an audio MIME type from a filename extension.
///
/// Defaults to `application/octet-stream` for unrecognised extensions.
fn guess_audio_mime(filename: &str) -> &'static str {
    let lower = filename.to_lowercase();
    if lower.ends_with(".wav") {
        "audio/wav"
    } else if lower.ends_with(".mp3") {
        "audio/mpeg"
    } else if lower.ends_with(".ogg") {
        "audio/ogg"
    } else if lower.ends_with(".flac") {
        "audio/flac"
    } else if lower.ends_with(".m4a") {
        "audio/mp4"
    } else {
        "application/octet-stream"
    }
}

fn is_default_base_url(base_url: &str) -> bool {
    base_url.eq_ignore_ascii_case(DEFAULT_BASE_URL)
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
