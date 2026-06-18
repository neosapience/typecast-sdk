use crate::client::TypecastClient;
use crate::errors::{Result, TypecastError};
use crate::models::{AudioFormat, Output, TTSModel, TTSPrompt, TTSRequest, TTSResponse};

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
        settings: ComposerSettings,
    },
    Pause(f64),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct WavSpec {
    sample_rate: u32,
    channels: u16,
    bits_per_sample: u16,
}

#[derive(Debug, Clone)]
struct ParsedWav {
    spec: WavSpec,
    samples: Vec<i16>,
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
            settings: self.defaults.clone(),
        });
        self
    }

    pub fn say_with(mut self, text: impl Into<String>, settings: ComposerSettings) -> Self {
        self.parts.push(ComposerPart::Speech {
            text: text.into(),
            settings: merge_settings(&self.defaults, &settings),
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
            return validation_error("at least one speech segment is required");
        }

        let output_format = self
            .defaults
            .output
            .as_ref()
            .and_then(|output| output.audio_format)
            .unwrap_or(AudioFormat::Wav);

        let mut wav_spec: Option<WavSpec> = None;
        let mut output_samples = Vec::new();
        for part in plan {
            match part {
                ComposerPart::Pause(seconds) => {
                    if !seconds.is_finite() || seconds <= 0.0 {
                        return validation_error("pause seconds must be greater than 0");
                    }
                    let Some(spec) = wav_spec else {
                        return validation_error("pause cannot be the first composed part");
                    };
                    output_samples.extend(vec![0; seconds_to_samples(seconds, spec.sample_rate)]);
                }
                ComposerPart::Speech { text, settings } => {
                    let response = self
                        .client
                        .text_to_speech(&request_from_settings(text, settings)?)
                        .await?;
                    let wav = parse_wav(&response.audio_data)?;
                    if let Some(spec) = wav_spec {
                        if spec != wav.spec {
                            return validation_error(
                                "all composed WAV segments must use the same PCM format",
                            );
                        }
                    }
                    wav_spec = Some(wav.spec);
                    output_samples.extend(trim_silence(&wav.samples));
                }
            }
        }

        let Some(spec) = wav_spec else {
            return validation_error("at least one speech segment is required");
        };
        let audio_data = encode_wav(&output_samples, spec);
        if output_format == AudioFormat::Mp3 {
            return validation_error("ffmpeg is required to encode composed speech as mp3");
        }

        Ok(TTSResponse {
            audio_data,
            duration: output_samples.len() as f64 / spec.sample_rate as f64,
            format: AudioFormat::Wav,
        })
    }

    fn build_plan(&self) -> Result<Vec<ComposerPart>> {
        let mut plan = Vec::new();
        for part in &self.parts {
            match part {
                ComposerPart::Pause(seconds) => {
                    if !seconds.is_finite() || *seconds <= 0.0 {
                        return validation_error("pause seconds must be greater than 0");
                    }
                    plan.push(ComposerPart::Pause(*seconds));
                }
                ComposerPart::Speech { text, settings } => {
                    for parsed in parse_pause_markup(text) {
                        match parsed {
                            SpeechPart::Pause(seconds) => plan.push(ComposerPart::Pause(seconds)),
                            SpeechPart::Text(text) => {
                                if text.trim().is_empty() {
                                    continue;
                                }
                                if settings.voice_id.as_deref().unwrap_or("").is_empty() {
                                    return validation_error(
                                        "voice_id is required for composed speech segments",
                                    );
                                }
                                if settings.model.is_none() {
                                    return validation_error(
                                        "model is required for composed speech segments",
                                    );
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
                if let Ok(seconds) = seconds_text.parse::<f64>() {
                    if token_start > last_emit {
                        parts.push(SpeechPart::Text(text[last_emit..token_start].to_string()));
                    }
                    parts.push(SpeechPart::Pause(seconds));
                    last_emit = token_end;
                    search_from = token_end;
                    continue;
                }
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

fn request_from_settings(text: String, settings: ComposerSettings) -> Result<TTSRequest> {
    let Some(voice_id) = settings.voice_id else {
        return validation_error("voice_id is required for composed speech segments");
    };
    let Some(model) = settings.model else {
        return validation_error("model is required for composed speech segments");
    };
    Ok(TTSRequest {
        voice_id,
        text,
        model,
        language: settings.language,
        prompt: settings.prompt,
        output: merge_output(
            settings.output,
            Some(Output::new().audio_format(AudioFormat::Wav)),
        ),
        seed: settings.seed,
    })
}

fn parse_wav(data: &[u8]) -> Result<ParsedWav> {
    if data.len() < 12 || &data[0..4] != b"RIFF" || &data[8..12] != b"WAVE" {
        return validation_error("unsupported WAV data");
    }

    let mut offset = 12usize;
    let mut spec = None;
    let mut samples = None;
    while offset + 8 <= data.len() {
        let chunk_id = &data[offset..offset + 4];
        let chunk_size = u32::from_le_bytes([
            data[offset + 4],
            data[offset + 5],
            data[offset + 6],
            data[offset + 7],
        ]) as usize;
        let chunk_data_offset = offset + 8;
        let chunk_end = chunk_data_offset + chunk_size;
        if chunk_end > data.len() {
            return validation_error("unsupported WAV data");
        }

        match chunk_id {
            b"fmt " => {
                if chunk_size < 16 {
                    return validation_error("unsupported WAV data");
                }
                let audio_format =
                    u16::from_le_bytes([data[chunk_data_offset], data[chunk_data_offset + 1]]);
                let channels =
                    u16::from_le_bytes([data[chunk_data_offset + 2], data[chunk_data_offset + 3]]);
                let sample_rate = u32::from_le_bytes([
                    data[chunk_data_offset + 4],
                    data[chunk_data_offset + 5],
                    data[chunk_data_offset + 6],
                    data[chunk_data_offset + 7],
                ]);
                let bits_per_sample = u16::from_le_bytes([
                    data[chunk_data_offset + 14],
                    data[chunk_data_offset + 15],
                ]);
                if audio_format != 1 || channels != 1 || bits_per_sample != 16 {
                    return validation_error(
                        "only mono 16-bit PCM WAV is supported for composed speech",
                    );
                }
                spec = Some(WavSpec {
                    sample_rate,
                    channels,
                    bits_per_sample,
                });
            }
            b"data" => {
                let mut parsed_samples = Vec::with_capacity(chunk_size / 2);
                for sample in data[chunk_data_offset..chunk_end].chunks_exact(2) {
                    parsed_samples.push(i16::from_le_bytes([sample[0], sample[1]]));
                }
                samples = Some(parsed_samples);
            }
            _ => {}
        }
        offset = chunk_end + (chunk_size % 2);
    }

    let Some(spec) = spec else {
        return validation_error("unsupported WAV data");
    };
    let Some(samples) = samples else {
        return validation_error("unsupported WAV data");
    };
    Ok(ParsedWav { spec, samples })
}

fn encode_wav(samples: &[i16], spec: WavSpec) -> Vec<u8> {
    let data_size = (samples.len() * 2) as u32;
    let mut wav = Vec::with_capacity(44 + samples.len() * 2);
    wav.extend_from_slice(b"RIFF");
    wav.extend_from_slice(&(36 + data_size).to_le_bytes());
    wav.extend_from_slice(b"WAVE");
    wav.extend_from_slice(b"fmt ");
    wav.extend_from_slice(&16u32.to_le_bytes());
    wav.extend_from_slice(&1u16.to_le_bytes());
    wav.extend_from_slice(&spec.channels.to_le_bytes());
    wav.extend_from_slice(&spec.sample_rate.to_le_bytes());
    wav.extend_from_slice(&(spec.sample_rate * spec.channels as u32 * 2).to_le_bytes());
    wav.extend_from_slice(&(spec.channels * 2).to_le_bytes());
    wav.extend_from_slice(&spec.bits_per_sample.to_le_bytes());
    wav.extend_from_slice(b"data");
    wav.extend_from_slice(&data_size.to_le_bytes());
    for sample in samples {
        wav.extend_from_slice(&sample.to_le_bytes());
    }
    wav
}

fn trim_silence(samples: &[i16]) -> Vec<i16> {
    let mut start = 0usize;
    let mut end = samples.len();
    while start < end && samples[start].abs() <= 0 {
        start += 1;
    }
    while end > start && samples[end - 1].abs() <= 0 {
        end -= 1;
    }
    samples[start..end].to_vec()
}

fn seconds_to_samples(seconds: f64, sample_rate: u32) -> usize {
    (seconds * sample_rate as f64).round() as usize
}

fn validation_error<T>(detail: impl Into<String>) -> Result<T> {
    Err(TypecastError::ValidationError {
        detail: detail.into(),
    })
}
