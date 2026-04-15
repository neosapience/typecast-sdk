<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class UnprocessableEntityException extends TypecastException
{
    public function __construct(string $message = 'Unprocessable entity', ?\Throwable $previous = null)
    {
        parent::__construct($message, 422, $previous);
    }
}
