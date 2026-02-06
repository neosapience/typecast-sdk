package com.neosapience.exceptions;

/**
 * Base exception for all Typecast SDK errors.
 */
public class TypecastException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    /**
     * Creates a new TypecastException.
     * 
     * @param message      the error message
     * @param statusCode   the HTTP status code
     * @param responseBody the raw response body
     */
    public TypecastException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Creates a new TypecastException with a cause.
     * 
     * @param message      the error message
     * @param statusCode   the HTTP status code
     * @param responseBody the raw response body
     * @param cause        the underlying cause
     */
    public TypecastException(String message, int statusCode, String responseBody, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Creates a new TypecastException with only a message.
     * 
     * @param message the error message
     */
    public TypecastException(String message) {
        super(message);
        this.statusCode = 0;
        this.responseBody = null;
    }

    /**
     * Creates a new TypecastException with a message and cause.
     * 
     * @param message the error message
     * @param cause   the underlying cause
     */
    public TypecastException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    /**
     * Gets the HTTP status code.
     * 
     * @return the status code, or 0 if not applicable
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Gets the raw response body.
     * 
     * @return the response body, or null if not available
     */
    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "message='" + getMessage() + '\'' +
                ", statusCode=" + statusCode +
                '}';
    }
}
