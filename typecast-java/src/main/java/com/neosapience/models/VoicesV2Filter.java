package com.neosapience.models;

/**
 * Filter options for V2 voices API.
 */
public class VoicesV2Filter {
    private TTSModel model;
    private GenderEnum gender;
    private AgeEnum age;
    private UseCaseEnum useCases;

    /**
     * Creates a new VoicesV2Filter with no filters.
     */
    public VoicesV2Filter() {
    }

    /**
     * Creates a new VoicesV2Filter with all filter options.
     * 
     * @param model    filter by TTS model
     * @param gender   filter by gender
     * @param age      filter by age category
     * @param useCases filter by use case
     */
    public VoicesV2Filter(TTSModel model, GenderEnum gender, AgeEnum age, UseCaseEnum useCases) {
        this.model = model;
        this.gender = gender;
        this.age = age;
        this.useCases = useCases;
    }

    /**
     * Gets the model filter.
     * 
     * @return the model, or null if not filtering by model
     */
    public TTSModel getModel() {
        return model;
    }

    /**
     * Sets the model filter.
     * 
     * @param model the model to filter by
     * @return this VoicesV2Filter for chaining
     */
    public VoicesV2Filter setModel(TTSModel model) {
        this.model = model;
        return this;
    }

    /**
     * Gets the gender filter.
     * 
     * @return the gender, or null if not filtering by gender
     */
    public GenderEnum getGender() {
        return gender;
    }

    /**
     * Sets the gender filter.
     * 
     * @param gender the gender to filter by
     * @return this VoicesV2Filter for chaining
     */
    public VoicesV2Filter setGender(GenderEnum gender) {
        this.gender = gender;
        return this;
    }

    /**
     * Gets the age filter.
     * 
     * @return the age category, or null if not filtering by age
     */
    public AgeEnum getAge() {
        return age;
    }

    /**
     * Sets the age filter.
     * 
     * @param age the age category to filter by
     * @return this VoicesV2Filter for chaining
     */
    public VoicesV2Filter setAge(AgeEnum age) {
        this.age = age;
        return this;
    }

    /**
     * Gets the use cases filter.
     * 
     * @return the use case, or null if not filtering by use case
     */
    public UseCaseEnum getUseCases() {
        return useCases;
    }

    /**
     * Sets the use cases filter.
     * 
     * @param useCases the use case to filter by
     * @return this VoicesV2Filter for chaining
     */
    public VoicesV2Filter setUseCases(UseCaseEnum useCases) {
        this.useCases = useCases;
        return this;
    }

    /**
     * Creates a builder for VoicesV2Filter.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for VoicesV2Filter.
     */
    public static class Builder {
        private final VoicesV2Filter filter = new VoicesV2Filter();

        /**
         * Sets the model filter.
         * 
         * @param model the model
         * @return this Builder for chaining
         */
        public Builder model(TTSModel model) {
            filter.setModel(model);
            return this;
        }

        /**
         * Sets the gender filter.
         * 
         * @param gender the gender
         * @return this Builder for chaining
         */
        public Builder gender(GenderEnum gender) {
            filter.setGender(gender);
            return this;
        }

        /**
         * Sets the age filter.
         * 
         * @param age the age category
         * @return this Builder for chaining
         */
        public Builder age(AgeEnum age) {
            filter.setAge(age);
            return this;
        }

        /**
         * Sets the use cases filter.
         * 
         * @param useCases the use case
         * @return this Builder for chaining
         */
        public Builder useCases(UseCaseEnum useCases) {
            filter.setUseCases(useCases);
            return this;
        }

        /**
         * Builds the VoicesV2Filter instance.
         * 
         * @return the configured VoicesV2Filter
         */
        public VoicesV2Filter build() {
            return filter;
        }
    }

    @Override
    public String toString() {
        return "VoicesV2Filter{" +
                "model=" + model +
                ", gender=" + gender +
                ", age=" + age +
                ", useCases=" + useCases +
                '}';
    }
}
