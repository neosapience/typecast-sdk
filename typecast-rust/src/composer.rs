use crate::client::TypecastClient;
use crate::errors::{Result, TypecastError};
use crate::models::{AudioFormat, Output, TTSModel, TTSPrompt, TTSRequest, TTSResponse};
use serde::Serialize;

#[derive(Debug, Clone, PartialEq)]
pub enum SpeechPart {
    Text(String),
    Pause(f64),
}

#[derive(Debug, Clone, Default)]
pub struct ComposerSettings {
    pub voice_id: Option<String>,
    pub model: Option<TTSModel>,
    pub language: Option<String>,
    pub prompt: Option<TTSPrompt>,
    pub output: Option<Output>,
    pub seed: Option<i32>,
}

impl ComposerSettings {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn voice_id(mut self, voice_id: impl Into<String>) -> Self {
        self.voice_id = Some(voice_id.into());
        self
    }

    pub fn model(mut self, model: TTSModel) -> Self {
        self.model = Some(model);
        self
    }

    pub fn language(mut self, language: impl Into<String>) -> Self {
        self.language = Some(language.into());
        self
    }

    pub fn prompt(mut self, prompt: impl Into<TTSPrompt>) -> Self {
        self.prompt = Some(prompt.into());
        self
    }

    pub fn output(mut self, output: Output) -> Self {
        self.output = Some(output);
        self
    }

    pub fn seed(mut self, seed: i32) -> Self {
        self.seed = Some(seed);
        self
    }
}

#[derive(Debug, Clone)]
enum ComposerPart {
    Speech {
        text: String,
        settings: Box<ComposerSettings>,
    },
    Pause(f64),
}

#[derive(Serialize)]
#[serde(tag = "type")]
enum ComposeSegment {
    #[serde(rename = "tts")]
    Tts {
        #[serde(flatten)]
        request: Box<TTSRequest>,
    },
    #[serde(rename = "pause")]
    Pause { duration_seconds: f64 },
}

#[derive(Serialize)]
struct ComposeRequest {
    segments: Vec<ComposeSegment>,
}

pub struct SpeechComposer<'a> {
    client: &'a TypecastClient,
    defaults: ComposerSettings,
    parts: Vec<ComposerPart>,
}

impl<'a> SpeechComposer<'a> {
    pub(crate) fn new(client: &'a TypecastClient) -> Self {
        Self {
            client,
            defaults: ComposerSettings::new(),
            parts: Vec::new(),
        }
    }

    pub fn defaults(mut self, settings: ComposerSettings) -> Self {
        self.defaults = merge_settings(&self.defaults, &settings);
        self
    }

    pub fn say(mut self, text: impl Into<String>) -> Self {
        self.parts.push(ComposerPart::Speech {
            text: text.into(),
            settings: Box::new(self.defaults.clone()),
        });
        self
    }

    pub fn say_with(mut self, text: impl Into<String>, settings: ComposerSettings) -> Self {
        self.parts.push(ComposerPart::Speech {
            text: text.into(),
            settings: Box::new(merge_settings(&self.defaults, &settings)),
        });
        self
    }

    /// Inserts silence between speech segments.
    ///
    /// `seconds` is a duration in seconds. Use `0.3` for 300 ms, `3.0` for
    /// three seconds.
    pub fn pause(mut self, seconds: f64) -> Self {
        self.parts.push(ComposerPart::Pause(seconds));
        self
    }

    pub async fn generate(self) -> Result<TTSResponse> {
        let plan = self.build_plan()?;
        if !plan
            .iter()
            .any(|part| matches!(part, ComposerPart::Speech { .. }))
        {
            return Err(TypecastError::ValidationError {
                detail: "at least one speech segment is required".to_string(),
            });
        }

        let output_format = self
            .defaults
            .output
            .as_ref()
            .and_then(|output| output.audio_format)
            .unwrap_or(AudioFormat::Wav);

        let mut segments = Vec::with_capacity(plan.len());
        for part in plan {
            match part {
                ComposerPart::Pause(duration_seconds) => {
                    segments.push(ComposeSegment::Pause { duration_seconds })
                }
                ComposerPart::Speech { text, settings } => {
                    segments.push(ComposeSegment::Tts {
                        request: Box::new(request_from_settings(text, *settings, output_format)?),
                    });
                }
            }
        }
        self.client
            .compose_text_to_speech(&ComposeRequest { segments })
            .await
    }

    fn build_plan(&self) -> Result<Vec<ComposerPart>> {
        let mut plan = Vec::new();
        for part in &self.parts {
            match part {
                ComposerPart::Pause(seconds) => {
                    if !seconds.is_finite() || *seconds <= 0.0 {
                        return Err(TypecastError::ValidationError {
                            detail: "pause seconds must be greater than 0".to_string(),
                        });
                    }
                    plan.push(ComposerPart::Pause(*seconds));
                }
                ComposerPart::Speech { text, settings } => {
                    for parsed in parse_pause_markup(text) {
                        match parsed {
                            SpeechPart::Pause(seconds) => plan.push(ComposerPart::Pause(seconds)),
                            SpeechPart::Text(text) => {
                                if !text.trim().is_empty() {
                                    if settings.voice_id.as_deref().unwrap_or("").is_empty() {
                                        return Err(TypecastError::ValidationError {
                                            detail:
                                                "voice_id is required for composed speech segments"
                                                    .to_string(),
                                        });
                                    }
                                    if settings.model.is_none() {
                                        return Err(TypecastError::ValidationError {
                                            detail:
                                                "model is required for composed speech segments"
                                                    .to_string(),
                                        });
                                    }
                                    plan.push(ComposerPart::Speech {
                                        text,
                                        settings: settings.clone(),
                                    });
                                }
                            }
                        }
                    }
                }
            }
        }
        Ok(plan)
    }
}

