# Contributing to Typecast Python SDK

## Development Setup

```bash
# Install dependencies
make install

# Or manually
uv sync --all-extras
```

## Testing

### Unit Tests
```bash
# Run all tests
make test

# Run with integration tests (requires API key)
make test-integration

# Run linters
make lint

# Format code
make format
```

### Package Testing

Before publishing, test the built package in an isolated environment:

```bash
# Test built package locally
make test-package
```

This will:
1. Build the package
2. Create a clean virtual environment
3. Install the built package
4. Run smoke tests (imports)
5. Run integration test if .env exists
6. Clean up automatically

**Other options:**

```bash
# Test in Docker (complete isolation)
make docker-test-package

# Test in CI environment
make ci-test-package
```

## Publishing

```bash
# Publish to PyPI (includes package testing)
make publish

# Publish to TestPyPI
make publish-test
```

## Code Quality

- Follow PEP 8 style guide
- Use type hints
- Write docstrings for public APIs
- Add tests for new features
- Run `make check` before committing

## Project Structure

```
typecast-python/
├── src/typecast/       # Main package
│   ├── client.py       # Sync client
│   ├── async_client.py # Async client
│   ├── models/         # Data models
│   └── ...
├── tests/              # Test suite
├── examples/           # Usage examples
└── Makefile           # Development commands
```

