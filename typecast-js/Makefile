.PHONY: help install test coverage e2e clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Install dependencies
	npm install

test: ## Run unit tests
	npm test -- --run

coverage: ## Run unit tests with 100% coverage gate
	npm run coverage

e2e: ## Run e2e tests (requires TYPECAST_API_KEY)
	npm run test:e2e

clean: ## Remove build artifacts
	rm -rf lib coverage
