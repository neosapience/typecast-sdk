<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Models;

/**
 * Filter options for V2 voices endpoint.
 */
class VoicesV2Filter
{
    public function __construct(
        public ?string $model = null,
        public ?string $gender = null,
        public ?string $age = null,
        public ?string $useCases = null,
    ) {
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
