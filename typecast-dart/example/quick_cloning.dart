import 'dart:io';

import 'package:typecast_dart/typecast.dart';

Future<void> main() async {
  final client = TypecastClient();
  final voice = await client.cloneVoice(
    audio: await File('sample.wav').readAsBytes(),
    filename: 'sample.wav',
    name: 'My Voice',
    model: TtsModel.ssfmV30,
  );
  print('Created custom voice: ${voice.voiceId}');
}
