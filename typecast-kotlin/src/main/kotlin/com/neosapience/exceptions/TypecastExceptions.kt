package com.neosapience.exceptions

/**
 * Base exception for all Typecast API errors.
 */
open class TypecastException(
    message: String,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    override fun toString(): String {
        return buildString {
            append(this@TypecastException.javaClass.simpleName)
            append(": ")
            append(message)
            statusCode?.let { append(" (status: $it)") }
        }
    }
}

/**
 * 400 Bad Request - Invalid request parameters.
 */
class BadRequestException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 400, responseBody)

/**
 * 401 Unauthorized - Authentication failed.
 */
class UnauthorizedException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 401, responseBody)

/**
 * 402 Payment Required - Insufficient credits.
 */
class PaymentRequiredException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 402, responseBody)

/**
 * 403 Forbidden - Access denied.
 */
class ForbiddenException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 403, responseBody)

/**
 * 404 Not Found - Resource not found.
 */
class NotFoundException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 404, responseBody)

/**
 * 422 Unprocessable Entity - Request validation failed.
 */
class UnprocessableEntityException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 422, responseBody)

/**
 * 429 Too Many Requests - Rate limit exceeded.
 */
class RateLimitException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 429, responseBody)

/**
 * 500 Internal Server Error - Server processing failed.
 */
class InternalServerException(
    message: String,
    responseBody: String? = null
) : TypecastException(message, 500, responseBody)
