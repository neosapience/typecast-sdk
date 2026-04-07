# Coverage Infrastructure (Step 0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the shared infrastructure that every per-SDK coverage PR (steps 1–9) will build on: a small Node.js + TypeScript mock server for SSE/WebSocket fixtures, a written coverage exclusion policy, a CI workflow template, and a root README addendum.

**Architecture:** A standalone Node ≥20 package at `test-fixtures/mock-server/` exposes an HTTP server that serves static REST fixtures from disk and replays scripted SSE/WebSocket streams. The server has two runtime deps (`tsx`, `ws`), uses Node's built-in `node:test` runner for its own tests, and is started in CI by every SDK's coverage workflow before tests run. Policy and template files are pure documentation/YAML — no executable behavior.

**Tech Stack:**
- Node.js ≥20, TypeScript (executed via `tsx`, no compile step)
- `ws@^8` for WebSocket server (zero runtime deps of its own)
- `node:test` + `node:assert/strict` for tests (built-in)
- Plain YAML for the GitHub Actions template
- Markdown for policy and README

**Spec reference:** `docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md` §5

**Branch:** This plan executes on a fresh `feat/coverage-infra` branch cut from `main`. The plan document itself currently lives on `feat/coverage-spec`; once this plan is approved, cut `feat/coverage-infra` from `main` and execute there.

---

## Working Directory Convention

All paths in this plan are relative to the repo root: `/Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk/`. Each `cd` shown in commands assumes you start at the repo root for that step.

---

## File Structure

After this plan is complete, the repo will contain:

```text
test-fixtures/
  mock-server/
    .gitignore
    package.json
    package-lock.json          (generated)
    tsconfig.json
    README.md
    src/
      index.ts                 # CLI entry: parses --port, --fixtures-dir, starts server
      server.ts                # createServer(): HTTP + WS lifecycle, returns close()
      fixture-loader.ts        # loadFixtures(dir): walks disk, returns FixtureSet
      rest-handler.ts          # matchRest(req, fixtures): returns RestResponse | null
      sse-handler.ts           # streamSse(res, scriptPath): replays SSE script
      ws-handler.ts            # streamWs(socket, scriptPath): replays WS frame script
      types.ts                 # shared types (Fixture, RestResponse, etc.)
    test/
      fixture-loader.test.ts
      rest-handler.test.ts
      server.test.ts           # full integration: start server, fetch, assert, close
      sse-handler.test.ts
      ws-handler.test.ts
    fixtures/
      voices/
        list-200.json
        list-401.json
      tts/
        synthesis-200.bin
        synthesis-422.json
        synthesis-500.json
      sse/
        ssfm-stream-1.txt
        ssfm-stream-error.txt
      ws/
        synthesis-1.jsonl
        synthesis-disconnect.jsonl
docs/
  coverage-policy.md           # long-form whitelist policy
.github/
  workflows/
    coverage-template.yml      # reference template (NOT triggered: paths is empty)
README.md                      # root README, "Code Coverage" section appended
```

**File responsibility map:**

| File | Responsibility | Depends on |
|------|----------------|------------|
| `types.ts` | Shared TypeScript types | nothing |
| `fixture-loader.ts` | Pure: read fixtures dir → in-memory map | `node:fs/promises`, `types.ts` |
| `rest-handler.ts` | Pure: `(method, path, body) → RestResponse \| null` lookup | `types.ts` |
| `sse-handler.ts` | Side-effect: parse script, write SSE chunks to a `ServerResponse` | `node:fs/promises`, `types.ts` |
| `ws-handler.ts` | Side-effect: parse script, send frames over a `WebSocket`, optional disconnect | `ws`, `node:fs/promises`, `types.ts` |
| `server.ts` | Wire HTTP + WS together; expose `createServer({port, fixturesDir})` returning a `{ close }` handle | all of the above |
| `index.ts` | CLI: parse `process.argv`, call `createServer`, handle SIGINT | `server.ts` |

The split keeps each pure function unit-testable in isolation (`fixture-loader`, `rest-handler`) while the side-effecting modules get integration tests via `server.test.ts`.

---

## Test Strategy for the Mock Server Itself

The mock server is **test infrastructure**, not a shipped SDK. It is **not** part of the 100% coverage gate (it doesn't ship to any registry). However, it must have its own test suite so that when an SDK PR adds a fixture and the test fails, we can be confident the failure is in the SDK, not the mock server.

- Pure modules (`fixture-loader`, `rest-handler`) get unit tests against in-memory inputs.
- Side-effecting modules (`sse-handler`, `ws-handler`) get integration tests via `server.test.ts`: the test starts a real server on port `0` (OS-assigned), uses `fetch` / `ws` clients, asserts, then closes.
- Each test owns its own server instance — no shared state between tests.
- Tests are run via `npm test`, which runs `tsx --test test/*.test.ts`.

---

## Tasks

### Task 1: Bootstrap the mock-server package

**Files:**
- Create: `test-fixtures/mock-server/package.json`
- Create: `test-fixtures/mock-server/tsconfig.json`
- Create: `test-fixtures/mock-server/.gitignore`
- Create: `test-fixtures/mock-server/README.md` (skeleton — full content in Task 13)

- [ ] **Step 1: Create the package directory**

```bash
mkdir -p test-fixtures/mock-server/src test-fixtures/mock-server/test test-fixtures/mock-server/fixtures
```

- [ ] **Step 2: Write `package.json`**

`test-fixtures/mock-server/package.json`:

```json
{
  "name": "@typecast-sdk/mock-server",
  "version": "0.0.1",
  "private": true,
  "type": "module",
  "description": "Shared mock HTTP/SSE/WebSocket server for typecast-sdk SDK coverage tests",
  "engines": {
    "node": ">=20"
  },
  "scripts": {
    "start": "tsx src/index.ts",
    "test": "tsx --test test/*.test.ts",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "ws": "^8.18.0"
  },
  "devDependencies": {
    "@types/node": "^22.0.0",
    "@types/ws": "^8.5.13",
    "tsx": "^4.19.0",
    "typescript": "^5.7.0"
  }
}
```

- [ ] **Step 3: Write `tsconfig.json`**

`test-fixtures/mock-server/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "es2022",
    "module": "esnext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "noEmit": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "allowImportingTsExtensions": true,
    "lib": ["es2022"],
    "types": ["node"]
  },
  "include": ["src/**/*", "test/**/*"]
}
```

- [ ] **Step 4: Write `.gitignore`**

`test-fixtures/mock-server/.gitignore`:

```bash
node_modules/
*.log
.DS_Store
```

- [ ] **Step 5: Write README skeleton**

`test-fixtures/mock-server/README.md`:

```markdown
# Typecast SDK mock server

Shared HTTP / SSE / WebSocket mock server used by every SDK in this monorepo
to run coverage tests without hitting the real Typecast API.

See [`docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md`](../../docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md) §5 for the design.

> Full usage docs are added in Task 13.
```

- [ ] **Step 6: Install and verify**

```bash
cd test-fixtures/mock-server && npm install
```

Expected: `node_modules/` created, `package-lock.json` written, `npm test` is now runnable (will fail because there are no tests yet — that's fine for this step).

```bash
npx tsx --version
```

Expected: prints a version string starting with `tsx v4.`.

- [ ] **Step 7: Commit**

```bash
git add test-fixtures/mock-server/package.json \
        test-fixtures/mock-server/package-lock.json \
        test-fixtures/mock-server/tsconfig.json \
        test-fixtures/mock-server/.gitignore \
        test-fixtures/mock-server/README.md
git commit -m "feat(mock-server): bootstrap package skeleton"
```

---

### Task 2: Define shared types

**Files:**
- Create: `test-fixtures/mock-server/src/types.ts`

- [ ] **Step 1: Write the types**

`test-fixtures/mock-server/src/types.ts`:

```typescript
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';

export interface RestFixture {
  /** HTTP method this fixture matches. */
  method: HttpMethod;
  /** URL path this fixture matches (exact match, no wildcards). */
  path: string;
  /** HTTP status code to return. */
  status: number;
  /** Response Content-Type header. */
  contentType: string;
  /** Response body bytes. */
  body: Uint8Array;
}

export interface SseScriptChunk {
  /** Milliseconds to wait before sending this chunk. */
  delayMs: number;
  /** Raw SSE chunk text (must end with `\n\n` for the client to flush). */
  chunk: string;
}

export interface WsScriptFrame {
  /** Milliseconds to wait before sending this frame. */
  delayMs: number;
  /** WebSocket opcode: text, binary, or close. */
  opcode: 'text' | 'binary' | 'close';
  /** Frame payload. For binary, base64-encoded. For close, ignored. */
  payload: string;
  /** Optional close code (only used when opcode === 'close'). */
  closeCode?: number;
}

export interface FixtureSet {
  /** Map keyed by `${method} ${path}` → fixture. */
  rest: Map<string, RestFixture>;
  /** Map keyed by SSE script name (filename without extension) → absolute file path. */
  sse: Map<string, string>;
  /** Map keyed by WS script name (filename without extension) → absolute file path. */
  ws: Map<string, string>;
}
```

- [ ] **Step 2: Verify it typechecks**

```bash
cd test-fixtures/mock-server && npm run typecheck
```

Expected: no output, exit code 0.

- [ ] **Step 3: Commit**

```bash
git add test-fixtures/mock-server/src/types.ts
git commit -m "feat(mock-server): add shared types"
```

---

### Task 3: Fixture loader (TDD)

**Files:**
- Test: `test-fixtures/mock-server/test/fixture-loader.test.ts`
- Create: `test-fixtures/mock-server/src/fixture-loader.ts`

The loader walks `<fixturesDir>/voices/`, `<fixturesDir>/tts/`, `<fixturesDir>/sse/`, `<fixturesDir>/ws/` and builds a `FixtureSet`. REST fixtures are derived from filename convention: `<endpoint>-<status>.<ext>` where `endpoint` is the directory path and `status` is the HTTP status. Example: `voices/list-200.json` → `GET /voices/list` returns 200 with `application/json`.

For this step we only need: REST fixtures keyed by `(GET, /<dir>/<basename-without-status>)`. SSE and WS are just filename → absolute path maps.

- [ ] **Step 1: Write the failing test**

`test-fixtures/mock-server/test/fixture-loader.test.ts`:

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { loadFixtures } from '../src/fixture-loader.ts';

async function makeFixturesDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), 'mock-fixtures-'));
  await mkdir(join(dir, 'voices'));
  await mkdir(join(dir, 'tts'));
  await mkdir(join(dir, 'sse'));
  await mkdir(join(dir, 'ws'));
  return dir;
}

