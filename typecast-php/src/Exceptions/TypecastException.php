<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

use RuntimeException;

class TypecastException extends RuntimeException
{
    protected int $statusCode;

    public function __construct(string $message = '', int $statusCode = 0, ?\Throwable $previous = null)
    {
        $this->statusCode = $statusCode;
        parent::__construct($message, $statusCode, $previous);
    }

    public function getStatusCode(): int
    {
        return $this->statusCode;
    }
}
