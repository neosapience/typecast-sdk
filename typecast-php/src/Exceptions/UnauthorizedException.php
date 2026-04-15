<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class UnauthorizedException extends TypecastException
{
    public function __construct(string $message = 'Unauthorized', ?\Throwable $previous = null)
    {
        parent::__construct($message, 401, $previous);
    }
}