test('loadFixtures: empty directory returns empty maps', async () => {
  const dir = await makeFixturesDir();
  try {
    const set = await loadFixtures(dir);
    assert.equal(set.rest.size, 0);
    assert.equal(set.sse.size, 0);
    assert.equal(set.ws.size, 0);
  } finally {
    await rm(dir, { recursive: true });
  }
});

test('loadFixtures: parses REST fixture filename into key + status', async () => {
  const dir = await makeFixturesDir();
  try {
    await writeFile(
      join(dir, 'voices', 'list-200.json'),
      JSON.stringify({ voices: [] }),
    );
    const set = await loadFixtures(dir);
    assert.equal(set.rest.size, 1);
    const fixture = set.rest.get('GET /voices/list');
    assert.ok(fixture, 'expected GET /voices/list to be registered');
    assert.equal(fixture.status, 200);
    assert.equal(fixture.contentType, 'application/json');
    assert.equal(
      new TextDecoder().decode(fixture.body),
      JSON.stringify({ voices: [] }),
    );
  } finally {
    await rm(dir, { recursive: true });
  }
});

test('loadFixtures: binary REST fixture uses application/octet-stream', async () => {
  const dir = await makeFixturesDir();
  try {
    await writeFile(join(dir, 'tts', 'synthesis-200.bin'), Buffer.from([0xff, 0xfb]));
    const set = await loadFixtures(dir);
    const fixture = set.rest.get('GET /tts/synthesis');
    assert.ok(fixture);
    assert.equal(fixture.contentType, 'application/octet-stream');
    assert.deepEqual(Array.from(fixture.body), [0xff, 0xfb]);
  } finally {
    await rm(dir, { recursive: true });
  }
});

test('loadFixtures: SSE files indexed by basename', async () => {
  const dir = await makeFixturesDir();
  try {
    await writeFile(join(dir, 'sse', 'ssfm-stream-1.txt'), '');
    const set = await loadFixtures(dir);
    assert.equal(set.sse.size, 1);
    assert.ok(set.sse.has('ssfm-stream-1'));
    assert.ok(set.sse.get('ssfm-stream-1')!.endsWith('ssfm-stream-1.txt'));
  } finally {
    await rm(dir, { recursive: true });
  }
});

test('loadFixtures: WS files indexed by basename', async () => {
  const dir = await makeFixturesDir();
  try {
    await writeFile(join(dir, 'ws', 'synthesis-1.jsonl'), '');
    const set = await loadFixtures(dir);
    assert.equal(set.ws.size, 1);
    assert.ok(set.ws.has('synthesis-1'));
  } finally {
    await rm(dir, { recursive: true });
  }
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: All 5 tests fail with "Cannot find module '../src/fixture-loader.ts'" or similar.

- [ ] **Step 3: Implement the loader**

`test-fixtures/mock-server/src/fixture-loader.ts`:

```typescript
import { readdir, readFile } from 'node:fs/promises';
import { join, parse } from 'node:path';
import type { FixtureSet, HttpMethod, RestFixture } from './types.ts';

const REST_DIRS = ['voices', 'tts'] as const;
const CONTENT_TYPE_BY_EXT: Record<string, string> = {
  '.json': 'application/json',
  '.bin': 'application/octet-stream',
  '.txt': 'text/plain',
};

export async function loadFixtures(rootDir: string): Promise<FixtureSet> {
  const set: FixtureSet = {
    rest: new Map(),
    sse: new Map(),
    ws: new Map(),
  };

  for (const dir of REST_DIRS) {
    await loadRestDir(set, rootDir, dir);
  }
  await loadStreamDir(set.sse, join(rootDir, 'sse'));
  await loadStreamDir(set.ws, join(rootDir, 'ws'));

  return set;
}

async function loadRestDir(
  set: FixtureSet,
  rootDir: string,
  dirName: string,
): Promise<void> {
  const dir = join(rootDir, dirName);
  let entries: string[];
  try {
    entries = await readdir(dir);
  } catch {
    return;
  }
  for (const entry of entries) {
    const parsed = parse(entry);
    const match = parsed.name.match(/^(.+)-(\d{3})$/);
    if (!match) continue;
    const [, endpoint, statusStr] = match;
    const status = Number(statusStr);
    const contentType = CONTENT_TYPE_BY_EXT[parsed.ext] ?? 'application/octet-stream';
    const body = new Uint8Array(await readFile(join(dir, entry)));
    const fixture: RestFixture = {
      method: 'GET' satisfies HttpMethod,
      path: `/${dirName}/${endpoint}`,
      status,
      contentType,
      body,
    };
    set.rest.set(`${fixture.method} ${fixture.path}`, fixture);
  }
}

async function loadStreamDir(
  target: Map<string, string>,
  dir: string,
): Promise<void> {
  let entries: string[];
  try {
    entries = await readdir(dir);
  } catch {
    return;
  }
  for (const entry of entries) {
    const parsed = parse(entry);
    target.set(parsed.name, join(dir, entry));
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 5 passing, 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/fixture-loader.ts \
        test-fixtures/mock-server/test/fixture-loader.test.ts
git commit -m "feat(mock-server): add fixture loader"
```

---

### Task 4: REST handler matcher (TDD)

**Files:**
- Test: `test-fixtures/mock-server/test/rest-handler.test.ts`
- Create: `test-fixtures/mock-server/src/rest-handler.ts`

This is a pure function `matchRest(method, path, fixtures) → RestFixture | null`. It exists as its own module so the matching logic can be unit-tested without spinning up an HTTP server.

- [ ] **Step 1: Write the failing test**

`test-fixtures/mock-server/test/rest-handler.test.ts`:

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { FixtureSet, RestFixture } from '../src/types.ts';
import { matchRest } from '../src/rest-handler.ts';

function makeSet(...fixtures: RestFixture[]): FixtureSet {
  const set: FixtureSet = { rest: new Map(), sse: new Map(), ws: new Map() };
  for (const f of fixtures) {
    set.rest.set(`${f.method} ${f.path}`, f);
  }
  return set;
}

const sample: RestFixture = {
  method: 'GET',
  path: '/voices/list',
  status: 200,
  contentType: 'application/json',
  body: new TextEncoder().encode('{}'),
};

test('matchRest: returns fixture on exact match', () => {
  const set = makeSet(sample);
  const result = matchRest('GET', '/voices/list', set);
  assert.equal(result, sample);
});

test('matchRest: returns null when method differs', () => {
  const set = makeSet(sample);
  const result = matchRest('POST', '/voices/list', set);
  assert.equal(result, null);
});

test('matchRest: returns null when path differs', () => {
  const set = makeSet(sample);
  const result = matchRest('GET', '/voices/other', set);
  assert.equal(result, null);
});

test('matchRest: ignores query string when matching', () => {
  const set = makeSet(sample);
  const result = matchRest('GET', '/voices/list?model=ssfm-v30', set);
  assert.equal(result, sample);
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 4 new failures (`Cannot find module '../src/rest-handler.ts'`).

- [ ] **Step 3: Implement the matcher**

`test-fixtures/mock-server/src/rest-handler.ts`:

```typescript
import type { FixtureSet, HttpMethod, RestFixture } from './types.ts';

export function matchRest(
  method: HttpMethod,
  pathWithQuery: string,
  fixtures: FixtureSet,
): RestFixture | null {
  const path = stripQuery(pathWithQuery);
  return fixtures.rest.get(`${method} ${path}`) ?? null;
}

function stripQuery(pathWithQuery: string): string {
  const i = pathWithQuery.indexOf('?');
  return i === -1 ? pathWithQuery : pathWithQuery.slice(0, i);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 9 passing total (5 from Task 3 + 4 new), 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/rest-handler.ts \
        test-fixtures/mock-server/test/rest-handler.test.ts
git commit -m "feat(mock-server): add REST request matcher"
```

---

### Task 5: HTTP server bootstrap with health endpoint (TDD)

**Files:**
- Test: `test-fixtures/mock-server/test/server.test.ts`
- Create: `test-fixtures/mock-server/src/server.ts`

This task gets a server running and serving `GET /__mock_health` → `200 ok`. REST fixture serving comes in Task 6.

- [ ] **Step 1: Write the failing test**

`test-fixtures/mock-server/test/server.test.ts`:

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createServer } from '../src/server.ts';

async function emptyFixturesDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), 'mock-srv-'));
  await mkdir(join(dir, 'voices'));
  await mkdir(join(dir, 'tts'));
  await mkdir(join(dir, 'sse'));
  await mkdir(join(dir, 'ws'));
  return dir;
}

