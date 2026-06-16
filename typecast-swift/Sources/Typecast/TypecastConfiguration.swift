import Foundation

/// Configuration for the Typecast client
public struct TypecastConfiguration: Sendable {
  public static let defaultBaseURL = "https://api.typecast.ai"

  /// API key for authentication. Optional when using a proxy base URL.
  public let apiKey: String?
  /// Base URL for the API (default: https://api.typecast.ai)
  public let baseURL: String

  public init(apiKey: String? = nil, baseURL: String = TypecastConfiguration.defaultBaseURL) {
    let trimmedApiKey = apiKey?.trimmingCharacters(in: .whitespacesAndNewlines)
    self.apiKey = trimmedApiKey?.isEmpty == true ? nil : trimmedApiKey
    self.baseURL =
      baseURL
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .trimmingTrailingSlashes()
  }
}

extension String {
  fileprivate func trimmingTrailingSlashes() -> String {
    var result = self
    while result.hasSuffix("/") {
      result.removeLast()
    }
    return result
  }
}
