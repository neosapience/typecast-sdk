package com.neosapience.models;

/**
 * Age category enumeration for voice filtering.
 */
public enum AgeEnum {
    /** Child voice */
    CHILD("child"),
    /** Teenager voice */
    TEENAGER("teenager"),
    /** Young adult voice */
    YOUNG_ADULT("young_adult"),
    /** Middle age voice */
    MIDDLE_AGE("middle_age"),
    /** Elder voice */
    ELDER("elder");

    private final String value;

    AgeEnum(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the age category.
     * 
     * @return the age category string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates an AgeEnum from a string value.
     * 
     * @param value the age category string
     * @return the corresponding AgeEnum
     * @throws IllegalArgumentException if the value doesn't match any age category
     */
    public static AgeEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AgeEnum age : values()) {
            if (age.value.equalsIgnoreCase(value)) {
                return age;
            }
        }
        throw new IllegalArgumentException("Unknown age category: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
