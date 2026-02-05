<div align="center">

# Typecast SDK

**Official SDKs for the Typecast Text-to-Speech API**

[![API Docs](https://img.shields.io/badge/API-Documentation-blue?style=flat-square)](https://typecast.ai/docs)
[![Typecast](https://img.shields.io/badge/Typecast-AI-purple?style=flat-square)](https://typecast.ai/?lang=en)

---

_Transform text into natural, expressive speech with Typecast AI_

</div>

<br/>

## Overview

This monorepo contains official SDK clients for the [Typecast](https://typecast.ai/?lang=en) Text-to-Speech API across multiple programming languages. Each SDK provides a simple, idiomatic interface to convert text into high-quality AI-generated speech.

<br/>

## Available SDKs

|                                                   Language                                                    | Package                                  | Installation                                |
| :-----------------------------------------------------------------------------------------------------------: | :--------------------------------------- | :------------------------------------------ |
|     <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/python/python-original.svg" width="24"/>     | [**typecast-python**](./typecast-python) | `pip install typecast`                      |
| <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/javascript/javascript-original.svg" width="24"/> | [**typecast-js**](./typecast-js)         | `npm install @typecast-ai/typecast`         |
|         <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/go/go-original.svg" width="24"/>         | [**typecast-go**](./typecast-go)         | `go get github.com/typecast-ai/typecast-go` |
|       <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="24"/>       | [**typecast-java**](./typecast-java)     | Maven Central                               |
|     <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/kotlin/kotlin-original.svg" width="24"/>     | [**typecast-kotlin**](./typecast-kotlin) | Gradle                                      |
|     <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/csharp/csharp-original.svg" width="24"/>     | [**typecast-csharp**](./typecast-csharp) | `dotnet add package Typecast`               |
|      <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/swift/swift-original.svg" width="24"/>      | [**typecast-swift**](./typecast-swift)   | Swift Package Manager                       |
|       <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/rust/rust-original.svg" width="24"/>       | [**typecast-rust**](./typecast-rust)     | `cargo add typecast`                        |
|          <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/c/c-original.svg" width="24"/>          | [**typecast-c**](./typecast-c)           | CMake                                       |

<br/>

## Quick Start

### 1. Get Your API Key

Sign up at [typecast.ai](https://typecast.ai/?lang=en) and generate an API key from your dashboard.

### 2. Install Your Preferred SDK

Choose the SDK for your language and follow the installation instructions in its README.

### 3. Generate Speech

```python
# Python Example
from typecast import TypecastClient

client = TypecastClient(api_key="your-api-key")
audio = client.tts.speak(
    text="Hello, world!",
    actor_id="YOUR_ACTOR_ID"
)
audio.save("output.wav")
```

```typescript
// JavaScript/TypeScript Example
import { TypecastClient } from "@typecast-ai/typecast";

const client = new TypecastClient({ apiKey: "your-api-key" });
const audio = await client.tts.speak({
  text: "Hello, world!",
  actorId: "YOUR_ACTOR_ID",
});
```

<br/>

## Features

- **High-Quality Voices** — Natural, expressive AI voices in multiple languages
- **Emotion Control** — Fine-tune speech with emotion presets and parameters
- **Multiple Formats** — Export as WAV, MP3, and more
- **Streaming Support** — Real-time audio streaming for low-latency applications
- **Simple Integration** — Clean, idiomatic APIs for each language

<br/>

## Documentation

For detailed API documentation and guides, visit **[typecast.ai/docs](https://typecast.ai/docs)**

<br/>

## License

Each SDK is licensed under the MIT License. See individual SDK directories for details.

<br/>

---

<div align="center">

Made with ❤️ by [Typecast AI](https://typecast.ai/?lang=en)

</div>
