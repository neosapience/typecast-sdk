package com.neosapience;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.cdimascio.dotenv.Dotenv;
import com.neosapience.exceptions.*;
import com.neosapience.models.*;
import okhttp3.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Client for the Typecast Text-to-Speech API.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * TypecastClient client = new TypecastClient("your-api-key");
 * 
 * TTSRequest request = TTSRequest.builder()
 *     .voiceId("tc_...")
 *     .text("Hello, world!")
 *     .model(TTSModel.SSFM_V30)
 *     .build();
 * 
 * TTSResponse response = client.textToSpeech(request);
 * byte[] audioData = response.getAudioData();
 * }</pre>
 */
public class TypecastClient {
    private static final String DEFAULT_BASE_URL = "https://api.typecast.ai";
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(CONTENT_TYPE_JSON);

    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final Gson gson;

    /**
     * Creates a new TypecastClient with API key from environment.
     * 
     * <p>Looks for API key in the following order:</p>
     * <ol>
     *   <li>.env file (TYPECAST_API_KEY)</li>
     *   <li>System environment variable (TYPECAST_API_KEY)</li>
     * </ol>
     * 
     * @throws IllegalArgumentException if no API key is found
     */
    public TypecastClient() {
        this(null, null);
    }

    /**
     * Creates a new TypecastClient with the specified API key.
     * 
     * @param apiKey the Typecast API key
     * @throws IllegalArgumentException if the API key is null or empty
     */
    public TypecastClient(String apiKey) {
        this(apiKey, null);
    }

    /**
     * Creates a new TypecastClient with the specified API key and base URL.
     * 
     * @param apiKey  the Typecast API key (or null to use environment)
     * @param baseUrl the base URL for the API (or null to use default)
     * @throws IllegalArgumentException if no API key is found
     */
    public TypecastClient(String apiKey, String baseUrl) {
        this.apiKey = resolveApiKey(apiKey);
        this.baseUrl = resolveBaseUrl(baseUrl);
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    /**
     * Creates a new TypecastClient with custom OkHttpClient.
     * 
     * @param apiKey     the Typecast API key
     * @param baseUrl    the base URL for the API (or null to use default)
     * @param httpClient custom OkHttpClient instance
     */
    public TypecastClient(String apiKey, String baseUrl, OkHttpClient httpClient) {
        this.apiKey = resolveApiKey(apiKey);
        this.baseUrl = resolveBaseUrl(baseUrl);
        this.httpClient = httpClient;
        
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    private String resolveApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }

        // Try .env file first
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            String envKey = dotenv.get("TYPECAST_API_KEY");
            if (envKey != null && !envKey.isEmpty()) {
                return envKey;
            }
        } catch (Exception ignored) {
            // Continue to system environment
        }

        // Try system environment variable
        String envKey = System.getenv("TYPECAST_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }

        throw new IllegalArgumentException(
                "API key is required. Set TYPECAST_API_KEY environment variable or pass it to the constructor.");
    }

