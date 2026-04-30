# Typecast Kotlin SDK

[![coverage-kotlin](https://github.com/neosapience/typecast-sdk/actions/workflows/coverage-kotlin.yml/badge.svg)](https://github.com/neosapience/typecast-sdk/actions/workflows/coverage-kotlin.yml)
[![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)](https://github.com/neosapience/typecast-sdk/actions/workflows/coverage-kotlin.yml)

Official Kotlin SDK for the [Typecast](https://typecast.ai/?lang=en) Text-to-Speech API.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.neosapience:typecast-kotlin:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.neosapience:typecast-kotlin:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.neosapience</groupId>
    <artifactId>typecast-kotlin</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```kotlin
import com.neosapience.TypecastClient
import com.neosapience.models.*

// Create client with API key
val client = TypecastClient.create("your-api-key")

// Or use environment variable (TYPECAST_API_KEY)
val client = TypecastClient.create()

// Generate speech
val request = TTSRequest.builder()
    .voiceId("tc_60e5426de8b95f1d3000d7b5")
    .text("Hello, world!")
    .model(TTSModel.SSFM_V30)
    .language(LanguageCode.ENG)
    .build()

val response = client.textToSpeech(request)

// Save audio to file
File("output.wav").writeBytes(response.audioData)

// Don't forget to close the client when done
client.close()
```

## Features

- **Text-to-Speech**: Convert text to natural-sounding speech
- **TTS with Timestamps**: Generate audio with word/character-level alignment, export SRT/VTT captions
- **Voice Discovery**: Browse available voices with filtering options
- **Emotion Control**: Use preset emotions or context-aware smart emotions
- **Audio Customization**: Control volume, pitch, tempo, and output format

## Usage Examples

### List Available Voices

```kotlin
// Get all voices (V2 API with enhanced metadata)
val voices = client.getVoicesV2()
voices.forEach { voice ->
    println("${voice.voiceName} (${voice.voiceId})")
    println("  Gender: ${voice.gender}, Age: ${voice.age}")
    println("  Models: ${voice.models.map { it.version }}")
}

// Filter voices
val filter = VoicesV2Filter.builder()
    .model(TTSModel.SSFM_V30)
    .gender(GenderEnum.FEMALE)
    .age(AgeEnum.YOUNG_ADULT)
    .build()

val filteredVoices = client.getVoicesV2(filter)
```

### Get Specific Voice

```kotlin
val voice = client.getVoiceV2("tc_60e5426de8b95f1d3000d7b5")
println("Voice: ${voice.voiceName}")
println("Supported emotions: ${voice.models.flatMap { it.emotions }}")
```

### Text-to-Speech with Preset Emotion

```kotlin
val request = TTSRequest.builder()
    .voiceId("tc_60e5426de8b95f1d3000d7b5")
    .text("I am so excited about this!")
    .model(TTSModel.SSFM_V30)
    .language(LanguageCode.ENG)
    .prompt(PresetPrompt.builder()
        .emotionPreset(EmotionPreset.HAPPY)
        .emotionIntensity(1.5)
        .build())
    .build()

val response = client.textToSpeech(request)
```

### Text-to-Speech with Smart Emotion

Smart emotion automatically infers the appropriate emotion from context:

```kotlin
val request = TTSRequest.builder()
    .voiceId("tc_60e5426de8b95f1d3000d7b5")
    .text("Everything turned out perfectly.")
    .model(TTSModel.SSFM_V30)
    .language(LanguageCode.ENG)
    .prompt(SmartPrompt.builder()
        .previousText("After all that hard work,")
        .nextText("I couldn't be happier.")
        .build())
    .build()

val response = client.textToSpeech(request)
```

### Custom Audio Settings

```kotlin
val request = TTSRequest.builder()
    .voiceId("tc_60e5426de8b95f1d3000d7b5")
    .text("Testing audio settings.")
    .model(TTSModel.SSFM_V30)
    .output(Output.builder()
        .volume(120)         // 0-200, default 100
        .audioPitch(2)       // -12 to 12 semitones
        .audioTempo(1.1)     // 0.5 to 2.0
        .audioFormat(AudioFormat.MP3)  // WAV or MP3
        .build())
    .build()

val response = client.textToSpeech(request)
```

### Text-to-Speech with Timestamps

Get word/character-level alignment data alongside the generated audio, and export
SRT or WebVTT caption files:

```kotlin
import com.neosapience.TypecastClient
import com.neosapience.models.*
import java.nio.file.Paths

val client = TypecastClient.create("your-api-key")

val request = TTSRequestWithTimestamps(
    voiceId = "tc_60e5426de8b95f1d3000d7b5",
    text = "Hello, world!",
    model = TTSModel.SSFM_V30,
    language = LanguageCode.ENG,
)

// Request both word and character alignment (granularity = null means both)
val response = client.textToSpeechWithTimestamps(request, null)

// Save the audio file
response.saveAudio(Paths.get("output.wav"))

// Export captions
val srt = response.toSrt()          // SRT format
val vtt = response.toVtt()          // WebVTT format

// Or request only word-level alignment
val wordResponse = client.textToSpeechWithTimestamps(request, "word")
```

The `granularity` parameter controls what alignment data is returned:
- `null` — both word and character level
- `"word"` — word-level only
- `"char"` — character-level only

The caption exporter automatically picks the best available granularity:
words (if ≥ 2 words present) → characters (if present) → single word.

### Using with Seed for Reproducibility

```kotlin
val request = TTSRequest.builder()
    .voiceId("tc_60e5426de8b95f1d3000d7b5")
    .text("This will always sound the same.")
    .model(TTSModel.SSFM_V30)
    .seed(42)  // Same seed = same output
    .build()
```

## Configuration

### Environment Variables

- `TYPECAST_API_KEY`: Your Typecast API key
- `TYPECAST_API_HOST`: Custom API host (optional, default: `https://api.typecast.ai`)

### .env File

Create a `.env` file in your project root:

```env
TYPECAST_API_KEY=your_api_key_here
```

### Custom HTTP Client

```kotlin
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

val customHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .build()

val client = TypecastClient.builder()
    .apiKey("your-api-key")
    .httpClient(customHttpClient)
    .build()
```

## Error Handling

The SDK throws specific exceptions for different error cases:

```kotlin
import com.neosapience.exceptions.*

try {
    val response = client.textToSpeech(request)
} catch (e: UnauthorizedException) {
    // 401: Invalid API key
} catch (e: PaymentRequiredException) {
    // 402: Insufficient credits
} catch (e: ForbiddenException) {
    // 403: Access denied
} catch (e: NotFoundException) {
    // 404: Voice not found
} catch (e: RateLimitException) {
    // 429: Too many requests
} catch (e: TypecastException) {
    // Other API errors
    println("Error: ${e.message}, Status: ${e.statusCode}")
}
```

## Supported Languages

The SDK supports 37 languages with the `ssfm-v30` model. See `LanguageCode` enum for the full list.

Common languages:
- English (`LanguageCode.ENG`)
- Korean (`LanguageCode.KOR`)
- Japanese (`LanguageCode.JPN`)
- Chinese (`LanguageCode.ZHO`)
- Spanish (`LanguageCode.SPA`)
- German (`LanguageCode.DEU`)
- French (`LanguageCode.FRA`)

## Requirements

- JDK 17 or higher
- Kotlin 1.9+

## Development

### Running Tests

```bash
# Unit tests
./gradlew test

# E2E tests (requires TYPECAST_API_KEY)
./gradlew e2eTest
```

### Building

```bash
./gradlew build
```

## License

MIT License - see [LICENSE](LICENSE) for details.

## Links

- [Typecast Website](https://typecast.ai/?lang=en)
- [API Documentation](https://typecast.ai/docs)
- [GitHub Repository](https://github.com/typecast-ai/typecast-kotlin)
