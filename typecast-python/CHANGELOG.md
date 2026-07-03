# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.8] - 2026-07-02

### Added

- `AsyncTypecast` and `Typecast` now accept an optional `session` parameter for externally-managed `aiohttp.ClientSession` / `requests.Session`. When provided, the client will not create or close the session (the caller owns its lifecycle); auth headers (`X-API-KEY`, `User-Agent`) are attached per-request via the new `_request_headers()` helper. Enables integration with frameworks (e.g. Pipecat) that inject their own HTTP session.
