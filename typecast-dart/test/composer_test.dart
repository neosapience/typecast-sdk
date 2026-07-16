import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';
import 'package:typecast_dart/typecast_dart.dart';

void main() {
  test('composeSpeech uses Compose API and merges overrides', () async {
    final bodies = <Map<String, dynamic>>[];
    final paths = <String>[];
    final client = TypecastClient(
      apiKey: 'key',
      baseUrl: 'https://api.test',
      httpClient: MockClient((request) async {
        paths.add(request.url.path);
        bodies.add(jsonDecode(request.body) as Map<String, dynamic>);
        return http.Response.bytes(
          Uint8List.fromList(utf8.encode('composed-audio')),
          200,
          headers: {
            'content-type': 'audio/mpeg',
            'x-audio-duration': '1.25',
          },
        );
      }),
    );

    final response = await client
        .composeSpeech()
        .defaults(
          const ComposerSettings(
            voiceId: 'voice-a',
            model: TtsModel.ssfmV30,
            output: Output(audioPitch: 1, audioFormat: AudioFormat.mp3),
          ),
        )
        .say(
          'Hello<|0.3s|>world',
          overrides: const ComposerSettings(
            voiceId: 'voice-b',
            output: Output(audioTempo: 1.1),
          ),
        )
        .generate();

    expect(paths, ['/v1/text-to-speech/compose']);
    final segments = bodies.single['segments'] as List<dynamic>;
    expect(segments.map((segment) => segment['type']), ['tts', 'pause', 'tts']);
    expect(segments[0]['text'], 'Hello');
    expect(segments[0]['voice_id'], 'voice-b');
    expect(segments[0]['output']['audio_format'], 'mp3');
    expect(segments[0]['output']['audio_pitch'], 1);
    expect(segments[0]['output']['audio_tempo'], 1.1);
    expect(segments[1]['duration_seconds'], 0.3);
    expect(segments[2]['text'], 'world');
    expect(utf8.decode(response.audioData), 'composed-audio');
    expect(response.format, AudioFormat.mp3);
    expect(response.duration, 1.25);
  });

  test('composeSpeech validates before network', () async {
    final client = TypecastClient(
      apiKey: 'key',
      baseUrl: 'https://api.test',
      httpClient: MockClient((_) async => fail('network should not be called')),
    );
    expect(
      () => client.composeSpeech().say('Hello').generate(),
      throwsA(predicate((e) => e.toString().contains('voiceId is required'))),
    );
    expect(
      () => client.composeSpeech().pause(0).generate(),
      throwsA(predicate((e) =>
          e.toString().contains('pause seconds must be greater than 0'))),
    );
    expect(
      () => client
          .composeSpeech()
          .defaults(
            const ComposerSettings(
              voiceId: 'voice',
              model: TtsModel.ssfmV30,
              output: Output(audioFormat: AudioFormat.mp3),
            ),
          )
          .say('first')
          .say(
            'second',
            overrides: const ComposerSettings(
              output: Output(audioFormat: AudioFormat.wav),
            ),
          )
          .generate(),
      throwsA(predicate((e) => e.toString().contains('one audio format'))),
    );
  });

  test('parsePauseMarkup is lenient for invalid tokens', () {
    expect(parsePauseMarkup('a<|0.3s|>b<|abc|>c<|3s|>'), [
      const SpeechPart.text('a'),
      const SpeechPart.pause(0.3),
      const SpeechPart.text('b<|abc|>c'),
      const SpeechPart.pause(3),
    ]);
  });
}
