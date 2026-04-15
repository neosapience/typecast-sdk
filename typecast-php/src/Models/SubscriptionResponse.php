<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Response from GET /v1/users/me/subscription.
 */
class SubscriptionResponse
{
    public function __construct(
        public string $plan,
        public int $planCredits,
        public int $usedCredits,
        public int $concurrencyLimit,
    ) {
    }

    /**
     * Create from API JSON response.
     *
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            plan: $data['plan'] ?? '',
            planCredits: $data['credits']['plan_credits'] ?? 0,
            usedCredits: $data['credits']['used_credits'] ?? 0,
            concurrencyLimit: $data['limits']['concurrency_limit'] ?? 0,
        );
    }
}
