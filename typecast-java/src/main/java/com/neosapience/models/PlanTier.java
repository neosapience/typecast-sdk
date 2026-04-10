package com.neosapience.models;

import com.google.gson.annotations.SerializedName;

/**
 * Subscription plan tier.
 */
public enum PlanTier {
    /** Free plan tier */
    @SerializedName("free")
    FREE("free"),
    /** Lite plan tier */
    @SerializedName("lite")
    LITE("lite"),
    /** Plus plan tier */
    @SerializedName("plus")
    PLUS("plus"),
    /** Custom plan tier */
    @SerializedName("custom")
    CUSTOM("custom");

    private final String value;

    PlanTier(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the plan tier.
     *
     * @return the plan tier string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a PlanTier from a string value.
     *
     * @param value the plan tier string
     * @return the corresponding PlanTier, or null if input is null
     * @throws IllegalArgumentException if the value doesn't match any plan tier
     */
    public static PlanTier fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (PlanTier tier : values()) {
            if (tier.value.equalsIgnoreCase(value)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("Unknown plan tier: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
