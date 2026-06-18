# SDK Pause and Multi-Speaker Composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pause markup and `composeSpeech()` multi-speaker composition across Typecast SDKs.

**Architecture:** Each SDK adds a small composer layer beside the existing client. The composer parses `<|{seconds}s|>` tokens, plans speech/pause segments, requests WAV for all internal TTS calls, trims and concatenates PCM WAV audio, and optionally transcodes the final WAV to MP3 with `ffmpeg` on server/desktop runtimes.

**Tech Stack:** Existing SDK test runners and HTTP mocks; no real API key for unit tests. MP3 conversion uses external `ffmpeg` where supported.

---

### Task 1: JavaScript Reference Composer

**Files:**
- Create: `typecast-js/src/composer.ts`
- Modify: `typecast-js/src/client.ts`
- Modify: `typecast-js/src/index.ts`
- Test: `typecast-js/test/unit/composer.test.ts`

- [ ] **Step 1: Write failing tests**

Add tests covering pause token parsing, lenient invalid-token behavior, per-segment override merge, internal WAV requests, WAV concat sample counts, and missing-ffmpeg error.

- [ ] **Step 2: Verify RED**

Run: `cd typecast-js && npm test -- --run test/unit/composer.test.ts`
Expected: FAIL because `composeSpeech` / composer module does not exist.

- [ ] **Step 3: Implement minimal JS composer**

Implement parser, WAV PCM helpers, builder, and `ffmpeg` adapter. Keep `client.textToSpeech` unchanged.

- [ ] **Step 4: Verify GREEN**

Run: `cd typecast-js && npm test -- --run test/unit/composer.test.ts`
Expected: PASS.

- [ ] **Step 5: Run JS coverage gate**

Run: `cd typecast-js && make coverage`
Expected: PASS.

- [ ] **Step 6: Commit JS slice**

Commit message: `feat(js): add composed speech builder`

### Task 2: Python Composer

**Files:**
- Create: `typecast-python/src/typecast/composer.py`
- Modify: `typecast-python/src/typecast/client.py`
- Modify: `typecast-python/src/typecast/__init__.py`
- Test: `typecast-python/tests/unit/test_composer.py`

- [ ] **Step 1: Write failing tests**

Mirror the JS behavior using Python idioms: `client.compose_speech().defaults(...).say(...).pause(...).generate()`.

- [ ] **Step 2: Verify RED**

Run: `cd typecast-python && uv run pytest tests/unit/test_composer.py -v`
Expected: FAIL because composer APIs do not exist.

- [ ] **Step 3: Implement Python composer**

Implement the same parser, WAV helpers, builder, and `ffmpeg` adapter with clear exceptions.

- [ ] **Step 4: Verify GREEN**

Run: `cd typecast-python && uv run pytest tests/unit/test_composer.py -v`
Expected: PASS.

- [ ] **Step 5: Run Python coverage gate**

Run: `cd typecast-python && make coverage`
Expected: PASS.

- [ ] **Step 6: Commit Python slice**

Commit message: `feat(python): add composed speech builder`

### Task 3: Server/Desktop SDK Port

**Files:**
- Go: `typecast-go/composer.go`, `typecast-go/composer_test.go`
- Java: `typecast-java/src/main/java/com/neosapience/SpeechComposer.java`, tests under `typecast-java/src/test`
- Kotlin: `typecast-kotlin/src/main/kotlin/com/neosapience/SpeechComposer.kt`, tests under `typecast-kotlin/src/test`
- C#: `typecast-csharp/src/Typecast/SpeechComposer.cs`, tests under `typecast-csharp/tests/Typecast.Tests`
- Rust: `typecast-rust/src/composer.rs`, `typecast-rust/tests/composer_test.rs`
- PHP: `typecast-php/src/SpeechComposer.php`, `typecast-php/tests/Unit/ComposerTest.php`
- Ruby: `typecast-ruby/lib/typecast/composer.rb`, `typecast-ruby/test/composer_test.rb`
- C: `typecast-c/include/typecast.h`, `typecast-c/src/typecast.c`, `typecast-c/tests/test_composer.c`
- Zig: `typecast-zig/src/composer.zig`, `typecast-zig/tests/composer_test.zig`

- [ ] **Step 1: Port tests first per SDK**

For each SDK, write parser/planner/WAV tests before implementation.

- [ ] **Step 2: Verify RED per SDK**

Run the SDK's focused test command and confirm the missing composer failure.

- [ ] **Step 3: Port implementation per SDK**

Keep public naming idiomatic but behavior identical to JS/Python. Provide `ffmpeg` MP3 conversion for server/desktop SDKs.

- [ ] **Step 4: Verify GREEN per SDK**

Run focused tests and then each SDK coverage gate.

- [ ] **Step 5: Commit per SDK or small SDK group**

Use messages like `feat(go): add composed speech builder`.

### Task 4: Mobile-Oriented SDK Port

**Files:**
- Swift: `typecast-swift/Sources/Typecast/SpeechComposer.swift`, `typecast-swift/Tests/TypecastTests/SpeechComposerTests.swift`
- Dart: `typecast-dart/lib/src/composer.dart`, `typecast-dart/test/composer_test.dart`

- [ ] **Step 1: Write failing tests**

Test WAV composition and MP3 unsupported error without app-provided conversion.

- [ ] **Step 2: Implement mobile composer**

Provide WAV composition. Return a clear error for MP3 composition and document app-level conversion.

- [ ] **Step 3: Verify coverage**

Run `make coverage` in each SDK.

- [ ] **Step 4: Commit mobile slice**

Commit message: `feat(mobile): add wav composed speech builders`

### Task 5: Documentation and Final Verification

**Files:**
- Modify: root `README.md`
- Modify: each SDK `README.md`

- [ ] **Step 1: Document usage**

Add `<|0.3s|>` pause token and `composeSpeech()` usage, with language-specific method names.

- [ ] **Step 2: Document MP3 policy**

Server/desktop SDKs require `ffmpeg` on `PATH`; mobile SDKs require app-level conversion.

- [ ] **Step 3: Run full verification**

Run all feasible `make coverage` commands locally. If any toolchain is unavailable locally, record the exact skipped command and reason.

- [ ] **Step 4: Final status**

Ensure `git status --short` is clean after commits, then prepare PR/update notes.
