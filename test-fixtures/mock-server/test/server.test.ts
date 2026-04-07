import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
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
