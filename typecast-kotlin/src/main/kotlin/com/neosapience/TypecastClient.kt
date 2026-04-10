package com.neosapience

import io.github.cdimascio.dotenv.dotenv
import com.neosapience.exceptions.*
import com.neosapience.models.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Client for the Typecast Text-to-Speech API.
 *
 * Example usage:
 * ```kotlin
 * val client = TypecastClient("your-api-key")
 *
 * val request = TTSRequest.builder()
 *     .voiceId("tc_...")
 *     .text("Hello, world!")
 *     .model(TTSModel.SSFM_V30)
 *     .build()
 *
 * val response = client.textToSpeech(request)
 * val audioData = response.audioData
 * ```
 */
class TypecastClient private constructor(
    private val apiKey: String,
    private val baseUrl: String,
    private val httpClient: OkHttpClient
) : Closeable {
    
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.typecast.ai"
        private const val API_KEY_HEADER = "X-API-KEY"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        
        /**
         * Creates a TypecastClient with API key from environment.
         *
         * Looks for API key in the following order:
         * 1. .env file (TYPECAST_API_KEY)
         * 2. System environment variable (TYPECAST_API_KEY)
         *
         * @throws IllegalArgumentException if no API key is found
         */
        @JvmStatic
        fun create(): TypecastClient = Builder().build()
        
        /**
         * Creates a TypecastClient with the specified API key.
         *
         * @param apiKey the Typecast API key
         */
        @JvmStatic
        fun create(apiKey: String): TypecastClient = Builder().apiKey(apiKey).build()
        
        /**
         * Creates a builder for TypecastClient.
         */
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /**
     * Converts text to speech audio.
     *
     * @param request the TTS request parameters
     * @return the TTS response containing audio data
     * @throws TypecastException if the API call fails
     */
    fun textToSpeech(request: TTSRequest): TTSResponse {
        val url = "$baseUrl/v1/text-to-speech"
        val jsonBody = json.encodeToString(request)
        
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            // OkHttp 4.x always returns a non-null ResponseBody from execute().
            val body = response.body!!
            if (!response.isSuccessful) {
                throw buildError(response.code, body.string())
            }

            val audioData = body.bytes()

            // Extract duration from header
            val durationHeader = response.header("X-Audio-Duration")
            val duration = durationHeader?.toDoubleOrNull() ?: 0.0

            // Extract format from content type
            val contentType = response.header("Content-Type") ?: "audio/wav"
            val format = when {
                contentType.contains("mp3") || contentType.contains("mpeg") -> "mp3"
                else -> "wav"
            }

            return TTSResponse(audioData, duration, format)
        }
    }

    /**
     * Streams synthesized audio from `POST /v1/text-to-speech/stream`.
     *
     * Returns an [InputStream] that yields the chunked binary audio body
     * as the server produces it. For WAV, the first bytes contain the WAV
     * header followed by PCM data. For MP3, the stream is a sequence of
     * independently-decodable MP3 frames.
     *
     * The caller is responsible for closing the returned [InputStream];
     * doing so releases the underlying HTTP connection.
     *
     * @param request the streaming TTS request parameters
     * @return an [InputStream] of the audio body
     * @throws TypecastException if the API call fails
     */
    fun textToSpeechStream(request: TTSRequestStream): InputStream {
        val url = "$baseUrl/v1/text-to-speech/stream"
        val jsonBody = json.encodeToString(request)

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val streamClient = httpClient.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
        val response = streamClient.newCall(httpRequest).execute()
        // OkHttp 4.x always returns a non-null ResponseBody from execute().
        val body = response.body!!
        if (!response.isSuccessful) {
            val errorText = body.string()
            response.close()
            throw buildError(response.code, errorText)
        }
        return body.byteStream()
    }

    /**
     * Gets all available voices (V1 API).
     *
     * @param model Optional model filter
     * @return list of available voices
     * @throws TypecastException if the API call fails
     * @deprecated Use [getVoicesV2] instead
     */
    @Deprecated("Use getVoicesV2() for enhanced metadata", ReplaceWith("getVoicesV2()"))
    fun getVoices(model: TTSModel? = null): List<VoicesResponse> {
        val urlBuilder = "$baseUrl/v1/voices".toHttpUrl().newBuilder()
        model?.let { urlBuilder.addQueryParameter("model", it.value) }

        val httpRequest = Request.Builder()
            .url(urlBuilder.build())
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        return executeRequest(httpRequest)
    }

    /**
     * Gets a specific voice by ID (V1 API).
     *
     * @param voiceId the voice ID
     * @param model Optional model filter
     * @return the voice information
     * @throws TypecastException if the API call fails or voice not found
     * @deprecated Use [getVoiceV2] instead
     */
    @Deprecated("Use getVoiceV2() for enhanced metadata", ReplaceWith("getVoiceV2(voiceId)"))
    fun getVoice(voiceId: String, model: TTSModel? = null): VoicesResponse {
        val urlBuilder = "$baseUrl/v1/voices/$voiceId".toHttpUrl().newBuilder()
        model?.let { urlBuilder.addQueryParameter("model", it.value) }

        val httpRequest = Request.Builder()
            .url(urlBuilder.build())
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        val voices: List<VoicesResponse> = executeRequest(httpRequest)
        if (voices.isEmpty()) {
            throw NotFoundException("Voice not found: $voiceId")
        }
        return voices.first()
    }

    /**
     * Gets all available voices with enhanced metadata (V2 API).
     *
     * @param filter Optional filter options
     * @return list of available voices
     * @throws TypecastException if the API call fails
     */
    fun getVoicesV2(filter: VoicesV2Filter? = null): List<VoiceV2Response> {
        val urlBuilder = "$baseUrl/v2/voices".toHttpUrl().newBuilder()
        
        filter?.let { f ->
            f.model?.let { urlBuilder.addQueryParameter("model", it.value) }
            f.gender?.let { urlBuilder.addQueryParameter("gender", it.value) }
            f.age?.let { urlBuilder.addQueryParameter("age", it.value) }
            f.useCases?.let { urlBuilder.addQueryParameter("use_cases", it.value) }
        }

        val httpRequest = Request.Builder()
            .url(urlBuilder.build())
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        return executeRequest(httpRequest)
    }

    /**
     * Gets a specific voice by ID with enhanced metadata (V2 API).
     *
     * @param voiceId the voice ID
     * @return the voice information
     * @throws TypecastException if the API call fails or voice not found
     */
    fun getVoiceV2(voiceId: String): VoiceV2Response {
        val url = "$baseUrl/v2/voices/$voiceId"

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        return executeRequest(httpRequest)
    }

    /**
     * Gets the authenticated user's subscription information.
     *
     * @return the user's current subscription, including plan, credits, and limits
     * @throws TypecastException if the API call fails
     */
    fun getMySubscription(): SubscriptionResponse {
        val url = "$baseUrl/v1/users/me/subscription"

        val httpRequest = Request.Builder()
            .url(url)
            .addHeader(API_KEY_HEADER, apiKey)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

        return executeRequest(httpRequest)
    }

    private inline fun <reified T> executeRequest(request: Request): T {
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                throw buildError(response.code, responseBody)
            }

            return json.decodeFromString(responseBody)
        }
    }

    private fun buildError(statusCode: Int, responseBody: String): TypecastException {
        val message = extractErrorMessage(responseBody)

        return when (statusCode) {
            400 -> BadRequestException(message, responseBody)
            401 -> UnauthorizedException(message, responseBody)
            402 -> PaymentRequiredException(message, responseBody)
            403 -> ForbiddenException(message, responseBody)
            404 -> NotFoundException(message, responseBody)
            422 -> UnprocessableEntityException(message, responseBody)
            429 -> RateLimitException(message, responseBody)
            500 -> InternalServerException(message, responseBody)
            else -> TypecastException(message, statusCode, responseBody)
        }
    }

    private fun extractErrorMessage(responseBody: String): String {
        if (responseBody.isBlank()) return "Unknown error"

        return try {
            val jsonElement = json.parseToJsonElement(responseBody)
            val jsonObject = jsonElement as? kotlinx.serialization.json.JsonObject
                ?: return responseBody

            val field = jsonObject["detail"] ?: jsonObject["message"] ?: jsonObject["error"]
            if (field != null) field.toString().trim('"') else responseBody
        } catch (e: Exception) {
            responseBody
        }
    }

    /**
     * Gets the base URL being used.
     */
    fun getBaseUrl(): String = baseUrl

    /**
     * Closes the HTTP client and releases resources.
     */
    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    /**
     * Builder for TypecastClient.
     */
    class Builder {
        private var apiKey: String? = null
        private var baseUrl: String? = null
        private var httpClient: OkHttpClient? = null

        /**
         * Sets the API key.
         */
        fun apiKey(value: String) = apply { apiKey = value }

        /**
         * Sets the base URL.
         */
        fun baseUrl(value: String) = apply { baseUrl = value }

        /**
         * Sets a custom OkHttpClient.
         */
        fun httpClient(value: OkHttpClient) = apply { httpClient = value }

        /**
         * Builds the TypecastClient instance.
         *
         * @throws IllegalArgumentException if no API key is found
         */
        fun build(): TypecastClient {
            val resolvedApiKey = resolveApiKey()
            val resolvedBaseUrl = resolveBaseUrl()
            val resolvedHttpClient = httpClient ?: OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            return TypecastClient(resolvedApiKey, resolvedBaseUrl, resolvedHttpClient)
        }

        private fun resolveApiKey(): String {
            apiKey?.takeIf { it.isNotBlank() }?.let { return it }

            // Try .env file first
            try {
                val env = dotenv {
                    ignoreIfMissing = true
                }
                env["TYPECAST_API_KEY"]?.takeIf { it.isNotBlank() }?.let { return it }
            } catch (e: Exception) {
                // Continue to system environment
            }

            // Try system environment variable
            System.getenv("TYPECAST_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

            throw IllegalArgumentException(
                "API key is required. Set TYPECAST_API_KEY environment variable or pass it to the builder."
            )
        }

        private fun resolveBaseUrl(): String {
            baseUrl?.takeIf { it.isNotBlank() }?.let { 
                return it.trimEnd('/') 
            }

            // Try .env file first
            try {
                val env = dotenv {
                    ignoreIfMissing = true
                }
                env["TYPECAST_API_HOST"]?.takeIf { it.isNotBlank() }?.let { 
                    return it.trimEnd('/') 
                }
            } catch (e: Exception) {
                // Continue to system environment
            }

            // Try system environment variable
            System.getenv("TYPECAST_API_HOST")?.takeIf { it.isNotBlank() }?.let { 
                return it.trimEnd('/') 
            }

            return DEFAULT_BASE_URL
        }
    }
}
