import 'dart:typed_data';

import 'models.dart';
import 'typecast_client.dart';

class SpeechPart {
  const SpeechPart.text(this.text)
      : kind = 'text',
        seconds = null;

  const SpeechPart.pause(this.seconds)
      : kind = 'pause',
        text = null;

  final String kind;
  final String? text;
  final double? seconds;

  @override
  bool operator ==(Object other) =>
      other is SpeechPart &&
      other.kind == kind &&
      other.text == text &&
      other.seconds == seconds;

  @override
  int get hashCode => Object.hash(kind, text, seconds);
}

class ComposerSettings {
  const ComposerSettings({
    this.voiceId,
    this.model,
    this.language,
    this.prompt,
    this.output,
    this.seed,
  });

  final String? voiceId;
  final TtsModel? model;
  final LanguageCode? language;
  final Object? prompt;
  final Output? output;
  final int? seed;
}

class SpeechComposer {
  SpeechComposer(this._client);

  final TypecastClient _client;
  ComposerSettings _defaults = const ComposerSettings();
  final List<_ComposerPart> _parts = [];

  SpeechComposer defaults(ComposerSettings settings) {
    _defaults = _mergeSettings(_defaults, settings);
    return this;
  }

  SpeechComposer say(
    String text, {
    ComposerSettings overrides = const ComposerSettings(),
  }) {
    _parts
        .add(_ComposerPart.speech(text, _mergeSettings(_defaults, overrides)));
    return this;
  }

  /// Inserts silence between speech segments.
  ///
  /// [seconds] is a duration in seconds. Use 0.3 for 300 ms, 3 for three seconds.
  SpeechComposer pause(double seconds) {
    _parts.add(_ComposerPart.pause(seconds));
    return this;
  }

  Future<TtsResponse> generate() async {
    final plan = _buildPlan();
    if (!plan.any((part) => part.kind == 'speech')) {
      throw ArgumentError('at least one speech segment is required');
    }

    final outputFormat = _defaults.output?.audioFormat ?? AudioFormat.wav;
    _WavSpec? wavSpec;
    final outputSamples = <int>[];
    for (final part in plan) {
      if (part.kind == 'pause') {
        final seconds = part.seconds!;
        if (!_isValidPause(seconds)) {
          throw ArgumentError('pause seconds must be greater than 0');
        }
        final spec = wavSpec;
        if (spec == null) {
          throw ArgumentError('pause cannot be the first composed part');
        }
        outputSamples.addAll(
            List.filled(_secondsToSamples(seconds, spec.sampleRate), 0));
        continue;
      }

      final response = await _client.textToSpeech(_requestFromPart(part));
      final wav = _parseWav(response.audioData);
      if (wavSpec != null && wav.spec != wavSpec) {
        throw ArgumentError(
            'all composed WAV segments must use the same PCM format');
      }
      wavSpec = wav.spec;
      outputSamples.addAll(_trimSilence(wav.samples));
    }

    final finalSpec = wavSpec;
    if (finalSpec == null) {
      throw ArgumentError('at least one speech segment is required');
    }
    if (outputFormat == AudioFormat.mp3) {
      throw ArgumentError(
          'MP3 conversion is app-level responsibility for composed speech');
    }
    return TtsResponse(
      audioData: _encodeWav(outputSamples, finalSpec),
      duration: outputSamples.length / finalSpec.sampleRate,
      format: AudioFormat.wav,
    );
  }

  List<_ComposerPart> _buildPlan() {
    final plan = <_ComposerPart>[];
    for (final part in _parts) {
      if (part.kind == 'pause') {
        if (!_isValidPause(part.seconds!)) {
          throw ArgumentError('pause seconds must be greater than 0');
        }
        plan.add(part);
        continue;
      }
      for (final parsed in parsePauseMarkup(part.text!)) {
        if (parsed.kind == 'pause') {
          plan.add(_ComposerPart.pause(parsed.seconds!));
          continue;
        }
        final text = parsed.text!;
        if (text.trim().isEmpty) continue;
        if ((part.settings.voiceId ?? '').trim().isEmpty) {
          throw ArgumentError(
              'voiceId is required for composed speech segments');
        }
        if (part.settings.model == null) {
          throw ArgumentError('model is required for composed speech segments');
        }
        plan.add(_ComposerPart.speech(text, part.settings));
      }
    }
    return plan;
  }
}

extension SpeechComposerClient on TypecastClient {
  SpeechComposer composeSpeech() => SpeechComposer(this);
}

List<SpeechPart> parsePauseMarkup(String text) {
  final parts = <SpeechPart>[];
  var lastEmit = 0;
  var searchFrom = 0;
  while (true) {
    final relativeStart = text.indexOf('<|', searchFrom);
    if (relativeStart < 0) break;
    final bodyStart = relativeStart + 2;
    final bodyEnd = text.indexOf('|>', bodyStart);
    if (bodyEnd < 0) break;
    final tokenEnd = bodyEnd + 2;
    final tokenBody = text.substring(bodyStart, bodyEnd);
    if (tokenBody.endsWith('s')) {
      final secondsText = tokenBody.substring(0, tokenBody.length - 1);
      if (_validSecondsLiteral(secondsText)) {
        final seconds = double.tryParse(secondsText);
        if (seconds != null) {
          if (relativeStart > lastEmit) {
            parts.add(SpeechPart.text(text.substring(lastEmit, relativeStart)));
          }
          parts.add(SpeechPart.pause(seconds));
          lastEmit = tokenEnd;
          searchFrom = tokenEnd;
          continue;
        }
      }
    }
    searchFrom = bodyStart;
  }
  if (lastEmit < text.length) {
    parts.add(SpeechPart.text(text.substring(lastEmit)));
  }
  return parts;
}

