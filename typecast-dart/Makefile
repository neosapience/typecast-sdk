.PHONY: install format analyze test coverage e2e

install:
	dart pub get

format:
	dart format --set-exit-if-changed lib test example tool

analyze:
	dart analyze

test:
	dart test

coverage:
	rm -rf coverage
	dart test --coverage-path=coverage/lcov.info --coverage-package=typecast_dart
	dart run tool/check_coverage.dart coverage/lcov.info 70

e2e:
	dart test --run-skipped -t e2e
