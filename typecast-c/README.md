# Typecast C/C++ SDK

Text-to-Speech API client library for [Typecast AI](https://typecast.ai/?lang=en).

## Features

- **C and C++ Support**: Pure C API with optional C++ wrapper
- **Cross-Platform**: Windows, Linux, macOS, ARM (32/64-bit)
- **Embedded Ready**: Cross-compilation support for ARM, optimized for minimal footprint
- **Unreal Engine Ready**: Designed for easy integration with Unreal Engine
- **Full API Coverage**: TTS generation, voice listing, emotion control
- **Multiple Models**: ssfm-v21 and ssfm-v30 support
- **Smart Emotion**: Context-aware emotion inference (ssfm-v30)
- **MIT License**: Free for commercial and personal use

## Supported Platforms (E2E Tested)

The following platforms have been verified through automated E2E testing. The library is built and tested in each environment to ensure compatibility.

| Platform             | Architecture   | glibc | C Standard | Status      |
| -------------------- | -------------- | ----- | ---------- | ----------- |
| **CentOS 6.9**       | x86_64         | 2.12  | C99        | ✅ Verified |
| **CentOS 7**         | x86_64         | 2.17  | C11        | ✅ Verified |
| **Amazon Linux 2**   | x86_64         | 2.26  | C11        | ✅ Verified |
| **Ubuntu 20.04 LTS** | x86_64         | 2.31  | C11        | ✅ Verified |
| **Debian Bullseye**  | x86_64         | 2.31  | C11        | ✅ Verified |
| **Windows**          | x64            | N/A   | C11        | ✅ Verified |
| **macOS**            | x86_64 / arm64 | N/A   | C11        | ✅ Verified |

### Platform-Specific Notes

#### CentOS 6.9 (glibc 2.12)

- **Oldest supported Linux** - binaries built on CentOS 6 will run on all newer Linux distributions
- Requires `-std=c99` flag for compilation (GCC 4.4 default is C89)
- CMake 2.8 is too old; library is compiled directly with GCC in E2E tests
- Recommended for maximum Linux compatibility

#### CentOS 7 (glibc 2.17)

- EOL (End of Life) - repositories require vault.centos.org mirror configuration
- Good balance between compatibility and modern tooling
- CMake available in base repositories

#### Amazon Linux 2

- Recommended for **AWS deployments**
- Uses `cmake3` command (not `cmake`)
- Actively maintained with security updates

#### Ubuntu 20.04 / Debian Bullseye (glibc 2.31)

- **Modern Linux** distributions with up-to-date toolchains
- Full C11 support
- Recommended for new projects without legacy requirements

#### Windows (x64)

- Tested via MinGW-w64 cross-compilation and Wine emulation
- Requires libcurl DLL (pre-built binaries available from https://curl.se/windows/)
- MSVC builds also supported with vcpkg
- Runtime requires `libcurl.dll` in PATH or application directory

#### macOS

- Universal binary support (x86_64 and arm64)
- Tested on macOS Ventura and later
- Uses system libcurl (no additional installation required via Xcode)

## Requirements

- CMake 3.14+
- libcurl (with SSL support)
- C11 compatible compiler

### Platform-specific

**Windows (MSVC)**:

```powershell
vcpkg install curl:x64-windows
```

**Linux (Ubuntu/Debian)**:

```bash
sudo apt-get install libcurl4-openssl-dev
```

**macOS**:

```bash
brew install curl
```

## Building

```bash
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build .
```

### Build Options

| Option                    | Default | Description                            |
| ------------------------- | ------- | -------------------------------------- |
| `TYPECAST_BUILD_SHARED`   | ON      | Build shared library (.dll/.so/.dylib) |
| `TYPECAST_BUILD_STATIC`   | OFF     | Build static library                   |
| `TYPECAST_BUILD_EXAMPLES` | ON      | Build example programs                 |
| `TYPECAST_BUILD_TESTS`    | ON      | Build test programs                    |

## Quick Start

### C Example

```c
#include "typecast.h"

int main() {
    // Create client
    TypecastClient* client = typecast_client_create("your-api-key");
    if (!client) return 1;

    // Generate speech
    TypecastTTSRequest req = {0};
    req.text = "Hello, world!";
    req.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    req.model = TYPECAST_MODEL_SSFM_V30;
    req.language = "eng";

    TypecastTTSResponse* resp = typecast_text_to_speech(client, &req);
    if (resp) {
        // Save audio to file
        FILE* f = fopen("output.wav", "wb");
        fwrite(resp->audio_data, 1, resp->audio_size, f);
        fclose(f);

        typecast_tts_response_free(resp);
    }

    typecast_client_destroy(client);
    return 0;
}
```

### C++ Example (with TYPECAST_CPP_WRAPPER)

```cpp
#define TYPECAST_CPP_WRAPPER
#include "typecast.h"

int main() {
    typecast::Client client("your-api-key");

    auto response = client.textToSpeech({
        .text = "Hello, world!",
        .voiceId = "tc_60e5426de8b95f1d3000d7b5",
        .model = typecast::Model::SSFM_V30
    });

    // response.audioData contains the WAV data
    return 0;
}
```

## API Reference

### Client Functions

```c
// Create client with API key
TypecastClient* typecast_client_create(const char* api_key);

// Create client with custom host
TypecastClient* typecast_client_create_with_host(const char* api_key, const char* host);

// Destroy client
void typecast_client_destroy(TypecastClient* client);

// Get last error
const TypecastError* typecast_client_get_error(const TypecastClient* client);
```

### Text-to-Speech

```c
// Generate speech
TypecastTTSResponse* typecast_text_to_speech(
    TypecastClient* client,
    const TypecastTTSRequest* request
);

// Free response
void typecast_tts_response_free(TypecastTTSResponse* response);
```

### Voice Management

```c
// Get all voices
TypecastVoicesResponse* typecast_get_voices(
    TypecastClient* client,
    const TypecastVoicesFilter* filter
);

// Get single voice
TypecastVoice* typecast_get_voice(
    TypecastClient* client,
    const char* voice_id
);

// Free responses
void typecast_voices_response_free(TypecastVoicesResponse* response);
void typecast_voice_free(TypecastVoice* voice);
```

## Models

| Model                     | Description                        | Emotions                                             |
| ------------------------- | ---------------------------------- | ---------------------------------------------------- |
| `TYPECAST_MODEL_SSFM_V21` | Stable production model            | normal, happy, sad, angry                            |
| `TYPECAST_MODEL_SSFM_V30` | Latest model with improved prosody | normal, happy, sad, angry, whisper, toneup, tonedown |

## Emotion Control

### Preset Emotion (ssfm-v21 & ssfm-v30)

```c
TypecastPrompt prompt = TYPECAST_PROMPT_DEFAULT();
prompt.emotion_type = TYPECAST_EMOTION_TYPE_PRESET;
prompt.emotion_preset = TYPECAST_EMOTION_HAPPY;
prompt.emotion_intensity = 1.5f;  // 0.0 - 2.0
request.prompt = &prompt;
```

### Smart Emotion (ssfm-v30 only)

```c
TypecastPrompt prompt = {0};
prompt.emotion_type = TYPECAST_EMOTION_TYPE_SMART;
prompt.previous_text = "Context before the main text";
prompt.next_text = "Context after the main text";
request.prompt = &prompt;
```

## Audio Output Settings

```c
TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
output.volume = 100;           // 0-200
output.audio_pitch = 0;        // -12 to +12 semitones
output.audio_tempo = 1.0f;     // 0.5 to 2.0
output.audio_format = TYPECAST_AUDIO_FORMAT_WAV;  // or MP3
request.output = &output;
```

## Error Handling

```c
TypecastTTSResponse* resp = typecast_text_to_speech(client, &req);
if (!resp) {
    const TypecastError* err = typecast_client_get_error(client);
    printf("Error %d: %s\n", err->code, err->message);
}
```

### Error Codes

| Code | Name                            | Description          |
| ---- | ------------------------------- | -------------------- |
| 0    | TYPECAST_OK                     | Success              |
| 400  | TYPECAST_ERROR_BAD_REQUEST      | Invalid request      |
| 401  | TYPECAST_ERROR_UNAUTHORIZED     | Invalid API key      |
| 402  | TYPECAST_ERROR_PAYMENT_REQUIRED | Insufficient credits |
| 404  | TYPECAST_ERROR_NOT_FOUND        | Voice not found      |
| 429  | TYPECAST_ERROR_RATE_LIMIT       | Rate limit exceeded  |

## Embedded Systems Integration

This SDK can be integrated into embedded systems with network connectivity. Below are guidelines for cross-compiling and optimizing for resource-constrained environments.

### Supported Platforms

| Platform             | Compiler                | Notes                                |
| -------------------- | ----------------------- | ------------------------------------ |
| ARM Cortex-A (Linux) | arm-linux-gnueabihf-gcc | Raspberry Pi, BeagleBone             |
| ARM64 (Linux)        | aarch64-linux-gnu-gcc   | NVIDIA Jetson, RPi 4 64-bit          |
| ESP-IDF              | xtensa-esp32-elf-gcc    | ESP32 with PSRAM recommended         |
| Zephyr RTOS          | arm-zephyr-eabi-gcc     | Requires libcurl port                |
| Custom RTOS          | Various                 | Requires libcurl or HTTP abstraction |

### Cross-Compilation

**ARM Linux (32-bit)**:

```bash
mkdir build-arm && cd build-arm
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=../cmake/arm-linux-gnueabihf.cmake \
    -DTYPECAST_BUILD_STATIC=ON \
    -DTYPECAST_BUILD_SHARED=OFF \
    -DCMAKE_BUILD_TYPE=MinSizeRel
cmake --build .
```

**ARM64 Linux (NVIDIA Jetson, Raspberry Pi 4)**:

```bash
mkdir build-arm64 && cd build-arm64
cmake .. \
    -DCMAKE_SYSTEM_NAME=Linux \
    -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DTYPECAST_BUILD_STATIC=ON \
    -DTYPECAST_BUILD_SHARED=OFF \
    -DCMAKE_BUILD_TYPE=MinSizeRel
cmake --build .
```

### Memory Requirements

| Component                   | Approximate Size      |
| --------------------------- | --------------------- |
| Static library (MinSizeRel) | ~50 KB                |
| Runtime heap per client     | ~8 KB                 |
| TTS response buffer         | Variable (audio size) |
| JSON parsing buffer         | ~4 KB                 |

### Optimizing for Embedded

1. **Use Static Library**: Set `TYPECAST_BUILD_STATIC=ON` and `TYPECAST_BUILD_SHARED=OFF`
2. **Minimize Size**: Use `-DCMAKE_BUILD_TYPE=MinSizeRel` for smallest binary
3. **Disable Unused Features**: Set `TYPECAST_BUILD_EXAMPLES=OFF` and `TYPECAST_BUILD_TESTS=OFF`

```bash
cmake .. \
    -DTYPECAST_BUILD_STATIC=ON \
    -DTYPECAST_BUILD_SHARED=OFF \
    -DTYPECAST_BUILD_EXAMPLES=OFF \
    -DTYPECAST_BUILD_TESTS=OFF \
    -DCMAKE_BUILD_TYPE=MinSizeRel
```

### libcurl Considerations for Embedded

The SDK requires libcurl with SSL support. For embedded systems:

**Option 1: System libcurl (Recommended for Linux-based embedded)**

```bash
# Yocto/OpenEmbedded
DEPENDS += "curl"

# Buildroot
BR2_PACKAGE_LIBCURL=y
BR2_PACKAGE_LIBCURL_OPENSSL=y
```

**Option 2: Static libcurl (Minimal footprint)**

```bash
# Build libcurl with minimal features
./configure \
    --disable-shared \
    --enable-static \
    --with-ssl \
    --disable-ftp \
    --disable-ldap \
    --disable-dict \
    --disable-telnet \
    --disable-tftp \
    --disable-pop3 \
    --disable-imap \
    --disable-smtp \
    --disable-gopher \
    --disable-manual \
    --without-libidn \
    --without-librtmp \
    --without-libssh2
```

**Option 3: HTTP Abstraction (Advanced)**

For platforms without libcurl, you can implement a custom HTTP backend by modifying `src/typecast.c`:

```c
// Replace curl calls with your platform's HTTP implementation
// See src/typecast.c for the HTTP interface functions
```

### Example: Raspberry Pi Integration

```c
#include "typecast.h"
#include <stdio.h>
#include <stdlib.h>

// Play audio via ALSA on Raspberry Pi
void play_audio_alsa(const uint8_t* data, size_t size);

int main() {
    TypecastClient* client = typecast_client_create(getenv("TYPECAST_API_KEY"));
    if (!client) {
        fprintf(stderr, "Failed to create client\n");
        return 1;
    }

    TypecastTTSRequest req = {0};
    req.text = "Hello from Raspberry Pi!";
    req.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    req.model = TYPECAST_MODEL_SSFM_V30;
    req.language = "eng";

    TypecastTTSResponse* resp = typecast_text_to_speech(client, &req);
    if (resp) {
        printf("Generated %.2f seconds of audio\n", resp->duration);
        play_audio_alsa(resp->audio_data, resp->audio_size);
        typecast_tts_response_free(resp);
    } else {
        const TypecastError* err = typecast_client_get_error(client);
        fprintf(stderr, "TTS Error: %s\n", err->message);
    }

    typecast_client_destroy(client);
    return 0;
}
```

### Thread Safety

The SDK is **not** thread-safe by default. For multi-threaded embedded applications:

- Create separate `TypecastClient` instances per thread, or
- Use mutex/semaphore to synchronize access to a shared client

```c
// Example with pthread
pthread_mutex_t client_mutex = PTHREAD_MUTEX_INITIALIZER;
TypecastClient* shared_client;

void* tts_thread(void* arg) {
    pthread_mutex_lock(&client_mutex);
    TypecastTTSResponse* resp = typecast_text_to_speech(shared_client, &req);
    pthread_mutex_unlock(&client_mutex);
    // ... handle response
    return NULL;
}
```

## Unreal Engine Integration

This SDK is designed for seamless integration with Unreal Engine 4.27+ and Unreal Engine 5.x.

### Step 1: Build the SDK

Build as a static library for your target platforms:

**Windows (x64)**:

```powershell
mkdir build && cd build
cmake .. -DTYPECAST_BUILD_STATIC=ON -DTYPECAST_BUILD_SHARED=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
```

**Linux**:

```bash
mkdir build && cd build
cmake .. -DTYPECAST_BUILD_STATIC=ON -DTYPECAST_BUILD_SHARED=OFF -DCMAKE_BUILD_TYPE=Release
cmake --build .
```

### Step 2: Create Plugin Structure

Create a plugin in your Unreal project with the following structure:

```
Plugins/
└── TypecastTTS/
    ├── Source/
    │   └── TypecastTTS/
    │       ├── Private/
    │       │   ├── TypecastTTSModule.cpp
    │       │   └── TypecastClient.cpp
    │       ├── Public/
    │       │   ├── TypecastTTSModule.h
    │       │   └── TypecastClient.h
    │       └── ThirdParty/
    │           └── Typecast/
    │               ├── include/
    │               │   └── typecast.h
    │               └── lib/
    │                   ├── Win64/
    │                   │   └── typecast_static.lib
    │                   └── Linux/
    │                       └── libtypecast_static.a
    ├── TypecastTTS.uplugin
    └── TypecastTTS.Build.cs
```

### Step 3: Configure Build.cs

Create `TypecastTTS.Build.cs`:

```csharp
using UnrealBuildTool;
using System.IO;

public class TypecastTTS : ModuleRules
{
    public TypecastTTS(ReadOnlyTargetRules Target) : base(Target)
    {
        PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;

        PublicDependencyModuleNames.AddRange(new string[] {
            "Core",
            "CoreUObject",
            "Engine",
            "HTTP",
            "Json",
            "JsonUtilities"
        });

        // Typecast SDK paths
        string ThirdPartyPath = Path.Combine(ModuleDirectory, "ThirdParty", "Typecast");
        string IncludePath = Path.Combine(ThirdPartyPath, "include");
        string LibPath = Path.Combine(ThirdPartyPath, "lib");

        // Add include path
        PublicIncludePaths.Add(IncludePath);

        // Add static library definition
        PublicDefinitions.Add("TYPECAST_STATIC");

        // Platform-specific library linking
        if (Target.Platform == UnrealTargetPlatform.Win64)
        {
            PublicAdditionalLibraries.Add(Path.Combine(LibPath, "Win64", "typecast_static.lib"));

            // Link libcurl (use Unreal's bundled version)
            AddEngineThirdPartyPrivateStaticDependencies(Target, "libcurl");
        }
        else if (Target.Platform == UnrealTargetPlatform.Linux)
        {
            PublicAdditionalLibraries.Add(Path.Combine(LibPath, "Linux", "libtypecast_static.a"));

            // Link libcurl
            AddEngineThirdPartyPrivateStaticDependencies(Target, "libcurl");
        }
        else if (Target.Platform == UnrealTargetPlatform.Mac)
        {
            PublicAdditionalLibraries.Add(Path.Combine(LibPath, "Mac", "libtypecast_static.a"));

            // Link libcurl
            AddEngineThirdPartyPrivateStaticDependencies(Target, "libcurl");
        }
    }
}
```

### Step 4: Create Wrapper Class

Create `TypecastClient.h`:

```cpp
#pragma once

#include "CoreMinimal.h"
#include "typecast.h"
#include "TypecastClient.generated.h"

UENUM(BlueprintType)
enum class ETypecastModel : uint8
{
    SSFM_V21 UMETA(DisplayName = "SSFM V21"),
    SSFM_V30 UMETA(DisplayName = "SSFM V30")
};

UENUM(BlueprintType)
enum class ETypecastEmotion : uint8
{
    Normal,
    Happy,
    Sad,
    Angry,
    Whisper,
    ToneUp,
    ToneDown
};

USTRUCT(BlueprintType)
struct FTypecastTTSRequest
{
    GENERATED_BODY()

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    FString Text;

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    FString VoiceId;

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    ETypecastModel Model = ETypecastModel::SSFM_V30;

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    FString Language = TEXT("eng");

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    ETypecastEmotion Emotion = ETypecastEmotion::Normal;

    UPROPERTY(EditAnywhere, BlueprintReadWrite)
    float EmotionIntensity = 1.0f;
};

USTRUCT(BlueprintType)
struct FTypecastTTSResponse
{
    GENERATED_BODY()

    UPROPERTY(BlueprintReadOnly)
    TArray<uint8> AudioData;

    UPROPERTY(BlueprintReadOnly)
    float Duration = 0.0f;

    UPROPERTY(BlueprintReadOnly)
    bool bSuccess = false;

    UPROPERTY(BlueprintReadOnly)
    FString ErrorMessage;
};

DECLARE_DYNAMIC_DELEGATE_OneParam(FOnTTSComplete, const FTypecastTTSResponse&, Response);

UCLASS(BlueprintType)
class TYPECASTTTS_API UTypecastClient : public UObject
{
    GENERATED_BODY()

public:
    UTypecastClient();
    virtual ~UTypecastClient();

    UFUNCTION(BlueprintCallable, Category = "Typecast")
    void Initialize(const FString& ApiKey);

    UFUNCTION(BlueprintCallable, Category = "Typecast")
    void TextToSpeech(const FTypecastTTSRequest& Request, const FOnTTSComplete& OnComplete);

    UFUNCTION(BlueprintCallable, Category = "Typecast")
    FTypecastTTSResponse TextToSpeechSync(const FTypecastTTSRequest& Request);

    UFUNCTION(BlueprintCallable, Category = "Typecast")
    bool IsInitialized() const { return Client != nullptr; }

private:
    ::TypecastClient* Client = nullptr;
};
```

Create `TypecastClient.cpp`:

```cpp
#include "TypecastClient.h"
#include "Async/Async.h"

UTypecastClient::UTypecastClient()
{
}

UTypecastClient::~UTypecastClient()
{
    if (Client)
    {
        typecast_client_destroy(Client);
        Client = nullptr;
    }
}

void UTypecastClient::Initialize(const FString& ApiKey)
{
    if (Client)
    {
        typecast_client_destroy(Client);
    }

    Client = typecast_client_create(TCHAR_TO_UTF8(*ApiKey));
}

FTypecastTTSResponse UTypecastClient::TextToSpeechSync(const FTypecastTTSRequest& Request)
{
    FTypecastTTSResponse Response;

    if (!Client)
    {
        Response.ErrorMessage = TEXT("Client not initialized");
        return Response;
    }

    // Build request
    TypecastTTSRequest Req = {0};

    FString TextUtf8 = Request.Text;
    FString VoiceIdUtf8 = Request.VoiceId;
    FString LanguageUtf8 = Request.Language;

    Req.text = TCHAR_TO_UTF8(*TextUtf8);
    Req.voice_id = TCHAR_TO_UTF8(*VoiceIdUtf8);
    Req.model = (Request.Model == ETypecastModel::SSFM_V30) ?
        TYPECAST_MODEL_SSFM_V30 : TYPECAST_MODEL_SSFM_V21;
    Req.language = TCHAR_TO_UTF8(*LanguageUtf8);

    // Configure emotion
    TypecastPrompt Prompt = TYPECAST_PROMPT_DEFAULT();
    Prompt.emotion_type = TYPECAST_EMOTION_TYPE_PRESET;
    Prompt.emotion_preset = static_cast<TypecastEmotionPreset>(Request.Emotion);
    Prompt.emotion_intensity = Request.EmotionIntensity;
    Req.prompt = &Prompt;

    // Configure output
    TypecastOutput Output = TYPECAST_OUTPUT_DEFAULT();
    Output.audio_format = TYPECAST_AUDIO_FORMAT_WAV;
    Req.output = &Output;

    // Call API
    TypecastTTSResponse* Resp = typecast_text_to_speech(Client, &Req);

    if (Resp)
    {
        Response.bSuccess = true;
        Response.Duration = Resp->duration;
        Response.AudioData.SetNumUninitialized(Resp->audio_size);
        FMemory::Memcpy(Response.AudioData.GetData(), Resp->audio_data, Resp->audio_size);
        typecast_tts_response_free(Resp);
    }
    else
    {
        const TypecastError* Err = typecast_client_get_error(Client);
        Response.ErrorMessage = UTF8_TO_TCHAR(Err->message ? Err->message : "Unknown error");
    }

    return Response;
}

void UTypecastClient::TextToSpeech(const FTypecastTTSRequest& Request, const FOnTTSComplete& OnComplete)
{
    // Run on background thread to avoid blocking game thread
    AsyncTask(ENamedThreads::AnyBackgroundThreadNormalTask, [this, Request, OnComplete]()
    {
        FTypecastTTSResponse Response = TextToSpeechSync(Request);

        // Callback on game thread
        AsyncTask(ENamedThreads::GameThread, [OnComplete, Response]()
        {
            OnComplete.ExecuteIfBound(Response);
        });
    });
}
```

### Step 5: Blueprint Usage

Once the plugin is set up, you can use it in Blueprints:

1. **Create Client**: Create a `TypecastClient` object and call `Initialize` with your API key
2. **Generate Speech**: Call `TextToSpeech` with a request struct
3. **Play Audio**: Use the returned audio data with Unreal's audio system

Example Blueprint flow:

```
BeginPlay
    → Create TypecastClient
    → Initialize(ApiKey)

OnButtonClick
    → Create TTSRequest (Text, VoiceId, Model, Emotion)
    → TextToSpeech(Request)
    → OnTTSComplete
        → Import WAV to SoundWave
        → Play Sound
```

### Step 6: Playing Generated Audio

To play the generated WAV audio in Unreal:

```cpp
#include "Sound/SoundWaveProcedural.h"

USoundWave* CreateSoundWaveFromData(const TArray<uint8>& AudioData)
{
    // Parse WAV header and create SoundWave
    USoundWave* SoundWave = NewObject<USoundWave>();

    // WAV format: 44 byte header + PCM data
    // Sample rate: 44100 Hz, 16-bit, Mono

    if (AudioData.Num() > 44)
    {
        SoundWave->SetSampleRate(44100);
        SoundWave->NumChannels = 1;
        SoundWave->Duration = (AudioData.Num() - 44) / (44100.0f * 2);
        SoundWave->RawPCMDataSize = AudioData.Num() - 44;
        SoundWave->RawPCMData = static_cast<uint8*>(FMemory::Malloc(SoundWave->RawPCMDataSize));
        FMemory::Memcpy(SoundWave->RawPCMData, AudioData.GetData() + 44, SoundWave->RawPCMDataSize);
    }

    return SoundWave;
}

// Usage
FTypecastTTSResponse Response = Client->TextToSpeechSync(Request);
if (Response.bSuccess)
{
    USoundWave* Sound = CreateSoundWaveFromData(Response.AudioData);
    UGameplayStatics::PlaySound2D(GetWorld(), Sound);
}
```

### Important Notes for Unreal Engine

1. **Thread Safety**: Always call `TextToSpeech` (async version) to avoid blocking the game thread
2. **Memory Management**: The wrapper class handles cleanup automatically via destructor
3. **API Key Security**: Store your API key in a config file or environment variable, not in code
4. **Audio Format**: The SDK returns 16-bit PCM WAV at 44100 Hz sample rate
5. **Platforms**: Test on all target platforms as libcurl behavior may vary

## Running Tests

### Unit Tests

```bash
cd build

# Unit tests (no API key required)
./test_typecast

# Integration tests (requires API key)
./test_integration YOUR_API_KEY
# or
export TYPECAST_API_KEY=your-api-key
./test_integration
```

## E2E Testing

The SDK includes comprehensive E2E (End-to-End) tests written in pure C to verify library compatibility across multiple Linux distributions and macOS. Tests are executed inside Docker containers to simulate real deployment environments.

### What E2E Tests Verify

1. **Library Loading**: Verifies the shared library (`.so`/`.dylib`/`.dll`) can be loaded via `dlopen`/`LoadLibrary`
2. **Symbol Resolution**: Confirms all 17 exported API functions are present and accessible
3. **Version Function**: Tests `typecast_version()` returns valid version string
4. **Utility Functions**: Tests model/emotion/format conversion functions
5. **Client Lifecycle**: Tests client creation and destruction
6. **Robustness**: Tests NULL parameter handling to ensure no crashes

### Prerequisites

- **Docker**: Required for Linux and Windows environment testing (Docker Desktop or OrbStack on macOS)
- **GCC or Clang**: Required for macOS local testing

### Running E2E Tests

```bash
# Navigate to the project directory
cd typecast-c

# Default: Test on CentOS 6.9 and Amazon Linux 2
./scripts/e2e/run_e2e.sh

# Test on specific environments
./scripts/e2e/run_e2e.sh --centos6      # CentOS 6.9 (glibc 2.12) - oldest supported
./scripts/e2e/run_e2e.sh --centos7      # CentOS 7 (glibc 2.17)
./scripts/e2e/run_e2e.sh --amazonlinux  # Amazon Linux 2 (glibc 2.26)
./scripts/e2e/run_e2e.sh --ubuntu       # Ubuntu 20.04 LTS (glibc 2.31)
./scripts/e2e/run_e2e.sh --debian       # Debian Bullseye (glibc 2.31)
./scripts/e2e/run_e2e.sh --windows      # Windows x64 (MinGW-w64 + Wine)

# Test on all environments (Linux + Windows)
./scripts/e2e/run_e2e.sh --all

# Test on macOS (local execution, no Docker needed)
./scripts/e2e/run_e2e.sh --macos

# Show help
./scripts/e2e/run_e2e.sh --help
```

### Test Output Example

```
==================================================
  Typecast C SDK E2E Test
==================================================
Project directory: /path/to/typecast-c

==================================================
  Testing on CentOS 6.9 (x86_64)
==================================================
Library loaded successfully

=== Testing Symbol Loading ===
  [PASS] typecast_client_create symbol
  [PASS] typecast_client_destroy symbol
  ...

===========================================
Test Results:
  Total:  33
  Passed: 33
  Failed: 0
===========================================
✓ CentOS 6.9 (x86_64): ALL TESTS PASSED

==================================================
  Final E2E Test Results
==================================================
Environments tested: 2
Passed: 2
Failed: 0

✓ All E2E tests passed!
```

### E2E Test Files

| File                     | Description                                                 |
| ------------------------ | ----------------------------------------------------------- |
| `scripts/e2e/run_e2e.sh` | Bash script that orchestrates Docker-based testing          |
| `scripts/e2e/test_e2e.c` | Pure C test program (cross-platform: Linux, macOS, Windows) |

### Adding New Test Environments

To add a new Linux distribution to E2E tests, add a new function in `scripts/e2e/run_e2e.sh`:

```bash
run_mylinux() {
    print_header "Testing on MyLinux (x86_64)"

    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        mylinux:latest sh -c "
            # Install dependencies
            apt-get update && apt-get install -y gcc make cmake libcurl4-openssl-dev

            # Build and test
            mkdir -p /tmp/build && cd /tmp/build
            cmake /source -DCMAKE_BUILD_TYPE=Release
            make

            mkdir -p /lib_check
            cp -L /tmp/build/libtypecast.so /lib_check/

            gcc -o /tmp/test_e2e /source/scripts/e2e/test_e2e.c -ldl
            /tmp/test_e2e /lib_check/libtypecast.so
        "

    if [ $? -eq 0 ]; then
        print_success "MyLinux: ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "MyLinux: SOME TESTS FAILED"
        ((FAILED++))
    fi
}
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Copyright (c) 2025 Typecast

## Links

- [Typecast AI](https://typecast.ai/?lang=en)
- [API Documentation](https://typecast.ai/docs)