class _ComposerPart {
  const _ComposerPart.speech(this.text, this.settings)
      : kind = 'speech',
        seconds = null;

  const _ComposerPart.pause(this.seconds)
      : kind = 'pause',
        text = null,
        settings = const ComposerSettings();

  final String kind;
  final String? text;
  final double? seconds;
  final ComposerSettings settings;
}

class _WavSpec {
  const _WavSpec(this.sampleRate, this.channels, this.bitsPerSample);

  final int sampleRate;
  final int channels;
  final int bitsPerSample;

  @override
  bool operator ==(Object other) =>
      other is _WavSpec &&
      other.sampleRate == sampleRate &&
      other.channels == channels &&
      other.bitsPerSample == bitsPerSample;

  @override
  int get hashCode => Object.hash(sampleRate, channels, bitsPerSample);
}

class _ParsedWav {
  const _ParsedWav(this.spec, this.samples);

  final _WavSpec spec;
  final List<int> samples;
}

ComposerSettings _mergeSettings(
    ComposerSettings base, ComposerSettings override) {
  return ComposerSettings(
    voiceId: override.voiceId ?? base.voiceId,
    model: override.model ?? base.model,
    language: override.language ?? base.language,
    prompt: override.prompt ?? base.prompt,
    output: _mergeOutput(base.output, override.output),
    seed: override.seed ?? base.seed,
  );
}

Output? _mergeOutput(Output? base, Output? override) {
  if (base == null && override == null) return null;
  return Output(
    volume: override?.volume ?? base?.volume,
    targetLufs: override?.targetLufs ?? base?.targetLufs,
    audioPitch: override?.audioPitch ?? base?.audioPitch,
    audioTempo: override?.audioTempo ?? base?.audioTempo,
    audioFormat: override?.audioFormat ?? base?.audioFormat,
  );
}

TtsRequest _requestFromPart(_ComposerPart part) {
  final settings = part.settings;
  return TtsRequest(
    voiceId: settings.voiceId!,
    text: part.text!,
    model: settings.model!,
    language: settings.language,
    prompt: settings.prompt,
    output: _mergeOutput(
        settings.output, const Output(audioFormat: AudioFormat.wav)),
    seed: settings.seed,
  );
}

_ParsedWav _parseWav(Uint8List data) {
  if (data.length < 12 ||
      String.fromCharCodes(data.sublist(0, 4)) != 'RIFF' ||
      String.fromCharCodes(data.sublist(8, 12)) != 'WAVE') {
    throw ArgumentError('unsupported WAV data');
  }
  final view = ByteData.sublistView(data);
  var offset = 12;
  _WavSpec? spec;
  List<int>? samples;
  while (offset + 8 <= data.length) {
    final chunkId = String.fromCharCodes(data.sublist(offset, offset + 4));
    final chunkSize = view.getUint32(offset + 4, Endian.little);
    final chunkDataOffset = offset + 8;
    final chunkEnd = chunkDataOffset + chunkSize;
    if (chunkEnd > data.length) {
      throw ArgumentError('unsupported WAV data');
    }
    if (chunkId == 'fmt ') {
      if (chunkSize < 16) throw ArgumentError('unsupported WAV data');
      final audioFormat = view.getUint16(chunkDataOffset, Endian.little);
      final channels = view.getUint16(chunkDataOffset + 2, Endian.little);
      final sampleRate = view.getUint32(chunkDataOffset + 4, Endian.little);
      final bitsPerSample = view.getUint16(chunkDataOffset + 14, Endian.little);
      if (audioFormat != 1 || channels != 1 || bitsPerSample != 16) {
        throw ArgumentError(
            'only mono 16-bit PCM WAV is supported for composed speech');
      }
      spec = _WavSpec(sampleRate, channels, bitsPerSample);
    } else if (chunkId == 'data') {
      samples = [
        for (var i = chunkDataOffset; i + 1 < chunkEnd; i += 2)
          view.getInt16(i, Endian.little),
      ];
    }
    offset = chunkEnd + (chunkSize % 2);
  }
  if (spec == null || samples == null) {
    throw ArgumentError('unsupported WAV data');
  }
  return _ParsedWav(spec, samples);
}

Uint8List _encodeWav(List<int> samples, _WavSpec spec) {
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

  data.add('RIFF'.codeUnits);
  u32(36 + samples.length * 2);
  data.add('WAVEfmt '.codeUnits);
  u32(16);
  u16(1);
  u16(spec.channels);
  u32(spec.sampleRate);
  u32(spec.sampleRate * spec.channels * 2);
  u16(spec.channels * 2);
  u16(spec.bitsPerSample);
  data.add('data'.codeUnits);
  u32(samples.length * 2);
  for (final sample in samples) {
    i16(sample);
  }
  return data.toBytes();
}

List<int> _trimSilence(List<int> samples) {
  var start = 0;
  var end = samples.length;
  while (start < end && samples[start].abs() <= 0) {
    start++;
  }
  while (end > start && samples[end - 1].abs() <= 0) {
    end--;
  }
  return samples.sublist(start, end);
}

int _secondsToSamples(double seconds, int sampleRate) =>
    (seconds * sampleRate).round();

bool _isValidPause(double seconds) =>
    !seconds.isNaN && !seconds.isInfinite && seconds > 0;

bool _validSecondsLiteral(String value) {
  if (value.isEmpty) return false;
  final pieces = value.split('.');
  if (pieces.length > 2 || pieces.any((piece) => piece.isEmpty)) {
    return false;
  }
  return pieces.every(
      (piece) => piece.codeUnits.every((code) => code >= 48 && code <= 57));
}
