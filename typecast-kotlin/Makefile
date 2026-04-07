.PHONY: help install test coverage e2e clean all

all: coverage

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Fetch dependencies
	./gradlew dependencies

test: ## Run unit tests
	./gradlew test

coverage: ## Run unit tests with the 100% coverage gate
	./gradlew check jacocoTestReport

e2e: ## Run e2e tests (requires TYPECAST_API_KEY)
	./gradlew e2eTest

clean: ## Remove build artifacts
	./gradlew clean
