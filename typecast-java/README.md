<div align="center">

# Typecast Java SDK

**Official Java SDK for the Typecast Text-to-Speech API**

[![Java](https://img.shields.io/badge/Java-8%2B-orange?style=flat-square&logo=openjdk)](https://www.java.com)
[![Maven](https://img.shields.io/badge/Maven-3.6%2B-C71A36?style=flat-square&logo=apachemaven)](https://maven.apache.org)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

[Getting Started](#quick-start) •
[Documentation](#usage-examples) •
[API Reference](#api-reference) •
[Eclipse Setup](#eclipse-ide-setup)

---

</div>

## Features

| Feature                 | Description                                             |
| ----------------------- | ------------------------------------------------------- |
| **Text-to-Speech**      | Convert text to natural-sounding speech                 |
| **37 Languages**        | Support for multiple languages with automatic detection |
| **Emotion Control**     | Preset and smart context-aware emotion synthesis        |
| **Voice Discovery**     | Browse and filter available voices                      |
| **Audio Customization** | Control volume, pitch, tempo, and format (WAV/MP3)      |
| **Java 8+**             | Compatible with Java 8 and later versions               |

## Requirements

- **Java** 8 or higher
- **Maven** 3.6+ or **Gradle** 7+

## Installation

<details>
<summary><b>Maven</b></summary>

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.typecast</groupId>
    <artifactId>typecast-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

</details>

<details>
<summary><b>Gradle</b></summary>

Add to your `build.gradle`:

```groovy
implementation 'io.typecast:typecast-java:1.0.0'
```

</details>

<details>
<summary><b>Manual Installation</b></summary>

1. Clone the repository:

   ```bash
   git clone https://github.com/typecast-ai/typecast-java.git
   cd typecast-java
   ```

2. Build and install to local Maven repository:
   ```bash
   mvn clean install -DskipTests
   ```

</details>

## Quick Start

```java
import io.typecast.TypecastClient;
import io.typecast.models.*;

import java.io.FileOutputStream;

public class QuickStart {
    public static void main(String[] args) throws Exception {
        // Create client with API key
        TypecastClient client = new TypecastClient("your-api-key");

        // Get available voices
        var voices = client.getVoicesV2();
        String voiceId = voices.get(0).getVoiceId();

        // Generate speech
        TTSRequest request = TTSRequest.builder()
                .voiceId(voiceId)
                .text("Hello, world! Welcome to Typecast.")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .build();

        TTSResponse response = client.textToSpeech(request);

        // Save audio to file
        try (FileOutputStream fos = new FileOutputStream("output.wav")) {
            fos.write(response.getAudioData());
        }

        System.out.println("Audio saved! Duration: " + response.getDuration() + "s");

        // Clean up
        client.close();
    }
}
```

## Configuration

### API Key

The SDK looks for your API key in the following order:

| Priority | Source                | Example                                |
| -------- | --------------------- | -------------------------------------- |
| 1        | Constructor parameter | `new TypecastClient("your-api-key")`   |
| 2        | `.env` file           | `TYPECAST_API_KEY=your-api-key`        |
| 3        | Environment variable  | `export TYPECAST_API_KEY=your-api-key` |

```java
// Option 1: Pass directly
TypecastClient client = new TypecastClient("your-api-key");

// Option 2: From environment (recommended for production)
TypecastClient client = new TypecastClient();
```

### Environment File

Create a `.env` file in your project root:

```env
TYPECAST_API_KEY=your_api_key_here
```

### Custom Base URL

```java
TypecastClient client = new TypecastClient("api-key", "https://custom-api.example.com");
```

## Usage Examples

<details open>
<summary><b>Basic Text-to-Speech</b></summary>

```java
TTSRequest request = TTSRequest.builder()
        .voiceId("tc_voice_id")
        .text("Hello, this is a test.")
        .model(TTSModel.SSFM_V30)
        .build();

TTSResponse response = client.textToSpeech(request);
byte[] audioData = response.getAudioData();
```

</details>

<details>
<summary><b>With Emotion (Preset)</b></summary>

```java
TTSRequest request = TTSRequest.builder()
        .voiceId("tc_voice_id")
        .text("I'm so excited about this!")
        .model(TTSModel.SSFM_V30)
        .prompt(PresetPrompt.builder()
                .emotionPreset(EmotionPreset.HAPPY)
                .emotionIntensity(1.5)  // 0.0 to 2.0
                .build())
        .build();

TTSResponse response = client.textToSpeech(request);
```

</details>

<details>
<summary><b>With Smart Emotion (Context-Aware)</b></summary>

```java
TTSRequest request = TTSRequest.builder()
        .voiceId("tc_voice_id")
        .text("Everything worked out perfectly.")
        .model(TTSModel.SSFM_V30)
        .prompt(SmartPrompt.builder()
                .previousText("After weeks of hard work,")
                .nextText("I couldn't be happier.")
                .build())
        .build();

TTSResponse response = client.textToSpeech(request);
```

</details>

<details>
<summary><b>Audio Output Settings</b></summary>

```java
TTSRequest request = TTSRequest.builder()
        .voiceId("tc_voice_id")
        .text("Custom audio settings test.")
        .model(TTSModel.SSFM_V30)
        .output(Output.builder()
                .volume(120)           // 0-200 (default: 100)
                .audioPitch(2)         // -12 to +12 semitones
                .audioTempo(1.2)       // 0.5 to 2.0 (default: 1.0)
                .audioFormat(AudioFormat.MP3)  // WAV or MP3
                .build())
        .build();

TTSResponse response = client.textToSpeech(request);
```

</details>

<details>
<summary><b>Voice Discovery</b></summary>

```java
// Get all voices
List<VoiceV2Response> allVoices = client.getVoicesV2();

// Filter voices
VoicesV2Filter filter = VoicesV2Filter.builder()
        .model(TTSModel.SSFM_V30)
        .gender(GenderEnum.FEMALE)
        .age(AgeEnum.YOUNG_ADULT)
        .build();

List<VoiceV2Response> filteredVoices = client.getVoicesV2(filter);

// Get specific voice
VoiceV2Response voice = client.getVoiceV2("tc_voice_id");
```

</details>

<details>
<summary><b>Error Handling</b></summary>

```java
import io.typecast.exceptions.*;

try {
    TTSResponse response = client.textToSpeech(request);
} catch (UnauthorizedException e) {
    System.err.println("Invalid API key: " + e.getMessage());
} catch (PaymentRequiredException e) {
    System.err.println("Insufficient credits: " + e.getMessage());
} catch (RateLimitException e) {
    System.err.println("Rate limit exceeded, please wait and retry");
} catch (BadRequestException e) {
    System.err.println("Invalid request: " + e.getMessage());
} catch (NotFoundException e) {
    System.err.println("Voice not found: " + e.getMessage());
} catch (TypecastException e) {
    System.err.println("API error: " + e.getMessage());
}
```

</details>

## Available Models

| Model      | Languages | Emotion Presets                                                    |
| :--------- | :-------: | :----------------------------------------------------------------- |
| `SSFM_V21` |    27     | `normal`, `happy`, `sad`, `angry`                                  |
| `SSFM_V30` |    37     | `normal`, `happy`, `sad`, `angry`, `whisper`, `toneup`, `tonedown` |

## Supported Languages

<details>
<summary><b>37 Languages (ISO 639-3 codes)</b></summary>

| Code  | Language  | Code  | Language   | Code  | Language   |
| :---: | :-------- | :---: | :--------- | :---: | :--------- |
| `ENG` | English   | `KOR` | Korean     | `JPN` | Japanese   |
| `SPA` | Spanish   | `DEU` | German     | `FRA` | French     |
| `ITA` | Italian   | `POL` | Polish     | `NLD` | Dutch      |
| `RUS` | Russian   | `ZHO` | Chinese    | `POR` | Portuguese |
| `ARA` | Arabic    | `HIN` | Hindi      | `THA` | Thai       |
| `TUR` | Turkish   | `VIE` | Vietnamese | `IND` | Indonesian |
| `SWE` | Swedish   | `DAN` | Danish     | `NOR` | Norwegian  |
| `FIN` | Finnish   | `ELL` | Greek      | `HUN` | Hungarian  |
| `CES` | Czech     | `SLK` | Slovak     | `UKR` | Ukrainian  |
| `HRV` | Croatian  | `RON` | Romanian   | `BUL` | Bulgarian  |
| `BEN` | Bengali   | `TAM` | Tamil      | `TGL` | Tagalog    |
| `MSA` | Malay     | `PAN` | Punjabi    | `NAN` | Min Nan    |
| `YUE` | Cantonese |       |            |       |            |

</details>

## Eclipse IDE Setup

<details>
<summary><b>Importing the Project</b></summary>

1. **Clone or download** the project to your local machine

2. **Import as Maven Project**:
   - Open Eclipse
   - Go to `File` → `Import...`
   - Select `Maven` → `Existing Maven Projects`
   - Click `Next`
   - Browse to the `typecast-java` directory
   - Ensure `pom.xml` is selected
   - Click `Finish`

3. **Wait for Maven dependencies** to download (check the progress in the bottom right)

</details>

<details>
<summary><b>Using as a Dependency</b></summary>

1. **Build and install** the SDK to your local Maven repository:
   - Right-click on the project → `Run As` → `Maven install`
   - Or run from terminal: `mvn clean install -DskipTests`

2. **Add dependency** to your project's `pom.xml`:

   ```xml
   <dependency>
       <groupId>io.typecast</groupId>
       <artifactId>typecast-java</artifactId>
       <version>1.0.0</version>
   </dependency>
   ```

3. **Update Maven project**:
   - Right-click on your project → `Maven` → `Update Project...`

</details>

<details>
<summary><b>Running Tests</b></summary>

1. **Unit Tests**:
   - Right-click on `src/test/java` → `Run As` → `JUnit Test`
   - Or right-click on a specific test class → `Run As` → `JUnit Test`

2. **E2E Tests**:
   - Create a `.env` file in the project root with your API key
   - Right-click on `TypecastClientE2ETest.java` → `Run As` → `JUnit Test`

</details>

<details>
<summary><b>Troubleshooting</b></summary>

| Issue                      | Solution                                                                    |
| -------------------------- | --------------------------------------------------------------------------- |
| Dependencies not resolving | Right-click project → `Maven` → `Update Project...` → Check "Force Update"  |
| Java version mismatch      | Right-click project → `Properties` → `Java Build Path` → Ensure JRE is 1.8+ |
| Build errors               | `Project` → `Clean...` → Select the project → `Clean`                       |

</details>

## Building from Source

```bash
# Clone the repository
git clone https://github.com/typecast-ai/typecast-java.git
cd typecast-java

# Build the project
mvn clean package

# Run unit tests
mvn test

# Run E2E tests (requires API key in .env)
mvn verify -Pe2e

# Install to local Maven repository
mvn clean install
```

## API Reference

### TypecastClient

| Method                        | Description                          |
| ----------------------------- | ------------------------------------ |
| `textToSpeech(TTSRequest)`    | Convert text to speech audio         |
| `getVoicesV2()`               | Get all available voices (V2 API)    |
| `getVoicesV2(VoicesV2Filter)` | Get filtered voices (V2 API)         |
| `getVoiceV2(String voiceId)`  | Get a specific voice by ID           |
| `getVoices()`                 | Get voices (V1 API, deprecated)      |
| `getVoice(String voiceId)`    | Get voice by ID (V1 API, deprecated) |
| `close()`                     | Release resources                    |

### TTSRequest

| Field      | Type                                      | Required | Description                               |
| ---------- | ----------------------------------------- | :------: | ----------------------------------------- |
| `voiceId`  | `String`                                  |    ✅    | Voice ID (format: `tc_*` or `uc_*`)       |
| `text`     | `String`                                  |    ✅    | Text to synthesize (max 5000 chars)       |
| `model`    | `TTSModel`                                |    ✅    | TTS model (`SSFM_V21` or `SSFM_V30`)      |
| `language` | `LanguageCode`                            |          | ISO 639-3 code (auto-detected if omitted) |
| `prompt`   | `Prompt` / `PresetPrompt` / `SmartPrompt` |          | Emotion settings                          |
| `output`   | `Output`                                  |          | Audio output settings                     |
| `seed`     | `Integer`                                 |          | Random seed for reproducibility           |

### TTSResponse

| Field       | Type     | Description                   |
| ----------- | -------- | ----------------------------- |
| `audioData` | `byte[]` | Generated audio data          |
| `duration`  | `double` | Audio duration in seconds     |
| `format`    | `String` | Audio format (`wav` or `mp3`) |

---

<div align="center">

## License

MIT License - see [LICENSE](LICENSE) for details.

## Support

[Documentation](https://typecast.ai/docs) •
[Email](mailto:help@typecast.ai) •
[GitHub Issues](https://github.com/typecast-ai/typecast-java/issues)

---

Made with ❤️ by [Typecast](https://typecast.ai)

</div>