    private String resolveBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }

        // Try .env file first
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            String envHost = dotenv.get("TYPECAST_API_HOST");
            if (envHost != null && !envHost.isEmpty()) {
                return envHost.endsWith("/") ? envHost.substring(0, envHost.length() - 1) : envHost;
            }
        } catch (Exception ignored) {
            // Continue to system environment
        }

        // Try system environment variable
        String envHost = System.getenv("TYPECAST_API_HOST");
        if (envHost != null && !envHost.isEmpty()) {
            return envHost.endsWith("/") ? envHost.substring(0, envHost.length() - 1) : envHost;
        }

        return DEFAULT_BASE_URL;
    }

    /**
     * Converts text to speech audio.
     * 
     * @param request the TTS request parameters
     * @return the TTS response containing audio data
     * @throws TypecastException if the API call fails
     */
    public TTSResponse textToSpeech(TTSRequest request) {
        String url = baseUrl + "/v1/text-to-speech";
        String jsonBody = buildTTSRequestJson(request);

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                handleError(response.code(), responseBody);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new TypecastException("Empty response body");
            }

            byte[] audioData = body.bytes();
            
            // Extract duration from header
            String durationHeader = response.header("X-Audio-Duration");
            double duration = 0.0;
            if (durationHeader != null) {
                try {
                    duration = Double.parseDouble(durationHeader);
                } catch (NumberFormatException ignored) {
                }
            }

            // Extract format from content type
            String contentType = response.header("Content-Type");
            String format = "wav";
            if (contentType != null) {
                if (contentType.contains("mp3")) {
                    format = "mp3";
                } else if (contentType.contains("wav")) {
                    format = "wav";
                }
            }

            return new TTSResponse(audioData, duration, format);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    private String buildTTSRequestJson(TTSRequest request) {
        JsonObject json = new JsonObject();
        json.addProperty("voice_id", request.getVoiceId());
        json.addProperty("text", request.getText());
        json.addProperty("model", request.getModel().getValue());

        if (request.getLanguage() != null) {
            json.addProperty("language", request.getLanguage().getValue());
        }

        if (request.getSeed() != null) {
            json.addProperty("seed", request.getSeed());
        }

        if (request.getPrompt() != null) {
            Object prompt = request.getPrompt();
            JsonObject promptJson = new JsonObject();

            if (prompt instanceof Prompt) {
                Prompt p = (Prompt) prompt;
                if (p.getEmotionPreset() != null) {
                    promptJson.addProperty("emotion_preset", p.getEmotionPreset().getValue());
                }
                if (p.getEmotionIntensity() != null) {
                    promptJson.addProperty("emotion_intensity", p.getEmotionIntensity());
                }
            } else if (prompt instanceof PresetPrompt) {
                PresetPrompt p = (PresetPrompt) prompt;
                promptJson.addProperty("emotion_type", p.getEmotionType());
                if (p.getEmotionPreset() != null) {
                    promptJson.addProperty("emotion_preset", p.getEmotionPreset().getValue());
                }
                if (p.getEmotionIntensity() != null) {
                    promptJson.addProperty("emotion_intensity", p.getEmotionIntensity());
                }
            } else if (prompt instanceof SmartPrompt) {
                SmartPrompt p = (SmartPrompt) prompt;
                promptJson.addProperty("emotion_type", p.getEmotionType());
                if (p.getPreviousText() != null) {
                    promptJson.addProperty("previous_text", p.getPreviousText());
                }
                if (p.getNextText() != null) {
                    promptJson.addProperty("next_text", p.getNextText());
                }
            }

            json.add("prompt", promptJson);
        }

        if (request.getOutput() != null) {
            Output output = request.getOutput();
            JsonObject outputJson = new JsonObject();

            if (output.getVolume() != null) {
                outputJson.addProperty("volume", output.getVolume());
            }
            if (output.getAudioPitch() != null) {
                outputJson.addProperty("audio_pitch", output.getAudioPitch());
            }
            if (output.getAudioTempo() != null) {
                outputJson.addProperty("audio_tempo", output.getAudioTempo());
            }
            if (output.getAudioFormat() != null) {
                outputJson.addProperty("audio_format", output.getAudioFormat().getValue());
            }

            json.add("output", outputJson);
        }

        return json.toString();
    }

    /**
     * Gets all available voices (V1 API).
     * 
     * @return list of available voices
     * @throws TypecastException if the API call fails
     * @deprecated Use {@link #getVoicesV2()} instead
     */
    @Deprecated
    public List<VoicesResponse> getVoices() {
        return getVoices(null);
    }

    /**
     * Gets available voices for a specific model (V1 API).
     * 
     * @param model the TTS model to filter by, or null for all
     * @return list of available voices
     * @throws TypecastException if the API call fails
     * @deprecated Use {@link #getVoicesV2(VoicesV2Filter)} instead
     */
    @Deprecated
    public List<VoicesResponse> getVoices(TTSModel model) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/v1/voices").newBuilder();
        if (model != null) {
            urlBuilder.addQueryParameter("model", model.getValue());
        }

        Request httpRequest = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                handleError(response.code(), responseBody);
            }

            Type listType = new TypeToken<List<VoicesResponse>>(){}.getType();
            return gson.fromJson(responseBody, listType);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Gets a specific voice by ID (V1 API).
     * 
     * @param voiceId the voice ID
     * @return the voice information
     * @throws TypecastException if the API call fails or voice not found
     * @deprecated Use {@link #getVoiceV2(String)} instead
     */
    @Deprecated
    public VoicesResponse getVoice(String voiceId) {
        return getVoice(voiceId, null);
    }

    /**
     * Gets a specific voice by ID for a specific model (V1 API).
     * 
     * @param voiceId the voice ID
     * @param model   the TTS model, or null for any
     * @return the voice information
     * @throws TypecastException if the API call fails or voice not found
     * @deprecated Use {@link #getVoiceV2(String)} instead
     */
    @Deprecated
    public VoicesResponse getVoice(String voiceId, TTSModel model) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/v1/voices/" + voiceId).newBuilder();
        if (model != null) {
            urlBuilder.addQueryParameter("model", model.getValue());
        }

        Request httpRequest = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                handleError(response.code(), responseBody);
            }

            // V1 API returns an array, get the first element
            Type listType = new TypeToken<List<VoicesResponse>>(){}.getType();
            List<VoicesResponse> voices = gson.fromJson(responseBody, listType);
            if (voices == null || voices.isEmpty()) {
                throw new NotFoundException("Voice not found: " + voiceId, responseBody);
            }
            return voices.get(0);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Gets all available voices with enhanced metadata (V2 API).
     * 
     * @return list of available voices
     * @throws TypecastException if the API call fails
     */
    public List<VoiceV2Response> getVoicesV2() {
        return getVoicesV2(null);
    }

    /**
     * Gets available voices with filtering (V2 API).
     * 
     * @param filter the filter options, or null for all voices
     * @return list of available voices
     * @throws TypecastException if the API call fails
     */
    public List<VoiceV2Response> getVoicesV2(VoicesV2Filter filter) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + "/v2/voices").newBuilder();
        
        if (filter != null) {
            if (filter.getModel() != null) {
                urlBuilder.addQueryParameter("model", filter.getModel().getValue());
            }
            if (filter.getGender() != null) {
                urlBuilder.addQueryParameter("gender", filter.getGender().getValue());
            }
            if (filter.getAge() != null) {
                urlBuilder.addQueryParameter("age", filter.getAge().getValue());
            }
            if (filter.getUseCases() != null) {
                urlBuilder.addQueryParameter("use_cases", filter.getUseCases().getValue());
            }
        }

        Request httpRequest = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                handleError(response.code(), responseBody);
            }

            Type listType = new TypeToken<List<VoiceV2Response>>(){}.getType();
            return gson.fromJson(responseBody, listType);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Gets a specific voice by ID with enhanced metadata (V2 API).
     * 
     * @param voiceId the voice ID
     * @return the voice information
     * @throws TypecastException if the API call fails or voice not found
     */
    public VoiceV2Response getVoiceV2(String voiceId) {
        String url = baseUrl + "/v2/voices/" + voiceId;

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader(API_KEY_HEADER, apiKey)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                handleError(response.code(), responseBody);
            }

            return gson.fromJson(responseBody, VoiceV2Response.class);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    private void handleError(int statusCode, String responseBody) {
        String message = extractErrorMessage(responseBody);
        
        switch (statusCode) {
            case 400:
                throw new BadRequestException(message, responseBody);
            case 401:
                throw new UnauthorizedException(message, responseBody);
            case 402:
                throw new PaymentRequiredException(message, responseBody);
            case 403:
                throw new ForbiddenException(message, responseBody);
            case 404:
                throw new NotFoundException(message, responseBody);
            case 422:
                throw new UnprocessableEntityException(message, responseBody);
            case 429:
                throw new RateLimitException(message, responseBody);
            case 500:
                throw new InternalServerException(message, responseBody);
            default:
                throw new TypecastException(message, statusCode, responseBody);
        }
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "Unknown error";
        }

        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            if (json.has("detail")) {
                return json.get("detail").getAsString();
            }
            if (json.has("message")) {
                return json.get("message").getAsString();
            }
            if (json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {
            // Return raw body if JSON parsing fails
        }

        return responseBody;
    }

    /**
     * Gets the base URL being used.
     * 
     * @return the base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Closes the HTTP client and releases resources.
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
