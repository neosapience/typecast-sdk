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
