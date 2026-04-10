/**
 * Subscription plan tier returned by `GET /v1/users/me/subscription`.
 */
export type PlanTier = 'free' | 'lite' | 'plus' | 'custom';

/**
 * Credit usage information.
 */
export interface Credits {
  /** Total credits provided by the plan. */
  plan_credits: number;
  /** Number of credits used. */
  used_credits: number;
}

/**
 * Usage limit information.
 */
export interface Limits {
  /** Maximum number of concurrent requests allowed. */
  concurrency_limit: number;
}

/**
 * Response from `GET /v1/users/me/subscription`.
 */
export interface SubscriptionResponse {
  /** Current subscription plan tier. */
  plan: PlanTier;
  /** Credit usage information. */
  credits: Credits;
  /** Usage limit information. */
  limits: Limits;
}
