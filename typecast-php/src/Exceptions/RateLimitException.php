<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class RateLimitException extends TypecastException
{
    public function __construct(string $message = 'Rate limit exceeded', ?\Throwable $previous = null)
    {
        parent::__construct($message, 429, $previous);
    }
}
