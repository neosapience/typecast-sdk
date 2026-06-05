import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import 'errors.dart';
import 'models.dart';
import 'timestamps.dart';

class TypecastClient {
  TypecastClient({
    String? apiKey,
    String? baseUrl,
    http.Client? httpClient,
    this.requestTimeout = const Duration(seconds: 30),
  })  : apiKey = apiKey ?? Platform.environment['TYPECAST_API_KEY'] ?? '',
        baseUrl = baseUrl ??
            Platform.environment['TYPECAST_API_HOST'] ??
            'https://api.typecast.ai',
        _httpClient = httpClient ?? http.Client() {
    if (this.apiKey.isEmpty) {
      throw ArgumentError('TYPECAST_API_KEY must be provided');
    }
  }

  final String apiKey;
  final String baseUrl;
  final Duration requestTimeout;
  final http.Client _httpClient;

  Future<TtsResponse> textToSpeech(TtsRequest request) async {
    final response = await _httpClient
        .post(
          _url('/v1/text-to-speech'),
          headers: _jsonHeaders(),
          body: encodeJson(request.toJson()),
        )
        .timeout(requestTimeout);
    _raiseForStatus(response);
    final contentType = response.headers['content-type'] ?? 'audio/wav';
    return TtsResponse(
      audioData: Uint8List.fromList(response.bodyBytes),
      duration:
          double.tryParse(response.headers['x-audio-duration'] ?? '') ?? 0,
      format: contentType.contains('mp3') || contentType.contains('mpeg')
          ? AudioFormat.mp3
          : AudioFormat.wav,
    );
  }

  Future<Stream<List<int>>> textToSpeechStream(TtsRequestStream request) async {
    final httpRequest = http.Request('POST', _url('/v1/text-to-speech/stream'))
      ..headers.addAll(_jsonHeaders())
      ..body = encodeJson(request.toJson());
    final response =
        await _httpClient.send(httpRequest).timeout(requestTimeout);
    await _raiseForStreamStatus(response, requestTimeout);
    return response.stream;
  }

  Future<TtsWithTimestampsResponse> textToSpeechWithTimestamps(
    TtsRequest request, {
    String? granularity,
  }) async {
    if (granularity != null && granularity != 'word' && granularity != 'char') {
      throw ArgumentError("granularity must be 'word' or 'char'");
    }
    final response = await _httpClient
        .post(
          _url(
            '/v1/text-to-speech/with-timestamps',
            granularity == null ? null : {'granularity': granularity},
          ),
          headers: _jsonHeaders(),
          body: encodeJson(request.toJson()),
        )
        .timeout(requestTimeout);
    _raiseForStatus(response);
    return TtsWithTimestampsResponse.fromJson(
      (jsonDecode(response.body) as Map).cast<String, dynamic>(),
    );
  }

  Future<SubscriptionResponse> getMySubscription() async {
    final response = await _httpClient
        .get(_url('/v1/users/me/subscription'), headers: _headers())
        .timeout(requestTimeout);
    _raiseForStatus(response);
    return SubscriptionResponse.fromJson(
      (jsonDecode(response.body) as Map).cast<String, dynamic>(),
    );
  }

  Future<List<VoiceV2>> getVoicesV2([VoicesV2Filter? filter]) async {
    final response = await _httpClient
        .get(_url('/v2/voices', filter?.toQuery()), headers: _headers())
        .timeout(requestTimeout);
    _raiseForStatus(response);
    return (jsonDecode(response.body) as List)
        .map((item) => VoiceV2.fromJson((item as Map).cast()))
        .toList();
  }

  Future<VoiceV2> getVoiceV2(String voiceId) async {
    final response = await _httpClient
        .get(_url('/v2/voices/$voiceId'), headers: _headers())
        .timeout(requestTimeout);
    _raiseForStatus(response);
    return VoiceV2.fromJson((jsonDecode(response.body) as Map).cast());
  }

  Future<CustomVoice> cloneVoice({
    required List<int> audio,
    required String filename,
    required String name,
    required TtsModel model,
  }) async {
    if (audio.length > CustomVoice.maxFileSize) {
      throw ArgumentError('audio must be 25MB or smaller');
    }
    if (name.isEmpty || name.length > CustomVoice.maxNameLength) {
      throw ArgumentError('name must be 1-${CustomVoice.maxNameLength} chars');
    }
    final request = http.MultipartRequest('POST', _url('/v1/voices/clone'))
      ..headers['X-API-KEY'] = apiKey
      ..fields['name'] = name
      ..fields['model'] = model.value
      ..files.add(
        http.MultipartFile.fromBytes('file', audio, filename: filename),
      );
    final response = await _httpClient.send(request).timeout(requestTimeout);
    await _raiseForStreamStatus(response, requestTimeout);
    final body = await response.stream.bytesToString().timeout(requestTimeout);
    return CustomVoice.fromJson((jsonDecode(body) as Map).cast());
  }

  Future<void> deleteVoice(String voiceId) async {
    final response = await _httpClient
        .delete(_url('/v1/voices/$voiceId'), headers: _headers())
        .timeout(requestTimeout);
    if (response.statusCode == 204) return;
    _raiseForStatus(response);
  }

  Uri _url(String path, [Map<String, String>? query]) {
    final base = Uri.parse(baseUrl);
    return base.replace(
      path: '${base.path}/${path.replaceFirst(RegExp('^/'), '')}'.replaceAll(
        '//',
        '/',
      ),
      queryParameters: query,
    );
  }

  Map<String, String> _headers() => {'X-API-KEY': apiKey};

  Map<String, String> _jsonHeaders() => {
        ..._headers(),
        'Content-Type': 'application/json',
      };

  void close() => _httpClient.close();
}

void _raiseForStatus(http.Response response) {
  if (response.statusCode >= 200 && response.statusCode < 300) return;
  throw _exceptionFor(response.statusCode, _extractDetail(response.body));
}

Future<void> _raiseForStreamStatus(
  http.StreamedResponse response,
  Duration timeout,
) async {
  if (response.statusCode >= 200 && response.statusCode < 300) return;
  final body = await response.stream.bytesToString().timeout(timeout);
  throw _exceptionFor(response.statusCode, _extractDetail(body));
}

String? _extractDetail(String body) {
  try {
    final decoded = jsonDecode(body);
    if (decoded is Map && decoded['detail'] != null) {
      return decoded['detail'].toString();
    }
    if (decoded is Map && decoded['error'] != null) {
      return decoded['error'].toString();
    }
  } catch (_) {
    return body.isEmpty ? null : body;
  }
  return body.isEmpty ? null : body;
}

TypecastApiException _exceptionFor(int statusCode, String? detail) {
  switch (statusCode) {
    case 400:
      return BadRequestException(detail);
    case 401:
      return UnauthorizedException(detail);
    case 402:
      return PaymentRequiredException(detail);
    case 404:
      return NotFoundException(detail);
    case 422:
      return UnprocessableEntityException(detail);
    case 429:
      return RateLimitException(detail);
    case 500:
      return InternalServerException(detail);
    default:
      return TypecastApiException('HTTP error', statusCode, detail);
  }
}
