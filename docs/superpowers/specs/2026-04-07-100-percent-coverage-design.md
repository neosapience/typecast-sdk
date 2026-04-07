# 100% Code Coverage Across All SDKs — Design

**Date:** 2026-04-07
**Status:** Approved for planning
**Scope:** All 9 SDKs in this monorepo
**Author:** Brainstormed with @hmmhmmhm

## 1. Goal

Achieve and enforce **100% code coverage** across all 9 Typecast SDKs, with explicit, narrow exclusions for code that is physically unreachable on the test machine. Coverage must be enforced by CI gates so the percentage cannot regress, and each SDK's README must display a static `coverage-100%-brightgreen` shields.io badge that is trustworthy because the CI gate makes it true.

This is not a "best effort" or "directional" target. The PR is not mergeable until the SDK reports 100% line + function + branch coverage (line + function only on toolchains that cannot measure branches).

## 2. Non-Goals

- Reaching 100% on E2E tests against the real Typecast API. E2E tests are kept as opt-in jobs and are not part of the coverage gate.
- Setting up an external coverage hosting service (Codecov, Coveralls). Self-hosted, CI-gated, static badge only.
- Refactoring SDK source code beyond what is necessary to make untestable code testable.
- Touching SDKs unrelated to the current PR. Each SDK PR is fully independent.

## 3. Scope

### 3.1 SDKs in scope

| SDK | Current coverage | Tool to introduce |
|-----|------------------|-------------------|
| typecast-js | 56.66% (lines, vitest+v8 already present) | `vitest` + `@vitest/coverage-v8` |
| typecast-python | 58% (pytest-cov already present) | `pytest-cov` + `coverage` |
| typecast-csharp | partial (`coverlet.collector` referenced) | `coverlet.msbuild` + `ReportGenerator` |
| typecast-go | none | `go test -coverprofile` + `go tool cover` |
| typecast-rust | none | `cargo-llvm-cov` |
| typecast-java | none | `jacoco-maven-plugin` |
| typecast-kotlin | none | `jacoco` Gradle plugin |
| typecast-swift | none | `swift test --enable-code-coverage` + `xcrun llvm-cov` |
| typecast-c | none | `gcov` + `lcov` (CMake `-fprofile-arcs -ftest-coverage`) |

### 3.2 Coverage metric

**Line + function + branch** coverage at 100% wherever the toolchain supports it. Toolchains that cannot measure branches fall back to **line + function** at 100%:

- `go test -cover` — line only (no branch metric) → line + function 100%
- `swift test --enable-code-coverage` / `xccov` — line + function only → line + function 100%

All other toolchains (vitest+v8, coverage.py, jacoco, llvm-cov, coverlet, gcov/lcov) measure branches and must hit branch 100%.

## 4. Branch & Release Strategy

- **Per-SDK branches off `main`.** No umbrella branch. Each SDK PR is independent and can be merged the moment it passes its own CI.
- **Order:** infrastructure first, then SDKs in order of complexity (easiest with existing infra → hardest native).
- **Release after each SDK PR:** the SDK whose PR was merged gets an immediate patch version bump and is published to its registry, following the same workflow used in #10/#11. The infrastructure PR (step 0) is not released — it changes no SDK code.

### 4.1 PR sequence (10 PRs total)

| # | Branch | Touches | Releases |
|---|--------|---------|----------|
| 0 | `feat/coverage-infra` | `test-fixtures/mock-server/`, `docs/coverage-policy.md`, `.github/workflows/coverage-template.yml`, root README | none |
| 1 | `feat/coverage-js` | `typecast-js/` | `@neosapience/typecast-js` patch |
| 2 | `feat/coverage-python` | `typecast-python/` | `typecast-python` patch |
| 3 | `feat/coverage-csharp` | `typecast-csharp/` | `typecast-csharp` patch |
| 4 | `feat/coverage-go` | `typecast-go/` | tag patch |
| 5 | `feat/coverage-rust` | `typecast-rust/` | `typecast` crate patch |
| 6 | `feat/coverage-java` | `typecast-java/` | Maven Central patch |
| 7 | `feat/coverage-kotlin` | `typecast-kotlin/` | Maven Central patch |
| 8 | `feat/coverage-swift` | `typecast-swift/` | tag patch |
| 9 | `feat/coverage-c` | `typecast-c/` | conan/vcpkg patch |

The order is chosen to validate the approach against SDKs that already have partial coverage tooling, then propagate the established patterns to SDKs that need new tooling, and finally tackle the native SDKs whose build systems are most invasive to instrument.

