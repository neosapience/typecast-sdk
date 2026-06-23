import Foundation

/// Typecast API client for text-to-speech and voice operations
public final class TypecastClient: Sendable {
  private static let sdkVersion = "0.3.7"
  private let configuration: TypecastConfiguration
  private let session: URLSession
  private let decoder: JSONDecoder
  private let encoder: JSONEncoder

  /// Initialize the client with configuration
  /// - Parameters:
  ///   - configuration: Client configuration including API key
  ///   - session: Optional URLSession to use (default: URLSession.shared). Useful for injecting mocked sessions in tests.
  public init(configuration: TypecastConfiguration, session: URLSession = .shared) {
    self.configuration = configuration
    self.session = session
    self.decoder = JSONDecoder()
    self.encoder = JSONEncoder()
  }

  /// Initialize the client with API key
  /// - Parameters:
  ///   - apiKey: API key for authentication. Optional when using a proxy base URL.
  ///   - baseURL: Base URL for the API (default: https://api.typecast.ai)
  public convenience init(
    apiKey: String? = nil, baseURL: String = TypecastConfiguration.defaultBaseURL
  ) {
    self.init(configuration: TypecastConfiguration(apiKey: apiKey, baseURL: baseURL))
  }

  // MARK: - Private Helpers

  private func buildURL(path: String, queryParams: [String: String]? = nil) throws -> URL {
    try validateAuthentication()
    var components = URLComponents(string: configuration.baseURL + path)!
    let url = applyingQueryItems(components: &components, params: queryParams)!
    return url
  }

  private func applyingQueryItems(components: inout URLComponents, params: [String: String]?)
    -> URL?
  {
    if let params = params, !params.isEmpty {
      components.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
    }
    return components.url
  }

  private func createRequest(url: URL, method: String = "GET", body: Data? = nil) -> URLRequest {
    var request = URLRequest(url: url)
    request.httpMethod = method
    setAuthHeader(&request)
    setUserAgentHeader(&request)
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.httpBody = body
    return request
  }

  private func setAuthHeader(_ request: inout URLRequest) {
    if let apiKey = configuration.apiKey, !apiKey.isEmpty {
      request.setValue(apiKey, forHTTPHeaderField: "X-API-KEY")
    }
  }

  private func setUserAgentHeader(_ request: inout URLRequest) {
    request.setValue(buildUserAgent(), forHTTPHeaderField: "User-Agent")
  }

  internal func buildUserAgent() -> String {
    let base =
      configuration.baseURL.caseInsensitiveCompare(TypecastConfiguration.defaultBaseURL)
      == .orderedSame ? "default" : "custom"
    return "typecast-swift/\(Self.sdkVersion) Swift/unknown URLSession " +
      "(base=\(base); os=\(Self.osName()); arch=\(Self.archName()); sdk_env=swift; platform=server)"
  }

  private static func osName() -> String {
    #if os(macOS)
      return "macos"
    #elseif os(iOS)
      return "ios"
    #elseif os(Linux)
      return "linux"
    #elseif os(Windows)
      return "windows"
    #else
      return "unknown"
    #endif
  }

  private static func archName() -> String {
    #if arch(arm64)
      return "arm64"
    #elseif arch(x86_64)
      return "x64"
    #elseif arch(i386)
      return "x86"
    #elseif arch(arm)
      return "arm"
    #else
      return "unknown"
    #endif
  }

  private func validateAuthentication() throws {
    guard let scheme = URLComponents(string: configuration.baseURL)?.scheme?.lowercased() else {
      throw TypecastError.invalidResponse("Invalid base URL")
    }
    guard scheme == "https" else {
      throw TypecastError.invalidResponse("HTTPS is required for all network communication")
    }

    let isDefaultHost =
      configuration.baseURL.caseInsensitiveCompare(TypecastConfiguration.defaultBaseURL)
      == .orderedSame
    if isDefaultHost && configuration.apiKey == nil {
      throw TypecastError.invalidResponse("API key is required for the default Typecast API host")
    }
  }

  private func encodedPathComponent(_ value: String, name: String) throws -> String {
    var allowed = CharacterSet.urlPathAllowed
    allowed.remove(charactersIn: "/")
    guard let encoded = value.addingPercentEncoding(withAllowedCharacters: allowed),
      !encoded.isEmpty
    else {
      throw TypecastError.badRequest("\(name) must be a valid path component")
    }
    return encoded
  }

