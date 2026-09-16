import 'package:test/test.dart';
import 'package:typecast_dart/typecast_dart.dart';

void main() {
  test('optional silence preserves zero and validates the range', () {
    expect(const Output().toJson().containsKey('remove_silence_ms'), isFalse);
    expect(const OutputStream().toJson().containsKey('remove_silence_ms'),
        isFalse);
    for (final value in [0, 300, 1000]) {
      expect(
          Output(removeSilenceMs: value).toJson()['remove_silence_ms'], value);
      expect(OutputStream(removeSilenceMs: value).toJson()['remove_silence_ms'],
          value);
    }
    for (final value in [-1, 1001]) {
      expect(
          () => Output(removeSilenceMs: value).toJson(), throwsArgumentError);
      expect(() => OutputStream(removeSilenceMs: value).toJson(),
          throwsArgumentError);
    }
  });
}
