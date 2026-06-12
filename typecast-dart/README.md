# Typecast Dart SDK

Official Dart and Flutter SDK for the Typecast Text-to-Speech API.

## Installation

```yaml
dependencies:
  typecast_dart: ^0.1.0
```

For local development from this monorepo:

```yaml
dependencies:
  typecast_dart:
    path: ../typecast-dart
```

## Quick Start

```dart
import 'dart:io';
import 'package:typecast_dart/typecast_dart.dart';

Future<void> main() async {
  final client = TypecastClient(apiKey: Platform.environment['TYPECAST_API_KEY']);
  final response = await client.textToSpeech(
    const TtsRequest(
      voiceId: 'tc_60e5426de8b95f1d3000d7b5',
      text: 'Hello from Typecast Dart.',
      model: TtsModel.ssfmV30,
      language: LanguageCode.eng,
      output: Output(audioFormat: AudioFormat.wav),
    ),
  );
  await File('hello.wav').writeAsBytes(response.audioData);
}
```

## Playback in Flutter

Add an audio player package to your app:

```yaml
dependencies:
  audioplayers: ^6.0.0
```

Then play the generated audio bytes directly:

```dart
import 'package:audioplayers/audioplayers.dart';
import 'package:typecast_dart/typecast_dart.dart';

final client = TypecastClient(apiKey: 'YOUR_API_KEY');
final player = AudioPlayer();

Future<void> playTts(String text) async {
  final response = await client.textToSpeech(
    TtsRequest(
      voiceId: 'tc_672c5f5ce59fac2a48faeaee',
      text: text,
      model: TtsModel.ssfmV30,
      language: LanguageCode.eng,
      output: const Output(audioFormat: AudioFormat.wav),
    ),
  );

  await player.play(BytesSource(response.audioData));
}
```

For production Flutter apps, keep long-lived API keys on your backend. The Flutter app can request generated audio from your backend and still play the returned bytes with `BytesSource`.

## Features

- Text-to-speech synthesis
- Streaming synthesis
- Word and character timestamps with SRT/VTT helpers
- Voice listing and subscription APIs
- Instant voice cloning
- Works in Dart and Flutter applications

## Testing

```bash
make install
make analyze
make test
TYPECAST_API_KEY=... make e2e
```
