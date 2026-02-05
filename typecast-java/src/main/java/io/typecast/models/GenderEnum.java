package io.typecast.models;

/**
 * Gender enumeration for voice filtering.
 */
public enum GenderEnum {
    /** Male voice */
    MALE("male"),
    /** Female voice */
    FEMALE("female");

    private final String value;

    GenderEnum(String value) {
        this.value = value;
    }

    /**
     * Returns the string value of the gender.
     * 
     * @return the gender string
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a GenderEnum from a string value.
     * 
     * @param value the gender string
     * @return the corresponding GenderEnum
     * @throws IllegalArgumentException if the value doesn't match any gender
     */
    public static GenderEnum fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (GenderEnum gender : values()) {
            if (gender.value.equalsIgnoreCase(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException("Unknown gender: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
