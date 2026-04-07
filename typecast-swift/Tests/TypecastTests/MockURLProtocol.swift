import Foundation

/// URLProtocol subclass used to stub HTTP responses in unit tests.
final class MockURLProtocol: URLProtocol {
    /// Handler that produces a stubbed response for an incoming request.
    /// Returning `nil` for `data` means an empty body.
    nonisolated(unsafe) static var requestHandler: ((URLRequest) throws -> (HTTPURLResponse, Data?))?
    /// Lower-level handler that returns any `URLResponse` (used to simulate
    /// non-HTTP responses for defensive code paths).
    nonisolated(unsafe) static var rawResponseHandler: ((URLRequest) throws -> (URLResponse, Data?))?
    /// Captures the most recent request seen by the protocol so tests can assert
    /// on URL, headers, body, and HTTP method.
    nonisolated(unsafe) static var lastRequest: URLRequest?
    /// Captures the body bytes of the most recent request (URLProtocol strips
    /// `httpBody` from streamed bodies, so we read from `httpBodyStream`).
    nonisolated(unsafe) static var lastBody: Data?

    static func reset() {
        requestHandler = nil
        rawResponseHandler = nil
        lastRequest = nil
        lastBody = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        MockURLProtocol.lastRequest = request
        if let stream = request.httpBodyStream {
            stream.open()
            defer { stream.close() }
            var buffer = Data()
            let bufferSize = 4096
            var bytes = [UInt8](repeating: 0, count: bufferSize)
            while stream.hasBytesAvailable {
                let read = stream.read(&bytes, maxLength: bufferSize)
                if read <= 0 { break }
                buffer.append(bytes, count: read)
            }
            MockURLProtocol.lastBody = buffer
        } else {
            MockURLProtocol.lastBody = request.httpBody
        }

        do {
            if let raw = MockURLProtocol.rawResponseHandler {
                let (response, data) = try raw(request)
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                if let data = data {
                    client?.urlProtocol(self, didLoad: data)
                }
                client?.urlProtocolDidFinishLoading(self)
                return
            }
            guard let handler = MockURLProtocol.requestHandler else {
                client?.urlProtocol(self, didFailWithError: URLError(.cannotLoadFromNetwork))
                return
            }
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            if let data = data {
                client?.urlProtocol(self, didLoad: data)
            }
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

/// Helper that builds a `URLSession` configured to route through `MockURLProtocol`.
enum MockSession {
    static func make() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }
}
