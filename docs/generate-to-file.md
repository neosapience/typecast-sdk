# Generate To File

All SDKs provide a convenience API that synthesizes speech and writes the audio
bytes directly to a local file. The required inputs are the output path, `text`,
and `voice_id`. The model defaults to `ssfm-v30`, and `.mp3` / `.wav` file
extensions are used to infer `audio_format` when no output format is provided.

## JavaScript

```ts
await client.generateToFile("svelte.mp3", {
  text: "svelte",
  voice_id: VOICE_ID,
});
```

## Python

```py
client.generate_to_file("svelte.mp3", text="svelte", voice_id=VOICE_ID)
```

Async:

```py
await client.generate_to_file("svelte.mp3", text="svelte", voice_id=VOICE_ID)
```

## Go

```go
_, err := client.GenerateToFile(ctx, "svelte.mp3", typecast.GenerateToFileRequest{
    Text: "svelte",
    VoiceID: voiceID,
})
```

## Java

```java
client.generateToFile("svelte.mp3", GenerateToFileRequest.builder()
    .text("svelte")
    .voiceId(voiceId)
    .build());
```

## Kotlin

```kotlin
client.generateToFile("svelte.mp3", GenerateToFileRequest(
    text = "svelte",
    voiceId = voiceId,
))
```

## C#

```csharp
await client.GenerateToFileAsync("svelte.mp3", new GenerateToFileRequest {
    Text = "svelte",
    VoiceId = voiceId,
});
```

## Rust

```rust
client.generate_to_file(
    "svelte.mp3",
    GenerateToFileRequest::new(voice_id, "svelte"),
).await?;
```

## Dart

```dart
await client.generateToFile(
  'svelte.mp3',
  GenerateToFileRequest(text: 'svelte', voiceId: voiceId),
);
```

## Swift

```swift
try await client.generateToFile(
    "svelte.mp3",
    request: GenerateToFileRequest(voiceId: voiceId, text: "svelte")
)
```

## Ruby

```rb
client.generate_to_file("svelte.mp3", text: "svelte", voice_id: voice_id)
```

## PHP

```php
$client->generateToFile('svelte.mp3', 'svelte', $voiceId);
```

## C

```c
TypecastGenerateToFileRequest request = {0};
request.text = "svelte";
request.voice_id = voice_id;

TypecastErrorCode code = typecast_generate_to_file(client, "svelte.mp3", &request);
```

## C++

```cpp
typecast::GenerateToFileRequest request;
request.text = "svelte";
request.voiceId = voiceId;

auto response = client.generateToFile("svelte.mp3", request);
```

## Zig

```zig
const response = try client.generateToFile("svelte.mp3", .{
    .text = "svelte",
    .voice_id = voice_id,
});
defer allocator.free(response.audio_data);
```
