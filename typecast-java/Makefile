.PHONY: help install test coverage e2e clean all

all: coverage

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Fetch dependencies
	mvn dependency:resolve

test: ## Run unit tests
	mvn test

coverage: ## Run unit tests with 100% coverage gate
	mvn verify

e2e: ## Run E2E tests (requires TYPECAST_API_KEY)
	mvn -Pe2e verify

clean: ## Remove build artifacts
	mvn clean
