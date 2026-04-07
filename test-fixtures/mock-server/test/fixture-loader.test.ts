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

test('loadFixtures: when two REST fixtures collide, the lexicographically last filename wins', async () => {
  const dir = await makeFixturesDir();
  try {
    // 200 and 401 collide on the same (GET, /voices/list) key. After sorting,
    // `list-401.json` is processed last and must win on every OS.
    await writeFile(join(dir, 'voices', 'list-200.json'), JSON.stringify({ ok: true }));
    await writeFile(join(dir, 'voices', 'list-401.json'), JSON.stringify({ error: 'unauthorized' }));
    const set = await loadFixtures(dir);
    const fixture = set.rest.get('GET /voices/list');
    assert.ok(fixture);
    assert.equal(fixture.status, 401);
  } finally {
    await rm(dir, { recursive: true });
  }
});
