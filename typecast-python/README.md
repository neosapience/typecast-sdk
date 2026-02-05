<div align="center">

# Typecast SDK for Python

**The official Python SDK for the Typecast Text-to-Speech API**

Convert text to lifelike speech using AI-powered voices

[![PyPI version](https://img.shields.io/pypi/v/typecast-python.svg?style=flat-square)](https://pypi.org/project/typecast-python/)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)
[![Python](https://img.shields.io/badge/Python-3.9+-3776ab.svg?style=flat-square&logo=python&logoColor=white)](https://www.python.org/)

[Documentation](https://typecast.ai/docs) | [API Reference](https://typecast.ai/docs/api-reference) | [Get API Key](https://typecast.ai/developers/api/api-key)

</div>

---

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Features](#features)
- [Usage](#usage)
  - [Configuration](#configuration)
  - [Text to Speech](#text-to-speech)
  - [Voice Discovery](#voice-discovery)
  - [Emotion Control](#emotion-control)
  - [Async Client](#async-client)
- [Supported Languages](#supported-languages)
- [Error Handling](#error-handling)
- [License](#license)

---

## Installation

```bash
pip install typecast-python
```

---

## Quick Start

```python
from typecast import Typecast
from typecast.models import TTSRequest

client = Typecast(api_key="YOUR_API_KEY")

response = client.text_to_speech(TTSRequest(
    text="Hello! I'm your friendly text-to-speech assistant.",
    model="ssfm-v30",
    voice_id="tc_672c5f5ce59fac2a48faeaee"
))

with open("output.wav", "wb") as f:
    f.write(response.audio_data)

print(f"Saved: output.wav ({response.duration}s)")
```

---

## Features

| Feature | Description |
|---------|-------------|
| **Multiple Models** | Support for `ssfm-v21` and `ssfm-v30` AI voice models |
| **37 Languages** | English, Korean, Japanese, Chinese, Spanish, and 32 more |
| **Emotion Control** | Preset emotions or smart context-aware inference |
| **Audio Customization** | Volume, pitch, tempo, and format (WAV/MP3) |
| **Voice Discovery** | Filter voices by model, gender, age, and use cases |
| **Async Support** | Built-in async client for high-performance applications |
| **Type Hints** | Full type annotations with Pydantic models |

---

## Usage

### Configuration

```python
from typecast import Typecast

# Using environment variable (recommended)
# export TYPECAST_API_KEY="your-api-key"
client = Typecast()

# Or pass directly
client = Typecast(
    api_key="your-api-key",
    host="https://api.typecast.ai"  # optional
)
```

### Text to Speech

#### Basic Usage

```python
from typecast.models import TTSRequest

response = client.text_to_speech(TTSRequest(
    text="Hello, world!",
    voice_id="tc_672c5f5ce59fac2a48faeaee",
    model="ssfm-v30"
))
```

#### With Audio Options

```python
from typecast.models import TTSRequest, Output

response = client.text_to_speech(TTSRequest(
    text="Hello, world!",
    voice_id="tc_672c5f5ce59fac2a48faeaee",
    model="ssfm-v30",
    language="eng",
    output=Output(
        volume=120,        # 0-200 (default: 100)
        audio_pitch=2,     # -12 to +12 semitones
        audio_tempo=1.2,   # 0.5x to 2.0x
        audio_format="mp3" # "wav" or "mp3"
    ),
    seed=42  # for reproducible results
))
```

### Voice Discovery

```python
from typecast.models import VoicesV2Filter, TTSModel, GenderEnum, AgeEnum

# Get all voices (V2 API - recommended)
voices = client.voices_v2()

# Filter by criteria
filtered = client.voices_v2(VoicesV2Filter(
    model=TTSModel.SSFM_V30,
    gender=GenderEnum.FEMALE,
    age=AgeEnum.YOUNG_ADULT
))

# Display voice info
print(f"Name: {voices[0].voice_name}")
print(f"Gender: {voices[0].gender}, Age: {voices[0].age}")
print(f"Models: {', '.join(m.version.value for m in voices[0].models)}")
```

### Emotion Control

#### ssfm-v21: Basic Emotion

```python
from typecast.models import TTSRequest, Prompt

response = client.text_to_speech(TTSRequest(
    text="I'm so excited!",
    voice_id="tc_62a8975e695ad26f7fb514d1",
    model="ssfm-v21",
    prompt=Prompt(
        emotion_preset="happy",  # normal, happy, sad, angry
        emotion_intensity=1.5    # 0.0 to 2.0
    )
))
```

#### ssfm-v30: Preset Mode

```python
from typecast.models import TTSRequest, PresetPrompt, TTSModel

response = client.text_to_speech(TTSRequest(
    text="I'm so excited!",
    voice_id="tc_672c5f5ce59fac2a48faeaee",
    model=TTSModel.SSFM_V30,
    prompt=PresetPrompt(
        emotion_type="preset",
        emotion_preset="happy",  # normal, happy, sad, angry, whisper, toneup, tonedown
        emotion_intensity=1.5
    )
))
```

#### ssfm-v30: Smart Mode (Context-Aware)

```python
from typecast.models import TTSRequest, SmartPrompt, TTSModel

response = client.text_to_speech(TTSRequest(
    text="Everything is perfect.",
    voice_id="tc_672c5f5ce59fac2a48faeaee",
    model=TTSModel.SSFM_V30,
    prompt=SmartPrompt(
        emotion_type="smart",
        previous_text="I just got the best news!",
        next_text="I can't wait to celebrate!"
    )
))
```

### Async Client

```python
import asyncio
from typecast import AsyncTypecast
from typecast.models import TTSRequest

async def main():
    async with AsyncTypecast(api_key="YOUR_API_KEY") as client:
        response = await client.text_to_speech(TTSRequest(
            text="Hello from async!",
            model="ssfm-v30",
            voice_id="tc_672c5f5ce59fac2a48faeaee"
        ))

        with open("output.wav", "wb") as f:
            f.write(response.audio_data)

asyncio.run(main())
```

---

## Supported Languages

<details>
<summary><strong>View all 37 supported languages</strong></summary>

| Code | Language | Code | Language | Code | Language |
|------|----------|------|----------|------|----------|
| `eng` | English | `jpn` | Japanese | `ukr` | Ukrainian |
| `kor` | Korean | `ell` | Greek | `ind` | Indonesian |
| `spa` | Spanish | `tam` | Tamil | `dan` | Danish |
| `deu` | German | `tgl` | Tagalog | `swe` | Swedish |
| `fra` | French | `fin` | Finnish | `msa` | Malay |
| `ita` | Italian | `zho` | Chinese | `ces` | Czech |
| `pol` | Polish | `slk` | Slovak | `por` | Portuguese |
| `nld` | Dutch | `ara` | Arabic | `bul` | Bulgarian |
| `rus` | Russian | `hrv` | Croatian | `ron` | Romanian |
| `ben` | Bengali | `hin` | Hindi | `hun` | Hungarian |
| `nan` | Hokkien | `nor` | Norwegian | `pan` | Punjabi |
| `tha` | Thai | `tur` | Turkish | `vie` | Vietnamese |
| `yue` | Cantonese | | | | |

</details>

```python
from typecast.models import LanguageCode

# Auto-detect (recommended)
response = client.text_to_speech(TTSRequest(
    text="こんにちは",
    voice_id="...",
    model="ssfm-v30"
))

# Explicit language
response = client.text_to_speech(TTSRequest(
    text="안녕하세요",
    voice_id="...",
    model="ssfm-v30",
    language=LanguageCode.KOR
))
```

---

## Error Handling

```python
from typecast import (
    Typecast,
    TypecastError,
    BadRequestError,
    UnauthorizedError,
    PaymentRequiredError,
    NotFoundError,
    UnprocessableEntityError,
    RateLimitError,
    InternalServerError,
)

try:
    response = client.text_to_speech(request)
except UnauthorizedError:
    print("Invalid API key")
except PaymentRequiredError:
    print("Insufficient credits")
except RateLimitError:
    print("Rate limit exceeded - please retry later")
except TypecastError as e:
    print(f"Error {e.status_code}: {e.message}")
```

| Exception | Status Code | Description |
|-----------|-------------|-------------|
| `BadRequestError` | 400 | Invalid request parameters |
| `UnauthorizedError` | 401 | Invalid or missing API key |
| `PaymentRequiredError` | 402 | Insufficient credits |
| `NotFoundError` | 404 | Resource not found |
| `UnprocessableEntityError` | 422 | Validation error |
| `RateLimitError` | 429 | Rate limit exceeded |
| `InternalServerError` | 500 | Server error |

---

## License

[Apache-2.0](LICENSE) © [Neosapience](https://typecast.ai)
