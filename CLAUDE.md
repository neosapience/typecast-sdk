# Typecast SDK Development Guidelines

This document provides essential guidelines for AI assistants and developers working on the Typecast SDK project.

> **Important**: Please also read the Cursor rules at `.cursor/rules/sdk-development.mdc` for detailed development rules that apply to this project.

## Project Overview

This repository contains official Typecast SDKs for multiple programming languages:
- C (`typecast-c/`)
- C# (`typecast-csharp/`)
- Go (`typecast-go/`)
- Java (`typecast-java/`)
- JavaScript/TypeScript (`typecast-js/`)
- Kotlin (`typecast-kotlin/`)
- Python (`typecast-python/`)
- Rust (`typecast-rust/`)
- Swift (`typecast-swift/`)

## Testing

- Write unit tests for all new functionality
- Maintain integration/E2E tests where applicable
- Ensure tests don't require real API keys for unit tests (use mocks)
- E2E tests should use environment variables for credentials

## File Structure Convention

Each SDK typically follows this structure:
```
typecast-{language}/
├── README.md           # SDK documentation
├── LICENSE            # License file
├── src/               # Source code
├── tests/             # Test files
├── examples/          # Usage examples
└── .env.example       # Environment variable template
```

## Quick Reference

| Guideline | Priority |
|-----------|----------|
| Backward compatibility | CRITICAL |
| Cross-SDK consistency | HIGH |
| File length < 450 lines | MEDIUM |
| English comments only | HIGH |
| No secrets in code | CRITICAL |
| Follow OpenAPI spec | HIGH |