  private func handleResponse<T: Decodable>(data: Data, response: URLResponse) throws -> T {
    guard let httpResponse = response as? HTTPURLResponse else {
      throw TypecastError.invalidResponse("Invalid response type")
    }

    guard (200...299).contains(httpResponse.statusCode) else {
      throw TypecastError.fromResponse(statusCode: httpResponse.statusCode, data: data)
    }

    return try decoder.decode(T.self, from: data)
  }

  // MARK: - Text to Speech

  /// Convert text to speech
  /// - Parameter request: TTS request parameters including text, voice_id, model, and optional settings
  /// - Returns: TTSResponse containing audio data, duration, and format
  public func textToSpeech(_ request: TTSRequest) async throws -> TTSResponse {
    let url = try buildURL(path: "/v1/text-to-speech")
    let bodyData = try encoder.encode(request)
    let urlRequest = createRequest(url: url, method: "POST", body: bodyData)

    let (data, response) = try await session.data(for: urlRequest)

    guard let httpResponse = response as? HTTPURLResponse else {
      throw TypecastError.invalidResponse("Invalid response type")
    }

    guard (200...299).contains(httpResponse.statusCode) else {
      throw TypecastError.fromResponse(statusCode: httpResponse.statusCode, data: data)
    }

    // Parse response headers
    let contentType = httpResponse.value(forHTTPHeaderField: "Content-Type") ?? "audio/wav"
    let formatString = contentType.split(separator: "/").last.map { $0.lowercased() } ?? "wav"
    let format: AudioFormat = (formatString == "mp3" || formatString == "mpeg") ? .mp3 : .wav

    let durationHeader = httpResponse.value(forHTTPHeaderField: "X-Audio-Duration")
    let duration = durationHeader.flatMap(Double.init) ?? 0

    return TTSResponse(audioData: data, duration: duration, format: format)
  }

  /// Convert text to speech and write the audio bytes to a file.
  ///
  /// - Parameters:
  ///   - fileURL: Destination file URL.
  ///   - request: Generate-to-file request with required voiceId and text.
  /// - Returns: TTSResponse containing audio data, duration, and format.
  public func generateToFile(
    _ fileURL: URL,
    request: GenerateToFileRequest
  ) async throws -> TTSResponse {
    guard fileURL.isFileURL else {
      throw TypecastError.badRequest("fileURL must be a file URL")
    }
    let response = try await textToSpeech(request.toTTSRequest(fileURL: fileURL))
    try response.audioData.write(to: fileURL)
    return response
  }

