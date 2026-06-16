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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
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
     * @throws IllegalArgumentException if no API key is found and the default Typecast API host is used
     */
    public TypecastClient() {
        this(null, null);
    }

    /**
     * Creates a new TypecastClient with the specified API key.
     * 
     * @param apiKey the Typecast API key
     * @throws IllegalArgumentException if the API key is null or empty and the default Typecast API host is used
     */
    public TypecastClient(String apiKey) {
        this(apiKey, null);
    }

    /**
     * Creates a new TypecastClient with the specified API key and base URL.
     * 
     * @param apiKey  the Typecast API key (or null to use environment)
     * @param baseUrl the base URL for the API (or null to use default)
     * @throws IllegalArgumentException if no API key is found and the default Typecast API host is used
     */
    public TypecastClient(String apiKey, String baseUrl) {
        this.baseUrl = resolveBaseUrl(baseUrl);
        this.apiKey = resolveApiKey(apiKey, this.baseUrl);
        
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
        this.baseUrl = resolveBaseUrl(baseUrl);
        this.apiKey = resolveApiKey(apiKey, this.baseUrl);
        this.httpClient = httpClient;
        
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    private String resolveApiKey(String apiKey, String resolvedBaseUrl) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey.trim();
        }

        // dotenv-java reads both .env and system environment variables.
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String envKey = dotenv.get("TYPECAST_API_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }

        if (!isDefaultBaseUrl(resolvedBaseUrl)) {
            return "";
        }

        throw new IllegalArgumentException(
                "API key is required. Set TYPECAST_API_KEY environment variable or pass it to the constructor.");
    }

    private String resolveBaseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            return stripTrailingSlash(baseUrl.trim());
        }

        // dotenv-java reads both .env and system environment variables.
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String envHost = dotenv.get("TYPECAST_API_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            return stripTrailingSlash(envHost.trim());
        }

        return DEFAULT_BASE_URL;
    }

    private static String stripTrailingSlash(String url) {
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static boolean isDefaultBaseUrl(String url) {
        return DEFAULT_BASE_URL.equalsIgnoreCase(stripTrailingSlash(url.trim()));
    }

    private Request addAuthHeader(Request request) {
        Request.Builder builder = request.newBuilder();
        if (!apiKey.isEmpty()) {
            builder.addHeader(API_KEY_HEADER, apiKey);
        }
        return builder.build();
    }

    /**
     * Converts text to speech audio.
     * 
     * @param request the TTS request parameters
     * @return the TTS response containing audio data
     * @throws TypecastException if the API call fails
     */
    public TTSResponse textToSpeech(TTSRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        String url = baseUrl + "/v1/text-to-speech";
        String jsonBody = buildTTSRequestJson(request);

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(url)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw createException(response.code(), body.string());
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

    /**
     * Converts text to speech and saves the audio bytes to a file.
     *
     * @param filePath destination file path
     * @param request  generate-to-file request with required voiceId and text
     * @return the TTS response containing audio data
     * @throws TypecastException if the API call or file write fails
     */
    public TTSResponse generateToFile(String filePath, GenerateToFileRequest request) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        TTSResponse response = textToSpeech(request.toTTSRequest(filePath));
        try {
            Files.write(new File(filePath).toPath(), response.getAudioData());
        } catch (IOException e) {
            throw new TypecastException("Failed to write audio file", e);
        }
        return response;
    }

    /**
     * Converts text to speech and returns audio with word/character-level timestamps.
     *
     * <p>Calls {@code POST /v1/text-to-speech/with-timestamps}. An optional
     * {@code granularity} query parameter ({@code "word"} or {@code "char"}) restricts
     * the alignment data returned by the API.</p>
     *
     * @param request     the TTS request parameters
     * @param granularity alignment granularity — {@code "word"}, {@code "char"}, or
     *                    {@code null} to request both
     * @return the response containing base64 audio and alignment segments
     * @throws IllegalArgumentException if {@code granularity} is not null, "word", or "char"
     * @throws TypecastException if the API call fails
     */
    public TTSWithTimestampsResponse textToSpeechWithTimestamps(
            TTSRequestWithTimestamps request, String granularity) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (granularity != null && !granularity.equals("word") && !granularity.equals("char")) {
            throw new IllegalArgumentException(
                    "granularity must be \"word\", \"char\", or null; got: " + granularity);
        }

        HttpUrl.Builder urlBuilder =
                HttpUrl.parse(baseUrl + "/v1/text-to-speech/with-timestamps").newBuilder();
        if (granularity != null) {
            urlBuilder.addQueryParameter("granularity", granularity);
        }

        String jsonBody = buildTTSRequestWithTimestampsJson(request);

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw createException(response.code(), body.string());
            }
            String responseJson = body.string();
            return gson.fromJson(responseJson, TTSWithTimestampsResponse.class);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    private String buildTTSRequestWithTimestampsJson(TTSRequestWithTimestamps request) {
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
            } else {
                // Must be SmartPrompt
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
            if (output.getTargetLufs() != null) {
                outputJson.addProperty("target_lufs", output.getTargetLufs());
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
     * Streams synthesized audio from {@code POST /v1/text-to-speech/stream}.
     *
     * <p>Returns the raw response body as an {@link InputStream}. The caller is
     * responsible for closing the returned stream; closing it will also release
     * the underlying HTTP connection.</p>
     *
     * <p>For WAV the first chunk contains a WAV header (declared with size
     * {@code 0xFFFFFFFF} for streaming) followed by PCM data; subsequent reads
     * return PCM only. For MP3 the stream contains independently-decodable MP3
     * frames.</p>
     *
     * @param request the streaming TTS request parameters
     * @return an InputStream over the audio bytes (caller must close)
     * @throws IOException if the underlying HTTP call fails with an I/O error
     * @throws TypecastException if the API returns a non-200 status
     */
    public InputStream textToSpeechStream(TTSRequestStream request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        String url = baseUrl + "/v1/text-to-speech/stream";
        String jsonBody = buildTTSRequestStreamJson(request);

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(url)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .post(RequestBody.create(jsonBody, JSON_MEDIA_TYPE))
                .build());

        OkHttpClient streamClient = httpClient.newBuilder()
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
        Response response = streamClient.newCall(httpRequest).execute();
        if (!response.isSuccessful()) {
            String errorBody;
            try {
                errorBody = response.body().string();
            } finally {
                response.close();
            }
            throw createException(response.code(), errorBody);
        }

        // Caller is responsible for closing the returned stream, which will
        // also close the underlying Response/connection.
        return response.body().byteStream();
    }

    private String buildTTSRequestStreamJson(TTSRequestStream request) {
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
            } else {
                // Must be SmartPrompt — setPrompt only accepts Prompt/PresetPrompt/SmartPrompt
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
            OutputStream output = request.getOutput();
            JsonObject outputJson = new JsonObject();

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
            } else {
                // Must be SmartPrompt — setPrompt only accepts Prompt/PresetPrompt/SmartPrompt
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
            if (output.getTargetLufs() != null) {
                outputJson.addProperty("target_lufs", output.getTargetLufs());
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

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw createException(response.code(), responseBody);
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

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw createException(response.code(), responseBody);
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

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw createException(response.code(), responseBody);
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

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(url)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw createException(response.code(), responseBody);
            }

            return gson.fromJson(responseBody, VoiceV2Response.class);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Gets the authenticated user's subscription information.
     *
     * <p>Calls {@code GET /v1/users/me/subscription} and returns the current
     * plan tier, credit usage, and usage limits.</p>
     *
     * @return the subscription information
     * @throws TypecastException if the API call fails
     */
    public SubscriptionResponse getMySubscription() {
        String url = baseUrl + "/v1/users/me/subscription";

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(url)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .get()
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                throw createException(response.code(), responseBody);
            }

            return gson.fromJson(responseBody, SubscriptionResponse.class);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Creates a custom voice (created via instant cloning) from an audio sample.
     *
     * <p>Calls {@code POST /v1/voices/clone} with a multipart/form-data body
     * containing the audio file, voice name, and synthesis model.</p>
     *
     * @param audio    the raw audio bytes (WAV, MP3, or other format; max 25 MB)
     * @param filename the filename including extension, used to derive the MIME type
     *                 (e.g. {@code "sample.wav"}, {@code "voice.mp3"})
     * @param name     the display name for the new voice (1–30 characters)
     * @param model    the synthesis model to use (e.g. {@code "ssfm-v30"})
     * @return the created {@link CustomVoice} with its assigned {@code voiceId}
     * @throws IllegalArgumentException if {@code name} length is outside 1–30, or
     *                                  if {@code audio} exceeds 25 MB
     * @throws TypecastException        if the API returns an error response
     */
    public CustomVoice cloneVoice(byte[] audio, String filename, String name, String model) {
        if (name.length() < CustomVoice.NAME_MIN_LENGTH || name.length() > CustomVoice.NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be " + CustomVoice.NAME_MIN_LENGTH + "-" + CustomVoice.NAME_MAX_LENGTH
                    + " characters; got " + name.length());
        }
        if (audio.length > CustomVoice.CLONING_MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "audio file exceeds 25MB limit; got " + audio.length + " bytes");
        }

        MediaType mime = MediaType.parse(guessAudioMime(filename));
        RequestBody filePart = RequestBody.create(audio, mime);
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", name)
                .addFormDataPart("model", model)
                .addFormDataPart("file", filename, filePart)
                .build();

        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(baseUrl + "/v1/voices/clone")
                .post(body)
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseJson = response.body().string();
            if (!response.isSuccessful()) {
                throw createException(response.code(), responseJson);
            }
            return gson.fromJson(responseJson, CustomVoice.class);
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Convenience overload that reads bytes from a {@link File} and delegates to
     * {@link #cloneVoice(byte[], String, String, String)}.
     *
     * @param audioFile the audio file to upload (max 25 MB)
     * @param name      the display name for the new voice (1–30 characters)
     * @param model     the synthesis model to use (e.g. {@code "ssfm-v30"})
     * @return the created {@link CustomVoice} with its assigned {@code voiceId}
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if {@code name} length or file size is invalid
     * @throws TypecastException        if the API returns an error response
     */
    public CustomVoice cloneVoice(File audioFile, String name, String model) throws IOException {
        byte[] bytes = Files.readAllBytes(audioFile.toPath());
        return cloneVoice(bytes, audioFile.getName(), name, model);
    }

    /**
     * Deletes a custom voice (created via instant cloning).
     *
     * <p>Calls {@code DELETE /v1/voices/{voiceId}}. A 204 or 200 response is
     * treated as success.</p>
     *
     * @param voiceId the ID of the custom voice to delete (must have "uc_" prefix)
     * @throws TypecastException if the API returns an error response
     */
    public void deleteVoice(String voiceId) {
        Request httpRequest = addAuthHeader(new Request.Builder()
                .url(baseUrl + "/v1/voices/" + voiceId)
                .delete()
                .build());

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw createException(response.code(), response.body().string());
            }
        } catch (IOException e) {
            throw new TypecastException("Failed to make API request", e);
        }
    }

    /**
     * Guesses the audio MIME type from a filename extension.
     *
     * @param filename the filename (e.g. {@code "sample.wav"})
     * @return the MIME type string
     */
    private static String guessAudioMime(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        return "application/octet-stream";
    }

    private TypecastException createException(int statusCode, String responseBody) {
        String message = extractErrorMessage(responseBody);

        switch (statusCode) {
            case 400:
                return new BadRequestException(message, responseBody);
            case 401:
                return new UnauthorizedException(message, responseBody);
            case 402:
                return new PaymentRequiredException(message, responseBody);
            case 403:
                return new ForbiddenException(message, responseBody);
            case 404:
                return new NotFoundException(message, responseBody);
            case 422:
                return new UnprocessableEntityException(message, responseBody);
            case 429:
                return new RateLimitException(message, responseBody);
            case 500:
                return new InternalServerException(message, responseBody);
            default:
                return new TypecastException(message, statusCode, responseBody);
        }
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody.isEmpty()) {
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
