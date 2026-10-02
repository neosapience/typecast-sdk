import 'package:test/test.dart';
import 'package:typecast_dart/typecast_dart.dart';

void main() {
  test('legacy seed values are not serialized', () {
    for (final seed in [0, 42]) {
      final normal = TtsRequest(voiceId: 'tc_test', text: 'hello', model: TtsModel.ssfmV30, seed: seed);
      final stream = TtsRequestStream(voiceId: 'tc_test', text: 'hello', model: TtsModel.ssfmV30, seed: seed);
      expect(normal.toJson().containsKey('seed'), isFalse);
      expect(stream.toJson().containsKey('seed'), isFalse);
    }
  });
}
