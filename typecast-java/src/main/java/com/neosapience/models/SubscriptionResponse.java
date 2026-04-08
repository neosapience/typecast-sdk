package com.neosapience.models;

/**
 * Response from {@code GET /v1/users/me/subscription}.
 */
public class SubscriptionResponse {
    private PlanTier plan;
    private Credits credits;
    private Limits limits;

    /**
     * Creates a new SubscriptionResponse.
     */
    public SubscriptionResponse() {
    }

    /**
     * Creates a new SubscriptionResponse with all fields.
     *
     * @param plan    the current subscription plan tier
     * @param credits credit usage information
     * @param limits  usage limit information
     */
    public SubscriptionResponse(PlanTier plan, Credits credits, Limits limits) {
        this.plan = plan;
        this.credits = credits;
        this.limits = limits;
    }

    /**
     * Gets the current subscription plan tier.
     *
     * @return the plan tier
     */
    public PlanTier getPlan() {
        return plan;
    }

    /**
     * Sets the current subscription plan tier.
     *
     * @param plan the plan tier
     */
    public void setPlan(PlanTier plan) {
        this.plan = plan;
    }

    /**
     * Gets the credit usage information.
     *
     * @return the credits
     */
    public Credits getCredits() {
        return credits;
    }

    /**
     * Sets the credit usage information.
     *
     * @param credits the credits
     */
    public void setCredits(Credits credits) {
        this.credits = credits;
    }

    /**
     * Gets the usage limit information.
     *
     * @return the limits
     */
    public Limits getLimits() {
        return limits;
    }

    /**
     * Sets the usage limit information.
     *
     * @param limits the limits
     */
    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    @Override
    public String toString() {
        return "SubscriptionResponse{" +
                "plan=" + plan +
                ", credits=" + credits +
                ", limits=" + limits +
                '}';
    }
}
