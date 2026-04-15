<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class InternalServerException extends TypecastException
{
    public function __construct(string $message = 'Internal server error', ?\Throwable $previous = null)
    {
        parent::__construct($message, 500, $previous);
    }
}
