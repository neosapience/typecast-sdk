package com.neosapience.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subscription plan tier.
 */
@Serializable
enum class PlanTier(val value: String) {
    @SerialName("free")
    FREE("free"),

    @SerialName("lite")
    LITE("lite"),

    @SerialName("plus")
    PLUS("plus"),

    @SerialName("custom")
    CUSTOM("custom")
}

/**
 * Credit usage information.
 */
@Serializable
data class Credits(
    @SerialName("plan_credits")
    val planCredits: Long,

    @SerialName("used_credits")
    val usedCredits: Long
)

/**
 * Usage limit information.
 */
@Serializable
data class Limits(
    @SerialName("concurrency_limit")
    val concurrencyLimit: Long
)

/**
 * Response from `GET /v1/users/me/subscription`.
 */
@Serializable
data class SubscriptionResponse(
    @SerialName("plan")
    val plan: PlanTier,

    @SerialName("credits")
    val credits: Credits,

    @SerialName("limits")
    val limits: Limits
)
