# typecast-js 100% Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reach **100% line + function + branch + statement coverage** for `typecast-js/src/**`, enforced by a vitest threshold and a CI gate, then publish a patch release.

**Architecture:** typecast-js currently exposes a thin REST client (`TypecastClient`) and a dedicated error class (`TypecastAPIError`). It does **not** use SSE or WebSockets, so the shared mock server from step 0 is not needed for this SDK — every test mocks the global `fetch` directly via `vi.stubGlobal`. Coverage is measured by `@vitest/coverage-v8` with the source scope narrowed to `src/**/*.ts` (excluding `src/types/**`, which is type-only). The existing real-API integration tests are moved under `test/e2e/` and gated on `TYPECAST_API_KEY` so they don't run in coverage CI.

**Tech Stack:**
- vitest 3.x + `@vitest/coverage-v8`
- Node.js ≥ 16 (existing engine constraint)
- TypeScript 5.7
- `vi.stubGlobal('fetch', ...)` for fetch mocking (no msw / nock needed)
- shields.io static badge

**Spec reference:** `docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md` §3, §4.1 step 1, §6, §7

**Branch:** This plan executes on `feat/coverage-js` cut from `main` (already created).

---

## 0. Current state baseline

Captured before this plan was authored:

| File | Lines | Stmts | Branch | Funcs | Notes |
|---|---|---|---|---|---|
| `src/client.ts` | 153 | 75.75% | 66.66% | 75% | `getVoiceById`, `getVoicesV2(filter)`, `getVoiceV2`, several `textToSpeech` branches, `handleResponse` JSON-parse-fail catch all uncovered |
| `src/errors.ts` | 55 | 62.79% | 22.22% | 100% | Only the 401 case is exercised; 400/402/404/422/500/default and `data.detail` variants all uncovered |
| `src/index.ts` | 3 | 0% | — | — | Re-export barrel; not imported by any test |
| `src/types/**` | 296 | 0% | — | — | Type-only files (interfaces, unions, enums); should be excluded at the tool-config level (whitelist category 3) |
| `examples/simple.ts` | 38 | 0% | — | — | Currently picked up because v8 instruments all `.ts` files; should be excluded by scoping coverage to `src/**/*.ts` (whitelist category 4) |

The `Error.captureStackTrace` conditional in `errors.ts:14` is the only line that needs an inline pragma — V8 always defines it on Node ≥16 so the false branch is unreachable on supported runtimes. Whitelist category 1 (physically unreachable on the test host).

**Existing tests:**

- `test/client.test.ts` — 5 vitest tests covering `textToSpeech` happy path (wav), `getVoices` (with/without model), `getVoicesV2` (no filter), and one 401 error case
- `test/integration.tts.test.ts` — real API call, requires `TYPECAST_API_KEY`, writes audio to disk
- `test/integration.voices.test.ts` — real API call, validates voice schema and 422 for unknown model

**Out of scope for this PR:** SSE/WS support (typecast-js has none), real-API E2E running (kept opt-in, not part of the gate), README rewrites unrelated to the badge.

---

## 1. File structure after this plan

```text
typecast-js/
  src/                                   (unchanged source)
    client.ts
    errors.ts                            (1 inline pragma added)
    index.ts
    types/                               (unchanged; excluded from coverage)
  test/
    unit/                                (NEW directory)
      client.test.ts                     (moved + expanded from test/client.test.ts)
      errors.test.ts                     (NEW — exhaustive switch coverage)
      index.test.ts                      (NEW — barrel re-export smoke test)
    e2e/                                 (NEW directory)
      tts.test.ts                        (moved from test/integration.tts.test.ts, env-guarded)
      voices.test.ts                     (moved from test/integration.voices.test.ts, env-guarded)
  vite.config.ts                         (coverage config + thresholds)
  Makefile                               (NEW — `make coverage` target)
  package.json                           (devDep + script tweaks)
  README.md                              (badge added near the top)
.github/workflows/
  coverage-js.yml                        (NEW — paths-filtered to typecast-js/)
```

**Why this layout:**

- `test/unit/` is what the coverage gate measures. Vitest's default test glob (`**/*.{test,spec}.?(c|m)[jt]s?(x)`) discovers it without any config change.
- `test/e2e/` is excluded from the unit run via `--exclude` so no test in it can reach the production API on a CI runner without a key. The e2e files remain runnable on demand by developers who have a key.
- The existing `test/client.test.ts` is moved (not deleted) so git history follows the rename.
- The existing `test/integration.*.test.ts` files are moved into `test/e2e/` and renamed to drop the redundant `integration.` prefix.

---

## 2. Coverage targets (recap of the policy applied to this SDK)

- **Lines: 100%**
- **Functions: 100%**
- **Branches: 100%**
- **Statements: 100%**
- **Excluded files (tool config, not inline pragmas):** `src/types/**`, `examples/**`, `test/**`, `lib/**`, `node_modules/**`
- **Inline pragma budget for typecast-js:** **1 line** (the `Error.captureStackTrace` V8 guard)
- **Forbidden exclusions reminder:** no HTTP/network/timeout/auth/input-validation paths may carry pragmas; they must be tested

---

## Tasks

### Task 1: Add coverage tooling and bare vitest config (no threshold yet)

**Files:**
- Modify: `typecast-js/package.json`
- Modify: `typecast-js/vite.config.ts`

This task adds the dev dependency and the coverage configuration scaffolding **without** turning on the 100% threshold. We turn the threshold on later (Task 9), once the test suite actually reaches 100%. Doing it the other way around would make CI red until the very last task.

- [ ] **Step 1: Verify branch**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git branch --show-current
```

Expected: `feat/coverage-js`. If not, stop and report `BLOCKED`.

- [ ] **Step 2: Read current `package.json` then add the dev dep + script**

Add `"@vitest/coverage-v8": "^3.0.9"` to `devDependencies` (matching the existing vitest version) and add a new `"coverage"` script. The existing `test:coverage` script is left in place for compatibility.

After this step, the relevant slice of `typecast-js/package.json` should look like:

```json
  "scripts": {
    "build": "tsup",
    "test": "vitest",
    "test:coverage": "vitest run --coverage",
    "coverage": "vitest run --coverage --exclude 'test/e2e/**'",
    "test:e2e": "vitest run test/e2e",
    "test:package": "bash scripts/test-package.sh",
    ...
  },
  "devDependencies": {
    "@types/node": "^22.13.5",
    "@typescript-eslint/eslint-plugin": "^7.3.1",
    "@typescript-eslint/parser": "^7.3.1",
    "@vitest/coverage-v8": "^3.0.9",
    "dotenv": "^16.4.7",
    "eslint": "^8.57.0",
    "prettier": "^3.2.5",
    "tsup": "^8.4.0",
    "typescript": "^5.7.3",
    "vitest": "^3.0.9"
  },
