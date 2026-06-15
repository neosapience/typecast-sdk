//! Example: text-to-speech with word/character timestamps, SRT and VTT export.

use typecast_rust::{TTSModel, TTSRequestWithTimestamps, TypecastClient};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = TypecastClient::from_env()?;

    let request = TTSRequestWithTimestamps {
        voice_id: "tc_60e5426de8b95f1d3000d7b5".to_string(),
        text: "Hello. How are you?".to_string(),
        model: TTSModel::SsfmV30,
        language: Some("eng".to_string()),
        prompt: None,
        output: None,
        seed: None,
    };

    let resp = client
        .text_to_speech_with_timestamps(&request, None)
        .await?;

    resp.save_audio("/tmp/with_timestamps_rust.wav")?;

    let srt = resp.to_srt()?;
    std::fs::write("/tmp/with_timestamps_rust.srt", &srt)?;

    let vtt = resp.to_vtt()?;
    std::fs::write("/tmp/with_timestamps_rust.vtt", &vtt)?;

    println!(
        "audio: /tmp/with_timestamps_rust.wav ({:.2}s, format={:?})",
        resp.audio_duration, resp.audio_format
    );
    println!(
        "words: {}, characters: {}",
        resp.words.as_ref().map(|w| w.len()).unwrap_or(0),
        resp.characters.as_ref().map(|c| c.len()).unwrap_or(0)
    );
    // Print first cue
    let first_cue: String = srt.lines().take(4).collect::<Vec<_>>().join("\n");
    println!("SRT first cue:\n{}", first_cue);

    Ok(())
}
