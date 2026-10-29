# Typecast Ruby SDK

Official Ruby SDK for the Typecast Text-to-Speech API.

## Installation

```bash
gem install typecast-ruby
```

For local development from this monorepo:

```ruby
gem "typecast-ruby", path: "../typecast-ruby"
```

## Quick Start

Choose `ssfm-v21`, `ssfm-v30`, or `ssfm-v31` through the request's model setting.
For SSFM 3.1, use `Typecast::Models::TTS_MODEL_V31` and a compatible voice from `GET /v3/voices?model=ssfm-v31`.
The voice's `models` entry lists its supported emotions.

```ruby
require "typecast"

client = Typecast::Client.new(api_key: ENV["TYPECAST_API_KEY"])
response = client.text_to_speech(
  Typecast::Models::TTSRequest.new(
    voice_id: "tc_60e5426de8b95f1d3000d7b5",
    text: "Hello from Typecast Ruby.",
    model: Typecast::Models::TTS_MODEL_V30,
    language: "eng",
    output: Typecast::Models::Output.new(audio_format: "wav")
  )
)

File.binwrite("hello.wav", response.audio_data)
```

## Features

- Text-to-speech synthesis
- Streaming synthesis
- Word and character timestamps with SRT/VTT helpers
- Voice listing and subscription APIs
- Instant voice cloning
- No runtime dependencies beyond the Ruby standard library

## Testing

```bash
make install
make test
TYPECAST_API_KEY=... make e2e
```