```

Use the Edit tool with the existing `package.json` text. The exact lines to change:

1. After `"test:coverage": "vitest run --coverage",` insert:

   ```json
       "coverage": "vitest run --coverage --exclude 'test/e2e/**'",
       "test:e2e": "vitest run test/e2e",
   ```

2. In `devDependencies`, after `"@typescript-eslint/parser": "^7.3.1",` insert `"@vitest/coverage-v8": "^3.0.9",`.

- [ ] **Step 3: Replace `vite.config.ts` with the coverage-aware version**

Overwrite `typecast-js/vite.config.ts` with:

```typescript
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: true,
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.ts'],
      exclude: [
        'src/types/**',
        'src/**/*.d.ts',
      ],
      // thresholds will be enabled in a later task once coverage actually
      // reaches 100% — turning them on now would block every interim commit.
    },
  },
});
```

- [ ] **Step 4: Run npm install**

```bash
cd typecast-js && npm install
```

Expected: `@vitest/coverage-v8` is added to `node_modules`, `package-lock.json` is updated, no errors.

- [ ] **Step 5: Sanity check — coverage runs and reports the same baseline**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -25
```

Expected: prints a coverage table. Because `src/types/**` is now excluded and `examples/` is no longer in scope, the totals should be **higher** than the 56.66% baseline — roughly 70%+ for src files. The exact number doesn't matter at this step; what matters is that the table includes only `client.ts`, `errors.ts`, and `index.ts` under `src/`, and **none** of the type or example files.

- [ ] **Step 6: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/package.json typecast-js/package-lock.json typecast-js/vite.config.ts
git commit -m "test(js): add @vitest/coverage-v8 and scoped coverage config

