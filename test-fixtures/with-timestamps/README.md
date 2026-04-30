# with-timestamps fixtures

This directory contains canned API responses captured against `POST /v1/text-to-speech/with-timestamps`. They are the source of truth for cross-SDK consistency tests across all 11 SDKs (Python, JS, Go, Java, Kotlin, C#, Swift, Rust, C, Zig, PHP).

## Fixtures

- `both.json` — granularity omitted, English `"Hello. How are you?"`, returns words (4) + characters (19).
- `word_only.json` — `granularity=word`, English same text, characters=null.
- `char_only.json` — `granularity=char`, English same text, words=null.
- `jpn_char.json` — `granularity=char`, Japanese `"こんにちは。お元気ですか?"`, multi-character segments (13).

All fixtures use voice `tc_60e5426de8b95f1d3000d7b5` ("Jack"), model `ssfm-v30`, prompt `{"emotion_type":"preset","emotion_preset":"normal","emotion_intensity":1.0}`, seed `42`.

## Expected outputs

`expected/<fixture>.srt` and `expected/<fixture>.vtt` are the canonical SRT/WebVTT outputs that every SDK's `to_srt()` / `to_vtt()` helper must produce **byte-for-byte** when given the matching `<fixture>.json`. They are generated once by the Python SDK's helper and locked in. Cross-SDK consistency is verified by `scripts/check_with_timestamps_consistency.py`.

## Captioning rules locked into expected outputs

Every SDK's helper applies the same rules:

- Caption boundary on sentence terminators (`.` `?` `!` `。` `？` `！`) at the end of a segment text, OR when the cue would exceed 7.0 seconds, OR when the cue would exceed 42 characters.
- For non-whitespace languages where `words` collapses to a single segment, the helper falls back to `characters` and applies the same rules at character granularity.
- SRT timestamps `HH:MM:SS,mmm`, VTT timestamps `HH:MM:SS.mmm`, ms always padded to 3 digits.
- SRT cue index starts at 1 (integer), VTT cue identifier omitted.
- All output uses LF newlines. Each cue is followed by a blank-line separator (including the last cue), so the file ends with `\n\n`.

Hard caps (7.0s per cue, 42 characters per cue) follow Netflix's Timed Text Style Guide and BBC's Subtitle Guidelines: BBC recommends 32–42 chars per line and a maximum of ~7 seconds per cue, while Netflix mandates 42 characters per line. The 42-char value matches both upper bounds; the 7s value matches BBC and stays within Netflix's typical guidance.

## Server response evolution policy

When the server adds new fields to the JSON response, all 11 SDKs MUST ignore unknown fields rather than fail deserialization. Per-SDK status:

- Python (Pydantic v2): default is `extra="ignore"` (safe)
- JS (TypeScript interface): structural — extra fields tolerated at runtime (safe)
- Go (encoding/json): default behavior — extra fields ignored (safe)
- Java (Gson default): unknown fields ignored (safe)
- Kotlin (kotlinx.serialization with `ignoreUnknownKeys = true`): verify (currently set in client)
- C# (System.Text.Json default): unknown fields ignored (safe)
- Swift (Codable): unknown fields ignored by default (safe)
- Rust (serde with `#[derive(Deserialize)]` default): unknown fields ignored (safe)
- C (cJSON manual parsing): only fields the parser asks for; safe by construction
- Zig (manual JSON parsing): only fields the parser asks for; safe by construction
- PHP (json_decode + manual extraction): only fields explicitly read; safe

Removing or renaming a field is a breaking change requiring SDK version coordination.

## Re-capture

Re-running the requests with the same voice + model + seed produces equivalent timing within rounding noise. If audio bytes differ but timing/text fields don't, the fixtures stay valid. If timing changes meaningfully, regenerate `expected/*.srt` and `expected/*.vtt` from the new fixtures (Python SDK's `to_srt()` / `to_vtt()` are the source of truth for those expected files).