test('server: GET /__mock_health returns 200 ok', async () => {
  const dir = await emptyFixturesDir();
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const res = await fetch(`${handle.url}/__mock_health`);
    assert.equal(res.status, 200);
    assert.equal(await res.text(), 'ok');
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: failure on `Cannot find module '../src/server.ts'`.

- [ ] **Step 3: Implement the server bootstrap**

`test-fixtures/mock-server/src/server.ts`:

```typescript
import { createServer as createHttpServer, IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { loadFixtures } from './fixture-loader.ts';

export interface ServerOptions {
  port: number;
  fixturesDir: string;
}

export interface ServerHandle {
  url: string;
  close: () => Promise<void>;
}

export async function createServer(options: ServerOptions): Promise<ServerHandle> {
  await loadFixtures(options.fixturesDir);

  const httpServer = createHttpServer((req, res) => {
    handleRequest(req, res);
  });

  await new Promise<void>((resolve) => {
    httpServer.listen(options.port, '127.0.0.1', () => resolve());
  });

  const address = httpServer.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}`;

  return {
    url,
    close: () =>
      new Promise<void>((resolve, reject) => {
        httpServer.close((err) => (err ? reject(err) : resolve()));
      }),
  };
}

function handleRequest(req: IncomingMessage, res: ServerResponse): void {
  if (req.method === 'GET' && req.url === '/__mock_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'no fixture matched', method: req.method, path: req.url }));
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 10 passing total (9 prior + 1 new), 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/server.ts \
        test-fixtures/mock-server/test/server.test.ts
git commit -m "feat(mock-server): add HTTP server bootstrap with health endpoint"
```

---

### Task 6: Integrate REST fixture serving into the server (TDD)

**Files:**
- Modify: `test-fixtures/mock-server/test/server.test.ts`
- Modify: `test-fixtures/mock-server/src/server.ts`

- [ ] **Step 1: Append failing tests**

Append to `test-fixtures/mock-server/test/server.test.ts`:

```typescript
import { writeFile } from 'node:fs/promises';

async function fixturesWith(rest: { dir: string; file: string; body: string }): Promise<string> {
  const dir = await emptyFixturesDir();
  await writeFile(join(dir, rest.dir, rest.file), rest.body);
  return dir;
}

test('server: serves a REST fixture by GET', async () => {
  const dir = await fixturesWith({
    dir: 'voices',
    file: 'list-200.json',
    body: JSON.stringify({ voices: [{ id: 'v1' }] }),
  });
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const res = await fetch(`${handle.url}/voices/list`);
    assert.equal(res.status, 200);
    assert.equal(res.headers.get('content-type'), 'application/json');
    assert.deepEqual(await res.json(), { voices: [{ id: 'v1' }] });
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});

test('server: returns 404 with diagnostic body for unmatched request', async () => {
  const dir = await emptyFixturesDir();
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const res = await fetch(`${handle.url}/voices/missing`);
    assert.equal(res.status, 404);
    const body = await res.json() as { error: string; method: string; path: string };
    assert.equal(body.error, 'no fixture matched');
    assert.equal(body.method, 'GET');
    assert.equal(body.path, '/voices/missing');
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});

test('server: serves a REST fixture matching ignores query string', async () => {
  const dir = await fixturesWith({
    dir: 'voices',
    file: 'list-200.json',
    body: '{}',
  });
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const res = await fetch(`${handle.url}/voices/list?model=ssfm-v30`);
    assert.equal(res.status, 200);
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});
```

- [ ] **Step 2: Run tests to verify the new ones fail**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 3 new failures (server returns 404 instead of fixture).

- [ ] **Step 3: Wire the matcher into the server**

Replace the entire `test-fixtures/mock-server/src/server.ts` with:

```typescript
import { createServer as createHttpServer, IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { loadFixtures } from './fixture-loader.ts';
import { matchRest } from './rest-handler.ts';
import type { FixtureSet, HttpMethod } from './types.ts';

export interface ServerOptions {
  port: number;
  fixturesDir: string;
}

export interface ServerHandle {
  url: string;
  close: () => Promise<void>;
}

export async function createServer(options: ServerOptions): Promise<ServerHandle> {
  const fixtures = await loadFixtures(options.fixturesDir);

  const httpServer = createHttpServer((req, res) => {
    handleRequest(req, res, fixtures);
  });

  await new Promise<void>((resolve) => {
    httpServer.listen(options.port, '127.0.0.1', () => resolve());
  });

  const address = httpServer.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}`;

  return {
    url,
    close: () =>
      new Promise<void>((resolve, reject) => {
        httpServer.close((err) => (err ? reject(err) : resolve()));
      }),
  };
}

function handleRequest(
  req: IncomingMessage,
  res: ServerResponse,
  fixtures: FixtureSet,
): void {
  if (req.method === 'GET' && req.url === '/__mock_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }

  const method = (req.method ?? 'GET') as HttpMethod;
  const fixture = matchRest(method, req.url ?? '/', fixtures);
  if (fixture) {
    res.writeHead(fixture.status, { 'Content-Type': fixture.contentType });
    res.end(Buffer.from(fixture.body));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'no fixture matched', method: req.method, path: req.url }));
}
```

- [ ] **Step 4: Run tests**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 13 passing total, 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/server.ts \
        test-fixtures/mock-server/test/server.test.ts
git commit -m "feat(mock-server): serve REST fixtures from disk"
```

---

### Task 7: SSE handler — script player (TDD)

**Files:**
- Test: `test-fixtures/mock-server/test/sse-handler.test.ts`
- Create: `test-fixtures/mock-server/src/sse-handler.ts`

The SSE script file is plain text. Each chunk is separated from the next by a line containing only `---` (three hyphens). An optional `# delay-ms: 50` comment line at the start of a chunk sets the pre-send delay; if absent the delay is 0. Example file:

```text
# delay-ms: 0
event: progress
data: {"progress": 0.1}

---
# delay-ms: 100
event: progress
data: {"progress": 0.5}

---
event: done
data: [DONE]

```

The handler is invoked with a `ServerResponse` already configured for SSE (status 200, headers set), and writes the chunks in order with the appropriate delays.

- [ ] **Step 1: Write the failing test**

`test-fixtures/mock-server/test/sse-handler.test.ts`:

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseSseScript } from '../src/sse-handler.ts';

test('parseSseScript: single chunk, no delay comment', () => {
  const text = 'event: ping\ndata: 1\n\n';
  const chunks = parseSseScript(text);
  assert.equal(chunks.length, 1);
  assert.equal(chunks[0].delayMs, 0);
  assert.equal(chunks[0].chunk, 'event: ping\ndata: 1\n\n');
});

test('parseSseScript: multiple chunks separated by ---', () => {
  const text = 'event: a\ndata: 1\n\n---\nevent: b\ndata: 2\n\n';
  const chunks = parseSseScript(text);
  assert.equal(chunks.length, 2);
  assert.equal(chunks[0].chunk, 'event: a\ndata: 1\n\n');
  assert.equal(chunks[1].chunk, 'event: b\ndata: 2\n\n');
});

test('parseSseScript: delay-ms comment is parsed and stripped', () => {
  const text = '# delay-ms: 50\nevent: a\ndata: 1\n\n---\n# delay-ms: 200\nevent: b\ndata: 2\n\n';
  const chunks = parseSseScript(text);
  assert.equal(chunks.length, 2);
  assert.equal(chunks[0].delayMs, 50);
  assert.equal(chunks[1].delayMs, 200);
  assert.equal(chunks[0].chunk, 'event: a\ndata: 1\n\n');
});

test('parseSseScript: ignores trailing whitespace and empty chunks', () => {
  const text = 'event: a\ndata: 1\n\n---\n   \n';
  const chunks = parseSseScript(text);
  assert.equal(chunks.length, 1);
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 4 failures (`Cannot find module '../src/sse-handler.ts'`).

- [ ] **Step 3: Implement the parser + streamer**

`test-fixtures/mock-server/src/sse-handler.ts`:

```typescript
import { readFile } from 'node:fs/promises';
import type { ServerResponse } from 'node:http';
import type { SseScriptChunk } from './types.ts';

export function parseSseScript(text: string): SseScriptChunk[] {
  const rawChunks = text.split(/\n---\n/);
  const chunks: SseScriptChunk[] = [];

  for (const raw of rawChunks) {
    const lines = raw.split('\n');
    let delayMs = 0;
    let startIdx = 0;

    if (lines[0]?.startsWith('# delay-ms:')) {
      const match = lines[0].match(/^# delay-ms:\s*(\d+)\s*$/);
      if (match) {
        delayMs = Number(match[1]);
        startIdx = 1;
      }
    }

    const chunkText = lines.slice(startIdx).join('\n');
    if (chunkText.trim().length === 0) continue;
    chunks.push({ delayMs, chunk: ensureTrailingBlankLine(chunkText) });
  }

  return chunks;
}

function ensureTrailingBlankLine(text: string): string {
  if (text.endsWith('\n\n')) return text;
  if (text.endsWith('\n')) return text + '\n';
  return text + '\n\n';
}

export async function streamSse(
  res: ServerResponse,
  scriptPath: string,
): Promise<void> {
  const text = await readFile(scriptPath, 'utf8');
  const chunks = parseSseScript(text);

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });

  for (const chunk of chunks) {
    if (chunk.delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, chunk.delayMs));
    }
    if (res.writableEnded) return;
    res.write(chunk.chunk);
  }

  res.end();
}
```

- [ ] **Step 4: Run tests**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 17 passing total, 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/sse-handler.ts \
        test-fixtures/mock-server/test/sse-handler.test.ts
git commit -m "feat(mock-server): add SSE script parser and streamer"
```

---

### Task 8: Wire SSE handler into the server (TDD)

**Files:**
- Modify: `test-fixtures/mock-server/test/server.test.ts`
- Modify: `test-fixtures/mock-server/src/server.ts`

URL convention: `GET /__mock_sse/<script-name>` plays the SSE script named `<script-name>` (filename without extension) from the `sse/` fixtures directory.

- [ ] **Step 1: Append failing test**

Append to `test-fixtures/mock-server/test/server.test.ts`:

```typescript
test('server: GET /__mock_sse/<name> streams the named SSE script', async () => {
  const dir = await emptyFixturesDir();
  await writeFile(
    join(dir, 'sse', 'demo.txt'),
    'event: a\ndata: 1\n\n---\nevent: b\ndata: 2\n\n',
  );
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const res = await fetch(`${handle.url}/__mock_sse/demo`);
    assert.equal(res.status, 200);
    assert.equal(res.headers.get('content-type'), 'text/event-stream');
    const text = await res.text();
    assert.match(text, /event: a/);
    assert.match(text, /event: b/);
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});

test('server: GET /__mock_sse/<missing> returns 404', async () => {
  const dir = await emptyFixturesDir();
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const res = await fetch(`${handle.url}/__mock_sse/none`);
    assert.equal(res.status, 404);
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});
```

- [ ] **Step 2: Run tests to verify the new ones fail**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 2 new failures.

- [ ] **Step 3: Wire the SSE handler**

In `test-fixtures/mock-server/src/server.ts`, add an import and a route check inside `handleRequest`. Replace the `handleRequest` function with:

```typescript
import { streamSse } from './sse-handler.ts';

// ... (keep imports above unchanged)

function handleRequest(
  req: IncomingMessage,
  res: ServerResponse,
  fixtures: FixtureSet,
): void {
  if (req.method === 'GET' && req.url === '/__mock_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }

  if (req.method === 'GET' && req.url?.startsWith('/__mock_sse/')) {
    const name = req.url.slice('/__mock_sse/'.length);
    const scriptPath = fixtures.sse.get(name);
    if (!scriptPath) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'sse script not found', name }));
      return;
    }
    void streamSse(res, scriptPath);
    return;
  }

  const method = (req.method ?? 'GET') as HttpMethod;
  const fixture = matchRest(method, req.url ?? '/', fixtures);
  if (fixture) {
    res.writeHead(fixture.status, { 'Content-Type': fixture.contentType });
    res.end(Buffer.from(fixture.body));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'no fixture matched', method: req.method, path: req.url }));
}
```

The new `import { streamSse } from './sse-handler.ts';` line goes immediately after the existing `import { matchRest }` line. Everything else above `handleRequest` is unchanged from Task 6.

- [ ] **Step 4: Run tests**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 19 passing total, 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/server.ts \
        test-fixtures/mock-server/test/server.test.ts
git commit -m "feat(mock-server): expose SSE scripts at /__mock_sse/<name>"
```

