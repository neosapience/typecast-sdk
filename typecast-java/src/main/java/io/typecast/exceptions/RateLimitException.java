package io.typecast.exceptions;

/**
 * Exception thrown when the API returns a 429 Too Many Requests error.
 * 
 * <p>This indicates the rate limit has been exceeded.</p>
 */
public class RateLimitException extends TypecastException {
    
    /**
     * Creates a new RateLimitException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public RateLimitException(String message, String responseBody) {
        super(message, 429, responseBody);
    }

    /**
     * Creates a new RateLimitException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public RateLimitException(String message, String responseBody, Throwable cause) {
        super(message, 429, responseBody, cause);
    }
}
