import { ApiErrorResponse } from './types';

export class TypecastAPIError extends Error {
  public readonly statusCode: number;
  public readonly response?: ApiErrorResponse;

  constructor(message: string, statusCode: number, response?: ApiErrorResponse) {
    super(message);
    this.name = 'TypecastAPIError';
    this.statusCode = statusCode;
    this.response = response;

    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, TypecastAPIError);
    }
  }

  static fromResponse(statusCode: number, statusText: string, data?: ApiErrorResponse): TypecastAPIError {
    let message: string;

    switch (statusCode) {
      case 400:
        message = 'Bad Request - The request was invalid or cannot be served';
        break;
      case 401:
        message = 'Unauthorized - Invalid or missing API key';
        break;
      case 402:
        message = 'Payment Required - Insufficient credits to complete the request';
        break;
      case 404:
        message = 'Not Found - The requested resource does not exist';
        break;
      case 422:
        message = 'Validation Error - The request data failed validation';
        break;
      case 500:
        message = 'Internal Server Error - Something went wrong on the server';
        break;
      default:
        message = `API request failed with status ${statusCode}: ${statusText}`;
    }

    if (data?.detail) {
      const detailStr = typeof data.detail === 'string' 
        ? data.detail 
        : JSON.stringify(data.detail);
      message += ` - ${detailStr}`;
    }

    return new TypecastAPIError(message, statusCode, data);
  }
}

