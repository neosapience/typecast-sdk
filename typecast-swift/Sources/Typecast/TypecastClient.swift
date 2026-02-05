import Foundation

/// Configuration for the Typecast client
public struct TypecastConfiguration: Sendable {
    /// API key for authentication
    public let apiKey: String
    /// Base URL for the API (default: https://api.typecast.ai)
    public let baseURL: String
    
    public init(apiKey: String, baseURL: String = "https://api.typecast.ai") {
        self.apiKey = apiKey
        self.baseURL = baseURL
    }
}

/// Typecast API client for text-to-speech and voice operations
public final class TypecastClient: Sendable {
    private let configuration: TypecastConfiguration
    private let session: URLSession
    private let decoder: JSONDecoder
    private let encoder: JSONEncoder
    
    /// Initialize the client with configuration
    /// - Parameter configuration: Client configuration including API key
    public init(configuration: TypecastConfiguration) {
        self.configuration = configuration
        self.session = URLSession.shared
        self.decoder = JSONDecoder()
        self.encoder = JSONEncoder()
    }
    
    /// Initialize the client with API key
    /// - Parameters:
    ///   - apiKey: API key for authentication
    ///   - baseURL: Base URL for the API (default: https://api.typecast.ai)
    public convenience init(apiKey: String, baseURL: String = "https://api.typecast.ai") {
        self.init(configuration: TypecastConfiguration(apiKey: apiKey, baseURL: baseURL))
    }
    
    // MARK: - Private Helpers
    
    private func buildURL(path: String, queryParams: [String: String]? = nil) throws -> URL {
        guard var components = URLComponents(string: configuration.baseURL + path) else {
            throw TypecastError.invalidResponse("Invalid base URL")
        }
        
        if let params = queryParams, !params.isEmpty {
            components.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        
        guard let url = components.url else {
            throw TypecastError.invalidResponse("Failed to build URL")
        }
        
        return url
    }
    
    private func createRequest(url: URL, method: String = "GET", body: Data? = nil) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(configuration.apiKey, forHTTPHeaderField: "X-API-KEY")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        return request
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
        let formatString = contentType.split(separator: "/").last.map(String.init) ?? "wav"
        let format: AudioFormat = formatString == "mp3" ? .mp3 : .wav
        
        let durationHeader = httpResponse.value(forHTTPHeaderField: "X-Audio-Duration")
        let duration = durationHeader.flatMap(Double.init) ?? 0
        
        return TTSResponse(audioData: data, duration: duration, format: format)
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
        let url = try buildURL(path: "/v2/voices/\(voiceId)")
        let request = createRequest(url: url)
        
        let (data, response) = try await session.data(for: request)
        return try handleResponse(data: data, response: response)
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

// MARK: - Convenience Extensions

public extension TypecastClient {
    /// Simple text-to-speech with minimal parameters
    /// - Parameters:
    ///   - text: Text to convert to speech
    ///   - voiceId: Voice ID to use
    ///   - model: TTS model to use (default: ssfm-v30)
    /// - Returns: TTSResponse containing audio data
    func speak(
        _ text: String,
        voiceId: String,
        model: TTSModel = .ssfmV30
    ) async throws -> TTSResponse {
        let request = TTSRequest(voiceId: voiceId, text: text, model: model)
        return try await textToSpeech(request)
    }
    
    /// Text-to-speech with emotion preset
    /// - Parameters:
    ///   - text: Text to convert to speech
    ///   - voiceId: Voice ID to use
    ///   - model: TTS model to use
    ///   - emotion: Emotion preset to apply
    ///   - intensity: Emotion intensity (0.0 to 2.0)
    /// - Returns: TTSResponse containing audio data
    func speak(
        _ text: String,
        voiceId: String,
        model: TTSModel = .ssfmV30,
        emotion: EmotionPreset,
        intensity: Double = 1.0
    ) async throws -> TTSResponse {
        let prompt: TTSPrompt
        if model == .ssfmV30 {
            prompt = .preset(PresetPrompt(emotionPreset: emotion, emotionIntensity: intensity))
        } else {
            prompt = .basic(Prompt(emotionPreset: emotion, emotionIntensity: intensity))
        }
        
        let request = TTSRequest(voiceId: voiceId, text: text, model: model, prompt: prompt)
        return try await textToSpeech(request)
    }
}
