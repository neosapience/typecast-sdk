.PHONY: install format analyze test coverage e2e

install:
	dart pub get

format:
	dart format --set-exit-if-changed lib test example

analyze:
	dart analyze

test:
	dart test

coverage:
	dart test

e2e:
	dart test --run-skipped -t e2e
