.PHONY: help install test coverage e2e clean all

all: coverage

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Resolve packages
	swift package resolve

test: ## Run unit tests
	swift test

coverage: ## Run unit tests with 100% coverage gate
	swift test --enable-code-coverage
	@BIN_DIR=$$(find .build -name 'TypecastPackageTests.xctest' -type d | head -1); \
	BIN="$$BIN_DIR/Contents/MacOS/TypecastPackageTests"; \
	[ -f "$$BIN" ] || BIN=$$(find .build -name 'TypecastPackageTests' -type f -perm +111 | head -1); \
	PROFDATA=$$(find .build -name 'default.profdata' | head -1); \
	if [ -z "$$BIN" ] || [ -z "$$PROFDATA" ]; then echo "coverage data not found"; exit 1; fi; \
	xcrun llvm-cov report "$$BIN" -instr-profile="$$PROFDATA" \
		-ignore-filename-regex='Tests|\.build' | tee coverage-summary.txt; \
	LINE=$$(awk '/^TOTAL/ {print $$10}' coverage-summary.txt | tr -d '%'); \
	FUNC=$$(awk '/^TOTAL/ {print $$7}' coverage-summary.txt | tr -d '%'); \
	echo "LINE: $$LINE% FUNC: $$FUNC%"; \
	awk -v l="$$LINE" -v f="$$FUNC" 'BEGIN { ok=1; if (l != "100.00" && l != "100") { print "FAIL: line coverage " l "% < 100%"; ok=0 } if (f != "100.00" && f != "100") { print "FAIL: function coverage " f "% < 100%"; ok=0 } if (!ok) exit 1 }'

e2e: ## Run e2e tests (requires TYPECAST_API_KEY)
	swift test --filter IntegrationTests

clean: ## Remove build artifacts
	swift package clean
	rm -rf .build coverage-summary.txt
