import 'dart:io';

import 'package:typecast_dart/typecast_dart.dart';

Future<void> main() async {
  final client = TypecastClient();
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
