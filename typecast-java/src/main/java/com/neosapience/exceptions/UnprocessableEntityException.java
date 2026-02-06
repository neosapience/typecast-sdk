package com.neosapience.exceptions;

/**
 * Exception thrown when the API returns a 422 Unprocessable Entity error.
 * 
 * <p>This typically indicates a validation error in the request.</p>
 */
public class UnprocessableEntityException extends TypecastException {
    
    /**
     * Creates a new UnprocessableEntityException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public UnprocessableEntityException(String message, String responseBody) {
        super(message, 422, responseBody);
    }

    /**
     * Creates a new UnprocessableEntityException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public UnprocessableEntityException(String message, String responseBody, Throwable cause) {
        super(message, 422, responseBody, cause);
    }
}
