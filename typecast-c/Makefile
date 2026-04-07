# Typecast C SDK - convenience Makefile for coverage workflows
#
# The CMake project remains the source of truth for the build itself; this
# Makefile only wires up the coverage gate so that local runs and CI use the
# same flow.

.PHONY: help install build test coverage e2e clean all

LCOV_IGNORE := --ignore-errors mismatch,unused,inconsistent,gcov,format,unsupported,empty,negative

all: coverage

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Configure CMake build with coverage instrumentation
	cmake -B build -DTYPECAST_COVERAGE=ON

build: install ## Build the test binaries
	cmake --build build

test: build ## Run unit tests (requires coverage-enabled build)
	cd build && ctest --output-on-failure

coverage: test ## Run unit tests with the 100% coverage gate
	lcov --capture --directory build --output-file coverage.info $(LCOV_IGNORE) --rc branch_coverage=1
	lcov --remove coverage.info \
		'*/tests/*' '*/cJSON.*' '*/build/*' '/usr/*' '*/examples/*' '/Applications/*' \
		--output-file coverage.filtered.info $(LCOV_IGNORE) --rc branch_coverage=1
	lcov --summary coverage.filtered.info $(LCOV_IGNORE) --rc branch_coverage=1 | tee coverage-summary.txt
	@LINE=$$(grep -E '^\s*lines' coverage-summary.txt | grep -oE '[0-9]+\.[0-9]+%' | head -1 | tr -d '%'); \
	FUNC=$$(grep -E '^\s*functions' coverage-summary.txt | grep -oE '[0-9]+\.[0-9]+%' | head -1 | tr -d '%'); \
	echo "line=$${LINE}% func=$${FUNC}%"; \
	awk -v l="$$LINE" -v f="$$FUNC" 'BEGIN { \
		if (l != "100.0" && l != "100") { print "FAIL: line " l "% < 100%"; exit 1 } \
		if (f != "100.0" && f != "100") { print "FAIL: func " f "% < 100%"; exit 1 } \
		print "OK: line=" l "% func=" f "%" \
	}'

e2e: build ## Run e2e/integration tests (requires TYPECAST_API_KEY)
	cd build && ctest -R integration --output-on-failure

clean: ## Remove build artifacts and coverage reports
	rm -rf build coverage.info coverage.filtered.info coverage-summary.txt