Adds the dev dependency and a vitest coverage block scoped to
src/**/*.ts (excluding the type-only src/types/** files and the
already-excluded examples/). Also adds an npm script that runs the
coverage suite while excluding the e2e tests that need a real API key.
Threshold enforcement is intentionally deferred to a later commit
once coverage actually reaches 100%."
```

---

### Task 2: Move integration tests to `test/e2e/` and add API-key skip guard

**Files:**
- Move: `typecast-js/test/integration.tts.test.ts` → `typecast-js/test/e2e/tts.test.ts`
- Move: `typecast-js/test/integration.voices.test.ts` → `typecast-js/test/e2e/voices.test.ts`
- Modify: contents of both moved files to add `describe.skipIf` guard

The existing integration tests hit the real Typecast API and need `TYPECAST_API_KEY`. We keep them as opt-in e2e tests but move them out of the default coverage run path and gate execution on the env var so a developer without a key gets a clean skip rather than a 401 failure.

- [ ] **Step 1: Create the e2e directory and move the files**

```bash
cd typecast-js
mkdir -p test/e2e
git mv test/integration.tts.test.ts test/e2e/tts.test.ts
git mv test/integration.voices.test.ts test/e2e/voices.test.ts
```

Expected: `git status` shows two renames, no other changes.

- [ ] **Step 2: Replace `test/e2e/tts.test.ts` body with the env-guarded version**

Overwrite `typecast-js/test/e2e/tts.test.ts` with:

```typescript
import { describe, it, expect, beforeEach } from 'vitest';
import dotenv from 'dotenv';
import { TypecastClient } from '../../src/client.js';
import { TTSModel } from '../../src/types/TextToSpeech.js';
import fs from 'fs';

dotenv.config();

const hasApiKey = Boolean(process.env.TYPECAST_API_KEY);

describe.skipIf(!hasApiKey)('TypecastClient e2e: textToSpeech', () => {
  let client: TypecastClient;

  beforeEach(() => {
    client = new TypecastClient();
  });

  it('should convert text to speech with real API', async () => {
    const voices = await client.getVoices();
    const voice = voices.filter((voice) => voice.model === 'ssfm-v21')[0];

    const request = {
      text: 'Hello, how are you?',
      voice_id: voice.voice_id,
      model: voice.model as TTSModel,
      prompt: {
        emotion_preset: 'normal' as const,
        emotion_intensity: 1.0,
      },
      output: {
        audio_format: 'wav' as const,
        audio_tempo: 1.0,
      },
    };

    const response = await client.textToSpeech(request);

    expect(response.format).toBe('wav');
    expect(response.audioData).toBeInstanceOf(ArrayBuffer);
    expect(response.audioData.byteLength).toBeGreaterThan(0);

    const outputPath = './test-output.wav';
    await fs.promises.writeFile(outputPath, Buffer.from(response.audioData));
    expect(fs.existsSync(outputPath)).toBe(true);
  }, 30000);
});
```

Two changes vs the original:
1. `describe.skipIf(!hasApiKey)` so the suite is skipped (not failed) when no key.
2. `audioData` is checked with `toBeInstanceOf(ArrayBuffer)` instead of `Buffer` — the SDK actually returns `ArrayBuffer` per `client.ts:90`. The original test was wrong but never ran in CI so the bug went unnoticed.

- [ ] **Step 3: Replace `test/e2e/voices.test.ts` body with the env-guarded version**

Overwrite `typecast-js/test/e2e/voices.test.ts` with:

```typescript
import { describe, it, expect, beforeEach } from 'vitest';
import dotenv from 'dotenv';
import { TypecastClient } from '../../src/client.js';
import { TypecastAPIError } from '../../src/errors.js';

dotenv.config();

const hasApiKey = Boolean(process.env.TYPECAST_API_KEY);

describe.skipIf(!hasApiKey)('TypecastClient e2e: voices', () => {
  let client: TypecastClient;

  beforeEach(() => {
    client = new TypecastClient();
  });

  it('should return voices with the expected schema', async () => {
    const voices = await client.getVoices();
    expect(voices).toBeDefined();
    expect(Array.isArray(voices)).toBe(true);
    expect(voices.length).toBeGreaterThan(0);

    const voice = voices[0];
    expect(voice).toHaveProperty('voice_name');
    expect(voice).toHaveProperty('voice_id');
    expect(voice).toHaveProperty('model');
    expect(voice).toHaveProperty('emotions');
  }, 30000);

  it('should filter voices by model', async () => {
    const targetModel = 'ssfm-v21';
    const voices = await client.getVoices(targetModel);

    expect(voices.length).toBeGreaterThan(0);
    voices.forEach((voice) => {
      expect(voice.model).toBe(targetModel);
    });
  }, 30000);

  it('should throw TypecastAPIError for non-existent model', async () => {
    try {
      await client.getVoices('non-existent-model');
      expect.fail('Expected an error to be thrown');
    } catch (error: unknown) {
      expect(error).toBeInstanceOf(TypecastAPIError);
      if (error instanceof TypecastAPIError) {
        expect(error.statusCode).toBe(422);
      }
    }
  }, 30000);
});
```

The original `voices.test.ts` had two near-duplicate `getVoices()` tests; we consolidate into one schema-check test. The 422 error test stays.

- [ ] **Step 4: Verify the unit run no longer picks up the e2e files**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -15
```

Expected:
- `Test Files  1 passed (1)` — only `test/client.test.ts` runs
- E2E files are not listed
- No 401/network errors

- [ ] **Step 5: Verify the e2e run is skipped without an API key**

```bash
cd typecast-js && unset TYPECAST_API_KEY && npm run test:e2e 2>&1 | tail -20
```

Expected: `Test Files  2 skipped (2)` (or similar wording — vitest reports skipped suites as skipped). No real API call is made.

- [ ] **Step 6: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add -A typecast-js/test/
git commit -m "test(js): move integration tests to test/e2e and gate on API key

The integration suites hit the real Typecast API and were failing in
unit runs because no key was present. Move them under test/e2e/ so
the default coverage run no longer picks them up, add a
describe.skipIf(!process.env.TYPECAST_API_KEY) guard so contributors
without a key get a clean skip, and fix the audioData assertion
(arrayBuffer, not Buffer) that was wrong in the original."
```

---

### Task 3: Expand `client.test.ts` for `textToSpeech` full coverage (TDD)

**Files:**
- Move: `typecast-js/test/client.test.ts` → `typecast-js/test/unit/client.test.ts`
- Modify: contents

This is the largest test task. We move the existing client test into `test/unit/`, keep the existing 5 cases, and add cases that exercise every branch of `textToSpeech`. After this task, `textToSpeech` is at 100%.

- [ ] **Step 1: Move the file to the new directory**

```bash
cd typecast-js
mkdir -p test/unit
git mv test/client.test.ts test/unit/client.test.ts
```

- [ ] **Step 2: Replace the file body with the expanded suite**

Overwrite `typecast-js/test/unit/client.test.ts` with the following. This replaces the old 5-test file with a structured suite. The old top-level imports and the existing 5 tests are preserved (the existing tests are inside the new `describe('textToSpeech')` and `describe('voices V1')` and `describe('voices V2')` blocks).

```typescript
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TypecastClient } from '../../src/client';
import { TypecastAPIError } from '../../src/errors';
import { TTSModel } from '../../src/types/TextToSpeech';

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

const baseRequest = {
  text: 'Hello',
  voice_id: 'tc_mock_001',
  model: 'ssfm-v21' as TTSModel,
  language: 'eng' as const,
  seed: 12345,
  prompt: {
    emotion_preset: 'normal' as const,
    emotion_intensity: 1.0,
  },
  output: {
    volume: 100,
    audio_pitch: 0,
    audio_tempo: 1.0,
    audio_format: 'wav' as const,
  },
};

describe('TypecastClient', () => {
  let client: TypecastClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new TypecastClient({
      baseHost: 'https://dummy-api.ai',
      apiKey: 'test-api-key',
    });
  });

  describe('textToSpeech', () => {
    it('returns wav audio with duration from headers', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({
          'x-audio-duration': '1.5',
          'content-type': 'audio/wav',
        }),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(16)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.duration).toBe(1.5);
      expect(response.format).toBe('wav');
      expect(response.audioData.byteLength).toBe(16);
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/text-to-speech',
        expect.objectContaining({
          method: 'POST',
          headers: {
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(baseRequest),
        }),
      );
    });

    it('returns mp3 format when content-type header says audio/mp3', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({
          'x-audio-duration': '2.0',
          'content-type': 'audio/mp3',
        }),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(8)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.format).toBe('mp3');
      expect(response.duration).toBe(2.0);
    });

    it('falls back to wav and duration 0 when headers are missing', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(4)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.format).toBe('wav');
      expect(response.duration).toBe(0);
    });

    it('falls back to wav when content-type has no slash subtype', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'content-type': 'audio' }),
        arrayBuffer: () => Promise.resolve(new ArrayBuffer(4)),
      });

      const response = await client.textToSpeech(baseRequest);

      expect(response.format).toBe('wav');
    });

    it('throws TypecastAPIError when the API returns a JSON error body', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 422,
        statusText: 'Unprocessable Entity',
        json: () =>
          Promise.resolve({ detail: 'voice_id is required' }),
      });

      await expect(client.textToSpeech(baseRequest)).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 422,
      });
    });

    it('throws TypecastAPIError when the error body is not JSON', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new SyntaxError('not json')),
      });

      await expect(client.textToSpeech(baseRequest)).rejects.toBeInstanceOf(
        TypecastAPIError,
      );
    });
  });
});
```

The test set covers:
- Happy path with explicit `content-type: audio/wav` and `x-audio-duration` (existing case)
- mp3 content-type branch (`format === 'mp3'`)
- Missing headers fallback (`format === 'wav'` default, `duration === 0`)
- Content-type without `/` (covers `formatFromHeader = ''` → `'wav'` branch)
- Error body that **is** JSON (covers the `json()` success path inside the catch's else)
- Error body that **is not** JSON (covers the `catch` block at `client.ts:71-75`)

- [ ] **Step 3: Run the new tests**

```bash
cd typecast-js && npm test -- --run test/unit/client.test.ts 2>&1 | tail -20
```

Expected: 6 passing tests, 0 failing.

- [ ] **Step 4: Run coverage to confirm `textToSpeech` is now 100%**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -20
```

Expected: `client.ts` lines/branches/functions all rise. `textToSpeech` no longer appears in the "Uncovered Line #s" column for that function. `getVoiceById`, `getVoiceV2`, `handleResponse` JSON-parse-fail catch are still uncovered — those land in Tasks 4 and 5.

- [ ] **Step 5: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add -A typecast-js/test/unit/client.test.ts
git commit -m "test(js): expand textToSpeech tests to full branch coverage

Move test/client.test.ts into test/unit/, restructure into describe
blocks, and add cases for: mp3 content-type, missing audio headers,
content-type without a subtype, API error with JSON body, and API
error whose body fails to parse as JSON. Brings textToSpeech to
100% line + branch + function coverage."
```

---

### Task 4: Add `client.test.ts` cases for the four voice methods

**Files:**
- Modify: `typecast-js/test/unit/client.test.ts`

After Task 3 the file has a `describe('textToSpeech')` block. We append two more describe blocks for the V1 and V2 voice methods. After this task, `getVoices`, `getVoiceById`, `getVoicesV2`, and `getVoiceV2` are all at 100%.

- [ ] **Step 1: Append the voices V1 describe block**

In `typecast-js/test/unit/client.test.ts`, add the following block immediately after the closing `});` of the `describe('textToSpeech', ...)` block (before the outer `});` that closes `describe('TypecastClient')`).

```typescript
  describe('voices V1', () => {
    it('getVoices returns the array on success', async () => {
      const mockVoices = [
        { voice_id: 'voice1', voice_name: 'Voice 1', model: 'ssfm-v21', emotions: ['normal', 'happy'] },
        { voice_id: 'voice2', voice_name: 'Voice 2', model: 'ssfm-v30', emotions: ['normal', 'sad'] },
      ];
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVoices),
      });

      const voices = await client.getVoices();

      expect(voices).toHaveLength(2);
      expect(voices[0].voice_id).toBe('voice1');
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices',
        expect.objectContaining({
          headers: {
            'X-API-KEY': 'test-api-key',
            'Content-Type': 'application/json',
          },
        }),
      );
    });

    it('getVoices forwards the model query parameter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.getVoices('ssfm-v21');

      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices?model=ssfm-v21',
        expect.anything(),
      );
    });

    it('getVoiceById hits the by-id endpoint', async () => {
      const single = [
        { voice_id: 'tc_001', voice_name: 'Voice 1', model: 'ssfm-v21', emotions: ['normal'] },
      ];
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(single),
      });

      const result = await client.getVoiceById('tc_001');

      expect(result).toEqual(single);
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices/tc_001',
        expect.anything(),
      );
    });

    it('getVoiceById forwards the model query parameter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.getVoiceById('tc_001', 'ssfm-v30');

      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v1/voices/tc_001?model=ssfm-v30',
        expect.anything(),
      );
    });

    it('getVoices propagates a JSON error response as TypecastAPIError', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 401,
        statusText: 'Unauthorized',
        json: () => Promise.resolve({ message: 'Invalid API key' }),
      });

      await expect(client.getVoices()).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 401,
      });
    });

    it('getVoices propagates a non-JSON error body as TypecastAPIError', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        json: () => Promise.reject(new SyntaxError('not json')),
      });

      await expect(client.getVoices()).rejects.toBeInstanceOf(TypecastAPIError);
    });
  });
```

The two error tests at the end specifically cover the `handleResponse` private method's `try { errorData = await response.json() } catch {}` block — one test where `json()` resolves, one where it rejects.

- [ ] **Step 2: Append the voices V2 describe block**

Immediately after the V1 describe block, add:

```typescript
  describe('voices V2', () => {
    const mockVoiceV2 = {
      voice_id: 'tc_v2_001',
      voice_name: 'V2 Voice',
      models: [{ version: 'ssfm-v30', emotions: ['normal', 'happy'] }],
      gender: 'female',
      age: 'young_adult',
      use_cases: ['Audiobook'],
    };

    it('getVoicesV2 returns the array on success without filter', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([mockVoiceV2]),
      });

      const voices = await client.getVoicesV2();

      expect(voices).toHaveLength(1);
      expect(voices[0].voice_id).toBe('tc_v2_001');
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v2/voices',
        expect.anything(),
      );
    });

    it('getVoicesV2 forwards filter parameters in the query string', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await client.getVoicesV2({
        model: 'ssfm-v30',
        gender: 'female',
        age: 'young_adult',
        use_cases: 'Audiobook',
      });

      const [calledUrl] = mockFetch.mock.calls[0];
      const url = new URL(calledUrl as string);
      expect(url.pathname).toBe('/v2/voices');
      expect(url.searchParams.get('model')).toBe('ssfm-v30');
      expect(url.searchParams.get('gender')).toBe('female');
      expect(url.searchParams.get('age')).toBe('young_adult');
      expect(url.searchParams.get('use_cases')).toBe('Audiobook');
    });

    it('getVoiceV2 hits the by-id endpoint and returns the single voice', async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve(mockVoiceV2),
      });

      const voice = await client.getVoiceV2('tc_v2_001');

      expect(voice.voice_id).toBe('tc_v2_001');
      expect(mockFetch).toHaveBeenCalledWith(
        'https://dummy-api.ai/v2/voices/tc_v2_001',
        expect.anything(),
      );
    });

    it('getVoiceV2 throws TypecastAPIError on 404', async () => {
      mockFetch.mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        json: () => Promise.resolve({ detail: 'voice not found' }),
      });

      await expect(client.getVoiceV2('tc_unknown')).rejects.toMatchObject({
        name: 'TypecastAPIError',
        statusCode: 404,
      });
    });
  });
```

- [ ] **Step 3: Run the new tests**

```bash
cd typecast-js && npm test -- --run test/unit/client.test.ts 2>&1 | tail -10
```

Expected: 16 passing tests total (6 textToSpeech + 6 V1 voices + 4 V2 voices), 0 failing.

- [ ] **Step 4: Coverage check**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -15
```

Expected: `client.ts` is now at or very close to 100% lines/branches/functions. The constructor's env var fallback may still be uncovered (that's Task 5). `index.ts` and `errors.ts` are still under-covered.

- [ ] **Step 5: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/test/unit/client.test.ts
git commit -m "test(js): cover all four voice methods at the unit level

Adds tests for getVoices (with/without model + JSON and non-JSON
error bodies), getVoiceById (with/without model), getVoicesV2
(with/without filter), and getVoiceV2 (success and 404). Drives
client.ts toward full branch coverage and exercises both branches
of the handleResponse private method's error-body parsing."
```

---

### Task 5: Cover the constructor env-var fallback

**Files:**
- Modify: `typecast-js/test/unit/client.test.ts`

The `TypecastClient` constructor reads `process.env.TYPECAST_API_HOST` and `process.env.TYPECAST_API_KEY` as defaults. None of the existing tests exercise that path. We use `vi.stubEnv` to set them.

- [ ] **Step 1: Append a constructor describe block**

In `typecast-js/test/unit/client.test.ts`, add the following block immediately after the V2 describe block (still inside the outer `describe('TypecastClient')`):

```typescript
  describe('constructor defaults', () => {
    afterEach(() => {
      vi.unstubAllEnvs();
    });

    it('falls back to env vars when no config is provided', async () => {
      vi.stubEnv('TYPECAST_API_HOST', 'https://env-host.example');
      vi.stubEnv('TYPECAST_API_KEY', 'env-api-key');

      const envClient = new TypecastClient();
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await envClient.getVoices();

      expect(mockFetch).toHaveBeenCalledWith(
        'https://env-host.example/v1/voices',
        expect.objectContaining({
          headers: {
            'X-API-KEY': 'env-api-key',
            'Content-Type': 'application/json',
          },
        }),
      );
    });

    it('falls back to the production host when no env var is set', async () => {
      vi.stubEnv('TYPECAST_API_HOST', '');
      vi.stubEnv('TYPECAST_API_KEY', '');

      const envClient = new TypecastClient();
      mockFetch.mockResolvedValue({
        ok: true,
        status: 200,
        json: () => Promise.resolve([]),
      });

      await envClient.getVoices();

      const [calledUrl] = mockFetch.mock.calls[0];
      expect(calledUrl).toBe('https://api.typecast.ai/v1/voices');
    });
  });
```

The first test exercises `process.env.TYPECAST_API_HOST || 'https://api.typecast.ai'` truthy branch and the API key truthy branch. The second test exercises both falsy branches (empty string is falsy in JS, so `||` selects the literal default).

- [ ] **Step 2: Run the tests**

```bash
cd typecast-js && npm test -- --run test/unit/client.test.ts 2>&1 | tail -10
```

Expected: 18 passing tests (16 + 2 new), 0 failing.

- [ ] **Step 3: Coverage check**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -15
```

Expected: `client.ts` is now at **100%** lines/branches/functions. `errors.ts` and `index.ts` are still uncovered.

- [ ] **Step 4: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/test/unit/client.test.ts
git commit -m "test(js): cover the TypecastClient constructor env-var fallback

Adds two cases under a 'constructor defaults' describe: one with
TYPECAST_API_HOST and TYPECAST_API_KEY both set in the environment,
and one with both empty so the literal production-host fallback is
exercised. Brings client.ts to 100% line/branch/function coverage."
```

---

### Task 6: Add `errors.test.ts` covering every switch branch and detail variant

**Files:**
- Create: `typecast-js/test/unit/errors.test.ts`

`TypecastAPIError.fromResponse` has a 7-case switch (400, 401, 402, 404, 422, 500, default) and a `data?.detail` block with three sub-paths (no detail, string detail, object detail). All 10 paths need a test.

- [ ] **Step 1: Write the file**

Create `typecast-js/test/unit/errors.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import { TypecastAPIError } from '../../src/errors';

describe('TypecastAPIError', () => {
  describe('fromResponse status mapping', () => {
    const cases: Array<[number, string, RegExp]> = [
      [400, 'Bad Request', /Bad Request/],
      [401, 'Unauthorized', /Unauthorized - Invalid or missing API key/],
      [402, 'Payment Required', /Payment Required/],
      [404, 'Not Found', /Not Found/],
      [422, 'Unprocessable Entity', /Validation Error/],
      [500, 'Internal Server Error', /Internal Server Error/],
    ];

    for (const [status, statusText, expectedMessage] of cases) {
      it(`maps status ${status} to a friendly message`, () => {
        const err = TypecastAPIError.fromResponse(status, statusText);

        expect(err).toBeInstanceOf(TypecastAPIError);
        expect(err.statusCode).toBe(status);
        expect(err.message).toMatch(expectedMessage);
        expect(err.name).toBe('TypecastAPIError');
      });
    }

    it('falls back to a generic message for unknown status codes', () => {
      const err = TypecastAPIError.fromResponse(418, "I'm a teapot");

      expect(err.statusCode).toBe(418);
      expect(err.message).toBe(
        "API request failed with status 418: I'm a teapot",
      );
    });
  });

  describe('fromResponse detail handling', () => {
    it('appends a string detail to the message', () => {
      const err = TypecastAPIError.fromResponse(422, 'Unprocessable Entity', {
        detail: 'voice_id is required',
      });

      expect(err.message).toMatch(/Validation Error/);
      expect(err.message).toMatch(/voice_id is required$/);
    });

    it('JSON-stringifies a non-string detail', () => {
      const detail = [{ loc: ['body', 'voice_id'], msg: 'field required', type: 'value_error.missing' }];
      const err = TypecastAPIError.fromResponse(422, 'Unprocessable Entity', { detail });

      expect(err.message).toMatch(/Validation Error/);
      expect(err.message).toMatch(/voice_id/);
      expect(err.message).toMatch(/field required/);
    });

    it('omits the detail suffix when no detail is provided', () => {
      const err = TypecastAPIError.fromResponse(500, 'Internal Server Error', {});

      expect(err.message).toBe(
        'Internal Server Error - Something went wrong on the server',
      );
    });

    it('omits the detail suffix when data is undefined', () => {
      const err = TypecastAPIError.fromResponse(500, 'Internal Server Error');

      expect(err.message).toBe(
        'Internal Server Error - Something went wrong on the server',
      );
    });
  });

  describe('constructor', () => {
    it('exposes statusCode and response on the instance', () => {
      const data = { detail: 'something went wrong' };
      const err = new TypecastAPIError('boom', 503, data);

      expect(err.message).toBe('boom');
      expect(err.statusCode).toBe(503);
      expect(err.response).toBe(data);
      expect(err.name).toBe('TypecastAPIError');
      expect(err.stack).toContain('TypecastAPIError');
    });

    it('does not require a response payload', () => {
      const err = new TypecastAPIError('boom', 500);

      expect(err.statusCode).toBe(500);
      expect(err.response).toBeUndefined();
    });
  });
});
```

- [ ] **Step 2: Run the new tests**

```bash
cd typecast-js && npm test -- --run test/unit/errors.test.ts 2>&1 | tail -15
```

Expected: 12 passing tests (6 status mappings + 1 default + 4 detail + 2 constructor), 0 failing.

- [ ] **Step 3: Coverage check**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -15
```

Expected: `errors.ts` is now at 100% lines + functions and very high branch coverage. The only remaining uncovered branch is the `if (Error.captureStackTrace)` false branch on line 14, which is unreachable on Node and gets a pragma in Task 8. `index.ts` is still 0%.

- [ ] **Step 4: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/test/unit/errors.test.ts
git commit -m "test(js): add TypecastAPIError unit tests covering every branch

Exhaustive coverage of fromResponse status mapping (all 6 enumerated
codes + the default), the three data.detail handling branches
(string, non-string-stringified, and missing/undefined), and the
constructor (with and without a response payload). Brings errors.ts
to full line and function coverage; the only remaining uncovered
branch is the V8-only Error.captureStackTrace guard, handled by
an inline pragma in a later task."
```

---

### Task 7: Add `index.test.ts` to mark the barrel re-export as covered

**Files:**
- Create: `typecast-js/test/unit/index.test.ts`

`src/index.ts` is three lines of `export { ... }` / `export * from ...` declarations. v8 reports it as 0% until something imports it. A trivial smoke test that imports the barrel and asserts the public surface gets it to 100% in one test.

- [ ] **Step 1: Write the file**

Create `typecast-js/test/unit/index.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import * as pkg from '../../src/index';

describe('package barrel', () => {
  it('re-exports TypecastClient and TypecastAPIError', () => {
    expect(pkg.TypecastClient).toBeDefined();
    expect(typeof pkg.TypecastClient).toBe('function');
    expect(pkg.TypecastAPIError).toBeDefined();
    expect(typeof pkg.TypecastAPIError).toBe('function');
  });

  it('TypecastClient from the barrel constructs the same shape as a direct import', () => {
    const instance = new pkg.TypecastClient({
      baseHost: 'https://example.test',
      apiKey: 'k',
    });
    expect(instance).toBeInstanceOf(pkg.TypecastClient);
  });
});
```

- [ ] **Step 2: Run the new tests**

```bash
cd typecast-js && npm test -- --run test/unit/index.test.ts 2>&1 | tail -10
```

Expected: 2 passing tests, 0 failing.

- [ ] **Step 3: Coverage check**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -20
```

Expected: `index.ts` is now 100%. `client.ts` is 100%. `errors.ts` is 100% lines/functions and ~95% branches (only the captureStackTrace false branch left). The "All files" totals are very close to 100%.

- [ ] **Step 4: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/test/unit/index.test.ts
git commit -m "test(js): cover the barrel re-export with a smoke test

src/index.ts is three pure re-export statements. A smoke test that
imports the barrel and verifies TypecastClient and TypecastAPIError
are exposed brings it to 100% coverage with no production code
change."
```

---

### Task 8: Add the inline pragma for `Error.captureStackTrace` and verify pre-threshold coverage

**Files:**
- Modify: `typecast-js/src/errors.ts`

The only remaining uncovered branch in the entire SDK is the false side of `if (Error.captureStackTrace)` in `errors.ts:14`. Since `Error.captureStackTrace` is a V8 built-in always present on Node ≥16, the false branch is unreachable on every supported runtime — whitelist category 1 (physically unreachable on the test host).

We use the v8 / istanbul `c8 ignore` hint syntax. Vitest's v8 provider honors `/* v8 ignore */` and `/* c8 ignore */` comments.

- [ ] **Step 1: Read the existing `errors.ts`**

```bash
cat typecast-js/src/errors.ts | head -20
```

You should see lines 13-16 as:

```typescript
    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, TypecastAPIError);
    }