---

### Task 9: WebSocket handler — frame script player (TDD)

**Files:**
- Test: `test-fixtures/mock-server/test/ws-handler.test.ts`
- Create: `test-fixtures/mock-server/src/ws-handler.ts`

The WS script is JSONL — one frame per line:

```jsonl
{"delayMs": 0, "opcode": "text", "payload": "hello"}
{"delayMs": 50, "opcode": "binary", "payload": "AAEC"}
{"delayMs": 0, "opcode": "close", "payload": "", "closeCode": 1000}
```

Binary payload is base64. The handler is invoked with an open `WebSocket` and a script file path; it reads, parses, and replays.

- [ ] **Step 1: Write the failing test**

`test-fixtures/mock-server/test/ws-handler.test.ts`:

```typescript
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseWsScript } from '../src/ws-handler.ts';

test('parseWsScript: parses text frame', () => {
  const text = '{"delayMs":0,"opcode":"text","payload":"hello"}\n';
  const frames = parseWsScript(text);
  assert.equal(frames.length, 1);
  assert.equal(frames[0].opcode, 'text');
  assert.equal(frames[0].payload, 'hello');
  assert.equal(frames[0].delayMs, 0);
});

test('parseWsScript: parses multiple frames', () => {
  const text = [
    '{"delayMs":0,"opcode":"text","payload":"a"}',
    '{"delayMs":10,"opcode":"binary","payload":"AAEC"}',
    '{"delayMs":0,"opcode":"close","payload":"","closeCode":1000}',
  ].join('\n');
  const frames = parseWsScript(text);
  assert.equal(frames.length, 3);
  assert.equal(frames[2].opcode, 'close');
  assert.equal(frames[2].closeCode, 1000);
});

test('parseWsScript: ignores blank lines', () => {
  const text = '\n{"delayMs":0,"opcode":"text","payload":"a"}\n\n';
  const frames = parseWsScript(text);
  assert.equal(frames.length, 1);
});
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 3 failures (`Cannot find module '../src/ws-handler.ts'`).

- [ ] **Step 3: Implement the parser + streamer**

`test-fixtures/mock-server/src/ws-handler.ts`:

```typescript
import { readFile } from 'node:fs/promises';
import type { WebSocket } from 'ws';
import type { WsScriptFrame } from './types.ts';

export function parseWsScript(text: string): WsScriptFrame[] {
  const frames: WsScriptFrame[] = [];
  for (const line of text.split('\n')) {
    const trimmed = line.trim();
    if (trimmed.length === 0) continue;
    frames.push(JSON.parse(trimmed) as WsScriptFrame);
  }
  return frames;
}

export async function streamWs(socket: WebSocket, scriptPath: string): Promise<void> {
  const text = await readFile(scriptPath, 'utf8');
  const frames = parseWsScript(text);

  for (const frame of frames) {
    if (frame.delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, frame.delayMs));
    }
    if (socket.readyState !== socket.OPEN) return;

    if (frame.opcode === 'text') {
      socket.send(frame.payload);
    } else if (frame.opcode === 'binary') {
      socket.send(Buffer.from(frame.payload, 'base64'));
    } else {
      socket.close(frame.closeCode ?? 1000);
      return;
    }
  }
}
```

- [ ] **Step 4: Run tests**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 22 passing total, 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/ws-handler.ts \
        test-fixtures/mock-server/test/ws-handler.test.ts
git commit -m "feat(mock-server): add WebSocket frame script player"
```

