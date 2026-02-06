package com.neosapience.exceptions;

/**
 * Exception thrown when the API returns a 500 Internal Server Error.
 * 
 * <p>This indicates a server-side error.</p>
 */
public class InternalServerException extends TypecastException {
    
    /**
     * Creates a new InternalServerException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public InternalServerException(String message, String responseBody) {
        super(message, 500, responseBody);
    }

    /**
     * Creates a new InternalServerException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public InternalServerException(String message, String responseBody, Throwable cause) {
        super(message, 500, responseBody, cause);
    }
}
