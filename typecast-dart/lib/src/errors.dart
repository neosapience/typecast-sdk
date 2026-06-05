class TypecastApiException implements Exception {
  TypecastApiException(this.message, this.statusCode, [this.detail]);

  final String message;
  final int statusCode;
  final String? detail;

  @override
  String toString() {
    final suffix = detail == null || detail!.isEmpty ? '' : ': $detail';
    return 'TypecastApiException($statusCode): $message$suffix';
  }
}

class BadRequestException extends TypecastApiException {
  BadRequestException([String? detail]) : super('Bad request', 400, detail);
}

class UnauthorizedException extends TypecastApiException {
  UnauthorizedException([String? detail]) : super('Unauthorized', 401, detail);
}

class PaymentRequiredException extends TypecastApiException {
  PaymentRequiredException([String? detail])
      : super('Payment required', 402, detail);
}

class NotFoundException extends TypecastApiException {
  NotFoundException([String? detail]) : super('Not found', 404, detail);
}

class UnprocessableEntityException extends TypecastApiException {
  UnprocessableEntityException([String? detail])
      : super('Unprocessable entity', 422, detail);
}

class RateLimitException extends TypecastApiException {
  RateLimitException([String? detail])
      : super('Rate limit exceeded', 429, detail);
}

class InternalServerException extends TypecastApiException {
  InternalServerException([String? detail])
      : super('Internal server error', 500, detail);
}
