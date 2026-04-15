<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Filter options for V2 voices endpoint.
 */
class VoicesV2Filter
{
    private const VALID_GENDERS = ['male', 'female'];
    private const VALID_AGES = ['child', 'youth', 'young_adult', 'middle_age', 'senior'];

    public function __construct(
        public ?string $model = null,
        public ?string $gender = null,
        public ?string $age = null,
        public ?string $useCases = null,
    ) {
        if ($this->model !== null && trim($this->model) === '') {
            throw new \InvalidArgumentException('model must be a non-empty string');
        }
        if ($this->gender !== null && !in_array($this->gender, self::VALID_GENDERS, true)) {
            throw new \InvalidArgumentException(
                "gender must be one of: " . implode(', ', self::VALID_GENDERS)
            );
        }
        if ($this->age !== null && !in_array($this->age, self::VALID_AGES, true)) {
            throw new \InvalidArgumentException(
                "age must be one of: " . implode(', ', self::VALID_AGES)
            );
        }
    }

    /**
     * Convert to query parameters array.
     *
     * @return array<string, string>
     */
    public function toQueryParams(): array
    {
        $params = [];

        if ($this->model !== null) {
            $params['model'] = $this->model;
        }
        if ($this->gender !== null) {
            $params['gender'] = $this->gender;
        }
        if ($this->age !== null) {
            $params['age'] = $this->age;
        }
        if ($this->useCases !== null) {
            $params['use_cases'] = $this->useCases;
        }

        return $params;
    }
}
