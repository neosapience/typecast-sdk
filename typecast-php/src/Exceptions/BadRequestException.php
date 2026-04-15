<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class BadRequestException extends TypecastException
{
    public function __construct(string $message = 'Bad request', ?\Throwable $previous = null)
    {
        parent::__construct($message, 400, $previous);
    }
}