pub fn parse_pause_markup(text: &str) -> Vec<SpeechPart> {
    let mut parts = Vec::new();
    let mut last_emit = 0usize;
    let mut search_from = 0usize;

    while let Some(relative_start) = text[search_from..].find("<|") {
        let token_start = search_from + relative_start;
        let body_start = token_start + 2;
        let Some(relative_end) = text[body_start..].find("|>") else {
            break;
        };
        let body_end = body_start + relative_end;
        let token_end = body_end + 2;
        let token_body = &text[body_start..body_end];

        if let Some(seconds_text) = token_body.strip_suffix('s') {
            if valid_seconds_literal(seconds_text) {
                let seconds = seconds_text
                    .parse::<f64>()
                    .expect("validated seconds literals parse as f64");
                if token_start > last_emit {
                    parts.push(SpeechPart::Text(text[last_emit..token_start].to_string()));
                }
                parts.push(SpeechPart::Pause(seconds));
                last_emit = token_end;
                search_from = token_end;
                continue;
            }
        }

        search_from = body_start;
    }

    if last_emit < text.len() {
        parts.push(SpeechPart::Text(text[last_emit..].to_string()));
    }
    parts
}

fn valid_seconds_literal(value: &str) -> bool {
    let mut split = value.split('.');
    let whole = split.next().unwrap_or("");
    let fraction = split.next();
    if split.next().is_some() || whole.is_empty() || !whole.chars().all(|c| c.is_ascii_digit()) {
        return false;
    }
    match fraction {
        Some(fraction) => !fraction.is_empty() && fraction.chars().all(|c| c.is_ascii_digit()),
        None => true,
    }
}

fn merge_settings(
    base: &ComposerSettings,
    override_settings: &ComposerSettings,
) -> ComposerSettings {
    ComposerSettings {
        voice_id: override_settings
            .voice_id
            .clone()
            .or_else(|| base.voice_id.clone()),
        model: override_settings.model.or(base.model),
        language: override_settings
            .language
            .clone()
            .or_else(|| base.language.clone()),
        prompt: override_settings
            .prompt
            .clone()
            .or_else(|| base.prompt.clone()),
        output: merge_output(base.output.clone(), override_settings.output.clone()),
        seed: override_settings.seed.or(base.seed),
    }
}

fn merge_output(base: Option<Output>, override_output: Option<Output>) -> Option<Output> {
    match (base, override_output) {
        (None, None) => None,
        (Some(output), None) | (None, Some(output)) => Some(output),
        (Some(base), Some(override_output)) => Some(Output {
            volume: override_output.volume.or(base.volume),
            target_lufs: override_output.target_lufs.or(base.target_lufs),
            audio_pitch: override_output.audio_pitch.or(base.audio_pitch),
            audio_tempo: override_output.audio_tempo.or(base.audio_tempo),
            audio_format: override_output.audio_format.or(base.audio_format),
        }),
    }
}

fn request_from_settings(
    text: String,
    settings: ComposerSettings,
    format: AudioFormat,
) -> Result<TTSRequest> {
    Ok(TTSRequest {
        voice_id: settings
            .voice_id
            .expect("build_plan validates composed speech voice_id"),
        text,
        model: settings
            .model
            .expect("build_plan validates composed speech model"),
        language: settings.language,
        prompt: settings.prompt,
        output: merge_output(settings.output, Some(Output::new().audio_format(format))),
        seed: settings.seed,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::client::ClientConfig;
    use crate::models::{EmotionPreset, PresetPrompt};
    use mockito::Server;
    use std::time::Duration;

    #[tokio::test]
    async fn compose_speech_smoke_for_lib_binary_coverage() {
        let mut server = Server::new_async().await;
        let mock = server
            .mock("POST", "/v1/text-to-speech/compose")
            .match_body(mockito::Matcher::Regex(
                r#"\"type\":\"pause\",\"duration_seconds\":0.001"#.to_string(),
            ))
            .with_status(200)
            .with_header("content-type", "audio/wav")
            .with_header("x-audio-duration", "1.25")
            .with_body("composed-audio")
            .create_async()
            .await;

        let config = ClientConfig::new("test-api-key")
            .base_url(server.url())
            .timeout(Duration::from_secs(5));
        let client = TypecastClient::new(config).expect("client builds");
        let response = client
            .compose_speech()
            .defaults(
                ComposerSettings::new()
                    .voice_id("voice-a")
                    .model(TTSModel::SsfmV30)
                    .language("eng")
                    .prompt(PresetPrompt::new().emotion_preset(EmotionPreset::Normal))
                    .output(Output::new().audio_format(AudioFormat::Wav))
                    .seed(1),
            )
            .say("Hello<|0.001s|>there")
            .say_with(
                "World<|0.001s|>again",
                ComposerSettings::new()
                    .voice_id("voice-b")
                    .model(TTSModel::SsfmV30),
            )
            .generate()
            .await
            .unwrap();

        assert_eq!(response.format, AudioFormat::Wav);
        assert_eq!(response.audio_data, b"composed-audio");
        assert_eq!(response.duration, 1.25);
        mock.assert_async().await;
    }
}
