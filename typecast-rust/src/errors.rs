//! Error types for the Typecast SDK
//!
//! This module contains all error types that can be returned by the SDK.

use crate::models::ErrorResponse;
use thiserror::Error;

/// Error type for Typecast API operations
#[derive(Error, Debug)]
pub enum TypecastError {
    /// Bad Request - The request was invalid or cannot be served
    #[error("Bad Request - {detail}")]
    BadRequest { detail: String },

    /// Unauthorized - Invalid or missing API key
    #[error("Unauthorized - Invalid or missing API key")]
    Unauthorized { detail: String },

    /// Payment Required - Insufficient credits to complete the request
    #[error("Payment Required - Insufficient credits")]
    PaymentRequired { detail: String },

    /// Forbidden - Access denied
    #[error("Forbidden - Access denied")]
    Forbidden { detail: String },

    /// Not Found - The requested resource does not exist
    #[error("Not Found - {detail}")]
    NotFound { detail: String },

    /// Validation Error - The request data failed validation
    #[error("Validation Error - {detail}")]
    ValidationError { detail: String },

    /// Too Many Requests - Rate limit exceeded
    #[error("Too Many Requests - Rate limit exceeded")]
    RateLimited { detail: String },

    /// Internal Server Error - Something went wrong on the server
    #[error("Internal Server Error - {detail}")]
    ServerError { detail: String },

    /// HTTP client error
    #[error("HTTP error: {0}")]
    HttpError(#[from] reqwest::Error),

    /// JSON serialization/deserialization error
    #[error("JSON error: {0}")]
    JsonError(#[from] serde_json::Error),

    /// Unknown error with status code
    #[error("API error (status {status_code}): {detail}")]
    Unknown { status_code: u16, detail: String },
}

impl TypecastError {
    /// Create an error from an HTTP response status code and optional error response
    pub fn from_response(status_code: u16, error_response: Option<ErrorResponse>) -> Self {
        let detail = error_response
            .map(|e| e.detail)
            .unwrap_or_else(|| "Unknown error".to_string());

        match status_code {
            400 => TypecastError::BadRequest { detail },
            401 => TypecastError::Unauthorized { detail },
            402 => TypecastError::PaymentRequired { detail },
            403 => TypecastError::Forbidden { detail },
            404 => TypecastError::NotFound { detail },
            422 => TypecastError::ValidationError { detail },
            429 => TypecastError::RateLimited { detail },
            500..=599 => TypecastError::ServerError { detail },
            _ => TypecastError::Unknown { status_code, detail },
        }
    }

    /// Check if this error is a bad request error
    pub fn is_bad_request(&self) -> bool {
        matches!(self, TypecastError::BadRequest { .. })
    }

    /// Check if this error is an unauthorized error
    pub fn is_unauthorized(&self) -> bool {
        matches!(self, TypecastError::Unauthorized { .. })
    }

    /// Check if this error is a payment required error
    pub fn is_payment_required(&self) -> bool {
        matches!(self, TypecastError::PaymentRequired { .. })
    }

    /// Check if this error is a forbidden error
    pub fn is_forbidden(&self) -> bool {
        matches!(self, TypecastError::Forbidden { .. })
    }

    /// Check if this error is a not found error
    pub fn is_not_found(&self) -> bool {
        matches!(self, TypecastError::NotFound { .. })
    }

    /// Check if this error is a validation error
    pub fn is_validation_error(&self) -> bool {
        matches!(self, TypecastError::ValidationError { .. })
    }

    /// Check if this error is a rate limit error
    pub fn is_rate_limited(&self) -> bool {
        matches!(self, TypecastError::RateLimited { .. })
    }

    /// Check if this error is a server error
    pub fn is_server_error(&self) -> bool {
        matches!(self, TypecastError::ServerError { .. })
    }

    /// Get the status code if available
    pub fn status_code(&self) -> Option<u16> {
        match self {
            TypecastError::BadRequest { .. } => Some(400),
            TypecastError::Unauthorized { .. } => Some(401),
            TypecastError::PaymentRequired { .. } => Some(402),
            TypecastError::Forbidden { .. } => Some(403),
            TypecastError::NotFound { .. } => Some(404),
            TypecastError::ValidationError { .. } => Some(422),
            TypecastError::RateLimited { .. } => Some(429),
            TypecastError::ServerError { .. } => Some(500),
            TypecastError::Unknown { status_code, .. } => Some(*status_code),
            _ => None,
        }
    }
}

/// Result type alias for Typecast operations
pub type Result<T> = std::result::Result<T, TypecastError>;
