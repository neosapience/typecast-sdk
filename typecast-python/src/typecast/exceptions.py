class TypecastError(Exception):
    """Base exception for Typecast SDK"""

    def __init__(self, message: str, status_code: int = None):
        self.message = message
        self.status_code = status_code
        super().__init__(self.message)


class BadRequestError(TypecastError):
    """400 Bad Request - Invalid request parameters"""

    def __init__(self, message: str):
        super().__init__(message, status_code=400)


class UnauthorizedError(TypecastError):
    """401 Unauthorized - Invalid or missing API key"""

    def __init__(self, message: str):
        super().__init__(message, status_code=401)


class PaymentRequiredError(TypecastError):
    """402 Payment Required - Insufficient credits or subscription required"""

    def __init__(self, message: str):
        super().__init__(message, status_code=402)


class NotFoundError(TypecastError):
    """404 Not Found - Resource not found"""

    def __init__(self, message: str):
        super().__init__(message, status_code=404)


class RateLimitError(TypecastError):
    """429 Too Many Requests - Rate limit exceeded"""

    def __init__(self, message: str):
        super().__init__(message, status_code=429)


class UnprocessableEntityError(TypecastError):
    """422 Unprocessable Entity - Validation error"""

    def __init__(self, message: str):
        super().__init__(message, status_code=422)


class InternalServerError(TypecastError):
    """500 Internal Server Error - Server error"""

    def __init__(self, message: str):
        super().__init__(message, status_code=500)
