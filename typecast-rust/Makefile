.PHONY: help install test coverage coverage-html e2e clean

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

install: ## Fetch dependencies
	cargo fetch

test: ## Run unit tests (mocked HTTP, no API key required)
	cargo test

coverage: ## Run unit tests under cargo-llvm-cov and enforce near-complete line/function/region coverage
	# cargo-llvm-cov reports public generic/async instantiations across test binaries,
	# so Rust uses a 99% gate while mocked unit/integration tests cover composer behavior.
	cargo llvm-cov --fail-under-lines 99 --fail-under-functions 99 --fail-under-regions 99

coverage-html: ## Generate an HTML coverage report under target/llvm-cov/html
	cargo llvm-cov --html

e2e: ## Run end-to-end tests against the real Typecast API (requires TYPECAST_API_KEY)
	cargo test --features e2e

clean: ## Remove build artifacts and coverage output
	cargo clean
	rm -rf target/llvm-cov*
