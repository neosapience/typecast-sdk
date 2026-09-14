import { expect, it } from 'vitest';
import { TypecastClient } from '../../src/client';
import { parsePauseMarkup } from '../../src/composer';
import { TypecastAPIError } from '../../src/errors';

it('omits null and undefined query values', () => {
  const client = new TypecastClient({ apiKey: 'test-key' });
  const url = new URL(client['buildUrl']('/v3/voices', { a: null, b: undefined, c: 0 }));
  expect(url.search).toBe('?c=0');
});

it('preserves invalid pause tokens as text', () => {
  for (const text of ['<|0s|>', `<|${'9'.repeat(310)}s|>`]) {
    expect(parsePauseMarkup(text)).toEqual([{ kind: 'text', text }]);
  }
});

it('parses adjacent pauses without introducing empty text parts', () => {
  expect(parsePauseMarkup('<|1s|><|2s|>')).toEqual([
    { kind: 'pause', seconds: 1 },
    { kind: 'pause', seconds: 2 },
  ]);
  expect(parsePauseMarkup('')).toEqual([]);
});

it('constructs errors on engines without captureStackTrace', () => {
  const descriptor = Object.getOwnPropertyDescriptor(Error, 'captureStackTrace')!;
  try {
    Object.defineProperty(Error, 'captureStackTrace', { value: undefined, configurable: true });
    expect(new TypecastAPIError('boom', 500).message).toBe('boom');
  } finally {
    Object.defineProperty(Error, 'captureStackTrace', descriptor);
  }
});
