<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class NotFoundException extends TypecastException
{
    public function __construct(string $message = 'Not found', ?\Throwable $previous = null)
    {
        parent::__construct($message, 404, $previous);
    }
}
