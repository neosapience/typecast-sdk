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
    _parts.add(
      _ComposerPart.speech(text, _mergeSettings(_defaults, overrides)),
    );
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

    final formats = plan
        .where((part) => part.kind == 'speech')
        .map((part) => part.settings.output?.audioFormat)
        .whereType<AudioFormat>()
        .toSet();
    if (formats.length > 1) {
      throw ArgumentError('composed speech segments must use one audio format');
    }
    final outputFormat = formats.isEmpty ? AudioFormat.wav : formats.single;
    final segments = <Map<String, dynamic>>[];
    for (final part in plan) {
      if (part.kind == 'pause') {
        final seconds = part.seconds!;
        if (!_isValidPause(seconds)) {
          throw ArgumentError('pause seconds must be greater than 0');
        }
        segments.add({'type': 'pause', 'duration_seconds': seconds});
        continue;
      }
      segments.add(
          {'type': 'tts', ..._requestFromPart(part, outputFormat).toJson()});
    }
    return _client.composeTextToSpeech(segments);
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
            'voiceId is required for composed speech segments',
          );
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

ComposerSettings _mergeSettings(
  ComposerSettings base,
  ComposerSettings override,
) {
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

TtsRequest _requestFromPart(_ComposerPart part,
    [AudioFormat format = AudioFormat.wav]) {
  final settings = part.settings;
  return TtsRequest(
    voiceId: settings.voiceId!,
    text: part.text!,
    model: settings.model!,
    language: settings.language,
    prompt: settings.prompt,
    output: _mergeOutput(
      settings.output,
      Output(audioFormat: format),
    ),
    seed: settings.seed,
  );
}

bool _isValidPause(double seconds) =>
    !seconds.isNaN && !seconds.isInfinite && seconds > 0;

bool _validSecondsLiteral(String value) {
  if (value.isEmpty) return false;
  final pieces = value.split('.');
  if (pieces.length > 2 || pieces.any((piece) => piece.isEmpty)) {
    return false;
  }
  return pieces.every(
    (piece) => piece.codeUnits.every((code) => code >= 48 && code <= 57),
  );
}