```

- [ ] **Step 2: Add the pragma comment**

Use the Edit tool to replace those four lines with the same code plus a c8-ignore-next comment annotated per the policy:

```typescript
    // Maintains proper stack trace for where our error was thrown (only available on V8)
    /* v8 ignore next 3 -- category=platform reason="Error.captureStackTrace is V8-only and is always defined on Node >=16; the false branch is unreachable on supported runtimes" */
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, TypecastAPIError);
    }
```

The exact `old_string` for the Edit tool should be:

```text
    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, TypecastAPIError);
    }
```

And the `new_string`:

```text
    // Maintains proper stack trace for where our error was thrown (only available on V8)
    /* v8 ignore next 3 -- category=platform reason="Error.captureStackTrace is V8-only and is always defined on Node >=16; the false branch is unreachable on supported runtimes" */
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, TypecastAPIError);
    }
```

- [ ] **Step 3: Run coverage and confirm 100% across the board**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -20
```

Expected: every column for every file under `src/` reports 100%. The "All files" line should read `100 | 100 | 100 | 100`. If any cell is below 100%, stop and report `BLOCKED` with which file/branch is still uncovered — that means a test is missing and should not be papered over with a pragma.

- [ ] **Step 4: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/src/errors.ts
git commit -m "test(js): annotate Error.captureStackTrace V8 guard with v8 ignore

