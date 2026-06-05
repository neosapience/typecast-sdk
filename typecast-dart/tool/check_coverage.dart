import 'dart:io';

void main(List<String> args) {
  if (args.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/check_coverage.dart <lcov.info> <min_percent>',
    );
    exitCode = 2;
    return;
  }

  final file = File(args[0]);
  if (!file.existsSync()) {
    stderr.writeln('Coverage file not found: ${file.path}');
    exitCode = 2;
    return;
  }
  final minimumPercent = double.tryParse(args[1]);
  if (minimumPercent == null ||
      minimumPercent.isNaN ||
      minimumPercent < 0 ||
      minimumPercent > 100) {
    stderr.writeln('min_percent must be a number from 0 to 100.');
    exitCode = 2;
    return;
  }

  var foundLines = 0;
  var hitLines = 0;
  for (final line in file.readAsLinesSync()) {
    if (!line.startsWith('DA:')) continue;
    final parts = line.substring(3).split(',');
    if (parts.length < 2) continue;
    foundLines += 1;
    if ((int.tryParse(parts[1]) ?? 0) > 0) {
      hitLines += 1;
    }
  }

  if (foundLines == 0) {
    stderr.writeln('Coverage report contains no executable lines.');
    exitCode = 2;
    return;
  }

  final percent = hitLines * 100 / foundLines;
  stdout.writeln(
    'Line coverage: ${percent.toStringAsFixed(2)}% '
    '($hitLines/$foundLines)',
  );
  if (percent < minimumPercent) {
    stderr.writeln(
      'Expected at least ${minimumPercent.toStringAsFixed(2)}% line coverage.',
    );
    exitCode = 1;
  }
}
