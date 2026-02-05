#!/bin/bash
# Typecast Swift SDK Test Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "========================================"
echo "  Typecast Swift SDK Test Runner"
echo "========================================"
echo ""

cd "$PROJECT_DIR"

# Check Swift version
echo "Swift Version:"
swift --version
echo ""

# Build the package
echo "Building package..."
swift build
echo "✅ Build successful"
echo ""

# Run unit tests
echo "Running unit tests..."
swift test --filter TypecastClientTests
echo "✅ Unit tests passed"
echo ""

# Run integration tests if API key is set
if [ -n "$TYPECAST_API_KEY" ]; then
    echo "Running integration tests..."
    swift test --filter IntegrationTests
    echo "✅ Integration tests passed"
else
    echo "⚠️  Skipping integration tests (TYPECAST_API_KEY not set)"
    echo "   Set TYPECAST_API_KEY environment variable to run integration tests"
fi

echo ""
echo "========================================"
echo "  All tests completed successfully!"
echo "========================================"