The Error.captureStackTrace conditional has a false branch that is
unreachable on every supported Node runtime (the symbol has been a
V8 built-in since Node 0.x and we require Node >=16). Tag the
conditional with a v8 ignore comment using the policy's
category=platform format so v8 coverage stops counting it.

After this commit, src/ reports 100% line / branch / function /
statement coverage."
```

---

### Task 9: Enable the 100% coverage threshold

**Files:**
- Modify: `typecast-js/vite.config.ts`

Now that the suite genuinely reports 100%, we make the threshold mandatory so any future regression turns the build red.

- [ ] **Step 1: Add the thresholds block**

Use Edit to extend `typecast-js/vite.config.ts`. Replace:

```typescript
      include: ['src/**/*.ts'],
      exclude: [
        'src/types/**',
        'src/**/*.d.ts',
      ],
      // thresholds will be enabled in a later task once coverage actually
      // reaches 100% — turning them on now would block every interim commit.
    },
```

with:

```typescript
      include: ['src/**/*.ts'],
      exclude: [
        'src/types/**',
        'src/**/*.d.ts',
      ],
      thresholds: {
        lines: 100,
        functions: 100,
        branches: 100,
        statements: 100,
      },
    },
```

- [ ] **Step 2: Run coverage to confirm the threshold passes**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -25
```

