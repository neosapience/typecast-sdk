# SDK Pause Markup and Multi-Speaker Composition Design

## Goal

Add client-side composition helpers across all Typecast SDKs so users can:

- Insert precise pauses inside text with a lightweight token.
- Compose multi-speaker scripts without making separate TTS calls manually.
- Receive the same `TTSResponse` shape they already use for normal TTS.

The feature targets every SDK in the monorepo: Python, JavaScript, Go, Java, Kotlin, C#, Swift, Rust, C, Zig, PHP, Dart, and Ruby.

## Non-Goals

- Do not add server-side pause or multi-speaker APIs in this task.
- Do not add string-based voice switching tokens such as `<|voice:...|>`.
- Do not change or remove existing `textToSpeech` APIs.
- Do not guarantee mobile MP3 encoding inside the SDK.

## Public API

Each SDK adds a `composeSpeech()` entry point on the client, adapted to language idioms.

JavaScript-style example:

```ts
const audio = await client
  .composeSpeech()
  .defaults({
    model: "ssfm-v30",
    voice_id: "tc_voice_a",
    output: { audio_format: "wav" },
  })
  .say("안녕하세요<|0.3s|>반갑습니다")
  .pause(0.5)
  .say("다른 화자입니다.", {
    voice_id: "tc_voice_b",
    output: { audio_pitch: 2, audio_tempo: 0.95 },
  })
  .generate();
```

The builder supports:

- `defaults(settings)`: common TTS settings used by later `.say()` segments.
- `say(text, overrides?)`: add a speech segment. `overrides` may replace defaults for this segment.
- `pause(seconds)`: add explicit silence in seconds.
- `generate()`: synthesize and compose the final audio.

The returned object is the SDK's existing TTS response type.

## Segment Overrides

Each `.say(text, overrides?)` can override any normal TTS request field that makes sense per segment:

- `voice_id`
- `model`
- `language`
- `prompt`
- `output.audio_pitch`
- `output.audio_tempo`
- `output.volume`
- `output.target_lufs`
- `seed`

The final output format is a composition-level setting. A segment may override voice and prosody, but the composer internally requests WAV for every segment so it can trim and concatenate PCM safely.

If both defaults and a segment provide nested `output`, the segment output is merged over the default output instead of replacing the whole object.

The composition-level output format comes from `defaults({ output: { audio_format } })` unless a language adds an idiomatic explicit option to `generate()`. Segment-level `output.audio_format` is ignored or rejected because mixed segment formats would make composition ambiguous.

## Pause Markup

Inside `.say()` text, the SDK recognizes pause tokens with this grammar:

```text
<|{seconds}s|>
```

Valid examples:

- `<|3s|>`
- `<|3000s|>`
- `<|0.3s|>`
- `<|0.34413s|>`

Invalid or incomplete tokens remain normal text because they may be intentional user content:

- `<|abc|>`
- `<|-1s|>`
- `<|s|>`
- `<|300ms|>`
- `<|3s`

Token parsing is lenient. The composer only extracts valid pause tokens and preserves all other text.

## Audio Pipeline

The composer uses WAV as the internal composition format:

1. Split each `.say()` text into text and pause parts.
2. Send only non-empty text parts to `/v1/text-to-speech`.
3. Force internal segment requests to WAV.
4. Decode WAV headers and extract PCM frames.
5. Trim leading and trailing silence from each synthesized segment.
6. Insert explicit silence from pause tokens and `.pause(seconds)`.
7. Concatenate PCM frames.
8. Return a new WAV response, or optionally transcode the final WAV to MP3.

The initial WAV implementation assumes the API's standard output format: mono 16-bit PCM WAV. The parser must validate compatible WAV headers and fail clearly if an unsupported WAV encoding appears.

## MP3 Output

For server and desktop SDKs, `output.audio_format: "mp3"` is supported as an optional post-processing step:

1. Compose final WAV.
2. Invoke `ffmpeg` from `PATH`.
3. Return MP3 bytes with `format = "mp3"`.

If `ffmpeg` is missing, the SDK raises a clear MP3 encoding unavailable error before returning a result.

Mobile-oriented SDKs still provide WAV composition. MP3 conversion is documented as app-level responsibility because apps may need AVFoundation, MediaCodec, ffmpeg-kit, or another platform-specific media stack.

## Errors

The composer must fail before network calls when:

- `.generate()` has no speech segment.
- A speech segment cannot resolve required fields such as `voice_id` or `model`.
- `.pause(seconds)` receives NaN, infinity, zero, or a negative value.
- The final output format is unsupported.

The composer must fail during processing when:

- A TTS segment request fails.
- A WAV segment has unsupported encoding or mismatched sample settings.
- MP3 was requested but the current SDK/runtime cannot encode MP3.

Invalid pause markup is not an error.

## Testing

Every SDK must add unit tests for:

- Pause markup parsing for valid tokens, invalid tokens, incomplete delimiters, and decimal seconds.
- Builder planning: defaults, per-segment overrides, nested `output` merge, and required-field validation.
- Network behavior: only non-empty text parts produce TTS calls; each internal call requests WAV.
- WAV composition: two small fixture WAVs plus inserted silence produce expected sample counts and duration.
- Silence trimming: leading and trailing zero samples are removed while interior audio remains intact.
- MP3 adapter: command construction and missing-`ffmpeg` error are tested without requiring real ffmpeg.

Where practical, each server or desktop SDK should add one availability-gated smoke test for real `ffmpeg` conversion. It must skip cleanly when `ffmpeg` is unavailable.

No unit test should require a real Typecast API key.

## Documentation

Each SDK README should document:

- `<|0.3s|>` pause markup.
- `composeSpeech()` chaining for multi-speaker scripts.
- `.pause(seconds)` uses seconds, not milliseconds.
- `.say(text, overrides)` supports per-speaker and per-segment pitch/tempo/prompt/options.
- MP3 composition requires `ffmpeg` on server/desktop runtimes.
- Mobile SDKs return WAV composition and require app-level MP3 conversion.

## Rollout

Implement the same behavior across all SDKs. Prefer one PR that keeps API and docs consistent, but split commits by SDK to make review tractable.

Keep all existing APIs backward compatible. Existing `textToSpeech` behavior must not change.
