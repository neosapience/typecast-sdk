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