---

### Task 10: Wire WebSocket server into HTTP server (TDD)

**Files:**
- Modify: `test-fixtures/mock-server/test/server.test.ts`
- Modify: `test-fixtures/mock-server/src/server.ts`

URL convention: WebSocket connections to `ws://host/__mock_ws/<script-name>` replay the named WS script.

- [ ] **Step 1: Append failing test**

Append to `test-fixtures/mock-server/test/server.test.ts`:

```typescript
import { WebSocket } from 'ws';

test('server: WS connection at /__mock_ws/<name> replays frames', async () => {
  const dir = await emptyFixturesDir();
  await writeFile(
    join(dir, 'ws', 'demo.jsonl'),
    [
      '{"delayMs":0,"opcode":"text","payload":"hello"}',
      '{"delayMs":0,"opcode":"text","payload":"world"}',
      '{"delayMs":0,"opcode":"close","payload":"","closeCode":1000}',
    ].join('\n'),
  );
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const wsUrl = handle.url.replace('http://', 'ws://') + '/__mock_ws/demo';
    const messages: string[] = [];
    await new Promise<void>((resolve, reject) => {
      const ws = new WebSocket(wsUrl);
      ws.on('message', (data) => messages.push(data.toString()));
      ws.on('close', () => resolve());
      ws.on('error', reject);
    });
    assert.deepEqual(messages, ['hello', 'world']);
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});

test('server: WS connection to unknown script closes immediately with 1008', async () => {
  const dir = await emptyFixturesDir();
  const handle = await createServer({ port: 0, fixturesDir: dir });
  try {
    const wsUrl = handle.url.replace('http://', 'ws://') + '/__mock_ws/none';
    const closeCode = await new Promise<number>((resolve, reject) => {
      const ws = new WebSocket(wsUrl);
      ws.on('close', (code) => resolve(code));
      ws.on('error', reject);
    });
    assert.equal(closeCode, 1008);
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});
```

- [ ] **Step 2: Run tests to verify the new ones fail**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 2 new failures (WS connection errors / no upgrade handler).

- [ ] **Step 3: Add WS server to `server.ts`**

Replace the entire `test-fixtures/mock-server/src/server.ts` with:

```typescript
import { createServer as createHttpServer, IncomingMessage, ServerResponse } from 'node:http';
import type { AddressInfo } from 'node:net';
import { WebSocketServer } from 'ws';
import { loadFixtures } from './fixture-loader.ts';
import { matchRest } from './rest-handler.ts';
import { streamSse } from './sse-handler.ts';
import { streamWs } from './ws-handler.ts';
import type { FixtureSet, HttpMethod } from './types.ts';

export interface ServerOptions {
  port: number;
  fixturesDir: string;
}

export interface ServerHandle {
  url: string;
  close: () => Promise<void>;
}

export async function createServer(options: ServerOptions): Promise<ServerHandle> {
  const fixtures = await loadFixtures(options.fixturesDir);

  const httpServer = createHttpServer((req, res) => {
    handleRequest(req, res, fixtures);
  });

  const wsServer = new WebSocketServer({ noServer: true });

  httpServer.on('upgrade', (req, socket, head) => {
    const url = req.url ?? '';
    if (!url.startsWith('/__mock_ws/')) {
      socket.destroy();
      return;
    }
    const name = url.slice('/__mock_ws/'.length);
    const scriptPath = fixtures.ws.get(name);
    wsServer.handleUpgrade(req, socket, head, (ws) => {
      if (!scriptPath) {
        ws.close(1008, 'script not found');
        return;
      }
      void streamWs(ws, scriptPath);
    });
  });

  await new Promise<void>((resolve) => {
    httpServer.listen(options.port, '127.0.0.1', () => resolve());
  });

  const address = httpServer.address() as AddressInfo;
  const url = `http://127.0.0.1:${address.port}`;

  return {
    url,
    close: () =>
      new Promise<void>((resolve, reject) => {
        wsServer.close();
        httpServer.close((err) => (err ? reject(err) : resolve()));
      }),
  };
}

function handleRequest(
  req: IncomingMessage,
  res: ServerResponse,
  fixtures: FixtureSet,
): void {
  if (req.method === 'GET' && req.url === '/__mock_health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('ok');
    return;
  }

  if (req.method === 'GET' && req.url?.startsWith('/__mock_sse/')) {
    const name = req.url.slice('/__mock_sse/'.length);
    const scriptPath = fixtures.sse.get(name);
    if (!scriptPath) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'sse script not found', name }));
      return;
    }
    void streamSse(res, scriptPath);
    return;
  }

  const method = (req.method ?? 'GET') as HttpMethod;
  const fixture = matchRest(method, req.url ?? '/', fixtures);
  if (fixture) {
    res.writeHead(fixture.status, { 'Content-Type': fixture.contentType });
    res.end(Buffer.from(fixture.body));
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'no fixture matched', method: req.method, path: req.url }));
}
```

- [ ] **Step 4: Run tests**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 24 passing total, 0 failing.

- [ ] **Step 5: Commit**

```bash
git add test-fixtures/mock-server/src/server.ts \
        test-fixtures/mock-server/test/server.test.ts
git commit -m "feat(mock-server): add WebSocket upgrade handler at /__mock_ws/<name>"
```

---

### Task 11: CLI entry

**Files:**
- Create: `test-fixtures/mock-server/src/index.ts`

This is a tiny CLI wrapper. It is exercised by hand and via the CI workflow, not by `npm test`. It does not get its own test (the surface is too thin).

- [ ] **Step 1: Write the CLI**

`test-fixtures/mock-server/src/index.ts`:

```typescript
#!/usr/bin/env -S tsx
import { createServer } from './server.ts';

interface CliArgs {
  port: number;
  fixturesDir: string;
}

function parseArgs(argv: string[]): CliArgs {
  let port = 8765;
  let fixturesDir = new URL('../fixtures', import.meta.url).pathname;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--port') {
      port = Number(argv[++i]);
    } else if (arg === '--fixtures-dir') {
      fixturesDir = argv[++i];
    } else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    } else {
      console.error(`unknown argument: ${arg}`);
      printHelp();
      process.exit(2);
    }
  }

  return { port, fixturesDir };
}

