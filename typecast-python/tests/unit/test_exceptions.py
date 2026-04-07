"""Tests for typecast.exceptions module."""

import pytest

from typecast.exceptions import (
    BadRequestError,
    InternalServerError,
    NotFoundError,
    PaymentRequiredError,
    RateLimitError,
    TypecastError,
    UnauthorizedError,
    UnprocessableEntityError,
)


def test_typecast_error_with_status_code():
    err = TypecastError("boom", status_code=503)
    assert str(err) == "boom"
    assert err.status_code == 503
    assert err.message == "boom"


def test_typecast_error_without_status_code():
    err = TypecastError("boom")
    assert str(err) == "boom"
    assert err.status_code is None


@pytest.mark.parametrize(
    "exc_class,expected_status",
    [
        (BadRequestError, 400),
        (UnauthorizedError, 401),
        (PaymentRequiredError, 402),
        (NotFoundError, 404),
        (UnprocessableEntityError, 422),
        (RateLimitError, 429),
        (InternalServerError, 500),
    ],
)
def test_subclass_carries_correct_status_code(exc_class, expected_status):
    err = exc_class("test message")
    assert isinstance(err, TypecastError)
    assert err.status_code == expected_status
    assert err.message == "test message"
    assert "test message" in str(err)


def test_subclass_can_be_raised_and_caught():
    with pytest.raises(BadRequestError) as exc_info:
        raise BadRequestError("invalid input")
    assert exc_info.value.status_code == 400


def test_typecast_error_is_exception():
    assert issubclass(TypecastError, Exception)
