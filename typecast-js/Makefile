.PHONY: help install clean lint test build publish publish-patch publish-minor publish-major check dry-run version-info

# Default target
help:
	@echo "📦 Typecast JS SDK"
	@echo ""
	@echo "Development:"
	@echo "  make install          Install dependencies"
	@echo "  make clean            Clean build artifacts"
	@echo "  make lint             Run linter"
	@echo "  make test             Run tests"
	@echo "  make build            Build the package"
	@echo ""
	@echo "Publishing:"
	@echo "  make check            Run all quality checks (lint + test + build)"
	@echo "  make dry-run          Test package without publishing"
	@echo "  make version-info     Show current version and git tag info"
	@echo ""
	@echo "  make publish-patch    Bump patch version, publish (0.1.2 → 0.1.3)"
	@echo "  make publish-minor    Bump minor version, publish (0.1.2 → 0.2.0)"
	@echo "  make publish-major    Bump major version, publish (0.1.2 → 1.0.0)"
	@echo "  make publish          Publish current version (no version bump)"
	@echo ""
	@echo "📝 Quick workflow:"
	@echo "  make publish-patch    # Version bump + publish"
	@echo "  git push origin main --tags"
	@echo ""
	@echo "Note: Versions are managed by 'npm version' (auto-commits & tags)"
	@echo "      Remember to 'git push --tags' after publishing!"

# Install dependencies
install:
	@echo "📥 Installing dependencies..."
	npm install
	cd examples && npm install

# Clean build artifacts
clean:
	@echo "🧹 Cleaning build artifacts..."
	rm -rf lib
	rm -rf node_modules/.cache
	rm -f *.tgz

# Run linter
lint:
	@echo "🔍 Running linter..."
	npm run lint

# Run tests
test:
	@echo "🧪 Running tests..."
	npm test

# Build package
build:
	@echo "🔨 Building package..."
	npm run build

# Show version information
version-info:
	@echo "📋 Version Information"
	@echo "────────────────────────────────────"
	@echo "Package name: $$(node -p "require('./package.json').name")"
	@echo "Current version: $$(node -p "require('./package.json').version")"
	@if git describe --tags --exact-match 2>/dev/null; then \
		echo "Git tag: $$(git describe --tags --exact-match)"; \
	else \
		echo "Git tag: (no tag on current commit)"; \
	fi
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "Git status: ⚠️  Uncommitted changes"; \
	else \
		echo "Git status: ✅ Clean"; \
	fi
	@echo "────────────────────────────────────"

# Quality checks (lint + test + build)
check: lint test build
	@echo "✅ All quality checks passed!"

# Dry run to test publishing without actually publishing
dry-run: check
	@echo "🧪 Performing dry run..."
	npm publish --dry-run --access public
	@echo "✅ Dry run successful!"

# Publish to npm (current version)
publish: check version-info
	@echo ""
	@read -p "📦 Publish to npm? [y/N] " confirm && [ "$$confirm" = "y" ] || (echo "Cancelled." && exit 1)
	@echo ""
	@echo "📦 Publishing to npm..."
	npm publish --access public
	@echo ""
	@echo "🎉 Successfully published!"
	@echo "📝 Don't forget to: git push origin main --tags"

# Publish with patch version bump (0.1.2 → 0.1.3)
publish-patch: check
	@echo "📦 Bumping patch version and publishing..."
	@if [ -z "$$NPM_TOKEN" ]; then \
		echo "❌ NPM_TOKEN not set. Please run: export NPM_TOKEN=your_token"; \
		exit 1; \
	fi
	npm version patch -m "chore: release v%s"
	npm publish --access public
	@echo ""
	@echo "🎉 Successfully published!"
	@echo "📝 Don't forget to: git push origin main --tags"

# Publish with minor version bump (0.1.2 → 0.2.0)
publish-minor: check
	@echo "📦 Bumping minor version and publishing..."
	@if [ -z "$$NPM_TOKEN" ]; then \
		echo "❌ NPM_TOKEN not set. Please run: export NPM_TOKEN=your_token"; \
		exit 1; \
	fi
	npm version minor -m "chore: release v%s"
	npm publish --access public
	@echo ""
	@echo "🎉 Successfully published!"
	@echo "📝 Don't forget to: git push origin main --tags"

# Publish with major version bump (0.1.2 → 1.0.0)
publish-major: check
	@echo "📦 Bumping major version and publishing..."
	@if [ -z "$$NPM_TOKEN" ]; then \
		echo "❌ NPM_TOKEN not set. Please run: export NPM_TOKEN=your_token"; \
		exit 1; \
	fi
	npm version major -m "chore: release v%s"
	npm publish --access public
	@echo ""
	@echo "🎉 Successfully published!"
	@echo "📝 Don't forget to: git push origin main --tags"

