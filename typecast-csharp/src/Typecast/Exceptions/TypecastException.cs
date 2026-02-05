namespace Typecast.Exceptions;

/// <summary>
/// Base exception for all Typecast API errors.
/// </summary>
public class TypecastException : Exception
{
    /// <summary>
    /// The HTTP status code returned by the API.
    /// </summary>
    public int StatusCode { get; }

    /// <summary>
    /// The raw response body from the API (if available).
    /// </summary>
    public string? ResponseBody { get; }

    /// <summary>
    /// Creates a new TypecastException.
    /// </summary>
    /// <param name="message">The error message</param>
    /// <param name="statusCode">The HTTP status code</param>
    /// <param name="responseBody">The raw response body (optional)</param>
    /// <param name="innerException">The inner exception (optional)</param>
    public TypecastException(string message, int statusCode = 0, string? responseBody = null, Exception? innerException = null)
        : base(message, innerException)
    {
        StatusCode = statusCode;
        ResponseBody = responseBody;
    }

    /// <summary>
    /// Creates an appropriate exception based on the HTTP status code.
    /// </summary>
    /// <param name="statusCode">The HTTP status code</param>
    /// <param name="responseBody">The response body</param>
    /// <returns>A TypecastException or derived exception</returns>
    public static TypecastException FromStatusCode(int statusCode, string? responseBody)
    {
        var message = GetMessageForStatusCode(statusCode, responseBody);
        
        return statusCode switch
        {
            400 => new BadRequestException(message, responseBody),
            401 or 403 => new UnauthorizedException(message, responseBody),
            402 => new PaymentRequiredException(message, responseBody),
            404 => new NotFoundException(message, responseBody),
            422 => new UnprocessableEntityException(message, responseBody),
            429 => new RateLimitException(message, responseBody),
            >= 500 and < 600 => new InternalServerException(message, statusCode, responseBody),
            _ => new TypecastException(message, statusCode, responseBody)
        };
    }

    private static string GetMessageForStatusCode(int statusCode, string? responseBody)
    {
        var baseMessage = statusCode switch
        {
            400 => "Bad request",
            401 or 403 => "Unauthorized: Invalid or missing API key",
            402 => "Payment required: Insufficient credits",
            404 => "Resource not found",
            422 => "Validation error",
            429 => "Rate limit exceeded",
            500 => "Internal server error",
            502 => "Bad gateway",
            503 => "Service unavailable",
            504 => "Gateway timeout",
            _ => $"HTTP error {statusCode}"
        };

        if (!string.IsNullOrWhiteSpace(responseBody))
        {
            return $"{baseMessage}: {responseBody}";
        }

        return baseMessage;
    }
}

/// <summary>
/// Exception thrown when the request is malformed or invalid (HTTP 400).
/// </summary>
public class BadRequestException : TypecastException
{
    /// <summary>
    /// Creates a new BadRequestException.
    /// </summary>
    public BadRequestException(string message, string? responseBody = null, Exception? innerException = null)
        : base(message, 400, responseBody, innerException)
    {
    }
}

/// <summary>
/// Exception thrown when authentication fails (HTTP 401).
/// </summary>
public class UnauthorizedException : TypecastException
{
    /// <summary>
    /// Creates a new UnauthorizedException.
    /// </summary>
    public UnauthorizedException(string message, string? responseBody = null, Exception? innerException = null)
        : base(message, 401, responseBody, innerException)
    {
    }
}

/// <summary>
/// Exception thrown when the account has insufficient credits (HTTP 402).
/// </summary>
public class PaymentRequiredException : TypecastException
{
    /// <summary>
    /// Creates a new PaymentRequiredException.
    /// </summary>
    public PaymentRequiredException(string message, string? responseBody = null, Exception? innerException = null)
        : base(message, 402, responseBody, innerException)
    {
    }
}

/// <summary>
/// Exception thrown when a requested resource is not found (HTTP 404).
/// </summary>
public class NotFoundException : TypecastException
{
    /// <summary>
    /// Creates a new NotFoundException.
    /// </summary>
    public NotFoundException(string message, string? responseBody = null, Exception? innerException = null)
        : base(message, 404, responseBody, innerException)
    {
    }
}

/// <summary>
/// Exception thrown when request validation fails (HTTP 422).
/// </summary>
public class UnprocessableEntityException : TypecastException
{
    /// <summary>
    /// Creates a new UnprocessableEntityException.
    /// </summary>
    public UnprocessableEntityException(string message, string? responseBody = null, Exception? innerException = null)
        : base(message, 422, responseBody, innerException)
    {
    }
}

/// <summary>
/// Exception thrown when rate limit is exceeded (HTTP 429).
/// </summary>
public class RateLimitException : TypecastException
{
    /// <summary>
    /// Creates a new RateLimitException.
    /// </summary>
    public RateLimitException(string message, string? responseBody = null, Exception? innerException = null)
        : base(message, 429, responseBody, innerException)
    {
    }
}

/// <summary>
/// Exception thrown when a server error occurs (HTTP 5xx).
/// </summary>
public class InternalServerException : TypecastException
{
    /// <summary>
    /// Creates a new InternalServerException.
    /// </summary>
    public InternalServerException(string message, int statusCode = 500, string? responseBody = null, Exception? innerException = null)
        : base(message, statusCode, responseBody, innerException)
    {
    }
}
