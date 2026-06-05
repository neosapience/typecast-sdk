module Typecast
  class ApiError < StandardError
    attr_reader :status_code, :detail

    def initialize(message, status_code, detail = nil)
      super(detail.nil? || detail.empty? ? message : "#{message}: #{detail}")
      @status_code = status_code
      @detail = detail
    end
  end

  class BadRequestError < ApiError
    def initialize(detail = nil)
      super("Bad request", 400, detail)
    end
  end

  class UnauthorizedError < ApiError
    def initialize(detail = nil)
      super("Unauthorized", 401, detail)
    end
  end

  class PaymentRequiredError < ApiError
    def initialize(detail = nil)
      super("Payment required", 402, detail)
    end
  end

  class NotFoundError < ApiError
    def initialize(detail = nil)
      super("Not found", 404, detail)
    end
  end

  class UnprocessableEntityError < ApiError
    def initialize(detail = nil)
      super("Unprocessable entity", 422, detail)
    end
  end

  class RateLimitError < ApiError
    def initialize(detail = nil)
      super("Rate limit exceeded", 429, detail)
    end
  end

  class InternalServerError < ApiError
    def initialize(detail = nil)
      super("Internal server error", 500, detail)
    end
  end
end
