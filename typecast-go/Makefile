.PHONY: help install test coverage e2e clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Download deps
	go mod download

test: ## Run unit tests
	go test ./...

coverage: ## Run unit tests with 100% coverage gate
	go test -coverprofile=coverage.out -covermode=atomic -coverpkg=github.com/neosapience/typecast-sdk/typecast-go .
	go tool cover -func=coverage.out
	@total=$$(go tool cover -func=coverage.out | tail -1 | awk '{print $$3}' | tr -d '%'); \
	echo "total coverage: $$total%"; \
	awk -v t=$$total 'BEGIN { if (t < 100.0) { print "FAIL: coverage " t "% < 100%"; exit 1 } }'

e2e: ## Run e2e tests (requires TYPECAST_API_KEY)
	go test -tags=e2e ./...

clean: ## Remove build artifacts
	rm -f coverage.out
