# Typecast Zig SDK Design

## Overview

Pure Zig implementation of the Typecast TTS API client. No C dependencies — uses only `std.http.Client` and `std.json` from the Zig standard library.

## Scope

All endpoints matching other SDKs:

| Method | Endpoint | Zig Function |
|--------|----------|--------------|
| POST | `/v1/text-to-speech` | `textToSpeech` |
| POST | `/v1/text-to-speech/stream` | `textToSpeechStream` |
| GET | `/v1/users/me/subscription` | `getMySubscription` |
| GET | `/v1/voices` | `getVoices` |
| GET | `/v2/voices` | `getVoicesV2` |
| GET | `/v2/voices?voice_id=X` | `getVoiceV2` |

## File Structure

```
typecast-zig/
├── build.zig
├── build.zig.zon
├── src/
│   ├── root.zig       # Public API re-exports
│   ├── client.zig     # TypecastClient: HTTP calls, error handling
│   ├── models.zig     # Request/response types, enums, validation
│   └── json.zig       # JSON serialization/deserialization helpers
├── tests/
│   ├── unit_test.zig        # Model validation, JSON round-trip, error mapping
│   ├── mock_test.zig        # Full API tests against local std.http.Server mock
│   └── integration_test.zig # Live tests against api.icepeak.in staging
├── examples/
│   └── tts.zig
├── README.md
├── LICENSE
└── .env.example
```

## Public API

### Client

```zig
pub const Client = struct {
    pub const Config = struct {
        api_key: []const u8,
        base_url: []const u8 = "https://api.typecast.ai",
    };

    pub fn init(allocator: std.mem.Allocator, config: Config) !Client
    pub fn deinit(self: *Client) void

    // TTS
    pub fn textToSpeech(self: *Client, request: TtsRequest) !TtsResponse
    pub fn textToSpeechStream(self: *Client, request: TtsRequestStream, on_chunk: *const fn([]const u8) anyerror!void) !void

    // Subscription
    pub fn getMySubscription(self: *Client) !SubscriptionResponse

    // Voices
    pub fn getVoices(self: *Client, model: ?TtsModel) ![]Voice
    pub fn getVoicesV2(self: *Client, filter: ?VoicesV2Filter) ![]VoiceV2
    pub fn getVoiceV2(self: *Client, voice_id: []const u8, model: ?TtsModel) !VoiceV2
};
```

### Models

```zig
pub const TtsModel = enum { ssfm_v21, ssfm_v30 };
pub const AudioFormat = enum { wav, mp3 };
pub const EmotionPreset = enum { normal, happy, sad, angry, whisper, toneup, tonedown };
pub const Gender = enum { male, female };
pub const Age = enum { child, teenager, young_adult, middle_age, elder };

pub const Output = struct {
    volume: ?i32 = 100,
    target_lufs: ?f64 = null,
    audio_pitch: ?i32 = 0,
    audio_tempo: ?f64 = 1.0,
    audio_format: ?AudioFormat = .wav,
};

pub const OutputStream = struct {
    audio_pitch: ?i32 = 0,
    audio_tempo: ?f64 = 1.0,
    audio_format: ?AudioFormat = .wav,
};

pub const SmartPrompt = struct {
    previous_text: ?[]const u8 = null,
    next_text: ?[]const u8 = null,
};

pub const PresetPrompt = struct {
    emotion_preset: EmotionPreset = .normal,
    emotion_intensity: ?f64 = 1.0,
};

pub const Prompt = struct { // ssfm-v21
    emotion_preset: EmotionPreset = .normal,
    emotion_intensity: ?f64 = 1.0,
};

pub const TtsPrompt = union(enum) {
    smart: SmartPrompt,
    preset: PresetPrompt,
    basic: Prompt,
};

pub const TtsRequest = struct {
    voice_id: []const u8,
    text: []const u8,
    model: TtsModel,
    language: ?[]const u8 = null,
    prompt: ?TtsPrompt = null,
    output: ?Output = null,
    seed: ?i32 = null,
};

pub const TtsRequestStream = struct {
    voice_id: []const u8,
    text: []const u8,
    model: TtsModel,
    language: ?[]const u8 = null,
    prompt: ?TtsPrompt = null,
    output: ?OutputStream = null,
    seed: ?i32 = null,
};

pub const TtsResponse = struct {
    audio_data: []const u8,
    duration: f64,
    format: AudioFormat,
};

pub const SubscriptionResponse = struct {
    plan: []const u8,
    credits: struct { plan_credits: i64, used_credits: i64 },
    limits: struct { concurrency_limit: i32 },
};
```

### Error Handling

```zig
pub const TypecastError = error{
    BadRequest,           // 400
    Unauthorized,         // 401
    PaymentRequired,      // 402
    NotFound,             // 404
    UnprocessableEntity,  // 422
    RateLimited,          // 429
    InternalServerError,  // 500
    NetworkError,         // connection/timeout
    JsonParseError,       // malformed response
};
```

## Key Decisions

- **HTTP**: `std.http.Client` — built-in, supports chunked transfer
- **JSON**: `std.json` — `parseFromSlice` / `stringifyAlloc`
- **Streaming**: Callback function pattern `fn([]const u8) anyerror!void`
- **Memory**: Caller provides allocator. `textToSpeech` returns owned slice (caller frees). `textToSpeechStream` streams through callback (no large allocation).
- **Minimum Zig**: 0.15.0

## Testing

### Unit Tests (`tests/unit_test.zig`)
- Model field validation (ranges, required fields)
- JSON serialization round-trip for all request/response types
- Error code mapping (HTTP status → Zig error)
- TtsPrompt union serialization (smart/preset/basic)

### Mock Tests (`tests/mock_test.zig`)
- Local `std.http.Server` on a separate thread
- All 6 endpoints: happy path + error responses
- Streaming: multi-chunk delivery, callback invocation count
- All HTTP error status codes (400, 401, 402, 404, 422, 429, 500)
- Edge cases: empty body, malformed JSON, timeout

### Integration Tests (`tests/integration_test.zig`)
- Live calls to `api.icepeak.in` staging server
- Enabled via `zig build test -Dintegration=true`
- Requires `TYPECAST_API_KEY` env var
- Tests: TTS (wav+mp3), streaming, voices list, subscription

## Version

- SDK version: 0.1.0
- Package name: `typecast-zig`
