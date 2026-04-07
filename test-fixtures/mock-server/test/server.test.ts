import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { WebSocket } from 'ws';
import { createServer } from '../src/server.ts';

async function emptyFixturesDir(): Promise<string> {
  const dir = await mkdtemp(join(tmpdir(), 'mock-srv-'));
  await mkdir(join(dir, 'voices'));
  await mkdir(join(dir, 'tts'));
  await mkdir(join(dir, 'sse'));
  await mkdir(join(dir, 'ws'));
  return dir;
}

function withTimeout<T>(p: Promise<T>, ms: number, label: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const t = setTimeout(() => reject(new Error(`timeout after ${ms}ms: ${label}`)), ms);
    p.then(
      (v) => {
        clearTimeout(t);
        resolve(v);
      },
      (e) => {
        clearTimeout(t);
        reject(e);
      },
    );
  });
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
    await withTimeout(
      new Promise<void>((resolve, reject) => {
        const ws = new WebSocket(wsUrl);
        ws.on('message', (data) => messages.push(data.toString()));
        ws.on('close', () => resolve());
        ws.on('error', reject);
      }),
      5000,
      'ws replay close',
    );
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
    const closeCode = await withTimeout(
      new Promise<number>((resolve, reject) => {
        const ws = new WebSocket(wsUrl);
        ws.on('close', (code) => resolve(code));
        ws.on('error', reject);
      }),
      5000,
      'unknown ws close',
    );
    assert.equal(closeCode, 1008);
  } finally {
    await handle.close();
    await rm(dir, { recursive: true });
  }
});
