@Tags(['e2e'])
library;

import 'dart:io';

import 'package:test/test.dart';
import 'package:typecast_dart/typecast_dart.dart';

void main() {
  test('live API lists voices and synthesizes speech', () async {
    final apiKey = Platform.environment['TYPECAST_API_KEY'];
    if (apiKey == null || apiKey.isEmpty) {
      markTestSkipped('TYPECAST_API_KEY is required for live API tests');
      return;
    }
    final client = TypecastClient(apiKey: apiKey);
    final voices = await client.getVoicesV2(
      const VoicesV2Filter(model: TtsModel.ssfmV30),
    );
    expect(voices, isNotEmpty);

    final audio = await client.textToSpeech(
      TtsRequest(
        voiceId: voices.first.voiceId,
        text: 'Hello from the Typecast Dart SDK.',
        model: TtsModel.ssfmV30,
        language: LanguageCode.eng,
      ),
    );
    expect(audio.audioData, isNotEmpty);
  });
}
