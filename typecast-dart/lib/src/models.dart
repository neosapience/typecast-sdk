import 'dart:convert';
import 'dart:typed_data';

enum TtsModel {
  ssfmV21('ssfm-v21'),
  ssfmV30('ssfm-v30');

  const TtsModel(this.value);
  final String value;
}

enum AudioFormat {
  wav('wav'),
  mp3('mp3');

  const AudioFormat(this.value);
  final String value;
}

enum EmotionPreset {
  normal('normal'),
  happy('happy'),
  sad('sad'),
  angry('angry'),
  whisper('whisper'),
  toneup('toneup'),
  tonedown('tonedown');

  const EmotionPreset(this.value);
  final String value;
}

enum LanguageCode {
  eng('eng'),
  kor('kor'),
  jpn('jpn'),
  spa('spa'),
  deu('deu'),
  fra('fra'),
  ita('ita'),
  pol('pol'),
  nld('nld'),
  rus('rus'),
  ell('ell'),
  tam('tam'),
  tgl('tgl'),
  fin('fin'),
  zho('zho'),
  slk('slk'),
  ara('ara'),
  hrv('hrv'),
  ukr('ukr'),
  ind('ind'),
  dan('dan'),
  swe('swe'),
  msa('msa'),
  ces('ces'),
  por('por'),
  bul('bul'),
  ron('ron'),
  ben('ben'),
  hin('hin'),
  hun('hun'),
  nan('nan'),
  nor('nor'),
  pan('pan'),
  tha('tha'),
  tur('tur'),
  vie('vie'),
  yue('yue');

  const LanguageCode(this.value);
  final String value;
}

class Output {
  const Output({
    this.volume,
    this.targetLufs,
    this.audioPitch,
    this.audioTempo,
    this.audioFormat,
  });

  final int? volume;
  final double? targetLufs;
  final int? audioPitch;
  final double? audioTempo;
  final AudioFormat? audioFormat;

  Map<String, Object?> toJson() => _withoutNulls({
        'volume': volume,
        'target_lufs': targetLufs,
        'audio_pitch': audioPitch,
        'audio_tempo': audioTempo,
        'audio_format': audioFormat?.value,
      });
}

class OutputStream {
  const OutputStream({this.audioPitch, this.audioTempo, this.audioFormat});

  final int? audioPitch;
  final double? audioTempo;
  final AudioFormat? audioFormat;

  Map<String, Object?> toJson() => _withoutNulls({
        'audio_pitch': audioPitch,
        'audio_tempo': audioTempo,
        'audio_format': audioFormat?.value,
      });
}

class Prompt {
  const Prompt({this.emotionPreset, this.emotionIntensity});

  final EmotionPreset? emotionPreset;
  final double? emotionIntensity;

  Map<String, Object?> toJson() => _withoutNulls({
        'emotion_preset': emotionPreset?.value,
        'emotion_intensity': emotionIntensity,
      });
}

class PresetPrompt {
  const PresetPrompt({this.emotionPreset, this.emotionIntensity});

  final EmotionPreset? emotionPreset;
  final double? emotionIntensity;

  Map<String, Object?> toJson() => _withoutNulls({
        'emotion_type': 'preset',
        'emotion_preset': emotionPreset?.value,
        'emotion_intensity': emotionIntensity,
      });
}

class SmartPrompt {
  const SmartPrompt({this.previousText, this.nextText});

  final String? previousText;
  final String? nextText;

  Map<String, Object?> toJson() => _withoutNulls({
        'emotion_type': 'smart',
        'previous_text': previousText,
        'next_text': nextText,
      });
}

class TtsRequest {
  const TtsRequest({
    required this.voiceId,
    required this.text,
    required this.model,
    this.language,
    this.prompt,
    this.output,
    this.seed,
  });

  final String voiceId;
  final String text;
  final TtsModel model;
  final LanguageCode? language;
  final Object? prompt;
  final Output? output;
  final int? seed;

  Map<String, Object?> toJson() => _withoutNulls({
        'voice_id': voiceId,
        'text': text,
        'model': model.value,
        'language': language?.value,
        'prompt': _promptToJson(prompt),
        'output': output?.toJson(),
        'seed': seed,
      });
}

class TtsRequestStream {
  const TtsRequestStream({
    required this.voiceId,
    required this.text,
    required this.model,
    this.language,
    this.prompt,
    this.output,
    this.seed,
  });

