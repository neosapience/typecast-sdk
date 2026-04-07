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
