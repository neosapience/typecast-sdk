# Contributing to Typecast JS SDK

## Development Setup

```bash
# Install dependencies
npm install
cd examples && npm install

# Run tests
npm test

# Run linter
npm run lint

# Build
npm run build
```

## Publishing to npm

### Prerequisites

1. **npm Authentication**
   - Option A: Login via CLI
     ```bash
     npm login
     ```
   - Option B: Use npm token (recommended for CI/CD)
     ```bash
     export NPM_TOKEN=npm_your_token_here
     ```

2. **All changes committed**
   - Ensure your working directory is clean before publishing

### Publishing Workflow (Recommended)

We provide a Makefile to streamline quality checks, versioning, and publishing:

```bash
# Option A: All-in-one (recommended)
make publish-patch    # Bump patch version + publish (0.1.2 → 0.1.3)
make publish-minor    # Bump minor version + publish (0.1.2 → 0.2.0)
make publish-major    # Bump major version + publish (0.1.2 → 1.0.0)
git push origin main --tags

# Option B: Step-by-step (more control)
make version-info     # Check current status
make check            # Run quality checks
make dry-run          # Test package
make publish-patch    # Version bump + publish
git push origin main --tags
```

#### What happens when you run `make publish-patch`:

1. ✅ Runs quality checks (lint + test + build)
2. ✅ Bumps version using `npm version patch`
   - Updates `package.json` and `package-lock.json`
   - Creates git commit: "chore: release v0.1.3"
   - Creates git tag: `v0.1.3`
3. ✅ Publishes to npm with `--access public`
4. ⚠️ **You still need to:** `git push origin main --tags`

**Note:** `npm version` automatically commits and tags, so git operations are partially automated for safety.

### Quick Reference

```bash
# Show all available commands
make help

# Development
make lint            # Run linter
make test            # Run tests
make build           # Build package

# Publishing (recommended)
make publish-patch   # Bump patch, publish (0.1.2 → 0.1.3)
make publish-minor   # Bump minor, publish (0.1.2 → 0.2.0)
make publish-major   # Bump major, publish (0.1.2 → 1.0.0)

# Publishing (manual control)
make version-info    # Check current version
make check           # Run all quality checks
make dry-run         # Test package
make publish         # Publish current version (no bump)
```

### Version Guidelines

Follow [Semantic Versioning](https://semver.org/):

- **Patch** (0.1.2 → 0.1.3): Bug fixes, no API changes
  ```bash
  npm version patch
  ```

- **Minor** (0.1.2 → 0.2.0): New features, backward compatible
  ```bash
  npm version minor
  ```

- **Major** (0.1.2 → 1.0.0): Breaking changes
  ```bash
  npm version major
  ```

## Pre-publish Checklist

Before publishing, ensure:

- [ ] All tests pass (`npm test`)
- [ ] Linter passes (`npm run lint`)
- [ ] Build succeeds (`npm run build`)
- [ ] README is up to date
- [ ] CHANGELOG is updated (if applicable)
- [ ] All changes are committed
- [ ] You're on the correct branch (usually `main`)

## npm Token Setup

### Creating an npm Token

1. Go to https://www.npmjs.com/
2. Login and navigate to **Access Tokens**
3. Click **Generate New Token** → **Granular Access Token**
4. Configure:
   - **Name**: `typecast-js-publish`
   - **Expiration**: Choose appropriate duration
   - **Packages and scopes**: Select `@neosapience/typecast-js`
   - **Permissions**: Read and write
5. Copy the token (you won't see it again!)

### Using the Token

**Local Development:**
```bash
export NPM_TOKEN=npm_your_token_here
```

**CI/CD (GitHub Actions):**
Add token to GitHub Secrets as `NPM_TOKEN`

**Persistent (for local):**
Add to your shell profile (~/.zshrc or ~/.bashrc):
```bash
export NPM_TOKEN=npm_your_token_here
```

## Testing Before Publishing

Always test the package before publishing:

```bash
# Dry run (simulates publishing without actually publishing)
npm publish --dry-run --access public
# or
make dry-run

# Test local installation
npm pack
npm install ./neosapience-typecast-js-*.tgz

# Test in examples
cd examples
npm install ../neosapience-typecast-js-*.tgz
npm test
```

## Troubleshooting

### "Working directory is not clean"
```bash
# Check what's uncommitted
git status

# Commit or stash changes
git add -A
git commit -m "your message"
```

### "401 Unauthorized" during publish
```bash
# Check if logged in
npm whoami

# Login if needed
npm login

# Or verify NPM_TOKEN is set
echo $NPM_TOKEN
```

### "403 Forbidden" during publish
- Ensure you have permission to publish `@neosapience/typecast-js`
- Verify you're using `--access public` flag

### Force push failed
```bash
# Use --force-with-lease for safer force push
git push --force-with-lease origin main

# If someone else pushed, pull first
git pull --rebase
git push origin main
```

## CI/CD Setup (GitHub Actions)

Example workflow for automated publishing:

```yaml
name: Publish to npm

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version bump type'
        required: true
        type: choice
        options:
          - patch
          - minor
          - major

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0
      
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
          registry-url: 'https://registry.npmjs.org'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Configure Git
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
      
      - name: Run quality checks
        run: make check
      
      - name: Bump version and publish
        run: make publish-${{ github.event.inputs.version }}
        env:
          NODE_AUTH_TOKEN: ${{ secrets.NPM_TOKEN }}
      
      - name: Push changes and tags
        run: git push origin main --tags
```

This workflow:
1. Runs manually via GitHub Actions UI
2. Lets you select version bump type (patch/minor/major)
3. Runs quality checks via Makefile
4. Executes `make publish-patch` (or minor/major)
   - Version bump
   - Auto-commit and tag
   - Publish to npm
5. Pushes changes and tags to GitHub

## Questions?

- GitHub Issues: https://github.com/neosapience/typecast-js/issues
- Email: help@typecast.ai

