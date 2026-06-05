import 'dart:convert';
import 'dart:io';

import 'package:test/test.dart';
import 'package:typecast_dart/typecast.dart';

void main() {
  test('timestamps response exposes audio bytes and captions', () async {
    final response = TtsWithTimestampsResponse.fromJson({
      'audio': base64Encode([82, 73, 70, 70]),
      'audio_format': 'wav',
      'audio_duration': 1.0,
      'words': [
        {'word': 'Hello', 'start_time': 0.0, 'end_time': 0.5},
        {'word': 'world', 'start_time': 0.5, 'end_time': 1.0},
      ],
    });

    expect(response.audioBytes(), [82, 73, 70, 70]);
    expect(response.toSrt(), contains('00:00:00,000 --> 00:00:00,500'));
    expect(response.toVtt(), startsWith('WEBVTT'));

    final file = File('${Directory.systemTemp.path}/typecast_dart_test.wav');
    await response.saveAudio(file.path);
    expect(await file.length(), 4);
    await file.delete();
  });

  test('timestamps response uses character segments when words are absent', () {
    final response = TtsWithTimestampsResponse.fromJson({
      'audio': base64Encode([1]),
      'audio_format': 'wav',
      'audio_duration': 0.5,
      'characters': [
        {'character': 'A', 'start_time': 0.0, 'end_time': 0.5},
      ],
    });

    expect(response.toSrt(), contains('A'));
  });

  test('timestamps response rejects missing alignment data', () {
    final response = TtsWithTimestampsResponse.fromJson({
      'audio': base64Encode([1]),
      'audio_format': 'wav',
      'audio_duration': 0.5,
    });

    expect(response.toSrt, throwsStateError);
  });
}
