package com.neosapience.exceptions;

/**
 * Exception thrown when the API returns a 401 Unauthorized error.
 * 
 * <p>This typically indicates an invalid or missing API key.</p>
 */
public class UnauthorizedException extends TypecastException {
    
    /**
     * Creates a new UnauthorizedException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public UnauthorizedException(String message, String responseBody) {
        super(message, 401, responseBody);
    }

    /**
     * Creates a new UnauthorizedException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public UnauthorizedException(String message, String responseBody, Throwable cause) {
        super(message, 401, responseBody, cause);
    }
}
