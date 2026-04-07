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
