package com.neosapience.exceptions;

/**
 * Exception thrown when the API returns a 403 Forbidden error.
 * 
 * <p>This typically indicates invalid credentials or access denied.</p>
 */
public class ForbiddenException extends TypecastException {
    
    /**
     * Creates a new ForbiddenException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public ForbiddenException(String message, String responseBody) {
        super(message, 403, responseBody);
    }

    /**
     * Creates a new ForbiddenException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public ForbiddenException(String message, String responseBody, Throwable cause) {
        super(message, 403, responseBody, cause);
    }
}
