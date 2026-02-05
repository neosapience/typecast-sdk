# Contributing to Typecast SDK

Thank you for your interest in contributing to the Typecast SDK! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Pull Request Process](#pull-request-process)
- [Style Guidelines](#style-guidelines)
- [Reporting Issues](#reporting-issues)

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for everyone. Please be considerate and constructive in your communications.

## Getting Started

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/typecast-sdk.git
   cd typecast-sdk
   ```
3. **Create a branch** for your changes:
   ```bash
   git checkout -b feature/your-feature-name
   ```

## How to Contribute

### Types of Contributions

- **Bug fixes**: Fix issues and submit a pull request
- **New features**: Discuss in an issue first, then implement
- **Documentation**: Improve README, examples, or inline docs
- **Tests**: Add or improve test coverage
- **Translations**: Help translate documentation

### Which SDK to Contribute To

This monorepo contains SDKs for multiple languages:

| Directory | Language | Package Manager |
|-----------|----------|-----------------|
| `typecast-python` | Python | pip / uv |
| `typecast-js` | JavaScript/TypeScript | npm |
| `typecast-go` | Go | go modules |
| `typecast-java` | Java | Maven |
| `typecast-kotlin` | Kotlin | Gradle |
| `typecast-csharp` | C# | NuGet |
| `typecast-swift` | Swift | SPM |
| `typecast-rust` | Rust | Cargo |
| `typecast-c` | C | CMake |

Each SDK has its own `README.md` with specific setup instructions.

## Development Setup

### Prerequisites

Ensure you have the required tools for the SDK you want to work on:

- **Python**: Python 3.9+, uv (recommended) or pip
- **JavaScript**: Node.js 18+, npm
- **Go**: Go 1.21+
- **Java**: JDK 17+, Maven
- **Kotlin**: JDK 17+, Gradle
- **C#**: .NET 8.0+
- **Swift**: Swift 5.9+, Xcode (macOS)
- **Rust**: Rust 1.70+
- **C**: CMake 3.14+, libcurl

### SDK-Specific Setup

#### Python

```bash
cd typecast-python
uv sync --all-extras
uv run pytest tests/
```

#### JavaScript/TypeScript

```bash
cd typecast-js
npm install
npm test
```

#### Go

```bash
cd typecast-go
go test -v ./...
```

See individual SDK directories for more detailed instructions.

## Pull Request Process

1. **Ensure tests pass** locally before submitting
2. **Update documentation** if you've changed APIs or added features
3. **Follow the PR template** when creating your pull request
4. **Link related issues** in your PR description
5. **Request a review** from maintainers
6. **Address feedback** promptly and constructively

### PR Checklist

- [ ] Tests added/updated and passing
- [ ] Documentation updated (if applicable)
- [ ] Code follows project style guidelines
- [ ] Commit messages are clear and descriptive
- [ ] No unrelated changes included

## Style Guidelines

### General

- Write clear, self-documenting code
- Add comments for complex logic
- Keep functions focused and small
- Use meaningful variable and function names

### Language-Specific

| Language | Style Guide |
|----------|-------------|
| Python | [PEP 8](https://pep8.org/), type hints required |
| JavaScript/TypeScript | ESLint + Prettier (configured in repo) |
| Go | `gofmt` and `golint` |
| Java | Google Java Style Guide |
| Kotlin | Kotlin Coding Conventions |
| C# | .NET Coding Conventions |
| Swift | Swift API Design Guidelines |
| Rust | `rustfmt` and `clippy` |
| C | K&R style with 4-space indentation |

### Commit Messages

Use clear, descriptive commit messages:

```
feat(python): add streaming support for TTS

- Implement SSE client for real-time audio
- Add TypecastStreamingClient class
- Update documentation with streaming examples

Closes #123
```

Prefixes:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `test`: Adding or updating tests
- `refactor`: Code refactoring
- `chore`: Maintenance tasks

## Reporting Issues

### Before Submitting

1. **Search existing issues** to avoid duplicates
2. **Check the documentation** for answers
3. **Try the latest version** to see if it's already fixed

### Bug Reports

Include:
- SDK name and version
- Operating system and version
- Steps to reproduce
- Expected vs actual behavior
- Error messages (if any)
- Minimal code example

### Feature Requests

Include:
- Clear description of the feature
- Use case and motivation
- Proposed API (if applicable)
- Any alternatives considered

## Questions?

- **GitHub Issues**: For bugs and feature requests
- **Discussions**: For questions and ideas
- **Email**: help@typecast.ai

---

Thank you for contributing to Typecast SDK! 🎉
