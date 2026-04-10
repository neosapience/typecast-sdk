package com.neosapience.models;

/**
 * Credit usage information for a subscription.
 */
public class Credits {
    private long planCredits;
    private long usedCredits;

    /**
     * Creates a new Credits instance.
     */
    public Credits() {
    }

    /**
     * Creates a new Credits instance with the specified values.
     *
     * @param planCredits total credits provided by the plan
     * @param usedCredits number of credits already used
     */
    public Credits(long planCredits, long usedCredits) {
        this.planCredits = planCredits;
        this.usedCredits = usedCredits;
    }

    /**
     * Gets the total credits provided by the plan.
     *
     * @return the plan credits
     */
    public long getPlanCredits() {
        return planCredits;
    }

    /**
     * Sets the total credits provided by the plan.
     *
     * @param planCredits the plan credits
     */
    public void setPlanCredits(long planCredits) {
        this.planCredits = planCredits;
    }

    /**
     * Gets the number of credits already used.
     *
     * @return the used credits
     */
    public long getUsedCredits() {
        return usedCredits;
    }

    /**
     * Sets the number of credits already used.
     *
     * @param usedCredits the used credits
     */
    public void setUsedCredits(long usedCredits) {
        this.usedCredits = usedCredits;
    }

    @Override
    public String toString() {
        return "Credits{" +
                "planCredits=" + planCredits +
                ", usedCredits=" + usedCredits +
                '}';
    }
}
