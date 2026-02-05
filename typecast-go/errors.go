package typecast

import (
	"fmt"
)

// APIError represents an error returned by the Typecast API
type APIError struct {
	StatusCode int
	Message    string
	Detail     string
}

func (e *APIError) Error() string {
	if e.Detail != "" {
		return fmt.Sprintf("%s - %s", e.Message, e.Detail)
	}
	return e.Message
}

// NewAPIError creates a new APIError from an HTTP response
func NewAPIError(statusCode int, detail string) *APIError {
	var message string

	switch statusCode {
	case 400:
		message = "Bad Request - The request was invalid or cannot be served"
	case 401:
		message = "Unauthorized - Invalid or missing API key"
	case 402:
		message = "Payment Required - Insufficient credits to complete the request"
	case 403:
		message = "Forbidden - Access denied, check your API key"
	case 404:
		message = "Not Found - The requested resource does not exist"
	case 422:
		message = "Validation Error - The request data failed validation"
	case 429:
		message = "Too Many Requests - Rate limit exceeded"
	case 500:
		message = "Internal Server Error - Something went wrong on the server"
	default:
		message = fmt.Sprintf("API request failed with status %d", statusCode)
	}

	return &APIError{
		StatusCode: statusCode,
		Message:    message,
		Detail:     detail,
	}
}

// IsBadRequest returns true if the error is a 400 Bad Request
func (e *APIError) IsBadRequest() bool {
	return e.StatusCode == 400
}

// IsUnauthorized returns true if the error is a 401 Unauthorized
func (e *APIError) IsUnauthorized() bool {
	return e.StatusCode == 401
}

// IsPaymentRequired returns true if the error is a 402 Payment Required
func (e *APIError) IsPaymentRequired() bool {
	return e.StatusCode == 402
}

// IsNotFound returns true if the error is a 404 Not Found
func (e *APIError) IsNotFound() bool {
	return e.StatusCode == 404
}

// IsValidationError returns true if the error is a 422 Validation Error
func (e *APIError) IsValidationError() bool {
	return e.StatusCode == 422
}

// IsRateLimited returns true if the error is a 429 Too Many Requests
func (e *APIError) IsRateLimited() bool {
	return e.StatusCode == 429
}

// IsServerError returns true if the error is a 5xx Server Error
func (e *APIError) IsServerError() bool {
	return e.StatusCode >= 500 && e.StatusCode < 600
}

// IsForbidden returns true if the error is a 403 Forbidden
func (e *APIError) IsForbidden() bool {
	return e.StatusCode == 403
}
