import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:test/test.dart';
import 'package:typecast_dart/typecast_dart.dart';

void main() {
  test('composeSpeech composes WAV and merges overrides', () async {
    final responses = [
      makeTestWav([0, 1000, 2000, 0], sampleRate: 1000),
      makeTestWav([0, -1000, -2000, 0], sampleRate: 1000),
    ];
    final bodies = <Map<String, dynamic>>[];
    final client = TypecastClient(
      apiKey: 'key',
      baseUrl: 'https://api.test',
      httpClient: MockClient((request) async {
        bodies.add(jsonDecode(request.body) as Map<String, dynamic>);
        return http.Response.bytes(
          responses.removeAt(0),
          200,
          headers: {'content-type': 'audio/wav'},
        );
      }),
    );

    final response = await client
        .composeSpeech()
        .defaults(
          const ComposerSettings(
            voiceId: 'voice-a',
            model: TtsModel.ssfmV30,
            language: LanguageCode.eng,
            output: Output(audioPitch: 1, audioFormat: AudioFormat.wav),
          ),
        )
        .say(
          'Hello<|0.001s|>world',
          overrides: const ComposerSettings(
            voiceId: 'voice-b',
            output: Output(audioTempo: 1.1),
          ),
        )
        .generate();

    expect(bodies, hasLength(2));
    expect(bodies[0]['text'], 'Hello');
    expect(bodies[1]['text'], 'world');
    expect(bodies[0]['voice_id'], 'voice-b');
    expect(bodies[0]['output']['audio_format'], 'wav');
    expect(bodies[0]['output']['audio_pitch'], 1);
    expect(bodies[0]['output']['audio_tempo'], 1.1);
    expect(response.format, AudioFormat.wav);
    expect(samplesFromWav(response.audioData), [1000, 2000, 0, -1000, -2000]);
    expect(response.duration, closeTo(0.005, 0.0001));
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
      throwsA(
        predicate(
          (e) => e.toString().contains('pause seconds must be greater than 0'),
        ),
      ),
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

  test('composeSpeech rejects bad WAV, mismatched specs, and mp3', () async {
    final badWavClient = TypecastClient(
      apiKey: 'key',
      baseUrl: 'https://api.test',
      httpClient: MockClient(
        (_) async => http.Response.bytes(
          utf8.encode('not wav'),
          200,
          headers: {'content-type': 'audio/wav'},
        ),
      ),
    );
    expect(
      () => badWavClient
          .composeSpeech()
          .defaults(
            const ComposerSettings(voiceId: 'voice-a', model: TtsModel.ssfmV30),
          )
          .say('Hello')
          .generate(),
      throwsA(predicate((e) => e.toString().contains('unsupported WAV data'))),
    );

    final mismatchResponses = [
      makeTestWav([1000], sampleRate: 1000),
      makeTestWav([1000], sampleRate: 2000),
    ];
    final mismatchClient = TypecastClient(
      apiKey: 'key',
      baseUrl: 'https://api.test',
      httpClient: MockClient(
        (_) async => http.Response.bytes(
          mismatchResponses.removeAt(0),
          200,
          headers: {'content-type': 'audio/wav'},
        ),
      ),
    );
    expect(
      () => mismatchClient
          .composeSpeech()
          .defaults(
            const ComposerSettings(voiceId: 'voice-a', model: TtsModel.ssfmV30),
          )
          .say('one<|0.001s|>two')
          .generate(),
      throwsA(predicate((e) => e.toString().contains('same PCM format'))),
    );

    Map<String, dynamic>? body;
    final mp3Client = TypecastClient(
      apiKey: 'key',
      baseUrl: 'https://api.test',
      httpClient: MockClient((request) async {
        body = jsonDecode(request.body) as Map<String, dynamic>;
        return http.Response.bytes(
          makeTestWav([1000], sampleRate: 1000),
          200,
          headers: {'content-type': 'audio/wav'},
        );
      }),
    );
    expect(
      () => mp3Client
          .composeSpeech()
          .defaults(
            const ComposerSettings(
              voiceId: 'voice-a',
              model: TtsModel.ssfmV30,
              output: Output(audioFormat: AudioFormat.mp3),
            ),
          )
          .say('Hello')
          .generate(),
      throwsA(
        predicate(
          (e) => e.toString().contains(
                'MP3 conversion is app-level responsibility',
              ),
        ),
      ),
    );
    await Future<void>.delayed(Duration.zero);
    expect(body?['output']['audio_format'], 'wav');
  });
}

Uint8List makeTestWav(List<int> samples, {required int sampleRate}) {
  final data = BytesBuilder();
  void u16(int value) {
    final bytes = ByteData(2)..setUint16(0, value, Endian.little);
    data.add(bytes.buffer.asUint8List());
  }

  void u32(int value) {
    final bytes = ByteData(4)..setUint32(0, value, Endian.little);
    data.add(bytes.buffer.asUint8List());
  }

  void i16(int value) {
    final bytes = ByteData(2)..setInt16(0, value, Endian.little);
    data.add(bytes.buffer.asUint8List());
  }

  data.add(ascii.encode('RIFF'));
  u32(36 + samples.length * 2);
  data.add(ascii.encode('WAVEfmt '));
  u32(16);
  u16(1);
  u16(1);
  u32(sampleRate);
  u32(sampleRate * 2);
  u16(2);
  u16(16);
  data.add(ascii.encode('data'));
  u32(samples.length * 2);
  for (final sample in samples) {
    i16(sample);
  }
  return data.toBytes();
}

List<int> samplesFromWav(Uint8List data) {
  final marker = ascii.encode('data');
  var dataOffset = -1;
  for (var i = 0; i <= data.length - marker.length; i++) {
    if (data.sublist(i, i + marker.length).toString() == marker.toString()) {
      dataOffset = i + 8;
      break;
    }
  }
  final samples = <int>[];
  final view = ByteData.sublistView(data);
  for (var i = dataOffset; i + 1 < data.length; i += 2) {
    samples.add(view.getInt16(i, Endian.little));
  }
  return samples;
}