Expected: the table reports 100% everywhere AND the run exits with code 0 (no `ERROR: Coverage for ... does not meet threshold`).

- [ ] **Step 3: Sanity-check that the threshold actually fires**

Temporarily comment out one of the new tests (any of the errors.test.ts switch cases works) and rerun coverage. The run should fail. Restore the test and confirm coverage passes again. **This step does not produce a commit; it's a manual confirmation that the gate is wired correctly.** If you skip this step, you risk shipping a paper threshold that silently passes everything.

```bash
# Demonstration only — the comment-out + rerun + restore is left to the
# implementer. If you cannot easily verify the gate fires, escalate.
```

- [ ] **Step 4: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/vite.config.ts
git commit -m "test(js): enforce 100% coverage threshold in vitest config

Now that the unit suite reaches 100% lines/functions/branches/
statements, lock the threshold in vitest.config.ts so any future
regression fails the build. The threshold is enforced by vitest's
coverage tool itself; CI just runs make coverage and trusts the
exit code (matches the policy in docs/coverage-policy.md)."
```

---

### Task 10: Add `Makefile` with `coverage` target

**Files:**
- Create: `typecast-js/Makefile`

The CI workflow template in step 0 invokes `make coverage`. Add a small Makefile that wraps `npm run coverage` so the standard target works.

- [ ] **Step 1: Create the Makefile**

Create `typecast-js/Makefile`:

```makefile
.PHONY: help install test coverage e2e clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Install dependencies
	npm install

