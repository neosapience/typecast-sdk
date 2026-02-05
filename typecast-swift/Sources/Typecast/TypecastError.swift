import Foundation

/// Error response from the Typecast API
public struct APIErrorResponse: Codable, Sendable {
    public let detail: String
}

/// Typecast API errors
public enum TypecastError: LocalizedError, Sendable {
    /// Bad request (HTTP 400) - Invalid parameters
    case badRequest(String)
    /// Unauthorized (HTTP 401) - Invalid or missing API key
    case unauthorized(String)
    /// Payment required (HTTP 402) - Insufficient credits
    case paymentRequired(String)
    /// Not found (HTTP 404) - Voice model not available
    case notFound(String)
    /// Validation error (HTTP 422) - Request validation failed
    case validationError(String)
    /// Rate limit exceeded (HTTP 429) - Too many requests
    case rateLimitExceeded(String)
    /// Internal server error (HTTP 500) - Server processing failed
    case serverError(String)
    /// Network or other error
    case networkError(Error)
    /// Invalid response from server
    case invalidResponse(String)
    /// Unknown error with status code
    case unknown(statusCode: Int, message: String)
    
    public var errorDescription: String? {
        switch self {
        case .badRequest(let message):
            return "Bad Request: \(message)"
        case .unauthorized(let message):
            return "Unauthorized: \(message)"
        case .paymentRequired(let message):
            return "Payment Required: \(message)"
        case .notFound(let message):
            return "Not Found: \(message)"
        case .validationError(let message):
            return "Validation Error: \(message)"
        case .rateLimitExceeded(let message):
            return "Rate Limit Exceeded: \(message)"
        case .serverError(let message):
            return "Server Error: \(message)"
        case .networkError(let error):
            return "Network Error: \(error.localizedDescription)"
        case .invalidResponse(let message):
            return "Invalid Response: \(message)"
        case .unknown(let statusCode, let message):
            return "Error \(statusCode): \(message)"
        }
    }
    
    /// HTTP status code for the error
    public var statusCode: Int? {
        switch self {
        case .badRequest: return 400
        case .unauthorized: return 401
        case .paymentRequired: return 402
        case .notFound: return 404
        case .validationError: return 422
        case .rateLimitExceeded: return 429
        case .serverError: return 500
        case .unknown(let code, _): return code
        case .networkError, .invalidResponse: return nil
        }
    }
    
    /// Create error from HTTP response
    static func fromResponse(statusCode: Int, data: Data?) -> TypecastError {
        let message: String
        if let data = data,
           let errorResponse = try? JSONDecoder().decode(APIErrorResponse.self, from: data) {
            message = errorResponse.detail
        } else if let data = data,
                  let text = String(data: data, encoding: .utf8) {
            message = text
        } else {
            message = "Unknown error"
        }
        
        switch statusCode {
        case 400:
            return .badRequest(message)
        case 401:
            return .unauthorized(message)
        case 402:
            return .paymentRequired(message)
        case 404:
            return .notFound(message)
        case 422:
            return .validationError(message)
        case 429:
            return .rateLimitExceeded(message)
        case 500...599:
            return .serverError(message)
        default:
            return .unknown(statusCode: statusCode, message: message)
        }
    }
}