  /// Convert text to speech and write the audio bytes to a file path.
  public func generateToFile(
    _ filePath: String,
    request: GenerateToFileRequest
  ) async throws -> TTSResponse {
    guard !filePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
      throw TypecastError.badRequest("filePath cannot be empty")
    }
    return try await generateToFile(URL(fileURLWithPath: filePath), request: request)
  }

  /// Stream text-to-speech audio from `POST /v1/text-to-speech/stream`.
  ///
  /// Returns an `AsyncThrowingStream` that yields audio chunks (default
  /// 8192 bytes) in the order produced by the server. For WAV the first
  /// chunk contains the WAV header followed by PCM data; subsequent chunks
  /// are PCM only. For MP3 each chunk contains independently-decodable
  /// MP3 frames.
  ///
  /// - Note: Internally this method buffers the full HTTP response body
  ///   via `URLSession.data(for:)` and then re-emits it in fixed-size
  ///   chunks. This keeps the public API symmetric with what a true
  ///   streaming reader would expose while remaining compatible with the
  ///   `URLProtocol`-based mock used by the test suite.
  /// - Parameter request: Streaming TTS request parameters.
  /// - Returns: An async sequence of audio data chunks.
  public func textToSpeechStream(
    _ request: TTSRequestStream
  ) async throws -> AsyncThrowingStream<Data, Error> {
    let url = try buildURL(path: "/v1/text-to-speech/stream")
    let bodyData = try encoder.encode(request)
    let urlRequest = createRequest(url: url, method: "POST", body: bodyData)

    let (data, response) = try await session.data(for: urlRequest)

    guard let httpResponse = response as? HTTPURLResponse else {
      throw TypecastError.invalidResponse("Invalid response type")
    }

    guard (200...299).contains(httpResponse.statusCode) else {
      throw TypecastError.fromResponse(statusCode: httpResponse.statusCode, data: data)
    }

    let chunkSize = 8192
    return AsyncThrowingStream<Data, Error> { continuation in
      var offset = 0
      while offset < data.count {
        let end = min(offset + chunkSize, data.count)
        continuation.yield(data.subdata(in: offset..<end))
        offset = end
      }
      continuation.finish()
    }
  }

  // MARK: - Text to Speech with Timestamps

  /// Convert text to speech and receive word/character-level alignment timestamps.
  ///
  /// - Parameters:
  ///   - request: TTS request parameters (voice, text, model, …).
  ///   - granularity: Alignment granularity — `"word"` (words only) or `"char"` (characters only).
  ///     Defaults to both when `nil`.
  /// - Returns: `TTSWithTimestampsResponse` containing base64 audio, duration, and alignment segments.
  public func textToSpeechWithTimestamps(
    _ request: TTSRequestWithTimestamps,
    granularity: String? = nil
  ) async throws -> TTSWithTimestampsResponse {
    try request.validate()

    let validGranularities = ["word", "char"]
    if let g = granularity, !validGranularities.contains(g) {
      throw TimestampError.invalidGranularity(g)
    }

    var queryParams: [String: String]? = nil
    if let g = granularity {
      queryParams = ["granularity": g]
    }

    let url = try buildURL(path: "/v1/text-to-speech/with-timestamps", queryParams: queryParams)
    let bodyData = try encoder.encode(request)
    let urlRequest = createRequest(url: url, method: "POST", body: bodyData)

    let (data, response) = try await session.data(for: urlRequest)
    return try handleResponse(data: data, response: response)
  }

  // MARK: - Voices V2 API

  /// Get voices with enhanced metadata (V2 API)
  /// Returns voices with model-grouped emotions and additional metadata
  /// - Parameter filter: Optional filter options (model, gender, age, use_cases)
  /// - Returns: Array of VoiceV2 objects
  public func getVoices(filter: VoicesV2Filter? = nil) async throws -> [VoiceV2] {
    let queryParams = filter?.toQueryParams()
    let url = try buildURL(path: "/v2/voices", queryParams: queryParams)
    let request = createRequest(url: url)

    let (data, response) = try await session.data(for: request)
    return try handleResponse(data: data, response: response)
  }

  /// Get a specific voice by ID with enhanced metadata (V2 API)
  /// - Parameter voiceId: The voice ID (e.g., 'tc_62a8975e695ad26f7fb514d1')
  /// - Returns: Voice information with model-grouped emotions and metadata
  public func getVoice(voiceId: String) async throws -> VoiceV2 {
    let encodedVoiceId = try encodedPathComponent(voiceId, name: "voiceId")
    let url = try buildURL(path: "/v2/voices/\(encodedVoiceId)")
    let request = createRequest(url: url)

    let (data, response) = try await session.data(for: request)
    return try handleResponse(data: data, response: response)
  }

  // MARK: - Subscription

  /// Get the authenticated user's subscription
  /// - Returns: SubscriptionResponse containing plan, credits, and limits
  public func getMySubscription() async throws -> SubscriptionResponse {
    let url = try buildURL(path: "/v1/users/me/subscription")
    let request = createRequest(url: url)

    let (data, response) = try await session.data(for: request)
    return try handleResponse(data: data, response: response)
  }

  // MARK: - Instant cloning

  /// Clone a voice from an audio sample.
  ///
  /// Sends a `multipart/form-data` POST to `POST /v1/voices/clone` and
  /// returns the metadata of the newly created custom voice.
  ///
  /// - Parameters:
  ///   - audio: Raw audio bytes of the sample (WAV or MP3 recommended).
  ///   - filename: File name including extension — used to set the
  ///     `Content-Type` of the file part (e.g. `"sample.wav"`).
  ///   - name: Display name for the cloned voice (1–30 characters).
  ///   - model: TTS model to clone the voice for (e.g. `"ssfm-v30"`).
  /// - Returns: ``CustomVoice`` with the assigned `voiceId` (has "uc_" prefix).
  /// - Throws: ``TypecastError/badRequest(_:)`` when `name` length or file
  ///   size violates ``QuickCloningLimits``; other ``TypecastError`` cases
  ///   for HTTP errors.
  public func cloneVoice(
    audio: Data,
    filename: String,
    name: String,
    model: String
  ) async throws -> CustomVoice {
    let nameLength = name.count
    guard
      (QuickCloningLimits.nameMinLength...QuickCloningLimits.nameMaxLength).contains(nameLength)
    else {
      throw TypecastError.badRequest(
        "name must be \(QuickCloningLimits.nameMinLength)-\(QuickCloningLimits.nameMaxLength) characters; got \(nameLength)"
      )
    }
    guard audio.count <= QuickCloningLimits.cloningMaxFileSize else {
      throw TypecastError.badRequest(
        "audio file exceeds 25 MB limit; got \(audio.count) bytes"
      )
    }

    let boundary = "----TypecastBoundary\(UUID().uuidString)"
    var body = Data()
    let crlf = "\r\n"

    func appendStr(_ string: String) {
      body.append(string.data(using: .utf8)!)
    }

    // name field
    appendStr("--\(boundary)\(crlf)")
    appendStr("Content-Disposition: form-data; name=\"name\"\(crlf)\(crlf)")
    appendStr("\(name)\(crlf)")
    // model field
    appendStr("--\(boundary)\(crlf)")
    appendStr("Content-Disposition: form-data; name=\"model\"\(crlf)\(crlf)")
    appendStr("\(model)\(crlf)")
    // file field
    appendStr("--\(boundary)\(crlf)")
    appendStr("Content-Disposition: form-data; name=\"file\"; filename=\"\(filename)\"\(crlf)")
    appendStr("Content-Type: \(guessAudioMime(filename))\(crlf)\(crlf)")
    body.append(audio)
    appendStr("\(crlf)--\(boundary)--\(crlf)")

    let url = try buildURL(path: "/v1/voices/clone")
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue(
      "multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
    setAuthHeader(&request)
    setUserAgentHeader(&request)
    request.httpBody = body

    let (data, response) = try await session.data(for: request)
    return try handleResponse(data: data, response: response)
  }

  /// Clone a voice from an audio file on disk.
  ///
  /// Convenience overload that reads the file at `audioFileURL` into memory
  /// and delegates to ``cloneVoice(audio:filename:name:model:)``.
  ///
  /// - Parameters:
  ///   - audioFileURL: Local `file://` URL to the audio sample.
  ///   - name: Display name for the cloned voice (1–30 characters).
  ///   - model: TTS model to clone the voice for (e.g. `"ssfm-v30"`).
  /// - Returns: ``CustomVoice`` with the assigned `voiceId`.
  public func cloneVoice(
    audioFileURL: URL,
    name: String,
    model: String
  ) async throws -> CustomVoice {
    guard audioFileURL.isFileURL else {
      throw TypecastError.badRequest("audioFileURL must be a file URL")
    }
    let audio = try Data(contentsOf: audioFileURL)
    return try await cloneVoice(
      audio: audio,
      filename: audioFileURL.lastPathComponent,
      name: name,
      model: model
    )
  }

  /// Delete a previously cloned custom voice.
  ///
  /// Sends `DELETE /v1/voices/{voiceId}`. A 204 response is treated as
  /// success; any other status maps to a ``TypecastError``.
  ///
  /// - Parameter voiceId: The identifier of the custom voice to delete
  ///   (the "uc_" prefixed string returned by ``cloneVoice(audio:filename:name:model:)``).
  public func deleteVoice(_ voiceId: String) async throws {
    let encodedVoiceId = try encodedPathComponent(voiceId, name: "voiceId")
    let url = try buildURL(path: "/v1/voices/\(encodedVoiceId)")
    var request = URLRequest(url: url)
    request.httpMethod = "DELETE"
    setAuthHeader(&request)
    setUserAgentHeader(&request)

    let (data, response) = try await session.data(for: request)
    guard let httpResponse = response as? HTTPURLResponse else {
      throw TypecastError.invalidResponse("Invalid response type")
    }
    guard (200...299).contains(httpResponse.statusCode) else {
      throw TypecastError.fromResponse(statusCode: httpResponse.statusCode, data: data)
    }
  }

  // MARK: - Private helpers for voice cloning

  private func guessAudioMime(_ filename: String) -> String {
    let lower = filename.lowercased()
    if lower.hasSuffix(".wav") { return "audio/wav" }
    if lower.hasSuffix(".mp3") { return "audio/mpeg" }
    return "application/octet-stream"
  }

  // MARK: - Deprecated V1 API

  /// Get available voices (V1 API)
  /// - Parameter model: Optional model filter (e.g., 'ssfm-v21', 'ssfm-v30')
  /// - Returns: List of available voices with their emotions
  /// - Note: Deprecated. Use getVoices() for enhanced metadata and filtering options
  @available(*, deprecated, message: "Use getVoices() for enhanced metadata and filtering options")
  public func getVoicesV1(model: TTSModel? = nil) async throws -> [Voice] {
    var queryParams: [String: String]?
    if let model = model {
      queryParams = ["model": model.rawValue]
    }

    let url = try buildURL(path: "/v1/voices", queryParams: queryParams)
    let request = createRequest(url: url)

    let (data, response) = try await session.data(for: request)
    return try handleResponse(data: data, response: response)
  }
}