## 5. Step 0: Shared Infrastructure PR

This PR introduces no SDK changes. It lands the foundations every subsequent PR builds on.

### 5.1 Mock server

Located at `test-fixtures/mock-server/`. **Implementation language: Node.js + TypeScript**, with `tsx` (TypeScript runner) and `ws` (WebSocket server, no transitive deps) as the only runtime dependencies beyond the Node standard library. Node ≥20 has no built-in WebSocket server, so `ws` is unavoidable; we picked it because it has zero runtime dependencies of its own.

```text
test-fixtures/mock-server/
  package.json              # name, deps: tsx + ws; node engines >= 20
  tsconfig.json
  src/
    index.ts                # CLI entry: --port (default 8765), --fixtures-dir
    rest-handler.ts         # request → fixture file resolver (URL + method + body hash)
    sse-handler.ts          # SSE script player (event/data/retry chunks)
    ws-handler.ts           # WebSocket frame script player
    fixture-loader.ts       # filesystem → in-memory map
  fixtures/
    voices/
      list-200.json
      list-401.json
    tts/
      synthesis-200.bin
      synthesis-422.json
      synthesis-500.json
    sse/
      ssfm-stream-1.txt     # SSE chunk sequence (event:..\ndata:..\n\n)
      ssfm-stream-error.txt
    ws/
      synthesis-1.jsonl     # frame script: {delay_ms, opcode, payload}
      synthesis-disconnect.jsonl
  README.md                 # how to add a fixture, port convention, schema
```

**Behavior model**

- Single process, started by each SDK CI job in the background.
- Fixture matching: `(method, path, optional body-shape)` → static response file. Unmatched requests return 404 with a deterministic JSON body so SDK tests can assert on it.
- SSE streams: a script file is replayed verbatim on each connection with optional inter-chunk delays.
- WebSocket: a JSONL script file describing frames is replayed; the server can also be configured to disconnect mid-stream for failure-path tests.
- Deliberately small. **No** WireMock-style matchers, **no** dynamic responses, **no** request templating. If a test needs a new response, it adds a fixture file.
- Health check at `GET /__mock_health` returning `200 ok`. CI scripts wait on this before running tests.

### 5.2 Coverage policy document

`docs/coverage-policy.md` containing the whitelist policy from §6 below in long-form, plus examples of how to write the inline pragma comment in each language.

### 5.3 CI workflow template

`.github/workflows/coverage-template.yml` — a reference file (not actually triggered) that documents the standard shape every SDK workflow follows. Each SDK PR copies and adapts it.

### 5.4 Root README addendum

Add a short "Code Coverage" section to the root `README.md` linking to the policy doc and listing each SDK's badge once they exist (initially all blank/placeholder, filled in as PRs land).

## 6. Pragma Whitelist Policy

The whitelist defines the only categories under which a line may be excluded from coverage measurement. Anything outside the whitelist must be tested.

### 6.1 Allowed exclusion categories

1. **Physically unreachable on the test host** — platform-specific code paths whose preprocessor / compile-time guard is false on the current OS (e.g., C `#ifdef _WIN32` on Linux runners, Swift `#if os(Linux)` on macOS runners). Both halves are still tested, just on different runners.
2. **Language-enforced unreachable** — Rust `unreachable!()`, `unimplemented!()`, Kotlin `error("unreachable")`, Java private utility-class constructors that throw `AssertionError`, Go `panic("unreachable")` immediately following an exhaustive switch.
3. **Type-only / generated code** — TypeScript files containing only `type` and `interface` declarations, Python `if TYPE_CHECKING:` blocks, generated stubs.
4. **Build / configuration artifacts** — `examples/`, `dist/`, `lib/`, `vite.config.ts`, generated build outputs. These are excluded at the tool-config level (collection scope), not via inline pragmas.
5. **Runtime / language ergonomic limits** — Swift `defer` blocks whose unreachable cleanup path the compiler still emits, C `assert()` failure branches in release builds.

### 6.2 Forbidden exclusions

Inline pragmas may **never** be used to exclude:

- HTTP / network failure handling
- Timeout paths
- Input validation
- Response parsing errors
- Auth failures
- Any branch that depends on user-controlled input

These categories are exactly where bugs hide, and excluding them defeats the purpose of the gate.

### 6.3 Pragma format and budget

- Every excluded line carries an inline comment with **category and reason**:
  ```python
  if sys.platform == "win32":  # pragma: no cover  # category=platform reason="Windows-only fallback"
  ```
