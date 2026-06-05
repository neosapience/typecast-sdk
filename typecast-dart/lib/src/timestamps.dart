import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'models.dart';

class AlignmentSegmentWord {
  const AlignmentSegmentWord({
    required this.word,
    required this.startTime,
    required this.endTime,
  });

  factory AlignmentSegmentWord.fromJson(Map<String, dynamic> json) =>
      AlignmentSegmentWord(
        word: json['word'] as String? ?? '',
        startTime: (json['start_time'] as num?)?.toDouble() ?? 0,
        endTime: (json['end_time'] as num?)?.toDouble() ?? 0,
      );

  final String word;
  final double startTime;
  final double endTime;
}

class AlignmentSegmentCharacter {
  const AlignmentSegmentCharacter({
    required this.character,
    required this.startTime,
    required this.endTime,
  });

  factory AlignmentSegmentCharacter.fromJson(Map<String, dynamic> json) =>
      AlignmentSegmentCharacter(
        character: json['character'] as String? ?? '',
        startTime: (json['start_time'] as num?)?.toDouble() ?? 0,
        endTime: (json['end_time'] as num?)?.toDouble() ?? 0,
      );

  final String character;
  final double startTime;
  final double endTime;
}

class TtsWithTimestampsResponse {
  const TtsWithTimestampsResponse({
    required this.audio,
    required this.audioFormat,
    required this.audioDuration,
    this.words = const [],
    this.characters = const [],
  });

  factory TtsWithTimestampsResponse.fromJson(Map<String, dynamic> json) =>
      TtsWithTimestampsResponse(
        audio: json['audio'] as String? ?? '',
        audioFormat:
            json['audio_format'] == 'mp3' ? AudioFormat.mp3 : AudioFormat.wav,
        audioDuration: (json['audio_duration'] as num?)?.toDouble() ?? 0,
        words: (json['words'] as List? ?? [])
            .map((item) => AlignmentSegmentWord.fromJson((item as Map).cast()))
            .toList(),
        characters: (json['characters'] as List? ?? [])
            .map(
              (item) =>
                  AlignmentSegmentCharacter.fromJson((item as Map).cast()),
            )
            .toList(),
      );

  final String audio;
  final AudioFormat audioFormat;
  final double audioDuration;
  final List<AlignmentSegmentWord> words;
  final List<AlignmentSegmentCharacter> characters;

  Uint8List audioBytes() => base64Decode(audio);

  Future<void> saveAudio(String path) async {
    await File(path).writeAsBytes(audioBytes());
  }

  String toSrt() => _formatCaptions(webVtt: false);

  String toVtt() => 'WEBVTT\n\n${_formatCaptions(webVtt: true)}';

  String _formatCaptions({required bool webVtt}) {
    if (words.isEmpty && characters.isEmpty) {
      throw StateError('No alignment segments are available');
    }
    final segments = words.isNotEmpty
        ? words.map(
            (word) => _CaptionSegment(word.word, word.startTime, word.endTime),
          )
        : characters.map(
            (char) =>
                _CaptionSegment(char.character, char.startTime, char.endTime),
          );
    final lines = <String>[];
    var index = 1;
    for (final segment in segments) {
      if (!webVtt) lines.add('${index++}');
      lines.add(
        '${_formatTimestamp(segment.start, webVtt)} --> '
        '${_formatTimestamp(segment.end, webVtt)}',
      );
      lines
        ..add(segment.text)
        ..add('');
    }
    return lines.join('\n');
  }
}

class _CaptionSegment {
  const _CaptionSegment(this.text, this.start, this.end);

  final String text;
  final double start;
  final double end;
}

String _formatTimestamp(double seconds, bool webVtt) {
  final millis = (seconds * 1000).round();
  final hours = millis ~/ 3600000;
  final minutes = (millis % 3600000) ~/ 60000;
  final secs = (millis % 60000) ~/ 1000;
  final ms = millis % 1000;
  final decimal = webVtt ? '.' : ',';
  return '${hours.toString().padLeft(2, '0')}:'
      '${minutes.toString().padLeft(2, '0')}:'
      '${secs.toString().padLeft(2, '0')}$decimal'
      '${ms.toString().padLeft(3, '0')}';
}
