import { describe, it, expect } from 'vitest';
import { TypecastAPIError } from '../../src/errors';

describe('TypecastAPIError', () => {
  describe('fromResponse status mapping', () => {
    const cases: Array<[number, string, RegExp]> = [
      [400, 'Bad Request', /Bad Request/],
      [401, 'Unauthorized', /Unauthorized - Invalid or missing API key/],
      [402, 'Payment Required', /Payment Required/],
      [404, 'Not Found', /Not Found/],
      [422, 'Unprocessable Entity', /Validation Error/],
      [500, 'Internal Server Error', /Internal Server Error/],
    ];

    for (const [status, statusText, expectedMessage] of cases) {
      it(`maps status ${status} to a friendly message`, () => {
        const err = TypecastAPIError.fromResponse(status, statusText);

        expect(err).toBeInstanceOf(TypecastAPIError);
        expect(err.statusCode).toBe(status);
        expect(err.message).toMatch(expectedMessage);
        expect(err.name).toBe('TypecastAPIError');
      });
    }

    it('falls back to a generic message for unknown status codes', () => {
      const err = TypecastAPIError.fromResponse(418, "I'm a teapot");

      expect(err.statusCode).toBe(418);
      expect(err.message).toBe("API request failed with status 418: I'm a teapot");
    });
  });

  describe('fromResponse detail handling', () => {
    it('appends a string detail to the message', () => {
      const err = TypecastAPIError.fromResponse(422, 'Unprocessable Entity', {
        detail: 'voice_id is required',
      });

      expect(err.message).toMatch(/Validation Error/);
      expect(err.message).toMatch(/voice_id is required$/);
    });

    it('JSON-stringifies a non-string detail', () => {
      const detail = [
        { loc: ['body', 'voice_id'], msg: 'field required', type: 'value_error.missing' },
      ];
      const err = TypecastAPIError.fromResponse(422, 'Unprocessable Entity', { detail });

      expect(err.message).toMatch(/Validation Error/);
      expect(err.message).toMatch(/voice_id/);
      expect(err.message).toMatch(/field required/);
    });

    it('omits the detail suffix when no detail is provided', () => {
      const err = TypecastAPIError.fromResponse(500, 'Internal Server Error', {});

      expect(err.message).toBe('Internal Server Error - Something went wrong on the server');
    });

    it('omits the detail suffix when data is undefined', () => {
      const err = TypecastAPIError.fromResponse(500, 'Internal Server Error');

      expect(err.message).toBe('Internal Server Error - Something went wrong on the server');
    });
  });

  describe('constructor', () => {
    it('exposes statusCode and response on the instance', () => {
      const data = { detail: 'something went wrong' };
      const err = new TypecastAPIError('boom', 503, data);

      expect(err.message).toBe('boom');
      expect(err.statusCode).toBe(503);
      expect(err.response).toBe(data);
      expect(err.name).toBe('TypecastAPIError');
      expect(err.stack).toContain('TypecastAPIError');
    });

    it('does not require a response payload', () => {
      const err = new TypecastAPIError('boom', 500);

      expect(err.statusCode).toBe(500);
      expect(err.response).toBeUndefined();
    });
  });
});
