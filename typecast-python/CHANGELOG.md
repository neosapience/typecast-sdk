# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.14] - 2026-08-28

### Added

- V3 voice retrieval and custom voice management.

## [0.3.13] - 2026-08-20

### Added

- `api-page` and `api-docs` as supported User-Agent attribution sources.

## [0.3.12] - 2026-08-19

### Added

- Optional `source` and `generated_by` attribution in the SDK User-Agent.

## [0.3.8] - 2026-07-02

### Added

- `AsyncTypecast` and `Typecast` now accept an optional `session` parameter for externally-managed `aiohttp.ClientSession` / `requests.Session`. When provided, the client will not create or close the session (the caller owns its lifecycle); auth headers (`X-API-KEY`, `User-Agent`) are attached per-request via the new `_request_headers()` helper. Enables integration with frameworks (e.g. Pipecat) that inject their own HTTP session.