test: ## Run unit tests
	npm test -- --run

coverage: ## Run unit tests with 100% coverage gate
	npm run coverage

e2e: ## Run e2e tests (requires TYPECAST_API_KEY)
	npm run test:e2e

clean: ## Remove build artifacts
	rm -rf lib coverage
```

- [ ] **Step 2: Verify make coverage works**

```bash
cd typecast-js && make coverage 2>&1 | tail -20
```

Expected: same output as `npm run coverage`, exit 0.

- [ ] **Step 3: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/Makefile
git commit -m "build(js): add Makefile with coverage target

Adds a thin Makefile that wraps the npm scripts so the standard
'make coverage' invocation from the per-SDK CI workflow works.
Targets: install, test, coverage, e2e, clean, help."
```

---

### Task 11: Add the GitHub Actions coverage workflow

**Files:**
- Create: `.github/workflows/coverage-js.yml`

Copy the reference template from `.github/workflows/coverage-template.yml`, customize for typecast-js. Note: typecast-js doesn't need the mock server (no SSE/WS), but the template still starts it. We **keep** the mock server step so the workflow shape is identical across SDKs — the running server adds maybe 2 seconds and zero behavioral risk. Removing it would be a premature optimization.

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/coverage-js.yml`:

```yaml
name: coverage-js

on:
  push:
    branches: [main]
    paths:
      - 'typecast-js/**'
      - 'test-fixtures/mock-server/**'
      - '.github/workflows/coverage-js.yml'
  pull_request:
    paths:
      - 'typecast-js/**'
      - 'test-fixtures/mock-server/**'
      - '.github/workflows/coverage-js.yml'

jobs:
  coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Install mock server deps
        working-directory: test-fixtures/mock-server
        run: npm ci

      - name: Start mock server
        working-directory: test-fixtures/mock-server
        run: |
          npx tsx src/index.ts --port 8765 > mock-server.log 2>&1 &
          for i in {1..30}; do
            if curl -sf http://127.0.0.1:8765/__mock_health > /dev/null; then
              echo "mock server ready"
              exit 0
            fi
            sleep 1
          done
          echo "mock server failed to start within 30s"
          cat mock-server.log
          exit 1

      - name: Install typecast-js deps
        working-directory: typecast-js
        run: npm ci

      - name: Run coverage (gate enforced by vitest threshold)
        working-directory: typecast-js
        env:
          TYPECAST_MOCK_URL: http://127.0.0.1:8765
        run: make coverage

      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: coverage-js
          path: typecast-js/coverage/

      - name: Stop mock server
        if: always()
        run: pkill -f "tsx src/index.ts" || true
```

- [ ] **Step 2: Lint the YAML**

```bash
cd /tmp && (cd /tmp/yaml-check 2>/dev/null || (mkdir -p /tmp/yaml-check && cd /tmp/yaml-check && npm init -y > /dev/null 2>&1 && npm install --silent js-yaml))
node -e "
const yaml = require('/tmp/yaml-check/node_modules/js-yaml');
const fs = require('fs');
const txt = fs.readFileSync('/Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk/.github/workflows/coverage-js.yml', 'utf8');
const doc = yaml.load(txt);
console.log('parsed ok, jobs:', Object.keys(doc.jobs));
"
```

Expected: `parsed ok, jobs: [ 'coverage' ]`.

- [ ] **Step 3: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add .github/workflows/coverage-js.yml
git commit -m "ci(js): add coverage workflow for typecast-js

Wires the per-SDK coverage gate into GitHub Actions. Triggers on
PRs and main pushes that touch typecast-js, the shared mock server,
or the workflow itself. Starts the mock server for shape consistency
with other SDKs (typecast-js does not currently use it, but every
SDK's workflow starts it identically), then runs make coverage
which enforces the 100% threshold via vitest's own tool config.
Uploads the HTML coverage report as an artifact."
```

---

### Task 12: Add the coverage badge to the README

**Files:**
- Modify: `typecast-js/README.md`

Add the static shields.io badge at the top of the README, alongside the existing badges.

- [ ] **Step 1: Read the badge block**

```bash
sed -n '1,15p' typecast-js/README.md
```

You should see a `<div align="center">` block with several `[![...]](...)` badges.

- [ ] **Step 2: Add the coverage badge after the npm badge**

Use Edit to add the new badge. Replace:

```jsonc
[![npm version](https://img.shields.io/npm/v/@neosapience/typecast-js.svg?style=flat-square)](https://www.npmjs.com/package/@neosapience/typecast-js)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)
```

with:

```jsonc
[![npm version](https://img.shields.io/npm/v/@neosapience/typecast-js.svg?style=flat-square)](https://www.npmjs.com/package/@neosapience/typecast-js)
[![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen.svg?style=flat-square)](../docs/coverage-policy.md)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg?style=flat-square)](LICENSE)
```

- [ ] **Step 3: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/README.md
git commit -m "docs(js): add 100% coverage badge to README

Static shields.io badge linked to docs/coverage-policy.md. The
badge is trustworthy because the CI gate added in this branch
will fail any PR that drops below 100% coverage."
```

---

### Task 13: Update the root README to replace the placeholder for typecast-js

**Files:**
- Modify: `README.md` (root)

Step 0 added a placeholder table with "_coming soon_" for every SDK. Replace the typecast-js row with the actual badge.

- [ ] **Step 1: Edit the root README table**

Use Edit. Replace:

```text
| typecast-js | _coming soon_ |
```

with:

```text
| typecast-js | ![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen) |
```

- [ ] **Step 2: Commit**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add README.md
git commit -m "docs(coverage): mark typecast-js as 100% covered in root README"
```

---

### Task 14: Final verification, version bump, push, and PR

**Files:**
- Modify: `typecast-js/package.json`
- Modify: `typecast-js/package-lock.json`

This task is the last gate before opening the PR. Run the full coverage suite one more time, bump the patch version, push the branch, open the PR.

