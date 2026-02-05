package io.typecast.exceptions;

/**
 * Exception thrown when the API returns a 402 Payment Required error.
 * 
 * <p>This typically indicates insufficient credits in the account.</p>
 */
public class PaymentRequiredException extends TypecastException {
    
    /**
     * Creates a new PaymentRequiredException.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     */
    public PaymentRequiredException(String message, String responseBody) {
        super(message, 402, responseBody);
    }

    /**
     * Creates a new PaymentRequiredException with a cause.
     * 
     * @param message      the error message
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public PaymentRequiredException(String message, String responseBody, Throwable cause) {
        super(message, 402, responseBody, cause);
    }
}
