/**
 * API error response structure
 * Based on Typecast API documentation: https://typecast.ai/docs/api-reference
 */
export interface ApiErrorResponse {
  detail?: string | string[] | Record<string, unknown>;
  message?: string;
  error?: string;
  [key: string]: unknown;
}

/**
 * Validation error detail structure (422 responses)
 */
export interface ValidationError {
  loc: (string | number)[];
  msg: string;
  type: string;
}

export interface ValidationErrorResponse {
  detail: ValidationError[];
}

