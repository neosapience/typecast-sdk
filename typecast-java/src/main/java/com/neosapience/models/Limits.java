package com.neosapience.models;

/**
 * Usage limit information for a subscription.
 */
public class Limits {
    private long concurrencyLimit;

    /**
     * Creates a new Limits instance.
     */
    public Limits() {
    }

    /**
     * Creates a new Limits instance with the specified values.
     *
     * @param concurrencyLimit maximum number of concurrent requests allowed
     */
    public Limits(long concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    /**
     * Gets the maximum number of concurrent requests allowed.
     *
     * @return the concurrency limit
     */
    public long getConcurrencyLimit() {
        return concurrencyLimit;
    }

    /**
     * Sets the maximum number of concurrent requests allowed.
     *
     * @param concurrencyLimit the concurrency limit
     */
    public void setConcurrencyLimit(long concurrencyLimit) {
        this.concurrencyLimit = concurrencyLimit;
    }

    @Override
    public String toString() {
        return "Limits{" +
                "concurrencyLimit=" + concurrencyLimit +
                '}';
    }
}
