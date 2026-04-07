import { describe, it, expect } from 'vitest';
import * as pkg from '../../src/index';

describe('package barrel', () => {
  it('re-exports TypecastClient and TypecastAPIError', () => {
    expect(pkg.TypecastClient).toBeDefined();
    expect(typeof pkg.TypecastClient).toBe('function');
    expect(pkg.TypecastAPIError).toBeDefined();
    expect(typeof pkg.TypecastAPIError).toBe('function');
  });

  it('TypecastClient from the barrel constructs the same shape as a direct import', () => {
    const instance = new pkg.TypecastClient({
      baseHost: 'https://example.test',
      apiKey: 'k',
    });
    expect(instance).toBeInstanceOf(pkg.TypecastClient);
  });
});