function printHelp(): void {
  console.log('Usage: tsx src/index.ts [--port PORT] [--fixtures-dir DIR]');
  console.log('  --port           Port to listen on (default 8765)');
  console.log('  --fixtures-dir   Directory containing fixtures (default ../fixtures)');
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const handle = await createServer(args);
  console.log(`mock server listening on ${handle.url}`);
  console.log(`fixtures dir: ${args.fixturesDir}`);

  const shutdown = async (signal: string): Promise<void> => {
    console.log(`received ${signal}, shutting down...`);
    await handle.close();
    process.exit(0);
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

void main();
```

- [ ] **Step 2: Smoke-test the CLI**

```bash
cd test-fixtures/mock-server && (npm start -- --port 18765 &) && sleep 2 && curl -sf http://127.0.0.1:18765/__mock_health && pkill -f "tsx src/index.ts"
```

Expected: prints `ok`. The `pkill` line cleans up the background process. If it fails, find the process with `ps aux | grep tsx` and kill it manually.

- [ ] **Step 3: Verify typecheck still passes**

```bash
cd test-fixtures/mock-server && npm run typecheck
```

Expected: exit code 0, no output.

- [ ] **Step 4: Commit**

```bash
git add test-fixtures/mock-server/src/index.ts
git commit -m "feat(mock-server): add CLI entry point"
```

---

### Task 12: Add seed fixture files

**Files:**
- Create: `test-fixtures/mock-server/fixtures/voices/list-200.json`
- Create: `test-fixtures/mock-server/fixtures/voices/list-401.json`
- Create: `test-fixtures/mock-server/fixtures/tts/synthesis-200.bin`
- Create: `test-fixtures/mock-server/fixtures/tts/synthesis-422.json`
- Create: `test-fixtures/mock-server/fixtures/tts/synthesis-500.json`
- Create: `test-fixtures/mock-server/fixtures/sse/ssfm-stream-1.txt`
- Create: `test-fixtures/mock-server/fixtures/sse/ssfm-stream-error.txt`
- Create: `test-fixtures/mock-server/fixtures/ws/synthesis-1.jsonl`
- Create: `test-fixtures/mock-server/fixtures/ws/synthesis-disconnect.jsonl`

These are seed fixtures only. SDK PRs will add more as their tests need them. Content is sanitized — no real API keys, no real user data, no copied production responses.

- [ ] **Step 1: Voice list 200**

`test-fixtures/mock-server/fixtures/voices/list-200.json`:

```json
{
  "voices": [
    {
      "voice_id": "tc_mock_001",
      "voice_name": "Mock Voice 1",
      "model": "ssfm-v30",
      "language": "en",
      "gender": "male"
    },
    {
      "voice_id": "tc_mock_002",
      "voice_name": "Mock Voice 2",
      "model": "ssfm-v21",
      "language": "ko",
      "gender": "female"
    }
  ],
  "next_cursor": null
}
```

- [ ] **Step 2: Voice list 401**

`test-fixtures/mock-server/fixtures/voices/list-401.json`:

```json
{
  "error": {
    "code": "unauthorized",
    "message": "Invalid or missing API key"
  }
}
```

- [ ] **Step 3: TTS synthesis 200 (binary placeholder)**

Create a 64-byte placeholder binary file. Run:

```bash
printf 'TYPECAST_MOCK_AUDIO_FIXTURE_v1\n' > test-fixtures/mock-server/fixtures/tts/synthesis-200.bin
dd if=/dev/zero bs=1 count=32 >> test-fixtures/mock-server/fixtures/tts/synthesis-200.bin 2>/dev/null
```

Expected: `wc -c test-fixtures/mock-server/fixtures/tts/synthesis-200.bin` reports 63 (31 from the header + 32 zeros). This is a deliberately fake audio blob — tests assert byte length and content prefix, not real decode.

- [ ] **Step 4: TTS synthesis 422**

`test-fixtures/mock-server/fixtures/tts/synthesis-422.json`:

```json
{
  "error": {
    "code": "validation_error",
    "message": "voice_id is required",
    "field": "voice_id"
  }
}
```

- [ ] **Step 5: TTS synthesis 500**

`test-fixtures/mock-server/fixtures/tts/synthesis-500.json`:

```json
{
  "error": {
    "code": "internal_error",
    "message": "Mock server-side failure"
  }
}
```

- [ ] **Step 6: SSE happy-path script**

`test-fixtures/mock-server/fixtures/sse/ssfm-stream-1.txt`:

```text
# delay-ms: 0
event: progress
data: {"progress":0.1,"stage":"prepare"}

---
# delay-ms: 50
event: progress
data: {"progress":0.5,"stage":"synthesize"}

---
# delay-ms: 50
event: progress
data: {"progress":1.0,"stage":"complete"}

---
# delay-ms: 0
event: done
data: [DONE]

```

- [ ] **Step 7: SSE error script**

`test-fixtures/mock-server/fixtures/sse/ssfm-stream-error.txt`:

```text
# delay-ms: 0
event: progress
data: {"progress":0.1,"stage":"prepare"}

---
# delay-ms: 50
event: error
data: {"code":"synthesis_failed","message":"Mock failure mid-stream"}

```

- [ ] **Step 8: WS happy-path script**

`test-fixtures/mock-server/fixtures/ws/synthesis-1.jsonl`:

```jsonl
{"delayMs":0,"opcode":"text","payload":"{\"type\":\"start\",\"request_id\":\"mock-001\"}"}
{"delayMs":20,"opcode":"binary","payload":"VFlQRUNBU1RfTU9DS19BVURJT19DSFVOS18x"}
{"delayMs":20,"opcode":"binary","payload":"VFlQRUNBU1RfTU9DS19BVURJT19DSFVOS18y"}
{"delayMs":0,"opcode":"text","payload":"{\"type\":\"end\",\"request_id\":\"mock-001\"}"}
{"delayMs":0,"opcode":"close","payload":"","closeCode":1000}
```

The base64 payloads decode to `TYPECAST_MOCK_AUDIO_CHUNK_1` and `TYPECAST_MOCK_AUDIO_CHUNK_2`.

- [ ] **Step 9: WS disconnect script**

`test-fixtures/mock-server/fixtures/ws/synthesis-disconnect.jsonl`:

```jsonl
{"delayMs":0,"opcode":"text","payload":"{\"type\":\"start\",\"request_id\":\"mock-002\"}"}
{"delayMs":20,"opcode":"binary","payload":"VFlQRUNBU1RfTU9DS19BVURJT19DSFVOS18x"}
{"delayMs":0,"opcode":"close","payload":"","closeCode":1011}
```

- [ ] **Step 10: Verify everything still passes**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 24 passing total. (Existing tests use temporary fixture dirs so the new files do not affect them.)

- [ ] **Step 11: Commit**

```bash
git add test-fixtures/mock-server/fixtures/
git commit -m "feat(mock-server): add seed fixtures for voices, tts, sse, ws"
```

---

### Task 13: Mock server README — full documentation

**Files:**
- Modify: `test-fixtures/mock-server/README.md`

- [ ] **Step 1: Replace the skeleton with full docs**

Replace the entire contents of `test-fixtures/mock-server/README.md` with:

````markdown
# Typecast SDK mock server

A small Node.js mock HTTP / SSE / WebSocket server used by every SDK in
this monorepo to run coverage tests without hitting the real Typecast API.

See the rollout design at
[`docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md`](../../docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md) §5
for the bigger picture.

## Why this exists

Each SDK needs to test failure paths, streaming progress, and WebSocket
disconnects to reach the coverage target. Hand-rolling those mocks 9 times
in 9 different ecosystems is brittle and inconsistent. This server gives
every SDK the same set of fixtures to test against.

## Requirements

- Node.js ≥ 20

## Running the server

```bash
cd test-fixtures/mock-server
npm install
npm start                      # listens on http://127.0.0.1:8765
npm start -- --port 9000       # custom port
npm start -- --fixtures-dir ./alt-fixtures
```

Health check:

```bash
curl http://127.0.0.1:8765/__mock_health
# → ok
```

## Endpoints

| Path | Behavior |
|------|----------|
| `GET /__mock_health` | Returns `200 ok`. CI uses this to wait for readiness. |
| `GET /__mock_sse/<name>` | Streams the SSE script `fixtures/sse/<name>.txt`. |
| `WS  /__mock_ws/<name>` | Replays the WebSocket script `fixtures/ws/<name>.jsonl`. |
| `GET /<dir>/<endpoint>` | Returns the REST fixture `fixtures/<dir>/<endpoint>-<status>.<ext>`. |

Unmatched requests return `404 application/json` with a body of
`{"error":"no fixture matched","method":"...","path":"..."}` so SDK
tests can assert on it.

## Fixture format

### REST fixtures

Filename pattern: `fixtures/<dir>/<endpoint>-<status>.<ext>`.

| Example file | Serves |
|-------------|--------|
| `fixtures/voices/list-200.json` | `GET /voices/list` → 200 application/json |
| `fixtures/voices/list-401.json` | `GET /voices/list` → 401 application/json (overwrites the 200) |
| `fixtures/tts/synthesis-200.bin` | `GET /tts/synthesis` → 200 application/octet-stream |

The server holds **one** REST fixture per `(method, path)` key. To switch
between success and failure responses in a single test run, point a test
at a temporary fixtures directory containing only the variant you need
(this is what the SDK tests do).

The `Content-Type` is derived from the extension:

| Extension | Content-Type |
|-----------|--------------|
| `.json` | `application/json` |
| `.bin` | `application/octet-stream` |
| `.txt` | `text/plain` |

### SSE scripts

Plain-text files in `fixtures/sse/`. Chunks are separated by a line
containing only `---`. Each chunk may begin with a `# delay-ms: N` comment
that delays the chunk's send by N milliseconds. The chunk body must
include the SSE blank-line terminator (`\n\n`).

Example:

```
# delay-ms: 0
event: progress
data: {"progress":0.1}

---
# delay-ms: 50
event: progress
data: {"progress":1.0}

```

### WebSocket scripts

JSONL files in `fixtures/ws/`. Each line is a JSON object with these fields:

```jsonc
{
  "delayMs": 0,             // ms to wait before sending this frame
  "opcode": "text",         // "text" | "binary" | "close"
  "payload": "hello",       // text payload, or base64 for binary, or "" for close
  "closeCode": 1000         // optional, only for opcode "close"
}
```

## Adding a fixture

1. Drop the file under the right `fixtures/<kind>/` directory.
2. Make sure the filename follows the convention above.
3. The next time the server starts, it picks the file up automatically.
   No code change required.

## Running the tests

```bash
cd test-fixtures/mock-server
npm test
```

This runs the mock server's own test suite (`tsx --test test/*.test.ts`).
This is separate from the SDK coverage tests — those run from each SDK's
directory and start the mock server in the background.

## Out of scope

This server is deliberately small. It does **not** support:

- Request body matching or templating
- Dynamic responses
- Conditional fixture selection by header
- Recording / playback from real API traffic

If a future SDK test really needs one of those, the rule is: keep the
server tiny, push the dynamism into the test code via `--fixtures-dir`
pointing at a per-test directory.
````

- [ ] **Step 2: Commit**

```bash
git add test-fixtures/mock-server/README.md
git commit -m "docs(mock-server): add full README"
```

---

### Task 14: Coverage policy document

**Files:**
- Create: `docs/coverage-policy.md`

- [ ] **Step 1: Write the policy**

`docs/coverage-policy.md`:

````markdown
# Code Coverage Policy

This document is the authoritative source for what counts as 100% code
coverage in the typecast-sdk monorepo. Every SDK's CI gate enforces it,
and every per-SDK PR must follow it.

> See the rollout design at
> [`superpowers/specs/2026-04-07-100-percent-coverage-design.md`](superpowers/specs/2026-04-07-100-percent-coverage-design.md)
> for the larger context.

## Target

Every SDK must reach **100% line + function + branch coverage** on every
pull request and on every push to `main`. Toolchains that cannot measure
branch coverage (`go test -cover`, Swift `xccov`) fall back to **100%
line + function** coverage; everything else must hit branches too.

The percentage is enforced by each SDK's coverage tool itself (e.g.
`fail_under = 100` in `coverage.py`, `--fail-under-lines 100` in
`cargo-llvm-cov`, jacoco `<rule>` with `COVEREDRATIO = 1.00`). CI just
runs `make coverage` and trusts the tool's exit code.

## Pragma whitelist

A line may be excluded from coverage measurement **only** if it falls into
one of the following categories. Anything outside this list must be tested.

### Allowed categories

1. **Physically unreachable on the test host.** Platform-specific code
   paths whose preprocessor or compile-time guard is false on the
   current OS or CPU. Example: a C `#ifdef _WIN32` block on a Linux
   runner. Both halves are still tested — they just run on different
   CI runners. The C SDK uses a CI matrix over Linux, macOS, and Windows
   for exactly this reason.

2. **Language-enforced unreachable.** Code that the language semantics
   say cannot run, including:
   - Rust `unreachable!()`, `unimplemented!()`
   - Kotlin `error("unreachable")`
   - Java private utility-class constructors that throw `AssertionError`
   - Go `panic("unreachable")` immediately following an exhaustive switch
   - Swift `fatalError("unreachable")`

3. **Type-only or generated code.** Files whose contents are not runtime
   behavior:
   - TypeScript files containing only `type` and `interface` declarations
   - Python `if TYPE_CHECKING:` blocks
   - Generated stubs

4. **Build or configuration artifacts.** `examples/`, `dist/`, `lib/`,
   `vite.config.ts`, generated build outputs. These are excluded at the
   tool-config level (collection scope), not via inline pragmas.

5. **Runtime / language ergonomic limits.** Lines the compiler emits
   that have no observable user-facing behavior:
   - Swift `defer` blocks whose unreachable cleanup the compiler still emits
   - C `assert()` failure branches in release builds

### Forbidden exclusions

Inline pragmas may **never** be used to exclude:

- HTTP / network failure handling
- Timeout paths
- Input validation
- Response parsing errors
- Authentication / authorization failures
- Any branch that depends on user-controlled input
- Any code path you "know is rare" — rarity is not a reason to skip

These categories are exactly where bugs hide. Excluding them defeats the
purpose of the gate.

## Pragma format

Every excluded line carries an inline comment with the **category** and
the **reason**.

### Examples per language

**Python:**

```python
if sys.platform == "win32":  # pragma: no cover  # category=platform reason="Windows-only fallback path"
    return _windows_path_resolver(p)
```

**TypeScript / JavaScript (vitest + v8):**

```typescript
/* c8 ignore next 3  -- category=unreachable reason="exhaustive switch over union type" */
default:
    throw new Error('unreachable');
```

**Rust (cargo-llvm-cov):**

```rust
match value {
    Variant::A => handle_a(),
    Variant::B => handle_b(),
    // category=unreachable reason="enum is non-exhaustive but only A/B exist today"
    _ => unreachable!(),
}
```

**Java / Kotlin (jacoco):**

```java
private Util() {
    // category=unreachable reason="utility class private constructor"
    throw new AssertionError("no instances");
}
```

**Go (gocover-cobertura / go test -cover):** Go's cover tool has no
inline pragma syntax. Excluded code lives in files matched by the
`coverignore` Makefile pattern (e.g. `*_unreachable.go`). Each excluded
file's package doc comment must contain
`// coverage: category=... reason=...`.

**C (gcov / lcov):**

```c
#ifdef _WIN32
    // LCOV_EXCL_START -- category=platform reason="Windows-only fallback"
    return windows_resolve(path);
    // LCOV_EXCL_STOP
#else
    return posix_resolve(path);
#endif
```

**Swift (xccov):** Swift has no inline pragma. Excluded files are listed
in the `Makefile` `coverage` target's filter step, with a doc comment in
the file body explaining the category and reason.

## Per-SDK exclusion budget

Each SDK has a starting budget of **30 excluded lines maximum** counted
across all pragma categories combined. This is a starting threshold and
will be revisited after the first 1–2 SDK PRs land with real data.

Going over the budget is not a unilateral decision: the SDK PR must
either reduce the count by writing more tests, or open a separate policy
PR adjusting the budget with justification.

## PR body declaration

Every per-SDK coverage PR includes a one-line summary of the budget use
in its description, in this format:

```
Excluded: 7 lines (platform=4, unreachable=2, type-only=1)
```

If the line is missing, the PR is not approvable.

## When the gate fails

If a CI run reports < 100% on a branch, the policy is:

1. **Add a test** that exercises the missing line. This is the default
   answer.
2. **Refactor for testability** if the missing line is logically
   unreachable but the structure of the code makes the compiler think
   otherwise. (Example: removing a redundant `default:` after an
   exhaustive switch.)
3. **Add a pragma** only if the line falls into an allowed category and
   the budget has room.

In all three cases, the change is part of the same PR. The CI gate is
not bypassed.
````

- [ ] **Step 2: Commit**

```bash
git add docs/coverage-policy.md
git commit -m "docs(coverage): add code coverage policy"
```

---

### Task 15: CI workflow template

**Files:**
- Create: `.github/workflows/coverage-template.yml`

This file is a reference template that per-SDK PRs copy and adapt. It is
**not actually triggered** — the `paths` filter is set to a non-existent
file so it never runs.

- [ ] **Step 1: Write the template**

`.github/workflows/coverage-template.yml`:

```yaml
# Reference template for per-SDK coverage workflows.
#
# This file is intentionally NOT triggered (the `paths` filter targets a
# non-existent file). Per-SDK PRs copy this file to
# `.github/workflows/coverage-<lang>.yml` and:
#
#   1. Replace `<lang>` with the SDK directory name (js, python, ...)
#   2. Replace the "Setup language toolchain" step with the language-
#      specific setup action (setup-node, setup-python, setup-go, ...)
#   3. Adjust paths in the `paths:` filter and `working-directory`
#   4. Adjust the matrix if the SDK needs cross-OS coverage (typecast-c)
#
# See docs/coverage-policy.md for the policy this enforces and
# docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md §8
# for the design.

name: coverage-template

on:
  push:
    branches: [main]
    paths:
      - '.github/workflows/coverage-template.yml.never-triggered'
  pull_request:
    paths:
      - '.github/workflows/coverage-template.yml.never-triggered'

jobs:
  coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node (for mock server)
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

      # ----------------------------------------------------------------
      # Replace this section with the language-specific toolchain setup
      # in the per-SDK copy of this file.
      # ----------------------------------------------------------------
      - name: Setup language toolchain
        run: echo "REPLACE ME with setup-node / setup-python / setup-go / ..."

      - name: Run coverage (gate enforced by tool)
        working-directory: typecast-<lang>
        env:
          TYPECAST_MOCK_URL: http://127.0.0.1:8765
        run: make coverage

      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: coverage-<lang>
          path: typecast-<lang>/coverage/

      - name: Stop mock server
        if: always()
        run: pkill -f "tsx src/index.ts" || true
```

- [ ] **Step 2: Lint the YAML by trying to parse it**

```bash
python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/coverage-template.yml')); print('ok')"
```

Expected: prints `ok`. If `python3` is unavailable, run:

```bash
node -e "const fs=require('fs'); const txt=fs.readFileSync('.github/workflows/coverage-template.yml','utf8'); if(!txt.includes('jobs:')) {process.exit(1)} console.log('ok')"
```

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/coverage-template.yml
git commit -m "ci(coverage): add reference workflow template"
```

---

### Task 16: Root README addendum

**Files:**
- Modify: `README.md` (root)

- [ ] **Step 1: Read the current root README to find the right insertion point**

```bash
head -60 README.md
```

Look at the output and identify a sensible spot — typically just before the SDK installation table or just after it. The new section is "Code Coverage" and links to the policy.

- [ ] **Step 2: Append the new section to the end of the file**

Open `README.md` and append (at the very bottom, after the last existing line):

```markdown

## Code Coverage

Every SDK in this monorepo targets **100% code coverage** (line + function
+ branch where the toolchain supports branches; line + function only on
Go and Swift). The exclusion policy is documented at
[`docs/coverage-policy.md`](docs/coverage-policy.md). The shared mock
server used by every SDK's coverage tests lives at
[`test-fixtures/mock-server/`](test-fixtures/mock-server/).

| SDK | Coverage |
|-----|----------|
| typecast-c | _coming soon_ |
| typecast-csharp | _coming soon_ |
| typecast-go | _coming soon_ |
| typecast-java | _coming soon_ |
| typecast-js | _coming soon_ |
| typecast-kotlin | _coming soon_ |
| typecast-python | _coming soon_ |
| typecast-rust | _coming soon_ |
| typecast-swift | _coming soon_ |

Each `_coming soon_` cell is replaced with a
`![coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)`
badge as that SDK's per-SDK coverage PR (steps 1–9 of the rollout) lands.
```

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs(coverage): link policy and mock server from root README"
```

---

### Task 17: Final integration smoke test

**Files:** none

This task verifies the whole infra works end-to-end before opening the PR.

- [ ] **Step 1: Re-run the full mock server test suite**

```bash
cd test-fixtures/mock-server && npm test
```

Expected: 24 passing, 0 failing.

- [ ] **Step 2: Re-run typecheck**

```bash
cd test-fixtures/mock-server && npm run typecheck
```

Expected: exit code 0.

- [ ] **Step 3: Boot the server with the seed fixtures and hit each endpoint**

```bash
cd test-fixtures/mock-server
npm start -- --port 18765 > /tmp/mock.log 2>&1 &
sleep 2

curl -sf http://127.0.0.1:18765/__mock_health
echo
curl -sf http://127.0.0.1:18765/voices/list | head -c 200
echo
curl -sfI http://127.0.0.1:18765/tts/synthesis | head -3
echo
curl -sf http://127.0.0.1:18765/__mock_sse/ssfm-stream-1 | head -20
echo

pkill -f "tsx src/index.ts" || true
```

Expected:
- Health: `ok`
- Voices: JSON starting with `{"voices":[...`. Note: only the most recently loaded fixture for `voices/list` wins; with both `list-200.json` and `list-401.json` present, the order depends on filesystem `readdir` order. This is **expected** — SDK tests will use per-test fixture directories.
- TTS: header includes `Content-Type: application/octet-stream`
- SSE: text stream containing `event: progress`

- [ ] **Step 4: Verify the worktree is clean**

```bash
cd /Users/haminlee/Documents/GitHub/scope-for-sdk/typecast-sdk && git status
```

Expected: only the untracked `conan-center-index/` and `vcpkg/` directories should remain (these existed before this branch and are not part of this work).

- [ ] **Step 5: Push and open PR**

```bash
git push -u origin feat/coverage-infra
gh pr create --title "Add shared coverage infrastructure (mock server, policy, CI template)" --body "$(cat <<'PRBODY'
# PR: feat/coverage-infra

## Overview

Step 0 of the 100% coverage rollout. Lands the foundations every per-SDK
PR (steps 1–9) will build on. No SDK code is touched in this PR.

See [`docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md`](docs/superpowers/specs/2026-04-07-100-percent-coverage-design.md)
for the full design and decision log.

## Key Implementations

- **Shared mock server** at `test-fixtures/mock-server/` (Node.js + TypeScript, 2 runtime deps: `tsx`, `ws`). Serves static REST fixtures, replays SSE chunk scripts, and replays WebSocket frame scripts. 24 passing tests covering the loader, REST matcher, SSE parser, WS parser, and full HTTP+WS integration.
- **Coverage exclusion policy** at `docs/coverage-policy.md`. Whitelist of allowed pragma categories, forbidden categories, per-language pragma syntax, 30-line/SDK budget, PR body declaration format.
- **CI workflow template** at `.github/workflows/coverage-template.yml`. Not triggered itself; per-SDK PRs copy and adapt it.
- **Root README addendum** linking the policy and mock server, with placeholder badges for each SDK.

## Changes

### `test-fixtures/mock-server/` (new)
- `package.json`, `tsconfig.json`, `.gitignore`, `README.md`
- `src/types.ts`, `fixture-loader.ts`, `rest-handler.ts`, `sse-handler.ts`, `ws-handler.ts`, `server.ts`, `index.ts`
- `test/fixture-loader.test.ts`, `rest-handler.test.ts`, `server.test.ts`, `sse-handler.test.ts`, `ws-handler.test.ts`
- `fixtures/voices/`, `fixtures/tts/`, `fixtures/sse/`, `fixtures/ws/` seed fixtures

### `docs/coverage-policy.md` (new)
- Whitelist policy and per-language pragma examples

### `.github/workflows/coverage-template.yml` (new)
- Reference workflow template

### `README.md` (modified)
- Added "Code Coverage" section with placeholder per-SDK table

## Testing

- `cd test-fixtures/mock-server && npm test` → 24 passing
- `cd test-fixtures/mock-server && npm run typecheck` → clean
- Manual smoke: server boots, `/__mock_health`, REST, SSE endpoints all respond as expected

## Excluded

This PR introduces no SDK source code changes, so the coverage policy
does not apply to it. The mock server itself is test infrastructure,
not a shipped artifact, and is intentionally outside the SDK coverage
gate.

Excluded: 0 lines

## Release impact

None. No SDK is bumped or published. The next PRs (steps 1–9) introduce
coverage tooling per SDK and trigger their own patch releases.
PRBODY
)"
```

Expected: PR created successfully. Note the PR URL.

- [ ] **Step 6: No commit needed**

This task is verification only. If anything in steps 1–4 failed, fix it
in a new commit (or amend the most recent commit) and re-run from step 1.

---

## Self-Review

### Spec coverage check

| Spec section | Plan task |
|--------------|-----------|
| §5.1 Mock server (file layout) | Tasks 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 |
| §5.1 Mock server (fixtures) | Task 12 |
| §5.1 Mock server behavior model — health endpoint | Task 5 |
| §5.1 Mock server behavior model — REST static matching | Task 4, 6 |
| §5.1 Mock server behavior model — SSE script replay | Tasks 7, 8 |
| §5.1 Mock server behavior model — WS frame script replay | Tasks 9, 10 |
| §5.1 Mock server — single Node process, started by CI | Task 11 (CLI) + Task 15 (workflow template) |
| §5.1 Mock server — `--port`, `--fixtures-dir` | Task 11 |
| §5.1 Mock server — health check at `/__mock_health` | Task 5 |
| §5.1 Mock server — deliberately small | Task 13 (README "Out of scope") |
| §5.2 Coverage policy doc | Task 14 |
| §5.3 CI workflow template | Task 15 |
| §5.4 Root README addendum | Task 16 |

All §5 items are covered. Steps 1–9 (per-SDK PRs) get their own plans
later and are not in scope for this plan.

### Placeholder scan

No "TBD", "TODO", "implement later", or "fill in details" markers.
All code blocks contain real implementation. All commands have explicit
expected output.

### Type consistency

- `FixtureSet` is defined in Task 2 and used by tasks 3, 4, 5, 6, 8, 10
  with consistent shape (`rest`, `sse`, `ws` maps).
- `RestFixture` shape (`method`, `path`, `status`, `contentType`, `body`)
  is defined in Task 2 and used identically in Tasks 3, 4, 6.
- `SseScriptChunk` (`delayMs`, `chunk`) and `WsScriptFrame`
  (`delayMs`, `opcode`, `payload`, `closeCode`) are defined in Task 2
  and used as-is by Tasks 7 and 9.
- `createServer` signature `({ port, fixturesDir }) → { url, close }`
  is established in Task 5 and unchanged through Task 10.
- `loadFixtures(rootDir)` signature is established in Task 3 and used
  unchanged by Task 5.
- `matchRest(method, pathWithQuery, fixtures)` signature established
  in Task 4 and used unchanged by Task 6.

No naming drift. No undefined references.

### Scope check

This plan covers exactly Step 0 of the rollout. It produces a
self-contained, working PR that does not block on any other work. The
nine SDK PRs (Steps 1–9) will each get their own plan, generated after
this one is merged.