- Each PR body declares a one-line budget summary:

  ```text
  Excluded: 7 lines (platform=4, unreachable=2, type-only=1)
  ```

- **Maximum 30 excluded lines per SDK.** If a PR needs more, the policy must be revisited (separate discussion, not a unilateral PR change). The budget is set as a starting threshold and may be revisited after the first 1–2 SDK PRs land with real data.

## 7. Per-SDK PR Common Structure

Every SDK PR (steps 1–9) follows the same shape, modulo language-specific details.

### 7.1 Filesystem changes

```text
typecast-{lang}/
  src/                      (mostly unchanged; minor refactors only where needed for testability)
  tests/
    unit/                   # 100% coverage target, uses mock server + in-process mocks
    e2e/                    # opt-in, gated by TYPECAST_API_KEY env var
  Makefile                  # adds `coverage` target
  README.md                 # adds shields.io badge at top
  (language-specific config files: coverage thresholds, scopes, exclusions)
.github/workflows/
  coverage-{lang}.yml       # paths-filtered to typecast-{lang}/ + test-fixtures/mock-server/
```

### 7.2 Mocking strategy (hybrid, per Q4)

- **REST endpoints** → in-process language-native mocks:
  - js: `msw` or `nock`
  - python: `respx`/`pytest-httpx`
  - go: `httptest`
  - rust: `mockito` or `wiremock-rs`
  - java/kotlin: OkHttp `MockWebServer` (works for both)
  - csharp: `WireMock.Net`
  - swift: `URLProtocol` stub
  - c: function-pointer injection or `libcurl` write-callback redirection
- **SSE streams** → shared mock server (`test-fixtures/mock-server/`)
- **WebSocket frames** → shared mock server
- The mock server is started once per CI job via the workflow, on `localhost:8765`. Test code reads `TYPECAST_MOCK_URL` from env.

### 7.3 Existing integration test handling (per Q8)

For each SDK that already has integration tests touching the real API:

- Inspect each test's intent.
- If the intent is "exercise our SDK code paths" → rewrite on top of the mock server / language mock and place under `tests/unit/`.
- If the intent is "validate the real API contract" → move under `tests/e2e/`, gate execution on `TYPECAST_API_KEY`.
- If the intent is unclear → default to `tests/unit/` rewrite.

E2E tests are not part of the coverage gate and are not run in CI by this work. A separate `e2e.yml` workflow that runs on a schedule with secrets is out of scope here but should be enabled later.

### 7.4 Coverage gate configuration per SDK

| SDK | Where the 100% gate lives |
|-----|---------------------------|
| js | `vitest.config.ts` `coverage.thresholds.{lines,functions,branches,statements} = 100` |
| python | `pyproject.toml` `[tool.coverage.report] fail_under = 100` + `[tool.coverage.run] branch = true` |
| csharp | `coverlet.runsettings` `Threshold=100`, `ThresholdType=line,branch,method` |
| go | `Makefile` post-step: `awk` over `go tool cover -func` output; fail if any line < 100 |
| rust | `cargo llvm-cov --fail-under-lines 100 --fail-under-functions 100 --fail-under-regions 100` |
| java | `pom.xml` `jacoco-maven-plugin` `<rule>` with `LINE/BRANCH/METHOD COVEREDRATIO 1.00` |
| kotlin | `build.gradle.kts` `jacocoTestCoverageVerification` rule with same limits |
| swift | `Makefile` post-step: parse `xcrun llvm-cov report` output; fail if any line/function < 100 |
| c | `Makefile` post-step: parse `lcov --summary` output; fail if any line/branch/function < 100 |

CI does not duplicate the gate — it just runs `make coverage` (or the equivalent) and trusts the tool's exit code.

### 7.5 README badge

Each SDK README adds a shields.io badge at the top:

```text
![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)
```

The badge is trustworthy because the CI gate enforces 100% on every PR and on `main` push. If the gate ever breaks, the next PR fails and the badge is fixed before merge.

### 7.6 Version bump and release

After each SDK PR is merged to `main`:

1. Patch version bump on the SDK's version file (`package.json`, `pyproject.toml`, `Cargo.toml`, etc.) and lockfile refresh, landed via the same fast-forward-to-`main` flow used in #11.
2. Manual publish via the SDK's standard channel (npm, PyPI, Maven Central, crates.io, NuGet, GitHub release). Tokens read from the repo-root `.env` file (`NPM_TOKEN`, `PYPI_TOKEN`, `CARGO_REGISTRY_TOKEN`, etc.) as in the #10/#11 release flow.
3. Tags follow the existing convention per SDK (none for js/python, `typecast-{lang}/v{version}` for go/swift, etc.).

