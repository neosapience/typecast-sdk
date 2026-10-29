import 'package:test/test.dart';
import 'package:typecast_dart/typecast_dart.dart';

void main() {
  test('SSFM 3.1 preserves existing enum indexes and wire values', () {
    expect(TtsModel.ssfmV31.value, 'ssfm-v31');
    expect(TtsModel.ssfmV21.index, 0);
    expect(TtsModel.ssfmV30.index, 1);
  });
}
