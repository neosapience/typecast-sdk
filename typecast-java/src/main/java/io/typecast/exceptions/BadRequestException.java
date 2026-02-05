package io.typecast.exceptions;

/**
 * Exception thrown when the API returns a 400 Bad Request error.
 * 
 * <p>This typically indicates invalid request parameters.</p>
 */
public class BadRequestException extends TypecastException {
    
    /**
     * Creates a new BadRequestException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public BadRequestException(String message, String responseBody) {
        super(message, 400, responseBody);
    }

    /**
     * Creates a new BadRequestException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public BadRequestException(String message, String responseBody, Throwable cause) {
        super(message, 400, responseBody, cause);
    }
}
