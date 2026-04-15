<?php

declare(strict_types=1);

namespace Neosapience\Typecast\Exceptions;

class PaymentRequiredException extends TypecastException
{
    public function __construct(string $message = 'Payment required', ?\Throwable $previous = null)
    {
        parent::__construct($message, 402, $previous);
    }
}
