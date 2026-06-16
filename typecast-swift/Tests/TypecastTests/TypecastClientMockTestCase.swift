import XCTest
@testable import Typecast

class TypecastClientMockTestCase: XCTestCase {

    let baseURL = "https://test.typecast.local"
    var client: TypecastClient!

    override func setUp() {
        super.setUp()
        MockURLProtocol.reset()
        let session = MockSession.make()
        client = TypecastClient(
            configuration: TypecastConfiguration(apiKey: "test-key", baseURL: baseURL),
            session: session
        )
    }

    override func tearDown() {
        MockURLProtocol.reset()
        client = nil
        super.tearDown()
    }

    func httpResponse(url: URL, status: Int, headers: [String: String] = [:]) -> HTTPURLResponse {
        HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers)!
    }
}
