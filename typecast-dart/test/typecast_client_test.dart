import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';
import 'package:typecast_dart/typecast.dart';

void main() {
  group('TypecastClient', () {
    test('textToSpeech posts JSON and parses audio response', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/v1/text-to-speech');
          expect(request.headers['X-API-KEY'], 'key');
          final body = jsonDecode(request.body) as Map<String, dynamic>;
          expect(body['voice_id'], 'tc_123');
          expect(body['language'], 'eng');
          return http.Response.bytes(
            [1, 2, 3],
            200,
            headers: {'content-type': 'audio/wav', 'x-audio-duration': '1.25'},
          );
        }),
      );

      final response = await client.textToSpeech(
        const TtsRequest(
          voiceId: 'tc_123',
          text: 'Hello',
          model: TtsModel.ssfmV30,
          language: LanguageCode.eng,
          prompt: PresetPrompt(emotionPreset: EmotionPreset.normal),
          output: Output(audioFormat: AudioFormat.wav),
          seed: 42,
        ),
      );

      expect(response.audioData, [1, 2, 3]);
      expect(response.duration, 1.25);
      expect(response.format, AudioFormat.wav);
    });

    test('throws typed API errors', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient(
          (_) async => http.Response(jsonEncode({'detail': 'bad'}), 400),
        ),
      );

      expect(
        () => client.textToSpeech(
          const TtsRequest(
            voiceId: 'tc_123',
            text: 'Hello',
            model: TtsModel.ssfmV30,
          ),
        ),
        throwsA(isA<BadRequestException>()),
      );
    });

    test('getMySubscription parses subscription response', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient((request) async {
          expect(request.url.path, '/v1/users/me/subscription');
          return http.Response(
            jsonEncode({
              'plan': 'plus',
              'credits': {'plan_credits': 1000, 'used_credits': 25},
              'limits': {'concurrency_limit': 3},
            }),
            200,
          );
        }),
      );

      final subscription = await client.getMySubscription();
      expect(subscription.plan, 'plus');
      expect(subscription.planCredits, 1000);
      expect(subscription.concurrencyLimit, 3);
    });

    test('getVoicesV2 and getVoiceV2 parse voice metadata', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient((request) async {
          final payload = {
            'voice_id': 'tc_123',
            'voice_name': 'Voice',
            'models': [
              {
                'version': 'ssfm-v30',
                'emotions': ['normal'],
              },
            ],
            'gender': 'female',
            'age': 'young_adult',
            'use_cases': ['Podcast'],
          };
          if (request.url.path == '/v2/voices') {
            return http.Response(jsonEncode([payload]), 200);
          }
          expect(request.url.path, '/v2/voices/tc_123');
          return http.Response(jsonEncode(payload), 200);
        }),
      );

      final voices = await client.getVoicesV2(
        const VoicesV2Filter(model: TtsModel.ssfmV30),
      );
      expect(voices.single.models.single.version, 'ssfm-v30');

      final voice = await client.getVoiceV2('tc_123');
      expect(voice.voiceName, 'Voice');
    });

    test('cloneVoice validates size and posts multipart', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/v1/voices/clone');
          expect(request.headers['X-API-KEY'], 'key');
          expect(
            request.headers['content-type'],
            startsWith('multipart/form-data'),
          );
          return http.Response(
            jsonEncode({
              'voice_id': 'uc_123',
              'name': 'Mine',
              'model': 'ssfm-v30',
            }),
            200,
          );
        }),
      );

      final voice = await client.cloneVoice(
        audio: [1, 2, 3],
        filename: 'sample.wav',
        name: 'Mine',
        model: TtsModel.ssfmV30,
      );
      expect(voice.voiceId, 'uc_123');

      expect(
        () => client.cloneVoice(
          audio: List<int>.filled(CustomVoice.maxFileSize + 1, 0),
          filename: 'sample.wav',
          name: 'Mine',
          model: TtsModel.ssfmV30,
        ),
        throwsArgumentError,
      );
    });

    test('deleteVoice accepts 204', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient((request) async {
          expect(request.method, 'DELETE');
          expect(request.url.path, '/v1/voices/uc_123');
          return http.Response('', 204);
        }),
      );

      await client.deleteVoice('uc_123');
    });

    test('textToSpeechWithTimestamps validates granularity', () async {
      final client = TypecastClient(
        apiKey: 'key',
        baseUrl: 'https://api.test',
        httpClient: MockClient((_) async => http.Response('{}', 200)),
      );

      expect(
        () => client.textToSpeechWithTimestamps(
          const TtsRequest(
            voiceId: 'tc_123',
            text: 'Hello',
            model: TtsModel.ssfmV30,
          ),
          granularity: 'bad',
        ),
        throwsArgumentError,
      );
    });
  });
}
