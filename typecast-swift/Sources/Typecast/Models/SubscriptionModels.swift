import Foundation

// MARK: - Subscription Enums

/// Subscription plan tier
public enum PlanTier: String, Codable, Sendable {
    case free = "free"
    case lite = "lite"
    case plus = "plus"
    case custom = "custom"
}

// MARK: - Subscription Response Models

/// Credit usage information
public struct Credits: Codable, Sendable {
    /// Total credits provided by the plan
    public let planCredits: Int
    /// Number of credits used
    public let usedCredits: Int

    enum CodingKeys: String, CodingKey {
        case planCredits = "plan_credits"
        case usedCredits = "used_credits"
    }
}

/// Usage limit information
public struct Limits: Codable, Sendable {
    /// Maximum number of concurrent requests allowed
    public let concurrencyLimit: Int

    enum CodingKeys: String, CodingKey {
        case concurrencyLimit = "concurrency_limit"
    }
}

/// Response from `GET /v1/users/me/subscription`
public struct SubscriptionResponse: Codable, Sendable {
    /// Current subscription plan tier
    public let plan: PlanTier
    /// Credit usage information
    public let credits: Credits
    /// Usage limit information
    public let limits: Limits
}
