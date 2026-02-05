package io.typecast.exceptions;

/**
 * Exception thrown when the API returns a 404 Not Found error.
 * 
 * <p>This typically indicates the requested resource (e.g., voice) was not found.</p>
 */
public class NotFoundException extends TypecastException {
    
    /**
     * Creates a new NotFoundException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public NotFoundException(String message, String responseBody) {
        super(message, 404, responseBody);
    }

    /**
     * Creates a new NotFoundException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public NotFoundException(String message, String responseBody, Throwable cause) {
        super(message, 404, responseBody, cause);
    }
}
