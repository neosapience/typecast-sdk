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