- [ ] **Step 1: Final unit + coverage run**

```bash
cd typecast-js && npm run coverage 2>&1 | tail -25
```

Expected: 100% across all four metrics, exit 0, ~20 passing tests.

- [ ] **Step 2: Verify e2e is still skipped without API key**

```bash
cd typecast-js && unset TYPECAST_API_KEY && npm run test:e2e 2>&1 | tail -15
```

Expected: 2 test files, all skipped.

- [ ] **Step 3: Lint check (existing project lint, not new)**

```bash
cd typecast-js && npm run lint 2>&1 | tail -10
```

Expected: no errors. If lint complains about anything in the new test files, fix it before continuing — the existing CI/release pipeline runs lint via `prepublishOnly`.

- [ ] **Step 4: Bump the patch version**

Use Edit on `typecast-js/package.json` to change `"version": "0.1.7",` → `"version": "0.1.8",`. Then refresh the lockfile:

```bash
cd typecast-js && npm install --package-lock-only 2>&1 | tail -3
```

- [ ] **Step 5: Commit the version bump**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk
git add typecast-js/package.json typecast-js/package-lock.json
git commit -m "chore(js): bump version to 0.1.8 for coverage release

JS/TS: 0.1.7 → 0.1.8"
```

- [ ] **Step 6: Push the branch**

```bash
git push -u origin feat/coverage-js 2>&1 | tail -10
```

- [ ] **Step 7: Open the PR**

```bash
gh pr create --title "Add 100% coverage gate to typecast-js" --body "$(cat <<'PRBODY'
# PR: feat/coverage-js

## Overview

Step 1 of the per-SDK coverage rollout. Brings typecast-js from a
56.66% baseline to **100% line + function + branch + statement
coverage**, gated by a vitest threshold and a CI workflow.

See [`docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md`](docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md) for the rollout design and [`docs/superpowers/plans/2026-04-07-coverage-js.md`](docs/superpowers/plans/2026-04-07-coverage-js.md) for this SDK's task list.

## Key changes

- Added `@vitest/coverage-v8` and a `vite.config.ts` coverage block scoped to `src/**/*.ts`, excluding `src/types/**` (type-only)
- Reorganized tests under `test/unit/` (gated) and `test/e2e/` (opt-in via `TYPECAST_API_KEY`)
- Expanded `client.test.ts` from 5 to ~18 cases covering every method, every error path, and the constructor's env-var fallback
- Added `errors.test.ts` covering all 7 status mappings, all 3 detail variants, and the constructor (12 cases)
- Added `index.test.ts` smoke test for the barrel re-export
- One inline pragma: the `Error.captureStackTrace` V8 guard in `errors.ts` (whitelist category 1, platform-specific physically unreachable on Node)
- Added `Makefile` with `make coverage` and `.github/workflows/coverage-js.yml`
- Added 100% coverage badge to typecast-js README and updated the root README placeholder table

## Testing

- `cd typecast-js && make coverage` → **100% lines / functions / branches / statements**, ~20 passing tests, threshold gate enabled
- `cd typecast-js && npm run test:e2e` (no key) → 2 files skipped cleanly
- `cd typecast-js && npm run lint` → clean

## Excluded

Excluded: 1 line (platform=1)

The single excluded line is the `if (Error.captureStackTrace)` false branch in `errors.ts:14`. `Error.captureStackTrace` has been a V8 built-in since Node 0.x and we require Node ≥16, so the false branch is unreachable on every supported runtime. Annotated with `/* v8 ignore next 3 -- category=platform reason="..." */` per `docs/coverage-policy.md` §6.

## Release impact

`@neosapience/typecast-js` patch bump: 0.1.7 → 0.1.8. Will be published after merge following the same flow as #10/#11 (token from repo-root `.env`).
PRBODY
)" 2>&1 | tail -5
```

Expected: PR URL printed.

- [ ] **Step 8: Note the PR URL and stop**

This is the terminal task. Do not merge — that's the human reviewer's decision.

---

## Self-Review

### Spec coverage

| Spec section | Plan task |
|---|---|
| §3.1 typecast-js gets `vitest` + `@vitest/coverage-v8` | Task 1 |
| §3.2 100% line + function + branch + statement | Tasks 3–9 |
| §6 Pragma whitelist (category 1: physical platform) | Task 8 |
| §6.3 Inline format with category and reason | Task 8 |
| §6.3 Per-SDK budget ≤ 30 lines | This SDK uses 1 line (well within budget) |
| §7.1 `tests/unit/` and `tests/e2e/` directories | Tasks 2, 3 (uses `test/` per existing convention with `unit/` + `e2e/` subdirs) |
| §7.2 Mocking strategy for REST | Tasks 3–6 (`vi.stubGlobal('fetch', ...)` — typecast-js has no SSE/WS so the shared mock server is unused) |
| §7.3 Existing integration tests handled (e2e gated) | Task 2 |
| §7.4 100% gate via tool config | Task 9 |
| §7.5 README badge | Tasks 12, 13 |
| §7.6 Patch version bump and release flow | Task 14 |
| §8 CI workflow shape | Task 11 |

All §3–§8 items applicable to this SDK are covered. SSE/WS sections of the spec do not apply to typecast-js (no such code paths exist).

### Placeholder scan

No "TBD", "TODO", "implement later", "fill in details", or "similar to Task N" markers. All test code is shown in full. All commands have explicit expected outputs.

### Type / signature consistency

- `TypecastClient`, `TypecastAPIError`, `TTSModel`, `TypecastClient(config?)`, `getVoices(model?)`, `getVoiceById(voiceId, model?)`, `getVoicesV2(filter?)`, `getVoiceV2(voiceId)`, `textToSpeech(request)` are all used consistently across Tasks 3–7 with the same names and signatures as in `src/client.ts`.
- `TypecastAPIError.fromResponse(statusCode, statusText, data?)` is called identically in Task 6 and matches `src/errors.ts:19`.
- `process.env.TYPECAST_API_HOST` and `process.env.TYPECAST_API_KEY` env var names are used consistently (Task 5 + Task 2).
- The `coverage` script defined in Task 1 (`vitest run --coverage --exclude 'test/e2e/**'`) is referenced by Task 10's Makefile and Task 11's CI workflow.
- The `make coverage` invocation is used identically in Task 11's CI step and in the docs.

### Scope check

This plan covers exactly Step 1 (typecast-js) of the rollout. It produces an independently mergeable PR. The other 8 SDKs (Steps 2–9) get their own plans.
