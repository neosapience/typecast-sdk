# Typecast C# SDK

Official C# SDK for the [Typecast](https://typecast.ai/?lang=en) Text-to-Speech API. This SDK supports .NET Standard 2.0+, .NET 6+, Unity (via NuGetForUnity), and Blazor applications.

[![NuGet](https://img.shields.io/nuget/v/typecast-csharp.svg)](https://www.nuget.org/packages/typecast-csharp)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

## Features

- Full support for Typecast TTS API v1 and v2
- Both synchronous and asynchronous methods
- Multiple TTS models (ssfm-v21, ssfm-v30)
- Emotion control (preset and smart modes)
- Audio customization (volume, pitch, tempo, format)
- Voice discovery and filtering
- Unity and Blazor compatible
- Zero external dependencies for core functionality

## Prerequisites

### Installing .NET SDK

This SDK requires .NET 6.0 or later. Follow the instructions below for your operating system.

#### macOS

**Option 1: Using Homebrew (Recommended)**

```bash
# Install .NET 8 SDK
brew install dotnet@8

# Add to PATH (add to ~/.zshrc or ~/.bash_profile for persistence)
export PATH="/opt/homebrew/opt/dotnet@8/bin:$PATH"
export DOTNET_ROOT="/opt/homebrew/opt/dotnet@8/libexec"

# Verify installation
dotnet --version
```

**Option 2: Using Official Installer**

1. Download the installer from [dotnet.microsoft.com/download](https://dotnet.microsoft.com/download)
2. Run the `.pkg` installer
3. Verify installation:
   ```bash
   dotnet --version
   ```

#### Windows

**Option 1: Using winget (Windows Package Manager)**

```powershell
# Install .NET 8 SDK
winget install Microsoft.DotNet.SDK.8

# Verify installation
dotnet --version
```

**Option 2: Using Official Installer**

1. Download the installer from [dotnet.microsoft.com/download](https://dotnet.microsoft.com/download)
2. Run the `.exe` installer
3. Restart your terminal/PowerShell
4. Verify installation:
   ```powershell
   dotnet --version
   ```

**Option 3: Using Chocolatey**

```powershell
# Install Chocolatey first if not installed
# Then install .NET SDK
choco install dotnet-sdk

# Verify installation
dotnet --version
```

#### Linux (Ubuntu/Debian)

```bash
# Add Microsoft package repository
wget https://packages.microsoft.com/config/ubuntu/$(lsb_release -rs)/packages-microsoft-prod.deb -O packages-microsoft-prod.deb
sudo dpkg -i packages-microsoft-prod.deb
rm packages-microsoft-prod.deb

# Install .NET SDK
sudo apt-get update
sudo apt-get install -y dotnet-sdk-8.0

# Verify installation
dotnet --version
```

#### Verify Installation

After installation, verify that .NET SDK is properly installed:

```bash
# Check .NET SDK version
dotnet --version

# List installed SDKs
dotnet --list-sdks

# List installed runtimes
dotnet --list-runtimes
```

## Installation

### NuGet Package Manager

```bash
dotnet add package typecast-csharp
```

Or via Package Manager Console:

```powershell
Install-Package typecast-csharp
```

### Unity (via NuGetForUnity)

1. Install [NuGetForUnity](https://github.com/GlitchEnzo/NuGetForUnity) from the Unity Asset Store or via Git URL:
   - Open Package Manager (Window > Package Manager)
   - Click "+" > "Add package from git URL"
   - Enter: `https://github.com/GlitchEnzo/NuGetForUnity.git?path=/src/NuGetForUnity`

2. Open NuGet window (NuGet > Manage NuGet Packages)

3. Search for "typecast-csharp" and install

## Quick Start

### Basic Usage

```csharp
using Typecast;
using Typecast.Models;

// Create client with API key
using var client = new TypecastClient("your-api-key");

// Or use environment variable TYPECAST_API_KEY
using var client = new TypecastClient();

// Synthesize text to speech
var request = new TTSRequest(
    text: "Hello, world!",
    voiceId: "your-voice-id",
    model: TTSModel.SsfmV30
);

var response = await client.TextToSpeechAsync(request);

// Save audio to file
await response.SaveToFileAsync("output.wav");

// Or get audio as bytes
byte[] audioData = response.AudioData;
double duration = response.Duration;
```

### Get Available Voices

```csharp
// Get all voices
var voices = await client.GetVoicesV2Async();

foreach (var voice in voices)
{
    Console.WriteLine($"{voice.VoiceId}: {voice.VoiceName}");
}

// Filter voices
var filter = new VoicesV2Filter
{
    Model = TTSModel.SsfmV30,
    Gender = GenderEnum.Female,
    Age = AgeEnum.YoungAdult
};

var filteredVoices = await client.GetVoicesV2Async(filter);
```

### Emotion Control

#### Preset Mode (explicit emotion)

```csharp
var request = new TTSRequest("I'm so happy today!", voiceId, TTSModel.SsfmV30)
{
    Language = LanguageCode.English,
    Prompt = new PresetPrompt(EmotionPreset.Happy, emotionIntensity: 1.5)
};
```

#### Smart Mode (context-aware emotion)

```csharp
var request = new TTSRequest("This is amazing!", voiceId, TTSModel.SsfmV30)
{
    Language = LanguageCode.English,
    Prompt = new SmartPrompt(
        previousText: "I just heard some great news.",
        nextText: "I can't believe it happened!"
    )
};
```

### Audio Customization

```csharp
var request = new TTSRequest("Hello, world!", voiceId, TTSModel.SsfmV30)
{
    Output = new Output(
        volume: 120,           // 0-200, default: 100
        audioPitch: 2,         // -12 to +12 semitones, default: 0
        audioTempo: 1.2,       // 0.5 to 2.0, default: 1.0
        audioFormat: AudioFormat.Mp3  // Wav or Mp3, default: Wav
    )
};
```

### Configuration Options

```csharp
var config = new TypecastClientConfig
{
    ApiKey = "your-api-key",           // Or use TYPECAST_API_KEY env var
    ApiHost = "https://api.typecast.ai", // Optional, custom API host
    TimeoutSeconds = 60,                // HTTP timeout, default: 30
    HttpClient = customHttpClient       // Optional, use your own HttpClient
};

using var client = new TypecastClient(config);
```

## Unity Integration

### Basic Unity Example

```csharp
using UnityEngine;
using Typecast;
using Typecast.Models;
using System.Threading.Tasks;

public class TypecastTTS : MonoBehaviour
{
    private TypecastClient _client;
    private AudioSource _audioSource;

    void Start()
    {
        _client = new TypecastClient("your-api-key");
        _audioSource = GetComponent<AudioSource>();
    }

    public async void SpeakText(string text, string voiceId)
    {
        try
        {
            var request = new TTSRequest(text, voiceId, TTSModel.SsfmV30)
            {
                Language = LanguageCode.English,
                Output = new Output(audioFormat: AudioFormat.Wav)
            };

            var response = await _client.TextToSpeechAsync(request);

            // Convert to Unity AudioClip
            var audioClip = CreateAudioClipFromWav(response.AudioData);

            _audioSource.clip = audioClip;
            _audioSource.Play();
        }
        catch (TypecastException ex)
        {
            Debug.LogError($"TTS Error: {ex.Message}");
        }
    }

    private AudioClip CreateAudioClipFromWav(byte[] wavData)
    {
        // Parse WAV header
        int channels = wavData[22];
        int sampleRate = System.BitConverter.ToInt32(wavData, 24);
        int dataStart = 44; // Standard WAV header size

        // Find data chunk
        for (int i = 12; i < wavData.Length - 4; i++)
        {
            if (wavData[i] == 'd' && wavData[i+1] == 'a' &&
                wavData[i+2] == 't' && wavData[i+3] == 'a')
            {
                dataStart = i + 8;
                break;
            }
        }

        int sampleCount = (wavData.Length - dataStart) / 2;
        float[] samples = new float[sampleCount];

        for (int i = 0; i < sampleCount; i++)
        {
            short sample = System.BitConverter.ToInt16(wavData, dataStart + i * 2);
            samples[i] = sample / 32768f;
        }

        var audioClip = AudioClip.Create("TTS", sampleCount / channels, channels, sampleRate, false);
        audioClip.SetData(samples, 0);

        return audioClip;
    }

    void OnDestroy()
    {
        _client?.Dispose();
    }
}
```

### Unity UI Button Example

```csharp
using UnityEngine;
using UnityEngine.UI;
using TMPro;
using Typecast;
using Typecast.Models;

public class TTSButton : MonoBehaviour
{
    [SerializeField] private Button speakButton;
    [SerializeField] private TMP_InputField textInput;
    [SerializeField] private AudioSource audioSource;
    [SerializeField] private string voiceId;

    private TypecastClient _client;
    private bool _isProcessing;

    void Start()
    {
        _client = new TypecastClient("your-api-key");
        speakButton.onClick.AddListener(OnSpeakClicked);
    }

    async void OnSpeakClicked()
    {
        if (_isProcessing || string.IsNullOrWhiteSpace(textInput.text))
            return;

        _isProcessing = true;
        speakButton.interactable = false;

        try
        {
            var request = new TTSRequest(textInput.text, voiceId, TTSModel.SsfmV30);
            var response = await _client.TextToSpeechAsync(request);

            // Play audio...
        }
        catch (TypecastException ex)
        {
            Debug.LogError($"TTS Error: {ex.Message}");
        }
        finally
        {
            _isProcessing = false;
            speakButton.interactable = true;
        }
    }

    void OnDestroy()
    {
        _client?.Dispose();
    }
}
```

### Unity WebGL Considerations

For WebGL builds, you may need to use JavaScript interop for audio playback:

```csharp
#if UNITY_WEBGL && !UNITY_EDITOR
    // Use JavaScript for audio in WebGL
    PlayAudioViaJavaScript(response.AudioData);
#else
    // Standard Unity audio
    PlayAudioClip(response.AudioData);
#endif
```

## Blazor Integration

### Blazor Server Example

```csharp
// Program.cs
builder.Services.AddSingleton<TypecastClient>(sp =>
    new TypecastClient(builder.Configuration["Typecast:ApiKey"]));

// TTSService.cs
public class TTSService
{
    private readonly TypecastClient _client;

    public TTSService(TypecastClient client)
    {
        _client = client;
    }

    public async Task<byte[]> SynthesizeAsync(string text, string voiceId)
    {
        var request = new TTSRequest(text, voiceId, TTSModel.SsfmV30)
        {
            Output = new Output(audioFormat: AudioFormat.Mp3)
        };

        var response = await _client.TextToSpeechAsync(request);
        return response.AudioData;
    }
}
```

### Blazor Component Example

```razor
@page "/tts"
@inject TTSService TTSService
@inject IJSRuntime JSRuntime

<h3>Text-to-Speech</h3>

<div class="mb-3">
    <textarea @bind="Text" class="form-control" rows="4"
              placeholder="Enter text to synthesize..."></textarea>
</div>

<div class="mb-3">
    <select @bind="SelectedVoiceId" class="form-select">
        @foreach (var voice in Voices)
        {
            <option value="@voice.VoiceId">@voice.VoiceName</option>
        }
    </select>
</div>

<button class="btn btn-primary" @onclick="SynthesizeAsync" disabled="@IsProcessing">
    @(IsProcessing ? "Synthesizing..." : "Speak")
</button>

<audio id="ttsAudio" controls style="display: @(HasAudio ? "block" : "none")"></audio>

@code {
    private string Text { get; set; } = "";
    private string SelectedVoiceId { get; set; } = "";
    private List<VoiceV2Response> Voices { get; set; } = new();
    private bool IsProcessing { get; set; }
    private bool HasAudio { get; set; }

    protected override async Task OnInitializedAsync()
    {
        // Load voices...
    }

    private async Task SynthesizeAsync()
    {
        if (string.IsNullOrWhiteSpace(Text) || string.IsNullOrWhiteSpace(SelectedVoiceId))
            return;

        IsProcessing = true;

        try
        {
            var audioData = await TTSService.SynthesizeAsync(Text, SelectedVoiceId);
            var base64Audio = Convert.ToBase64String(audioData);

            await JSRuntime.InvokeVoidAsync("playAudio", $"data:audio/mp3;base64,{base64Audio}");
            HasAudio = true;
        }
        catch (TypecastException ex)
        {
            // Handle error
        }
        finally
        {
            IsProcessing = false;
        }
    }
}
```

### Blazor JavaScript Interop

```html
<!-- wwwroot/index.html or _Host.cshtml -->
<script>
  window.playAudio = function (src) {
    var audio = document.getElementById("ttsAudio");
    audio.src = src;
    audio.play();
  };
</script>
```

### Blazor WebAssembly Example

```csharp
// Program.cs
builder.Services.AddScoped(sp =>
{
    var httpClient = new HttpClient { BaseAddress = new Uri(builder.HostEnvironment.BaseAddress) };
    return new TypecastClient(new TypecastClientConfig
    {
        ApiKey = "your-api-key",
        HttpClient = httpClient
    });
});
```

> **Note:** For Blazor WebAssembly, consider proxying API calls through your server to protect your API key.

## Error Handling

The SDK throws specific exceptions for different error scenarios:

```csharp
try
{
    var response = await client.TextToSpeechAsync(request);
}
catch (UnauthorizedException)
{
    // Invalid or missing API key (401)
}
catch (PaymentRequiredException)
{
    // Insufficient credits (402)
}
catch (NotFoundException)
{
    // Resource not found (404)
}
catch (UnprocessableEntityException)
{
    // Validation error (422)
}
catch (RateLimitException)
{
    // Too many requests (429)
}
catch (InternalServerException)
{
    // Server error (5xx)
}
catch (TypecastException ex)
{
    // Other API errors
    Console.WriteLine($"Error {ex.StatusCode}: {ex.Message}");
}
```

## Supported Languages

The SDK supports 37 languages with ISO 639-3 codes:

| Language    | Code  | Language           | Code  |
| ----------- | ----- | ------------------ | ----- |
| Korean      | `kor` | English            | `eng` |
| Japanese    | `jpn` | Chinese (Mandarin) | `cmn` |
| Spanish     | `spa` | French             | `fra` |
| German      | `deu` | Italian            | `ita` |
| Portuguese  | `por` | Russian            | `rus` |
| Hindi       | `hin` | Vietnamese         | `vie` |
| Thai        | `tha` | Indonesian         | `ind` |
| Arabic      | `ara` | Dutch              | `nld` |
| Polish      | `pol` | Swedish            | `swe` |
| Turkish     | `tur` | Ukrainian          | `ukr` |
| And more... |       |                    |       |

## API Reference

### TypecastClient Methods

| Method                              | Description                                |
| ----------------------------------- | ------------------------------------------ |
| `TextToSpeechAsync(TTSRequest)`     | Synthesize text to speech (async)          |
| `TextToSpeech(TTSRequest)`          | Synthesize text to speech (sync)           |
| `GetVoicesV2Async(VoicesV2Filter?)` | Get available voices with metadata (async) |
| `GetVoicesV2(VoicesV2Filter?)`      | Get available voices with metadata (sync)  |
| `GetVoiceV2Async(string voiceId)`   | Get a specific voice by ID (async)         |
| `GetVoiceV2(string voiceId)`        | Get a specific voice by ID (sync)          |

### Models

- `TTSRequest` - Text-to-speech request
- `TTSResponse` - Contains audio data, duration, and format
- `Output` - Audio output settings
- `Prompt` / `PresetPrompt` / `SmartPrompt` - Emotion control
- `VoiceV2Response` - Voice information with metadata
- `VoicesV2Filter` - Voice filtering options

### Enums

- `TTSModel` - TTS model versions (SsfmV21, SsfmV30)
- `LanguageCode` - Supported languages
- `EmotionPreset` - Emotion presets
- `AudioFormat` - Output formats (Wav, Mp3)
- `GenderEnum` - Voice gender filter
- `AgeEnum` - Voice age category filter

## Running Tests

### Unit Tests

```bash
cd typecast-csharp
dotnet test tests/Typecast.Tests
```

### E2E Tests

```bash
# Set up environment
cp tests/Typecast.E2E.Tests/.env.example tests/Typecast.E2E.Tests/.env
# Edit .env and add your API key

# Run E2E tests
dotnet test tests/Typecast.E2E.Tests
```

## Building from Source

```bash
git clone https://github.com/neosapience/typecast-sdk.git
cd typecast-sdk/typecast-csharp
dotnet build
dotnet pack -c Release
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Support

- [Typecast Documentation](https://typecast.ai/docs)
- [API Reference](https://typecast.ai/docs/api-reference)
- [GitHub Issues](https://github.com/neosapience/typecast-sdk/issues)
