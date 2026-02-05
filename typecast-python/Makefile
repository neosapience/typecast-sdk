.PHONY: help install test test-integration lint format check clean build dist publish
.PHONY: docker-build docker-up docker-down docker-shell docker-test
.DEFAULT_GOAL := help

# Colors for output
BLUE := \033[0;34m
GREEN := \033[0;32m
YELLOW := \033[0;33m
NC := \033[0m # No Color

help: ## Show this help message
	@echo "$(BLUE)Typecast Python SDK - Available Commands$(NC)"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2}'
	@echo ""

# Local Development Commands
install: ## Install dependencies with uv
	uv sync --all-extras

test: ## Run all tests (skip integration tests without API key)
	uv run pytest tests/ -v

test-integration: ## Run all tests including integration tests (requires API key)
	uv run pytest tests/ -v --run-integration

test-watch: ## Run tests in watch mode
	uv run pytest-watch tests/

lint: ## Run all linters (ruff check + mypy)
	uv run ruff check src/ tests/ examples/
	uv run mypy src/

format: ## Format code with ruff
	uv run ruff format src/ tests/ examples/
	uv run ruff check --fix src/ tests/ examples/

check: lint test ## Run linters and tests

clean: ## Clean up build artifacts and cache
	rm -rf dist/ build/ *.egg-info .pytest_cache .mypy_cache .ruff_cache
	find . -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find . -type f -name "*.pyc" -delete

build: clean ## Build distribution packages
	uv build

test-package: build ## Test the built package in isolated environment
	@echo "$(BLUE)Testing built package in isolated environment...$(NC)"
	@rm -rf .test_venv
	@python3 -m venv .test_venv
	@.test_venv/bin/pip install --quiet dist/*.whl python-dotenv > /dev/null
	@echo "$(GREEN)✓ Package installed successfully$(NC)"
	@echo ""
	@echo "$(BLUE)Running basic smoke tests...$(NC)"
	@.test_venv/bin/python tests/smoke_test.py || (rm -rf .test_venv && exit 1)
	@echo ""
	@if [ -f .env ]; then \
		echo "$(BLUE)Running integration smoke tests (with real API calls)...$(NC)"; \
		.test_venv/bin/python tests/integration_smoke_test.py || (rm -rf .test_venv && exit 1); \
	else \
		echo "$(YELLOW)⚠ No .env file found, skipping integration tests$(NC)"; \
	fi
	@rm -rf .test_venv
	@echo ""
	@echo "$(GREEN)🎉 All package tests passed!$(NC)"

publish: test-package ## Publish to PyPI (requires credentials)
	uv publish

publish-test: test-package ## Publish to TestPyPI
	uv publish --publish-url https://test.pypi.org/legacy/

# Docker Commands
docker-build: ## Build Docker image
	docker compose build --progress=plain

docker-up: ## Start Docker container in background
	docker compose up -d

docker-down: ## Stop Docker container
	docker compose down

docker-shell: ## Open shell in Docker container
	docker compose exec typecast-dev /bin/bash

docker-test: ## Run tests in Docker
	docker compose exec typecast-dev uv run pytest tests/ -v

docker-lint: ## Run linters in Docker
	docker compose exec typecast-dev uv run ruff check src/ tests/ examples/

docker-format: ## Format code in Docker
	docker compose exec typecast-dev uv run ruff format src/ tests/ examples/

docker-test-package: build ## Test built package in Docker (isolated)
	@echo "$(BLUE)Testing package in Docker container...$(NC)"
	@docker run --rm -v $(PWD)/dist:/dist python:3.12-slim bash -c "\
		pip install --quiet /dist/*.whl && \
		python -c 'from typecast import Typecast, AsyncTypecast; print(\"✓ Package test passed in Docker\")' \
	"
	@echo "$(GREEN)✓ Docker package test completed$(NC)"

# CI/CD Commands (for CircleCI)
ci-install: ## Install dependencies for CI
	pip install uv
	uv sync --frozen --all-extras

ci-lint: ## Run linters in CI
	uv run ruff check src/ tests/ examples/ --output-format=github
	uv run mypy src/ --junit-xml=test-reports/mypy.xml

ci-test: ## Run tests in CI with coverage
	uv run pytest tests/ -v --junitxml=test-reports/pytest.xml --cov=src/typecast --cov-report=xml --cov-report=html

ci-build: ## Build package in CI
	uv build
	ls -lh dist/

ci-test-package: ci-build ## Test built package in CI
	@echo "Testing built package..."
	@python3 -m venv .ci_test_venv
	@.ci_test_venv/bin/pip install --quiet dist/*.whl
	@.ci_test_venv/bin/python -c "from typecast import Typecast, AsyncTypecast; print('✓ CI package test passed')"
	@rm -rf .ci_test_venv

# Development helpers
dev-setup: ## Setup development environment
	@echo "$(BLUE)Setting up development environment...$(NC)"
	uv sync --all-extras
	@echo "$(GREEN)✓ Dependencies installed$(NC)"
	@echo "$(YELLOW)Don't forget to copy .env.example to .env and add your API key!$(NC)"

version: ## Show current version
	@grep '^version = ' pyproject.toml | cut -d'"' -f2
