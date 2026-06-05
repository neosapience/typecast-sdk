import 'package:typecast_dart/typecast.dart';

Future<void> main() async {
  final client = TypecastClient();
  final response = await client.textToSpeechWithTimestamps(
    const TtsRequest(
      voiceId: 'tc_60e5426de8b95f1d3000d7b5',
      text: 'Hello with timestamps.',
      model: TtsModel.ssfmV30,
      language: LanguageCode.eng,
    ),
    granularity: 'word',
  );
  await response.saveAudio('hello.wav');
  print(response.toSrt());
}
