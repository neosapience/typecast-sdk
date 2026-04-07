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
