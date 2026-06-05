import 'dart:io';

import 'package:typecast_dart/typecast_dart.dart';

Future<void> main() async {
  final client = TypecastClient();
  final audioFile = File('sample.wav');
  if (!audioFile.existsSync()) {
    stderr
        .writeln('sample.wav not found. Provide a WAV sample before running.');
    exit(1);
  }
  final voice = await client.cloneVoice(
    audio: await audioFile.readAsBytes(),
    filename: 'sample.wav',
    name: 'My Voice',
    model: TtsModel.ssfmV30,
  );
  print('Created custom voice: ${voice.voiceId}');
}
