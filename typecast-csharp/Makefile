.PHONY: help install test coverage e2e clean

help:
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Restore deps
	dotnet restore

test: ## Run unit tests
	DOTNET_ROLL_FORWARD=Major dotnet test tests/Typecast.Tests --filter "FullyQualifiedName!~E2E"

coverage: ## Run unit tests with 100% coverage gate
	DOTNET_ROLL_FORWARD=Major dotnet test tests/Typecast.Tests --filter "FullyQualifiedName!~E2E" \
		/p:CollectCoverage=true \
		/p:Threshold=100 \
		/p:ThresholdType=line%2Cbranch%2Cmethod \
		/p:CoverletOutputFormat=cobertura \
		/p:CoverletOutput=./TestResults/coverage.cobertura.xml \
		/p:Exclude='[*]*ExcludeFromCodeCoverage*' \
		/p:ExcludeByAttribute=Obsolete%2CGeneratedCodeAttribute%2CCompilerGeneratedAttribute%2CExcludeFromCodeCoverage

e2e: ## Run e2e tests (requires TYPECAST_API_KEY)
	DOTNET_ROLL_FORWARD=Major dotnet test tests/Typecast.E2E.Tests

clean: ## Remove build artifacts
	dotnet clean
	rm -rf tests/Typecast.Tests/TestResults
