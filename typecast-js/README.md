<div align="center">

# Typecast SDK for JavaScript

**The official JavaScript/TypeScript SDK for the Typecast Text-to-Speech API**

Convert text to lifelike speech using AI-powered voices

[![npm version](https://img.shields.io/npm/v/@neosapience/typecast-js.svg?style=flat-square)](https://www.npmjs.com/package/@neosapience/typecast-js)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178c6.svg?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Node.js](https://img.shields.io/badge/Node.js-%3E%3D16.0.0-339933.svg?style=flat-square&logo=node.js&logoColor=white)](https://nodejs.org/)

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
- [Supported Languages](#supported-languages)
- [Error Handling](#error-handling)
- [TypeScript Support](#typescript-support)
- [License](#license)

---

## Installation

```bash
npm install @neosapience/typecast-js
```

<details>
<summary><strong>Node.js 16/17 Users</strong></summary>

This SDK uses the native `fetch` API. Node.js 18+ has built-in fetch support, but if you're using Node.js 16 or 17, you need to install a fetch polyfill:

```bash
npm install isomorphic-fetch
```

Then import it once at your application's entry point:

```javascript
import 'isomorphic-fetch';  // ESM
// or
require('isomorphic-fetch');  // CommonJS
```

</details>

---

## Quick Start

```typescript
import { TypecastClient } from '@neosapience/typecast-js';
import fs from 'fs';

const client = new TypecastClient({ apiKey: 'YOUR_API_KEY' });

const audio = await client.textToSpeech({
  text: "Hello! I'm your friendly text-to-speech assistant.",
  model: "ssfm-v30",
  voice_id: "tc_672c5f5ce59fac2a48faeaee"
});

await fs.promises.writeFile(`output.${audio.format}`, Buffer.from(audio.audioData));
console.log(`Saved: output.${audio.format} (${audio.duration}s)`);
```

<details>
<summary><strong>CommonJS Example</strong></summary>

```javascript
const { TypecastClient } = require('@neosapience/typecast-js');
const fs = require('fs');

async function main() {
  const client = new TypecastClient({ apiKey: 'YOUR_API_KEY' });
  const audio = await client.textToSpeech({
    text: "Hello! I'm your friendly text-to-speech assistant.",
    model: "ssfm-v30",
    voice_id: "tc_672c5f5ce59fac2a48faeaee"
  });
  await fs.promises.writeFile(`output.${audio.format}`, Buffer.from(audio.audioData));
}

main();
```

</details>

---

## Features

| Feature | Description |
|---------|-------------|
| **Multiple Models** | Support for `ssfm-v21` and `ssfm-v30` AI voice models |
| **37 Languages** | English, Korean, Japanese, Chinese, Spanish, and 32 more |
| **Emotion Control** | Preset emotions or smart context-aware inference |
| **Audio Customization** | Volume, pitch, tempo, and format (WAV/MP3) |
| **Voice Discovery** | Filter voices by model, gender, age, and use cases |
| **TypeScript** | Full type definitions included |
| **Zero Dependencies** | Uses native `fetch` API |

---

## Usage

### Configuration

```typescript
import { TypecastClient } from '@neosapience/typecast-js';

// Using environment variable (recommended)
// export TYPECAST_API_KEY="your-api-key"
const client = new TypecastClient();

// Or pass directly
const client = new TypecastClient({
  apiKey: 'your-api-key',
  baseHost: 'https://api.typecast.ai'  // optional
});
```

### Text to Speech

#### Basic Usage

```typescript
const audio = await client.textToSpeech({
  text: "Hello, world!",
  voice_id: "tc_672c5f5ce59fac2a48faeaee",
  model: "ssfm-v30"
});
```

#### With Audio Options

```typescript
const audio = await client.textToSpeech({
  text: "Hello, world!",
  voice_id: "tc_672c5f5ce59fac2a48faeaee",
  model: "ssfm-v30",
  language: "eng",
  output: {
    volume: 120,        // 0-200 (default: 100)
    audio_pitch: 2,     // -12 to +12 semitones
    audio_tempo: 1.2,   // 0.5x to 2.0x
    audio_format: "mp3" // "wav" or "mp3"
  },
  seed: 42  // for reproducible results
});
```

### Voice Discovery

```typescript
// Get all voices (V2 API - recommended)
const voices = await client.getVoicesV2();

// Filter by criteria
const filtered = await client.getVoicesV2({
  model: 'ssfm-v30',
  gender: 'female',
  age: 'young_adult'
});

// Display voice info
console.log(`Name: ${voices[0].voice_name}`);
console.log(`Gender: ${voices[0].gender}, Age: ${voices[0].age}`);
console.log(`Models: ${voices[0].models.map(m => m.version).join(', ')}`);
```

### Emotion Control

#### ssfm-v21: Basic Emotion

```typescript
const audio = await client.textToSpeech({
  text: "I'm so excited!",
  voice_id: "tc_62a8975e695ad26f7fb514d1",
  model: "ssfm-v21",
  prompt: {
    emotion_preset: "happy",  // normal, happy, sad, angry
    emotion_intensity: 1.5    // 0.0 to 2.0
  }
});
```

#### ssfm-v30: Preset Mode

```typescript
import { PresetPrompt } from '@neosapience/typecast-js';

const audio = await client.textToSpeech({
  text: "I'm so excited!",
  voice_id: "tc_672c5f5ce59fac2a48faeaee",
  model: "ssfm-v30",
  prompt: {
    emotion_type: "preset",
    emotion_preset: "happy",  // normal, happy, sad, angry, whisper, toneup, tonedown
    emotion_intensity: 1.5
  } as PresetPrompt
});
```

#### ssfm-v30: Smart Mode (Context-Aware)

```typescript
import { SmartPrompt } from '@neosapience/typecast-js';

const audio = await client.textToSpeech({
  text: "Everything is perfect.",
  voice_id: "tc_672c5f5ce59fac2a48faeaee",
  model: "ssfm-v30",
  prompt: {
    emotion_type: "smart",
    previous_text: "I just got the best news!",
    next_text: "I can't wait to celebrate!"
  } as SmartPrompt
});
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

```typescript
// Auto-detect (recommended)
const audio = await client.textToSpeech({
  text: "こんにちは",
  voice_id: "...",
  model: "ssfm-v30"
});

// Explicit language
const audio = await client.textToSpeech({
  text: "안녕하세요",
  voice_id: "...",
  model: "ssfm-v30",
  language: "kor"
});
```

---

## Error Handling

```typescript
import { TypecastClient, TypecastAPIError } from '@neosapience/typecast-js';

try {
  const audio = await client.textToSpeech({ ... });
} catch (error) {
  if (error instanceof TypecastAPIError) {
    console.error(`Error ${error.statusCode}: ${error.message}`);

    // Handle specific errors
    switch (error.statusCode) {
      case 401: // Invalid API key
      case 402: // Insufficient credits
      case 422: // Validation error
      case 429: // Rate limit exceeded
    }
  }
}
```

---

## TypeScript Support

Full type definitions are included:

```typescript
import type {
  TTSRequest,
  TTSResponse,
  TTSModel,
  LanguageCode,
  Prompt,
  PresetPrompt,
  SmartPrompt,
  Output,
  VoiceV2Response,
  VoicesV2Filter
} from '@neosapience/typecast-js';
```

---

## License

[Apache-2.0](LICENSE) © [Neosapience](https://typecast.ai)
