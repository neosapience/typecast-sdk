import XCTest
@testable import Typecast

/// Exhaustive coverage of every `TypecastError` case: status codes,
/// `errorDescription`, and `fromResponse` mapping for every documented
/// HTTP status as well as fallback paths.
final class TypecastErrorTests: XCTestCase {

    // MARK: - errorDescription

    func testBadRequestDescription() {
        XCTAssertEqual(
            TypecastError.badRequest("oops").errorDescription,
            "Bad Request: oops"
        )
    }

    func testUnauthorizedDescription() {
        XCTAssertEqual(
            TypecastError.unauthorized("nope").errorDescription,
            "Unauthorized: nope"
        )
    }

    func testPaymentRequiredDescription() {
        XCTAssertEqual(
            TypecastError.paymentRequired("pay").errorDescription,
            "Payment Required: pay"
        )
    }

    func testNotFoundDescription() {
        XCTAssertEqual(
            TypecastError.notFound("missing").errorDescription,
            "Not Found: missing"
        )
    }

    func testValidationErrorDescription() {
        XCTAssertEqual(
            TypecastError.validationError("bad input").errorDescription,
            "Validation Error: bad input"
        )
    }

    func testRateLimitDescription() {
        XCTAssertEqual(
            TypecastError.rateLimitExceeded("too many").errorDescription,
            "Rate Limit Exceeded: too many"
        )
    }

    func testServerErrorDescription() {
        XCTAssertEqual(
            TypecastError.serverError("boom").errorDescription,
            "Server Error: boom"
        )
    }

    func testNetworkErrorDescription() {
        let underlying = NSError(domain: "test", code: 1, userInfo: [NSLocalizedDescriptionKey: "down"])
        let desc = TypecastError.networkError(underlying).errorDescription
        XCTAssertEqual(desc, "Network Error: down")
    }

    func testInvalidResponseDescription() {
        XCTAssertEqual(
            TypecastError.invalidResponse("weird").errorDescription,
            "Invalid Response: weird"
        )
    }

    func testUnknownDescription() {
        XCTAssertEqual(
            TypecastError.unknown(statusCode: 418, message: "teapot").errorDescription,
            "Error 418: teapot"
        )
    }

    // MARK: - statusCode

    func testStatusCodeAllCases() {
        XCTAssertEqual(TypecastError.badRequest("").statusCode, 400)
        XCTAssertEqual(TypecastError.unauthorized("").statusCode, 401)
        XCTAssertEqual(TypecastError.paymentRequired("").statusCode, 402)
        XCTAssertEqual(TypecastError.notFound("").statusCode, 404)
        XCTAssertEqual(TypecastError.validationError("").statusCode, 422)
        XCTAssertEqual(TypecastError.rateLimitExceeded("").statusCode, 429)
        XCTAssertEqual(TypecastError.serverError("").statusCode, 500)
        XCTAssertEqual(TypecastError.unknown(statusCode: 418, message: "").statusCode, 418)
        XCTAssertNil(TypecastError.networkError(NSError(domain: "x", code: 0)).statusCode)
        XCTAssertNil(TypecastError.invalidResponse("").statusCode)
    }

    // MARK: - fromResponse

    private func decodeMessage(_ error: TypecastError) -> String {
        switch error {
        case .badRequest(let m), .unauthorized(let m), .paymentRequired(let m),
             .notFound(let m), .validationError(let m), .rateLimitExceeded(let m),
             .serverError(let m), .invalidResponse(let m):
            return m
        case .unknown(_, let m):
            return m
        case .networkError(let e):
            return e.localizedDescription
        }
    }

    func testFromResponseDecodesAPIErrorJSON() {
        let data = #"{"detail":"hello"}"#.data(using: .utf8)
        let cases: [(Int, String)] = [
            (400, "badRequest"), (401, "unauthorized"), (402, "paymentRequired"),
            (404, "notFound"), (422, "validationError"), (429, "rateLimitExceeded"),
            (500, "serverError"), (599, "serverError"), (418, "unknown")
        ]
        for (status, _) in cases {
            let error = TypecastError.fromResponse(statusCode: status, data: data)
            XCTAssertEqual(decodeMessage(error), "hello", "status \(status)")
            switch (status, error) {
            case (400, .badRequest): break
            case (401, .unauthorized): break
            case (402, .paymentRequired): break
            case (404, .notFound): break
            case (422, .validationError): break
            case (429, .rateLimitExceeded): break
            case (500, .serverError), (599, .serverError): break
            case (418, .unknown(let code, _)):
                XCTAssertEqual(code, 418)
            default:
                XCTFail("unexpected mapping for \(status): \(error)")
            }
        }
    }

    func testFromResponsePlainTextFallback() {
        let data = "raw text".data(using: .utf8)
        let error = TypecastError.fromResponse(statusCode: 400, data: data)
        XCTAssertEqual(decodeMessage(error), "raw text")
    }

    func testFromResponseEmptyDataFallback() {
        let error = TypecastError.fromResponse(statusCode: 500, data: Data())
        // Empty data still decodes as String (empty), not nil; this hits the
        // text fallback branch.
        XCTAssertEqual(decodeMessage(error), "")
    }

    func testFromResponseNilDataFallback() {
        let error = TypecastError.fromResponse(statusCode: 502, data: nil)
        XCTAssertEqual(decodeMessage(error), "Unknown error")
    }

    func testAPIErrorResponseRoundTrip() throws {
        let original = APIErrorResponse(detail: "msg")
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(APIErrorResponse.self, from: data)
        XCTAssertEqual(decoded.detail, "msg")
    }
}
