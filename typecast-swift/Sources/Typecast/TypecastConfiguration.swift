import Foundation

/// Configuration for the Typecast client
public struct TypecastConfiguration: Sendable {
  public static let defaultBaseURL = "https://api.typecast.ai"

  /// API key for authentication. Optional when using a proxy base URL.
  public let apiKey: String?
  /// Base URL for the API (default: https://api.typecast.ai)
  public let baseURL: String
  /// Integration source: "llms", "skill", "api-page", or "api-docs".
  public let source: String?
  /// Lowercase token identifying the coding agent.
  public let generatedBy: String?

  public init(
    apiKey: String? = nil,
    baseURL: String = TypecastConfiguration.defaultBaseURL,
    source: String? = nil,
    generatedBy: String? = nil
  ) {
    let trimmedApiKey = apiKey?.trimmingCharacters(in: .whitespacesAndNewlines)
    self.apiKey = trimmedApiKey?.isEmpty == true ? nil : trimmedApiKey
    self.baseURL =
      baseURL
      .trimmingCharacters(in: .whitespacesAndNewlines)
      .trimmingTrailingSlashes()
    let validAttribution =
      (source == "llms" || source == "skill" || source == "api-page" || source == "api-docs")
      && generatedBy?.range(
        of: "\\A[a-z0-9][a-z0-9._-]{0,31}\\z", options: .regularExpression) != nil
    self.source = validAttribution ? source : nil
    self.generatedBy = validAttribution ? generatedBy : nil
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