## 8. CI Workflow Shape

All `coverage-{lang}.yml` workflows follow this skeleton:

```yaml
name: coverage-{lang}
on:
  push:
    branches: [main]
    paths:
      - 'typecast-{lang}/**'
      - 'test-fixtures/mock-server/**'
      - '.github/workflows/coverage-{lang}.yml'
  pull_request:
    paths:
      - 'typecast-{lang}/**'
      - 'test-fixtures/mock-server/**'
      - '.github/workflows/coverage-{lang}.yml'
jobs:
  coverage:
    runs-on: ubuntu-latest  # or matrix for typecast-c
    steps:
      - uses: actions/checkout@v4
      - name: Setup Node (for mock server)
        uses: actions/setup-node@v4
        with: { node-version: '20' }
      - name: Install and start mock server
        working-directory: test-fixtures/mock-server
        run: |
          npm ci
          npx tsx src/index.ts --port 8765 &
          for i in {1..30}; do
            curl -sf http://localhost:8765/__mock_health && break
            sleep 1
          done
      - name: Setup {language toolchain}
        # language-specific
      - name: Run coverage
        working-directory: typecast-{lang}
        env:
          TYPECAST_MOCK_URL: http://localhost:8765
        run: make coverage
      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: coverage-{lang}
          path: typecast-{lang}/coverage/
```

`typecast-c` uses a matrix over `ubuntu-latest`, `macos-latest`, `windows-latest` so platform-specific `#ifdef` branches each get covered on their target OS.

## 9. Risks and Mitigations

| # | Risk | Mitigation |
|---|------|------------|
| 1 | Mock server cannot reproduce all 9 SDKs' cases | Validate against js + python in steps 1–2; lock the fixture schema before step 3. |
| 2 | C SDK `#ifdef` branches make 100% impossible on one OS | CI matrix over linux/macos/windows; each runner covers its own branch. The remaining branches use platform pragmas. |
| 3 | Swift branch coverage not measurable | Accepted: line + function 100% only (per Q3). |
| 4 | jacoco class-init synthetic branches | Treated as language-enforced unreachable (whitelist category 2). |
| 5 | 30-line exclusion budget too tight | Treated as starting threshold; revisit after step 1 (js) lands with real numbers. Budget changes require an explicit policy PR, not unilateral SDK PR drift. |
| 6 | Refactors needed for testability cause source-level changes that affect API consumers | Source-level changes must preserve public API. If a private function needs to become package-internal for testing, that is allowed. Public API changes are out of scope and would require their own PR. |
| 7 | Mock server becomes a maintenance burden | Keep it deliberately small (no matchers, no templating). Fixture additions are commits, not code changes. |
| 8 | Native SDKs (c, swift) take much longer than expected | They are scheduled last; an unexpected blocker there does not prevent the other 7 SDKs from being released. |

## 10. Open Questions

None at brainstorming time. The design has been validated through Q1–Q9 with the user. Implementation discoveries may surface follow-up questions per SDK; those are handled in each SDK's PR.

## 11. Appendix: Decision Log

| Q | Decision | Notes |
|---|----------|-------|
| Q1 | Option C — literal 100% with whitelist exclusions | Per-SDK PRs, not best-effort |
| Q2 | Option B — per-SDK branches off `main`, no umbrella | Matches existing repo flow |
| Q3 | Option C — line + function + branch (line+function only on Swift/Go) | "Real" 100% |
| Q4 | Option C — hybrid: per-language mocks for REST, shared server for SSE/WS | Best of both |
| Q5 | Option C — self-hosted CI gate + static shields.io badges | No external service |
| Q6 | Whitelist policy with 30-line/SDK budget | Forbidden categories explicit |
| Q7 | Option D — infra PR first, then easy-to-hard SDKs | 10 PRs total |
| Q8 | Option B — rewrite integration tests on mock server, real-API tests opt-in | E2E out of scope for gate |
| Q9 | Option C — infra PR no release, SDK PRs immediate patch + publish | Matches #10/#11 pattern |
| Mock server lang | Node.js + TypeScript (single dep: tsx) | Universally available in CI |
| Exclusion budget | 30 lines/SDK starting threshold | Revisit after step 1 |
