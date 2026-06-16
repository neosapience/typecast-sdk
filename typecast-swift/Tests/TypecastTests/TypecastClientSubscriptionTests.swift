import XCTest
@testable import Typecast

final class TypecastClientSubscriptionTests: TypecastClientMockTestCase {

    // MARK: - getMySubscription

    func testGetMySubscriptionSuccess() async throws {
        MockURLProtocol.requestHandler = { req in
            XCTAssertEqual(req.url?.path, "/v1/users/me/subscription")
            XCTAssertEqual(req.httpMethod, "GET")
            XCTAssertEqual(req.value(forHTTPHeaderField: "X-API-KEY"), "test-key")
            let body = """
            {
              "plan": "plus",
              "credits": {"plan_credits": 1000, "used_credits": 250},
              "limits": {"concurrency_limit": 4}
            }
            """.data(using: .utf8)
            return (self.httpResponse(url: req.url!, status: 200), body)
        }

        let subscription = try await client.getMySubscription()
        XCTAssertEqual(subscription.plan, .plus)
        XCTAssertEqual(subscription.credits.planCredits, 1000)
        XCTAssertEqual(subscription.credits.usedCredits, 250)
        XCTAssertEqual(subscription.limits.concurrencyLimit, 4)
    }

    func testGetMySubscriptionUnauthorized() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 401),
                #"{"detail":"invalid api key"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.getMySubscription()
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .unauthorized(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "invalid api key")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testGetMySubscriptionRateLimited() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 429),
                #"{"detail":"too many"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.getMySubscription()
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .rateLimitExceeded(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "too many")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }

    func testGetMySubscriptionServerError() async {
        MockURLProtocol.requestHandler = { req in
            (
                self.httpResponse(url: req.url!, status: 500),
                #"{"detail":"boom"}"#.data(using: .utf8)
            )
        }
        do {
            _ = try await client.getMySubscription()
            XCTFail("expected error")
        } catch let error as TypecastError {
            guard case .serverError(let message) = error else {
                XCTFail("wrong case: \(error)")
                return
            }
            XCTAssertEqual(message, "boom")
        } catch {
            XCTFail("unexpected error type: \(error)")
        }
    }
}