  final String voiceId;
  final String text;
  final TtsModel model;
  final LanguageCode? language;
  final Object? prompt;
  final OutputStream? output;
  final int? seed;

  Map<String, Object?> toJson() => _withoutNulls({
        'voice_id': voiceId,
        'text': text,
        'model': model.value,
        'language': language?.value,
        'prompt': _promptToJson(prompt),
        'output': output?.toJson(),
        'seed': seed,
      });
}

class TtsResponse {
  const TtsResponse({
    required this.audioData,
    required this.duration,
    required this.format,
  });

  final Uint8List audioData;
  final double duration;
  final AudioFormat format;
}

class SubscriptionResponse {
  const SubscriptionResponse({
    required this.plan,
    required this.planCredits,
    required this.usedCredits,
    required this.concurrencyLimit,
  });

  factory SubscriptionResponse.fromJson(Map<String, dynamic> json) {
    final credits = (json['credits'] as Map?)?.cast<String, dynamic>() ?? {};
    final limits = (json['limits'] as Map?)?.cast<String, dynamic>() ?? {};
    return SubscriptionResponse(
      plan: json['plan'] as String? ?? '',
      planCredits: (credits['plan_credits'] as num?)?.toInt() ?? 0,
      usedCredits: (credits['used_credits'] as num?)?.toInt() ?? 0,
      concurrencyLimit: (limits['concurrency_limit'] as num?)?.toInt() ?? 0,
    );
  }

  final String plan;
  final int planCredits;
  final int usedCredits;
  final int concurrencyLimit;
}

class VoiceModel {
  const VoiceModel({required this.version, required this.emotions});

  factory VoiceModel.fromJson(Map<String, dynamic> json) => VoiceModel(
        version: json['version'] as String? ?? '',
        emotions: (json['emotions'] as List? ?? []).cast<String>(),
      );

  final String version;
  final List<String> emotions;
}

class VoiceV2 {
  const VoiceV2({
    required this.voiceId,
    required this.voiceName,
    required this.models,
    this.gender,
    this.age,
    this.useCases = const [],
  });

  factory VoiceV2.fromJson(Map<String, dynamic> json) => VoiceV2(
        voiceId: json['voice_id'] as String? ?? '',
        voiceName: json['voice_name'] as String? ?? '',
        models: (json['models'] as List? ?? [])
            .map((item) => VoiceModel.fromJson((item as Map).cast()))
            .toList(),
        gender: json['gender'] as String?,
        age: json['age'] as String?,
        useCases: (json['use_cases'] as List? ?? []).cast<String>(),
      );

  final String voiceId;
  final String voiceName;
  final List<VoiceModel> models;
  final String? gender;
  final String? age;
  final List<String> useCases;
}

class VoicesV2Filter {
  const VoicesV2Filter({this.model, this.gender, this.age, this.useCases});

  final TtsModel? model;
  final String? gender;
  final String? age;
  final List<String>? useCases;

  Map<String, String> toQuery() => {
        if (model != null) 'model': model!.value,
        if (gender != null) 'gender': gender!,
        if (age != null) 'age': age!,
        if (useCases != null && useCases!.isNotEmpty)
          'use_cases': useCases!.join(','),
      };
}

class CustomVoice {
  const CustomVoice({required this.voiceId, required this.name, this.model});

  factory CustomVoice.fromJson(Map<String, dynamic> json) => CustomVoice(
        voiceId: json['voice_id'] as String? ?? '',
        name: json['name'] as String? ?? '',
        model: json['model'] as String?,
      );

  static const int maxNameLength = 30;
  static const int maxFileSize = 25 * 1024 * 1024;

  final String voiceId;
  final String name;
  final String? model;
}

Map<String, Object?> _withoutNulls(Map<String, Object?> value) {
  value.removeWhere((_, v) => v == null);
  return value;
}

Object? _promptToJson(Object? prompt) {
  if (prompt == null) return null;
  if (prompt is Prompt) return prompt.toJson();
  if (prompt is PresetPrompt) return prompt.toJson();
  if (prompt is SmartPrompt) return prompt.toJson();
  if (prompt is Map<String, Object?>) return prompt;
  throw ArgumentError(
    'prompt must be Prompt, PresetPrompt, SmartPrompt, or Map',
  );
}

String encodeJson(Map<String, Object?> json) => jsonEncode(json);
