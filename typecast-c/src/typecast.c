/**
 * Typecast C/C++ SDK Implementation
 *
 * Text-to-Speech API client library for Typecast AI.
 *
 * Copyright (c) 2025 Typecast
 */

#ifndef TYPECAST_BUILDING_DLL
#define TYPECAST_BUILDING_DLL
#endif

#if !defined(_WIN32) && !defined(_WIN64)
    #define _POSIX_C_SOURCE 200809L
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#if defined(_WIN32) || defined(_WIN64)
    #define strncasecmp _strnicmp
#else
    #include <strings.h>  /* for strncasecmp */
#endif
#include <curl/curl.h>

#include "typecast.h"
#include "cJSON.h"

/* ============================================
 * Internal Structures
 * ============================================ */

struct TypecastClient {
    char* api_key;
    char* host;
    CURL* curl;
    TypecastError last_error;
};

typedef struct {
    uint8_t* data;
    size_t size;
    size_t capacity;
} ResponseBuffer;

typedef struct {
    char* data;
    size_t size;
} HeaderBuffer;

/* ============================================
 * String Constants
 * ============================================ */

static const char* DEFAULT_HOST = "https://api.typecast.ai";

static const char* MODEL_STRINGS[] = {
    "ssfm-v21",
    "ssfm-v30"
};

static const char* EMOTION_STRINGS[] = {
    "normal",
    "happy",
    "sad",
    "angry",
    "whisper",
    "toneup",
    "tonedown"
};

static const char* AUDIO_FORMAT_STRINGS[] = {
    "wav",
    "mp3"
};

static const char* GENDER_STRINGS[] = {
    "unknown",
    "male",
    "female"
};

static const char* PLAN_TIER_STRINGS[] = {
    "free",
    "lite",
    "plus",
    "custom"
};

static const char* AGE_STRINGS[] = {
    "unknown",
    "child",
    "teenager",
    "young_adult",
    "middle_age",
    "elder"
};

static const char* ERROR_MESSAGES[] = {
    "Success",
    "Invalid parameter",
    "Out of memory",
    "Failed to initialize CURL",
    "Network error",
    "JSON parse error",
    "Bad request",
    "Unauthorized",
    "Payment required",
    "Not found",
    "Unprocessable entity",
    "Rate limit exceeded",
    "Internal server error"
};

/* ============================================
 * Helper Functions
 * ============================================ */

static char* strdup_safe(const char* str) {
    if (!str) return NULL;
    size_t len = strlen(str);
    char* dup = (char*)malloc(len + 1);
    if (dup) {
        memcpy(dup, str, len + 1);
    }
    return dup;
}

static void set_error(TypecastClient* client, TypecastErrorCode code, const char* message) {
    if (!client) return;
    
    client->last_error.code = code;
    if (client->last_error.message) {
        free(client->last_error.message);
    }
    client->last_error.message = message ? strdup_safe(message) : NULL;
}

static void clear_error(TypecastClient* client) {
    if (!client) return;
    client->last_error.code = TYPECAST_OK;
    if (client->last_error.message) {
        free(client->last_error.message);
        client->last_error.message = NULL;
    }
}

/* ============================================
 * CURL Callbacks
 * ============================================ */

static size_t write_callback(void* contents, size_t size, size_t nmemb, void* userp) {
    size_t realsize = size * nmemb;
    ResponseBuffer* buf = (ResponseBuffer*)userp;
    
    /* Grow buffer if needed */
    if (buf->size + realsize + 1 > buf->capacity) {
        size_t new_capacity = (buf->capacity == 0) ? 4096 : buf->capacity * 2;
        while (new_capacity < buf->size + realsize + 1) {
            new_capacity *= 2;
        }
        uint8_t* new_data = (uint8_t*)realloc(buf->data, new_capacity);
        if (!new_data) return 0;
        buf->data = new_data;
        buf->capacity = new_capacity;
    }
    
    memcpy(buf->data + buf->size, contents, realsize);
    buf->size += realsize;
    buf->data[buf->size] = 0;
    
    return realsize;
}

static size_t header_callback(char* buffer, size_t size, size_t nitems, void* userp) {
    size_t realsize = size * nitems;
    HeaderBuffer* headers = (HeaderBuffer*)userp;
    
    /* Append header to buffer */
    size_t new_size = headers->size + realsize + 1;
    char* new_data = (char*)realloc(headers->data, new_size);
    if (!new_data) return 0;
    
    headers->data = new_data;
    memcpy(headers->data + headers->size, buffer, realsize);
    headers->size += realsize;
    headers->data[headers->size] = 0;
    
    return realsize;
}

/* ============================================
 * JSON Helpers
 * ============================================ */

static cJSON* build_tts_request_json(const TypecastTTSRequest* request) {
    cJSON* root = cJSON_CreateObject();
    if (!root) return NULL;
    
    /* Required fields */
    cJSON_AddStringToObject(root, "text", request->text);
    cJSON_AddStringToObject(root, "voice_id", request->voice_id);
    cJSON_AddStringToObject(root, "model", typecast_model_to_string(request->model));
    
    /* Optional: language */
    if (request->language) {
        cJSON_AddStringToObject(root, "language", request->language);
    }
    
    /* Optional: prompt */
    if (request->prompt) {
        cJSON* prompt = cJSON_CreateObject();
        if (prompt) {
            switch (request->prompt->emotion_type) {
                case TYPECAST_EMOTION_TYPE_SMART:
                    cJSON_AddStringToObject(prompt, "emotion_type", "smart");
                    if (request->prompt->previous_text) {
                        cJSON_AddStringToObject(prompt, "previous_text", request->prompt->previous_text);
                    }
                    if (request->prompt->next_text) {
                        cJSON_AddStringToObject(prompt, "next_text", request->prompt->next_text);
                    }
                    break;
                    
                case TYPECAST_EMOTION_TYPE_PRESET:
                    cJSON_AddStringToObject(prompt, "emotion_type", "preset");
                    cJSON_AddStringToObject(prompt, "emotion_preset", 
                        typecast_emotion_to_string(request->prompt->emotion_preset));
                    cJSON_AddNumberToObject(prompt, "emotion_intensity", 
                        request->prompt->emotion_intensity);
                    break;
                    
                case TYPECAST_EMOTION_TYPE_NONE:
                default:
                    /* Basic prompt for ssfm-v21 style */
                    cJSON_AddStringToObject(prompt, "emotion_preset", 
                        typecast_emotion_to_string(request->prompt->emotion_preset));
                    cJSON_AddNumberToObject(prompt, "emotion_intensity", 
                        request->prompt->emotion_intensity);
                    break;
            }
            cJSON_AddItemToObject(root, "prompt", prompt);
        }
    }
    
    /* Optional: output */
    if (request->output) {
        cJSON* output = cJSON_CreateObject();
        if (output) {
            if (request->output->use_target_lufs) {
                cJSON_AddNumberToObject(output, "target_lufs", request->output->target_lufs);
            } else {
                cJSON_AddNumberToObject(output, "volume", request->output->volume);
            }
            if (request->output->audio_pitch != 0) {
                cJSON_AddNumberToObject(output, "audio_pitch", request->output->audio_pitch);
            }
            if (request->output->audio_tempo != 0.0f && request->output->audio_tempo != 1.0f) {
                cJSON_AddNumberToObject(output, "audio_tempo", request->output->audio_tempo);
            }
            cJSON_AddStringToObject(output, "audio_format",
                typecast_audio_format_to_string(request->output->audio_format));
            cJSON_AddItemToObject(root, "output", output);
        }
    }

    /* Optional: seed */
    if (request->seed != 0) {
        cJSON_AddNumberToObject(root, "seed", request->seed);
    }
    
    return root;
}

static TypecastErrorCode http_status_to_error(long status_code) {
    switch (status_code) {
        /* LCOV_EXCL_START */
        /* category=unreachable reason="callers only invoke this for non-200 responses" */
        case 200: return TYPECAST_OK;
        /* LCOV_EXCL_STOP */
        case 400: return TYPECAST_ERROR_BAD_REQUEST;
        case 401: return TYPECAST_ERROR_UNAUTHORIZED;
        case 402: return TYPECAST_ERROR_PAYMENT_REQUIRED;
        case 404: return TYPECAST_ERROR_NOT_FOUND;
        case 422: return TYPECAST_ERROR_UNPROCESSABLE_ENTITY;
        case 429: return TYPECAST_ERROR_RATE_LIMIT;
        case 500: return TYPECAST_ERROR_INTERNAL_SERVER;
        default: return TYPECAST_ERROR_NETWORK;
    }
}

static float parse_duration_header(const char* headers) {
    if (!headers) return 0.0f;
    
    const char* duration_key = "x-audio-duration:";
    const char* pos = headers;
    
    while ((pos = strstr(pos, duration_key)) != NULL) {
        pos += strlen(duration_key);
        while (*pos == ' ') pos++;
        return (float)atof(pos);
    }
    
    /* Try case-insensitive search */
    const char* lower_headers = headers;
    while (*lower_headers) {
        if ((*lower_headers == 'x' || *lower_headers == 'X') &&
            strncasecmp(lower_headers, "x-audio-duration:", 17) == 0) {
            const char* value = lower_headers + 17;
            while (*value == ' ') value++;
            return (float)atof(value);
        }
        lower_headers++;
    }
    
    return 0.0f;
}

/* ============================================
 * Voice Parsing
 * ============================================ */

static TypecastGender parse_gender(const char* str) {
    if (!str) return TYPECAST_GENDER_UNKNOWN;
    if (strcmp(str, "male") == 0) return TYPECAST_GENDER_MALE;
    if (strcmp(str, "female") == 0) return TYPECAST_GENDER_FEMALE;
    return TYPECAST_GENDER_UNKNOWN;
}

static TypecastAge parse_age(const char* str) {
    if (!str) return TYPECAST_AGE_UNKNOWN;
    if (strcmp(str, "child") == 0) return TYPECAST_AGE_CHILD;
    if (strcmp(str, "teenager") == 0) return TYPECAST_AGE_TEENAGER;
    if (strcmp(str, "young_adult") == 0) return TYPECAST_AGE_YOUNG_ADULT;
    if (strcmp(str, "middle_age") == 0) return TYPECAST_AGE_MIDDLE_AGE;
    if (strcmp(str, "elder") == 0) return TYPECAST_AGE_ELDER;
    return TYPECAST_AGE_UNKNOWN;
}

static TypecastVoice* parse_voice_json(cJSON* json) {
    if (!json || !cJSON_IsObject(json)) return NULL;
    
    TypecastVoice* voice = (TypecastVoice*)calloc(1, sizeof(TypecastVoice));
    if (!voice) return NULL;
    
    /* voice_id */
    cJSON* voice_id = cJSON_GetObjectItem(json, "voice_id");
    if (cJSON_IsString(voice_id)) {
        voice->voice_id = strdup_safe(voice_id->valuestring);
    }
    
    /* voice_name */
    cJSON* voice_name = cJSON_GetObjectItem(json, "voice_name");
    if (cJSON_IsString(voice_name)) {
        voice->voice_name = strdup_safe(voice_name->valuestring);
    }
    
    /* gender */
    cJSON* gender = cJSON_GetObjectItem(json, "gender");
    if (cJSON_IsString(gender)) {
        voice->gender = parse_gender(gender->valuestring);
    }
    
    /* age */
    cJSON* age = cJSON_GetObjectItem(json, "age");
    if (cJSON_IsString(age)) {
        voice->age = parse_age(age->valuestring);
    }
    
    /* models */
    cJSON* models = cJSON_GetObjectItem(json, "models");
    if (cJSON_IsArray(models)) {
        voice->models_count = cJSON_GetArraySize(models);
        voice->models = (TypecastModelInfo*)calloc(voice->models_count, sizeof(TypecastModelInfo));
        if (voice->models) {
            for (size_t i = 0; i < voice->models_count; i++) {
                cJSON* model = cJSON_GetArrayItem(models, (int)i);
                if (cJSON_IsObject(model)) {
                    cJSON* version = cJSON_GetObjectItem(model, "version");
                    if (cJSON_IsString(version)) {
                        int model_idx = typecast_model_from_string(version->valuestring);
                        if (model_idx >= 0) {
                            voice->models[i].version = (TypecastModel)model_idx;
                        }
                    }
                    
                    cJSON* emotions = cJSON_GetObjectItem(model, "emotions");
                    if (cJSON_IsArray(emotions)) {
                        voice->models[i].emotions_count = cJSON_GetArraySize(emotions);
                        voice->models[i].emotions = (char**)calloc(
                            voice->models[i].emotions_count, sizeof(char*));
                        if (voice->models[i].emotions) {
                            for (size_t j = 0; j < voice->models[i].emotions_count; j++) {
                                cJSON* emotion = cJSON_GetArrayItem(emotions, (int)j);
                                if (cJSON_IsString(emotion)) {
                                    voice->models[i].emotions[j] = strdup_safe(emotion->valuestring);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /* use_cases */
    cJSON* use_cases = cJSON_GetObjectItem(json, "use_cases");
    if (cJSON_IsArray(use_cases)) {
        voice->use_cases_count = cJSON_GetArraySize(use_cases);
        voice->use_cases = (char**)calloc(voice->use_cases_count, sizeof(char*));
        if (voice->use_cases) {
            for (size_t i = 0; i < voice->use_cases_count; i++) {
                cJSON* use_case = cJSON_GetArrayItem(use_cases, (int)i);
                if (cJSON_IsString(use_case)) {
                    voice->use_cases[i] = strdup_safe(use_case->valuestring);
                }
            }
        }
    }
    
    return voice;
}

/* ============================================
 * Client API Implementation
 * ============================================ */

TYPECAST_API TypecastClient* typecast_client_create(const char* api_key) {
    return typecast_client_create_with_host(api_key, NULL);
}

TYPECAST_API TypecastClient* typecast_client_create_with_host(
    const char* api_key,
    const char* host
) {
    if (!api_key || strlen(api_key) == 0) {
        return NULL;
    }
    
    TypecastClient* client = (TypecastClient*)calloc(1, sizeof(TypecastClient));
    if (!client) return NULL;
    
    client->api_key = strdup_safe(api_key);
    client->host = strdup_safe(host ? host : DEFAULT_HOST);
    
    /* LCOV_EXCL_START */
    /* category=unreachable reason="strdup_safe OOM; cannot be simulated without a malloc shim" */
    if (!client->api_key || !client->host) {
        typecast_client_destroy(client);
        return NULL;
    }
    /* LCOV_EXCL_STOP */

    /* Initialize CURL globally (safe to call multiple times) */
    curl_global_init(CURL_GLOBAL_DEFAULT);

    client->curl = curl_easy_init();
    /* LCOV_EXCL_START */
    /* category=unreachable reason="curl_easy_init failure requires libcurl internal OOM" */
    if (!client->curl) {
        typecast_client_destroy(client);
        return NULL;
    }
    /* LCOV_EXCL_STOP */
    
    return client;
}

TYPECAST_API void typecast_client_destroy(TypecastClient* client) {
    if (!client) return;
    
    if (client->api_key) free(client->api_key);
    if (client->host) free(client->host);
    if (client->last_error.message) free(client->last_error.message);
    if (client->curl) curl_easy_cleanup(client->curl);
    
    free(client);
}

TYPECAST_API const TypecastError* typecast_client_get_error(const TypecastClient* client) {
    if (!client) return NULL;
    return &client->last_error;
}

/* ============================================
 * Text-to-Speech Implementation
 * ============================================ */

TYPECAST_API TypecastTTSResponse* typecast_text_to_speech(
    TypecastClient* client,
    const TypecastTTSRequest* request
) {
    if (!client || !request) {
        if (client) set_error(client, TYPECAST_ERROR_INVALID_PARAM, "Invalid parameters");
        return NULL;
    }
    
    if (!request->text || !request->voice_id) {
        set_error(client, TYPECAST_ERROR_INVALID_PARAM, "text and voice_id are required");
        return NULL;
    }
    
    clear_error(client);
    
    /* Build URL */
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/text-to-speech", client->host);
    
    /* Build JSON body */
    cJSON* json = build_tts_request_json(request);
    /* LCOV_EXCL_START */
    /* category=unreachable reason="cJSON OOM; cannot be triggered in unit tests" */
    if (!json) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to build JSON");
        return NULL;
    }
    /* LCOV_EXCL_STOP */

    char* json_str = cJSON_PrintUnformatted(json);
    cJSON_Delete(json);

    /* LCOV_EXCL_START */
    /* category=unreachable reason="cJSON_PrintUnformatted OOM" */
    if (!json_str) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to serialize JSON");
        return NULL;
    }
    /* LCOV_EXCL_STOP */
    
    /* Setup response buffers */
    ResponseBuffer response_buf = {0};
    HeaderBuffer header_buf = {0};
    
    /* Setup CURL */
    CURL* curl = client->curl;
    curl_easy_reset(curl);
    
    struct curl_slist* headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    
    char api_key_header[256];
    snprintf(api_key_header, sizeof(api_key_header), "X-API-KEY: %s", client->api_key);
    headers = curl_slist_append(headers, api_key_header);
    
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_str);
    /* Explicit body size — without this libcurl uses strlen() which can
     * walk past the end of cJSON's print buffer on some platforms/glibc
     * heap layouts and include stale bytes in the request body. */
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(json_str));
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_buf);
    curl_easy_setopt(curl, CURLOPT_HEADERFUNCTION, header_callback);
    curl_easy_setopt(curl, CURLOPT_HEADERDATA, &header_buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    
    /* Perform request */
    CURLcode res = curl_easy_perform(curl);
    
    cJSON_free(json_str);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        set_error(client, TYPECAST_ERROR_NETWORK, curl_easy_strerror(res));
        if (response_buf.data) free(response_buf.data);
        if (header_buf.data) free(header_buf.data);
        return NULL;
    }
    
    /* Check HTTP status */
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
    
    if (http_code != 200) {
        TypecastErrorCode err_code = http_status_to_error(http_code);
        
        /* Try to parse error message from response */
        char* err_msg = NULL;
        if (response_buf.data && response_buf.size > 0) {
            cJSON* err_json = cJSON_Parse((const char*)response_buf.data);
            if (err_json) {
                cJSON* detail = cJSON_GetObjectItem(err_json, "detail");
                if (cJSON_IsString(detail)) {
                    err_msg = strdup_safe(detail->valuestring);
                }
                cJSON_Delete(err_json);
            }
        }
        
        set_error(client, err_code, err_msg ? err_msg : typecast_error_message(err_code));
        if (err_msg) free(err_msg);
        if (response_buf.data) free(response_buf.data);
        if (header_buf.data) free(header_buf.data);
        return NULL;
    }
    
    /* Create response */
    TypecastTTSResponse* resp = (TypecastTTSResponse*)calloc(1, sizeof(TypecastTTSResponse));
    /* LCOV_EXCL_START */
    /* category=unreachable reason="calloc OOM; cannot be triggered deterministically" */
    if (!resp) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate response");
        if (response_buf.data) free(response_buf.data);
        if (header_buf.data) free(header_buf.data);
        return NULL;
    }
    /* LCOV_EXCL_STOP */
    
    resp->audio_data = response_buf.data;
    resp->audio_size = response_buf.size;
    resp->duration = parse_duration_header(header_buf.data);
    
    /* Determine format from request or content-type */
    if (request->output) {
        resp->format = request->output->audio_format;
    } else {
        resp->format = TYPECAST_AUDIO_FORMAT_WAV;
    }
    
    if (header_buf.data) free(header_buf.data);
    
    return resp;
}

TYPECAST_API void typecast_tts_response_free(TypecastTTSResponse* response) {
    if (!response) return;
    if (response->audio_data) free(response->audio_data);
    free(response);
}

/* ============================================
 * Text-to-Speech Streaming Implementation
 * ============================================ */

typedef struct {
    typecast_stream_callback_t cb;
    void* user_data;
    int aborted;
} StreamCallbackCtx;

static size_t stream_write_callback(void* contents, size_t size, size_t nmemb, void* userp) {
    size_t realsize = size * nmemb;
    StreamCallbackCtx* ctx = (StreamCallbackCtx*)userp;

    if (ctx->cb((const uint8_t*)contents, realsize, ctx->user_data) != 0) {
        ctx->aborted = 1;
        /* Returning a value different from realsize signals an error to
         * libcurl, which will abort the transfer with CURLE_WRITE_ERROR. */
        return 0;
    }
    return realsize;
}

/* Build the streaming JSON body. Mirrors build_tts_request_json but the
 * output object intentionally omits volume / target_lufs (rejected by
 * /v1/text-to-speech/stream). */
static cJSON* build_tts_stream_request_json(const TypecastTTSRequestStream* request) {
    cJSON* root = cJSON_CreateObject();
    if (!root) return NULL;

    cJSON_AddStringToObject(root, "text", request->text);
    cJSON_AddStringToObject(root, "voice_id", request->voice_id);
    cJSON_AddStringToObject(root, "model", typecast_model_to_string(request->model));

    if (request->language) {
        cJSON_AddStringToObject(root, "language", request->language);
    }

    if (request->prompt) {
        cJSON* prompt = cJSON_CreateObject();
        if (prompt) {
            switch (request->prompt->emotion_type) {
                case TYPECAST_EMOTION_TYPE_SMART:
                    cJSON_AddStringToObject(prompt, "emotion_type", "smart");
                    if (request->prompt->previous_text) {
                        cJSON_AddStringToObject(prompt, "previous_text", request->prompt->previous_text);
                    }
                    if (request->prompt->next_text) {
                        cJSON_AddStringToObject(prompt, "next_text", request->prompt->next_text);
                    }
                    break;

                case TYPECAST_EMOTION_TYPE_PRESET:
                    cJSON_AddStringToObject(prompt, "emotion_type", "preset");
                    cJSON_AddStringToObject(prompt, "emotion_preset",
                        typecast_emotion_to_string(request->prompt->emotion_preset));
                    cJSON_AddNumberToObject(prompt, "emotion_intensity",
                        request->prompt->emotion_intensity);
                    break;

                case TYPECAST_EMOTION_TYPE_NONE:
                default:
                    cJSON_AddStringToObject(prompt, "emotion_preset",
                        typecast_emotion_to_string(request->prompt->emotion_preset));
                    cJSON_AddNumberToObject(prompt, "emotion_intensity",
                        request->prompt->emotion_intensity);
                    break;
            }
            cJSON_AddItemToObject(root, "prompt", prompt);
        }
    }

    if (request->output) {
        cJSON* output = cJSON_CreateObject();
        if (output) {
            /* NOTE: volume and target_lufs are deliberately omitted - the
             * streaming endpoint rejects them. */
            if (request->output->audio_pitch != 0) {
                cJSON_AddNumberToObject(output, "audio_pitch", request->output->audio_pitch);
            }
            if (request->output->audio_tempo != 0.0f && request->output->audio_tempo != 1.0f) {
                cJSON_AddNumberToObject(output, "audio_tempo", request->output->audio_tempo);
            }
            cJSON_AddStringToObject(output, "audio_format",
                typecast_audio_format_to_string(request->output->audio_format));
            cJSON_AddItemToObject(root, "output", output);
        }
    }

    if (request->seed != 0) {
        cJSON_AddNumberToObject(root, "seed", request->seed);
    }

    return root;
}

TYPECAST_API TypecastErrorCode typecast_text_to_speech_stream(
    TypecastClient* client,
    const TypecastTTSRequestStream* request,
    typecast_stream_callback_t on_chunk,
    void* user_data
) {
    if (!client || !request || !on_chunk) {
        if (client) set_error(client, TYPECAST_ERROR_INVALID_PARAM, "Invalid parameters");
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    if (!request->text || !request->voice_id) {
        set_error(client, TYPECAST_ERROR_INVALID_PARAM, "text and voice_id are required");
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    clear_error(client);

    /* Build URL */
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/text-to-speech/stream", client->host);

    /* Build JSON body */
    cJSON* json = build_tts_stream_request_json(request);
    /* LCOV_EXCL_START */
    /* category=unreachable reason="cJSON OOM; cannot be triggered in unit tests" */
    if (!json) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to build JSON");
        return TYPECAST_ERROR_OUT_OF_MEMORY;
    }
    /* LCOV_EXCL_STOP */

    char* json_str = cJSON_PrintUnformatted(json);
    cJSON_Delete(json);

    /* LCOV_EXCL_START */
    /* category=unreachable reason="cJSON_PrintUnformatted OOM" */
    if (!json_str) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to serialize JSON");
        return TYPECAST_ERROR_OUT_OF_MEMORY;
    }
    /* LCOV_EXCL_STOP */

    /* Setup CURL */
    CURL* curl = client->curl;
    curl_easy_reset(curl);

    struct curl_slist* headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");

    char api_key_header[256];
    snprintf(api_key_header, sizeof(api_key_header), "X-API-KEY: %s", client->api_key);
    headers = curl_slist_append(headers, api_key_header);

    StreamCallbackCtx ctx;
    ctx.cb = on_chunk;
    ctx.user_data = user_data;
    ctx.aborted = 0;

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_str);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(json_str));
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, stream_write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &ctx);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);

    /* Perform request */
    CURLcode res = curl_easy_perform(curl);

    cJSON_free(json_str);
    curl_slist_free_all(headers);

    if (res != CURLE_OK) {
        if (ctx.aborted) {
            set_error(client, TYPECAST_ERROR_NETWORK, "Stream aborted by callback");
        } else {
            set_error(client, TYPECAST_ERROR_NETWORK, curl_easy_strerror(res));
        }
        return TYPECAST_ERROR_NETWORK;
    }

    /* Check HTTP status. On non-200 the body went to the user callback,
     * which is not ideal but matches the streaming model: the SDK has
     * already started forwarding chunks before the status is known.
     * For our purposes the body has been consumed; we still need to
     * surface the proper error code. */
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    if (http_code != 200) {
        TypecastErrorCode err_code = http_status_to_error(http_code);
        set_error(client, err_code, typecast_error_message(err_code));
        return err_code;
    }

    return TYPECAST_OK;
}

/* ============================================
 * Voices API Implementation
 * ============================================ */

TYPECAST_API TypecastVoicesResponse* typecast_get_voices(
    TypecastClient* client,
    const TypecastVoicesFilter* filter
) {
    if (!client) return NULL;
    
    clear_error(client);
    
    /* Build URL with query parameters */
    char url[1024];
    snprintf(url, sizeof(url), "%s/v2/voices", client->host);
    
    /* Add filter parameters */
    int has_params = 0;
    if (filter) {
        if (filter->model) {
            snprintf(url + strlen(url), sizeof(url) - strlen(url), 
                "%smodel=%s", has_params ? "&" : "?", 
                typecast_model_to_string(*filter->model));
            has_params = 1;
        }
        if (filter->gender) {
            snprintf(url + strlen(url), sizeof(url) - strlen(url),
                "%sgender=%s", has_params ? "&" : "?",
                GENDER_STRINGS[*filter->gender]);
            has_params = 1;
        }
        if (filter->age) {
            snprintf(url + strlen(url), sizeof(url) - strlen(url),
                "%sage=%s", has_params ? "&" : "?",
                AGE_STRINGS[*filter->age]);
            has_params = 1;
        }
        if (filter->use_cases) {
            snprintf(url + strlen(url), sizeof(url) - strlen(url),
                "%suse_cases=%s", has_params ? "&" : "?",
                filter->use_cases);
            has_params = 1;
        }
    }
    
    /* Setup response buffer */
    ResponseBuffer response_buf = {0};
    
    /* Setup CURL */
    CURL* curl = client->curl;
    curl_easy_reset(curl);
    
    struct curl_slist* headers = NULL;
    char api_key_header[256];
    snprintf(api_key_header, sizeof(api_key_header), "X-API-KEY: %s", client->api_key);
    headers = curl_slist_append(headers, api_key_header);
    
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    
    /* Perform request */
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        set_error(client, TYPECAST_ERROR_NETWORK, curl_easy_strerror(res));
        if (response_buf.data) free(response_buf.data);
        return NULL;
    }
    
    /* Check HTTP status */
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
    
    if (http_code != 200) {
        TypecastErrorCode err_code = http_status_to_error(http_code);
        set_error(client, err_code, typecast_error_message(err_code));
        if (response_buf.data) free(response_buf.data);
        return NULL;
    }
    
    /* Parse JSON response */
    cJSON* json = cJSON_Parse((const char*)response_buf.data);
    free(response_buf.data);
    
    if (!json) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Failed to parse response");
        return NULL;
    }
    
    if (!cJSON_IsArray(json)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Expected array response");
        cJSON_Delete(json);
        return NULL;
    }
    
    /* Create response */
    TypecastVoicesResponse* resp = (TypecastVoicesResponse*)calloc(1, sizeof(TypecastVoicesResponse));
    /* LCOV_EXCL_START */
    /* category=unreachable reason="calloc OOM; cannot be triggered deterministically" */
    if (!resp) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate response");
        cJSON_Delete(json);
        return NULL;
    }
    /* LCOV_EXCL_STOP */

    resp->count = cJSON_GetArraySize(json);
    resp->voices = (TypecastVoice*)calloc(resp->count, sizeof(TypecastVoice));

    /* LCOV_EXCL_START */
    /* category=unreachable reason="calloc OOM; cannot be triggered deterministically" */
    if (!resp->voices) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate voices array");
        free(resp);
        cJSON_Delete(json);
        return NULL;
    }
    /* LCOV_EXCL_STOP */
    
    for (size_t i = 0; i < resp->count; i++) {
        cJSON* voice_json = cJSON_GetArrayItem(json, (int)i);
        TypecastVoice* parsed = parse_voice_json(voice_json);
        if (parsed) {
            memcpy(&resp->voices[i], parsed, sizeof(TypecastVoice));
            free(parsed);
        }
    }
    
    cJSON_Delete(json);
    return resp;
}

TYPECAST_API TypecastVoice* typecast_get_voice(
    TypecastClient* client,
    const char* voice_id
) {
    if (!client || !voice_id) {
        if (client) set_error(client, TYPECAST_ERROR_INVALID_PARAM, "voice_id is required");
        return NULL;
    }
    
    clear_error(client);
    
    /* Build URL */
    char url[512];
    snprintf(url, sizeof(url), "%s/v2/voices/%s", client->host, voice_id);
    
    /* Setup response buffer */
    ResponseBuffer response_buf = {0};
    
    /* Setup CURL */
    CURL* curl = client->curl;
    curl_easy_reset(curl);
    
    struct curl_slist* headers = NULL;
    char api_key_header[256];
    snprintf(api_key_header, sizeof(api_key_header), "X-API-KEY: %s", client->api_key);
    headers = curl_slist_append(headers, api_key_header);
    
    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);
    
    /* Perform request */
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);
    
    if (res != CURLE_OK) {
        set_error(client, TYPECAST_ERROR_NETWORK, curl_easy_strerror(res));
        if (response_buf.data) free(response_buf.data);
        return NULL;
    }
    
    /* Check HTTP status */
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
    
    if (http_code != 200) {
        TypecastErrorCode err_code = http_status_to_error(http_code);
        set_error(client, err_code, typecast_error_message(err_code));
        if (response_buf.data) free(response_buf.data);
        return NULL;
    }
    
    /* Parse JSON response */
    cJSON* json = cJSON_Parse((const char*)response_buf.data);
    free(response_buf.data);
    
    if (!json) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Failed to parse response");
        return NULL;
    }
    
    TypecastVoice* voice = parse_voice_json(json);
    cJSON_Delete(json);
    
    if (!voice) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Failed to parse voice");
    }
    
    return voice;
}

TYPECAST_API void typecast_voices_response_free(TypecastVoicesResponse* response) {
    if (!response) return;
    
    for (size_t i = 0; i < response->count; i++) {
        TypecastVoice* voice = &response->voices[i];
        if (voice->voice_id) free(voice->voice_id);
        if (voice->voice_name) free(voice->voice_name);
        
        for (size_t j = 0; j < voice->models_count; j++) {
            for (size_t k = 0; k < voice->models[j].emotions_count; k++) {
                if (voice->models[j].emotions[k]) free(voice->models[j].emotions[k]);
            }
            if (voice->models[j].emotions) free(voice->models[j].emotions);
        }
        if (voice->models) free(voice->models);
        
        for (size_t j = 0; j < voice->use_cases_count; j++) {
            if (voice->use_cases[j]) free(voice->use_cases[j]);
        }
        if (voice->use_cases) free(voice->use_cases);
    }
    
    if (response->voices) free(response->voices);
    free(response);
}

TYPECAST_API void typecast_voice_free(TypecastVoice* voice) {
    if (!voice) return;
    
    if (voice->voice_id) free(voice->voice_id);
    if (voice->voice_name) free(voice->voice_name);
    
    for (size_t j = 0; j < voice->models_count; j++) {
        for (size_t k = 0; k < voice->models[j].emotions_count; k++) {
            if (voice->models[j].emotions[k]) free(voice->models[j].emotions[k]);
        }
        if (voice->models[j].emotions) free(voice->models[j].emotions);
    }
    if (voice->models) free(voice->models);
    
    for (size_t j = 0; j < voice->use_cases_count; j++) {
        if (voice->use_cases[j]) free(voice->use_cases[j]);
    }
    if (voice->use_cases) free(voice->use_cases);
    
    free(voice);
}

/* ============================================
 * Subscription API Implementation
 * ============================================ */

TYPECAST_API TypecastSubscription* typecast_get_my_subscription(
    TypecastClient* client
) {
    if (!client) return NULL;

    clear_error(client);

    /* Build URL */
    char url[512];
    snprintf(url, sizeof(url), "%s/v1/users/me/subscription", client->host);

    /* Setup response buffer */
    ResponseBuffer response_buf = {0};

    /* Setup CURL */
    CURL* curl = client->curl;
    curl_easy_reset(curl);

    struct curl_slist* headers = NULL;
    char api_key_header[256];
    snprintf(api_key_header, sizeof(api_key_header), "X-API-KEY: %s", client->api_key);
    headers = curl_slist_append(headers, api_key_header);

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);

    /* Perform request */
    CURLcode res = curl_easy_perform(curl);
    curl_slist_free_all(headers);

    if (res != CURLE_OK) {
        set_error(client, TYPECAST_ERROR_NETWORK, curl_easy_strerror(res));
        if (response_buf.data) free(response_buf.data);
        return NULL;
    }

    /* Check HTTP status */
    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    if (http_code != 200) {
        TypecastErrorCode err_code = http_status_to_error(http_code);
        set_error(client, err_code, typecast_error_message(err_code));
        if (response_buf.data) free(response_buf.data);
        return NULL;
    }

    /* Parse JSON response */
    cJSON* json = cJSON_Parse((const char*)response_buf.data);
    free(response_buf.data);

    if (!json) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Failed to parse response");
        return NULL;
    }

    /* plan */
    cJSON* plan = cJSON_GetObjectItem(json, "plan");
    if (!cJSON_IsString(plan)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Missing or invalid 'plan' field");
        cJSON_Delete(json);
        return NULL;
    }
    int plan_idx = typecast_plan_tier_from_string(plan->valuestring);
    if (plan_idx < 0) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Unknown plan tier");
        cJSON_Delete(json);
        return NULL;
    }

    /* credits */
    cJSON* credits = cJSON_GetObjectItem(json, "credits");
    if (!cJSON_IsObject(credits)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Missing or invalid 'credits' field");
        cJSON_Delete(json);
        return NULL;
    }
    cJSON* plan_credits = cJSON_GetObjectItem(credits, "plan_credits");
    if (!cJSON_IsNumber(plan_credits)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Missing or invalid 'plan_credits' field");
        cJSON_Delete(json);
        return NULL;
    }
    cJSON* used_credits = cJSON_GetObjectItem(credits, "used_credits");
    if (!cJSON_IsNumber(used_credits)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Missing or invalid 'used_credits' field");
        cJSON_Delete(json);
        return NULL;
    }

    /* limits */
    cJSON* limits = cJSON_GetObjectItem(json, "limits");
    if (!cJSON_IsObject(limits)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Missing or invalid 'limits' field");
        cJSON_Delete(json);
        return NULL;
    }
    cJSON* concurrency_limit = cJSON_GetObjectItem(limits, "concurrency_limit");
    if (!cJSON_IsNumber(concurrency_limit)) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Missing or invalid 'concurrency_limit' field");
        cJSON_Delete(json);
        return NULL;
    }

    TypecastSubscription* sub = (TypecastSubscription*)calloc(1, sizeof(TypecastSubscription));
    /* LCOV_EXCL_START */
    /* category=unreachable reason="calloc OOM; cannot be triggered deterministically" */
    if (!sub) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate subscription");
        cJSON_Delete(json);
        return NULL;
    }
    /* LCOV_EXCL_STOP */

    sub->plan = (TypecastPlanTier)plan_idx;
    sub->credits.plan_credits = (int64_t)plan_credits->valuedouble;
    sub->credits.used_credits = (int64_t)used_credits->valuedouble;
    sub->limits.concurrency_limit = (int)concurrency_limit->valuedouble;

    cJSON_Delete(json);
    return sub;
}

TYPECAST_API void typecast_subscription_free(TypecastSubscription* subscription) {
    if (!subscription) return;
    free(subscription);
}

/* ============================================
 * Utility Functions Implementation
 * ============================================ */

TYPECAST_API const char* typecast_version(void) {
    return TYPECAST_VERSION;
}

TYPECAST_API const char* typecast_model_to_string(TypecastModel model) {
    if (model < 0 || model > TYPECAST_MODEL_SSFM_V30) {
        return "unknown";
    }
    return MODEL_STRINGS[model];
}

TYPECAST_API int typecast_model_from_string(const char* str) {
    if (!str) return -1;
    if (strcmp(str, "ssfm-v21") == 0) return TYPECAST_MODEL_SSFM_V21;
    if (strcmp(str, "ssfm-v30") == 0) return TYPECAST_MODEL_SSFM_V30;
    return -1;
}

TYPECAST_API const char* typecast_emotion_to_string(TypecastEmotionPreset preset) {
    if (preset < 0 || preset > TYPECAST_EMOTION_TONEDOWN) {
        return "normal";
    }
    return EMOTION_STRINGS[preset];
}

TYPECAST_API const char* typecast_audio_format_to_string(TypecastAudioFormat format) {
    if (format < 0 || format > TYPECAST_AUDIO_FORMAT_MP3) {
        return "wav";
    }
    return AUDIO_FORMAT_STRINGS[format];
}

TYPECAST_API const char* typecast_plan_tier_to_string(TypecastPlanTier plan) {
    if (plan < 0 || plan > TYPECAST_PLAN_TIER_CUSTOM) {
        return "unknown";
    }
    return PLAN_TIER_STRINGS[plan];
}

TYPECAST_API int typecast_plan_tier_from_string(const char* str) {
    if (!str) return -1;
    if (strcmp(str, "free") == 0) return TYPECAST_PLAN_TIER_FREE;
    if (strcmp(str, "lite") == 0) return TYPECAST_PLAN_TIER_LITE;
    if (strcmp(str, "plus") == 0) return TYPECAST_PLAN_TIER_PLUS;
    if (strcmp(str, "custom") == 0) return TYPECAST_PLAN_TIER_CUSTOM;
    return -1;
}

TYPECAST_API const char* typecast_error_message(TypecastErrorCode code) {
    switch (code) {
        case TYPECAST_OK: return ERROR_MESSAGES[0];
        case TYPECAST_ERROR_INVALID_PARAM: return ERROR_MESSAGES[1];
        case TYPECAST_ERROR_OUT_OF_MEMORY: return ERROR_MESSAGES[2];
        case TYPECAST_ERROR_CURL_INIT: return ERROR_MESSAGES[3];
        case TYPECAST_ERROR_NETWORK: return ERROR_MESSAGES[4];
        case TYPECAST_ERROR_JSON_PARSE: return ERROR_MESSAGES[5];
        case TYPECAST_ERROR_BAD_REQUEST: return ERROR_MESSAGES[6];
        case TYPECAST_ERROR_UNAUTHORIZED: return ERROR_MESSAGES[7];
        case TYPECAST_ERROR_PAYMENT_REQUIRED: return ERROR_MESSAGES[8];
        case TYPECAST_ERROR_NOT_FOUND: return ERROR_MESSAGES[9];
        case TYPECAST_ERROR_UNPROCESSABLE_ENTITY: return ERROR_MESSAGES[10];
        case TYPECAST_ERROR_RATE_LIMIT: return ERROR_MESSAGES[11];
        case TYPECAST_ERROR_INTERNAL_SERVER: return ERROR_MESSAGES[12];
        default: return "Unknown error";
    }
}

/* ============================================
 * Timestamp TTS — Captioning helpers
 * ============================================ */

/* Count UTF-8 codepoints: every byte that is NOT a continuation byte. */
static size_t utf8_codepoint_count(const char* s) {
    size_t count = 0;
    const unsigned char* p = (const unsigned char*)s;
    for (; *p; p++) {
        if ((*p & 0xc0) != 0x80) count++;
    }
    return count;
}

/* Sentence-ending terminators (ASCII + full-width punctuation as UTF-8). */
static const char* CAPTION_TERMINATORS[] = {
    ".",
    "?",
    "!",
    "\xe3\x80\x82",  /* 。 U+3002 */
    "\xef\xbc\x9f",  /* ？ U+FF1F */
    "\xef\xbc\x81",  /* ！ U+FF01 */
    NULL
};

static const float CAPTION_MAX_SECONDS = 7.0f;
static const size_t CAPTION_MAX_CHARS   = 42;

/* Return 1 if haystack ends with needle (both NUL-terminated byte strings). */
static int ends_with_str(const char* haystack, const char* needle) {
    size_t hl = strlen(haystack), nl = strlen(needle);
    if (nl > hl) return 0;
    return memcmp(haystack + hl - nl, needle, nl) == 0;
}

/* Return 1 if text (after stripping trailing whitespace) ends in a sentence
 * terminator.  Works with multi-byte UTF-8 terminators. */
static int ends_in_sentence(const char* text) {
    /* Make a mutable copy so we can trim in-place. */
    char* trimmed = strdup_safe(text);
    if (!trimmed) return 0;
    size_t n = strlen(trimmed);
    while (n > 0 && (trimmed[n-1] == ' ' || trimmed[n-1] == '\t' ||
                     trimmed[n-1] == '\n' || trimmed[n-1] == '\r')) {
        trimmed[--n] = '\0';
    }
    int found = 0;
    for (int i = 0; CAPTION_TERMINATORS[i]; i++) {
        if (ends_with_str(trimmed, CAPTION_TERMINATORS[i])) {
            found = 1;
            break;
        }
    }
    free(trimmed);
    return found;
}

/* Strip leading and trailing ASCII whitespace in-place on buf[0..len].
 * Returns the new (possibly 0) length via *new_len. */
static void strip_whitespace_inplace(char* buf, size_t* len) {
    size_t n = *len;
    /* Trailing */
    while (n > 0 && (buf[n-1] == ' ' || buf[n-1] == '\t' ||
                     buf[n-1] == '\n' || buf[n-1] == '\r')) {
        buf[--n] = '\0';
    }
    /* Leading */
    size_t lead = 0;
    while (lead < n && (buf[lead] == ' ' || buf[lead] == '\t' ||
                        buf[lead] == '\n' || buf[lead] == '\r')) {
        lead++;
    }
    if (lead > 0) {
        memmove(buf, buf + lead, n - lead + 1); /* +1 for '\0' */
        n -= lead;
    }
    *len = n;
}

/* ---- Dynamic string builder ---- */
typedef struct {
    char*  data;
    size_t len;
    size_t cap;
} StrBuf;

static int strbuf_append(StrBuf* b, const char* s) {
    size_t slen = strlen(s);
    if (b->len + slen + 1 > b->cap) {
        size_t new_cap = b->cap ? b->cap * 2 : 256;
        while (new_cap < b->len + slen + 1) new_cap *= 2;
        char* nd = (char*)realloc(b->data, new_cap);
        if (!nd) return 0;
        b->data = nd;
        b->cap  = new_cap;
    }
    memcpy(b->data + b->len, s, slen);
    b->len += slen;
    b->data[b->len] = '\0';
    return 1;
}

/* Format seconds -> HH:MM:SS,mmm (SRT) into caller-supplied buf (>=16 bytes) */
static void fmt_srt_time(float sec, char* buf, size_t buf_size) {
    int total_ms = (int)(sec * 1000.0f + 0.5f);
    if (total_ms < 0) total_ms = 0;
    int ms = total_ms % 1000;
    int s  = (total_ms / 1000) % 60;
    int m  = (total_ms / 60000) % 60;
    int h  = (total_ms / 3600000);
    snprintf(buf, buf_size, "%02d:%02d:%02d,%03d", h, m, s, ms);
}

/* Format seconds -> HH:MM:SS.mmm (VTT) */
static void fmt_vtt_time(float sec, char* buf, size_t buf_size) {
    int total_ms = (int)(sec * 1000.0f + 0.5f);
    if (total_ms < 0) total_ms = 0;
    int ms = total_ms % 1000;
    int s  = (total_ms / 1000) % 60;
    int m  = (total_ms / 60000) % 60;
    int h  = (total_ms / 3600000);
    snprintf(buf, buf_size, "%02d:%02d:%02d.%03d", h, m, s, ms);
}

/* ---- Caption cue ---- */
typedef struct {
    char*  text;   /* heap-allocated, caller frees */
    float  start;
    float  end;
} CaptionCue;

/* Free an array of CaptionCue.  cues[i].text is freed; then the array. */
static void cues_free(CaptionCue* cues, int n) {
    if (!cues) return;
    for (int i = 0; i < n; i++) {
        if (cues[i].text) free(cues[i].text);
    }
    free(cues);
}

/*
 * Build caption cues from an alignment segment array.
 *
 * word_mode = 1  ->  parts joined with " "
 * word_mode = 0  ->  parts concatenated directly (char mode)
 *
 * Returns heap-allocated array of CaptionCue (caller must call cues_free).
 * *out_count is set to the number of cues.
 *
 * TODO(TASK-12430-followup): expose max_seconds / max_chars override to match Python/JS API surface. Default 7.0s / 42 chars (BBC/Netflix guideline).
 * TODO(TASK-12430-followup): warn or error when alignment array contains majority-empty text segments — server contract should never produce these but defense-in-depth is desirable.
 */
static CaptionCue* group_into_cues(
    const TypecastAlignmentSegment* segs,
    size_t seg_count,
    int word_mode,
    int* out_count
) {
    *out_count = 0;
    if (!segs || seg_count == 0) return NULL;

    /* We build cue text into a dynamic string.
     * Max cues possible = seg_count (one cue per segment in worst case). */
    CaptionCue* cues = (CaptionCue*)calloc(seg_count, sizeof(CaptionCue));
    if (!cues) return NULL;

    int ncues = 0;

    /* Current in-progress cue parts.  We accumulate a single string. */
    StrBuf cur = {0};
    float  cur_start = 0.0f;
    float  last_end  = 0.0f;
    int    has_cur   = 0;

    for (size_t i = 0; i < seg_count; i++) {
        const TypecastAlignmentSegment* seg = &segs[i];

        if (has_cur) {
            /* Build the candidate text if we include this segment. */
            size_t candidate_len = cur.len
                + (word_mode ? 1 : 0)   /* space separator */
                + strlen(seg->text);
            /* Temp buffer to compute codepoint count.  We only need the count,
             * not the actual string, so compute it without a full strdup. */
            size_t candidate_cp;
            if (word_mode) {
                /* count = cur codepoints + 1 (space) + seg codepoints */
                candidate_cp = utf8_codepoint_count(cur.data)
                             + 1
                             + utf8_codepoint_count(seg->text);
            } else {
                candidate_cp = utf8_codepoint_count(cur.data)
                             + utf8_codepoint_count(seg->text);
            }
            int would_exceed_sec  = (seg->end - cur_start) > CAPTION_MAX_SECONDS;
            int would_exceed_chars = candidate_cp > CAPTION_MAX_CHARS;
            (void)candidate_len; /* suppress unused-variable warning */

            if (would_exceed_sec || would_exceed_chars) {
                /* Flush current cue. */
                strip_whitespace_inplace(cur.data, &cur.len);
                if (cur.len > 0) {
                    char* t = strdup_safe(cur.data);
                    if (!t) {
                        /* OOM — free partial work and bail */
                        free(cur.data);
                        cues_free(cues, ncues);
                        return NULL;
                    }
                    cues[ncues].text  = t;
                    cues[ncues].start = cur_start;
                    cues[ncues].end   = last_end;
                    ncues++;
                }

                /* Start fresh */
                cur.len = 0;
                if (cur.data) cur.data[0] = '\0';
                has_cur = 0;
            }
        }

        if (!has_cur) {
            cur_start = seg->start;
            /* Reset accumulated text */
            cur.len = 0;
            if (cur.data) cur.data[0] = '\0';
            if (!strbuf_append(&cur, seg->text)) {
                free(cur.data);
                cues_free(cues, ncues);
                return NULL;
            }
            has_cur = 1;
        } else {
            /* Append separator + segment */
            if (word_mode) {
                if (!strbuf_append(&cur, " ")) {
                    free(cur.data);
                    cues_free(cues, ncues);
                    return NULL;
                }
            }
            if (!strbuf_append(&cur, seg->text)) {
                free(cur.data);
                cues_free(cues, ncues);
                return NULL;
            }
        }
        last_end = seg->end;

        if (ends_in_sentence(seg->text)) {
            /* Flush */
            strip_whitespace_inplace(cur.data, &cur.len);

            if (cur.len > 0) {
                char* t = strdup_safe(cur.data);
                if (!t) {
                    free(cur.data);
                    cues_free(cues, ncues);
                    return NULL;
                }
                cues[ncues].text  = t;
                cues[ncues].start = cur_start;
                cues[ncues].end   = seg->end;
                ncues++;
            }
            cur.len = 0;
            if (cur.data) cur.data[0] = '\0';
            has_cur = 0;
        }
    }

    /* Flush any remaining partial cue. */
    if (has_cur && cur.len > 0) {
        strip_whitespace_inplace(cur.data, &cur.len);
        if (cur.len > 0) {
            char* t = strdup_safe(cur.data);
            if (!t) {
                free(cur.data);
                cues_free(cues, ncues);
                return NULL;
            }
            cues[ncues].text  = t;
            cues[ncues].start = cur_start;
            cues[ncues].end   = last_end;
            ncues++;
        }
    }

    free(cur.data);
    *out_count = ncues;
    return cues;
}

/*
 * Pick the segment list and word_mode from a response.
 * Returns 1 on success; 0 if no usable segments.
 */
static int pick_segments(
    const TypecastTTSWithTimestampsResponse* response,
    const TypecastAlignmentSegment** segs_out,
    size_t* count_out,
    int* word_mode_out
) {
    if (response->words && response->words_count >= 2) {
        *segs_out     = response->words;
        *count_out    = response->words_count;
        *word_mode_out = 1;
        return 1;
    }
    if (response->characters && response->characters_count >= 1) {
        *segs_out     = response->characters;
        *count_out    = response->characters_count;
        *word_mode_out = 0;
        return 1;
    }
    /* Single-entry words with no characters -> still valid */
    if (response->words && response->words_count == 1 &&
        (!response->characters || response->characters_count == 0)) {
        *segs_out     = response->words;
        *count_out    = response->words_count;
        *word_mode_out = 1;
        return 1;
    }
    return 0;
}

/* ============================================
 * Base64 decoder (public-domain algorithm)
 * ============================================ */

static const signed char B64_TABLE[256] = {
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,62, -1,-1,-1,63,
    52,53,54,55, 56,57,58,59, 60,61,-1,-1, -1, 0,-1,-1,
    -1, 0, 1, 2,  3, 4, 5, 6,  7, 8, 9,10, 11,12,13,14,
    15,16,17,18, 19,20,21,22, 23,24,25,-1, -1,-1,-1,-1,
    -1,26,27,28, 29,30,31,32, 33,34,35,36, 37,38,39,40,
    41,42,43,44, 45,46,47,48, 49,50,51,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1,
    -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1, -1,-1,-1,-1
};

/* Decode base64 src into newly allocated *out (caller frees).
 * Sets *out_len to decoded byte count.
 * Returns 0 on success, -1 on error. */
static int base64_decode(const char* src, uint8_t** out, size_t* out_len) {
    if (!src || !out || !out_len) return -1;

    size_t src_len = strlen(src);
    /* Remove padding from length calculation */
    size_t padding = 0;
    if (src_len >= 1 && src[src_len - 1] == '=') padding++;
    if (src_len >= 2 && src[src_len - 2] == '=') padding++;

    size_t decoded_len = (src_len / 4) * 3 - padding;
    uint8_t* buf = (uint8_t*)malloc(decoded_len + 1);
    if (!buf) return -1;

    size_t j = 0;
    for (size_t i = 0; i + 3 < src_len; i += 4) {
        signed char a = B64_TABLE[(unsigned char)src[i]];
        signed char b = B64_TABLE[(unsigned char)src[i+1]];
        signed char c = B64_TABLE[(unsigned char)src[i+2]];
        signed char d = B64_TABLE[(unsigned char)src[i+3]];

        if (a < 0 || b < 0) { free(buf); return -1; }

        buf[j++] = (uint8_t)((a << 2) | (b >> 4));
        if (src[i+2] != '=') {
            if (c < 0) { free(buf); return -1; }
            buf[j++] = (uint8_t)((b << 4) | (c >> 2));
        }
        if (src[i+3] != '=') {
            if (d < 0) { free(buf); return -1; }
            buf[j++] = (uint8_t)((c << 6) | d);
        }
    }
    buf[j] = 0;
    *out     = buf;
    *out_len = j;
    return 0;
}

/* ============================================
 * Timestamp TTS — JSON request builder
 * ============================================ */

static cJSON* build_tts_with_timestamps_request_json(
    const TypecastTTSRequestWithTimestamps* request
) {
    /* Re-use the base TTS request builder by mapping fields. */
    TypecastTTSRequest base = {0};
    base.text     = request->text;
    base.voice_id = request->voice_id;
    base.model    = request->model;
    base.language = request->language;
    base.prompt   = request->prompt;
    base.output   = request->output;
    base.seed     = request->seed;

    cJSON* root = build_tts_request_json(&base);
    if (!root) return NULL;

    /* granularity is sent as a query parameter, not in the JSON body */

    return root;
}

/* ============================================
 * JSON Unicode unescape helper
 *
 * The bundled cJSON replaces \uXXXX with '?' (intentional simplification).
 * We pre-process the JSON body to convert \uXXXX (and surrogate pairs) to
 * their UTF-8 byte sequences before passing to cJSON_Parse so that Japanese,
 * Chinese, and other non-ASCII text survives the round-trip.
 * ============================================ */

/* Write a Unicode codepoint as UTF-8 into buf (must have >= 4 bytes).
 * Returns number of bytes written, or 0 on error. */
static int codepoint_to_utf8(unsigned int cp, char* buf) {
    if (cp < 0x80) {
        buf[0] = (char)(cp);
        return 1;
    } else if (cp < 0x800) {
        buf[0] = (char)(0xC0 | (cp >> 6));
        buf[1] = (char)(0x80 | (cp & 0x3F));
        return 2;
    } else if (cp < 0x10000) {
        buf[0] = (char)(0xE0 | (cp >> 12));
        buf[1] = (char)(0x80 | ((cp >> 6) & 0x3F));
        buf[2] = (char)(0x80 | (cp & 0x3F));
        return 3;
    } else if (cp < 0x110000) {
        buf[0] = (char)(0xF0 | (cp >> 18));
        buf[1] = (char)(0x80 | ((cp >> 12) & 0x3F));
        buf[2] = (char)(0x80 | ((cp >> 6) & 0x3F));
        buf[3] = (char)(0x80 | (cp & 0x3F));
        return 4;
    }
    return 0;
}

/* Parse 4 hex digits at p; return codepoint or -1 on invalid. */
static int parse_hex4(const char* p) {
    unsigned int cp = 0;
    for (int i = 0; i < 4; i++) {
        cp <<= 4;
        char c = p[i];
        if (c >= '0' && c <= '9') cp |= (unsigned int)(c - '0');
        else if (c >= 'a' && c <= 'f') cp |= (unsigned int)(c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') cp |= (unsigned int)(c - 'A' + 10);
        else return -1;
    }
    return (int)cp;
}

/*
 * Scan the JSON string src and return a heap-allocated copy with all
 * \uXXXX (and surrogate-pair \uD800\uDCxx) sequences replaced by their
 * UTF-8 equivalents.  Caller frees the result.
 *
 * This runs before cJSON_Parse so that non-ASCII characters survive the
 * bundled cJSON's simplified string parser.
 */
static char* json_unescape_unicode(const char* src) {
    size_t src_len = strlen(src);
    /* Worst case: each \uXXXX (6 chars) becomes 4 UTF-8 bytes, so output
     * fits in src_len bytes (replacement is always shorter or equal). */
    char* out = (char*)malloc(src_len + 1);
    if (!out) return NULL;

    const char* r = src;
    char* w = out;
    int in_string = 0;
    char prev = 0;

    while (*r) {
        if (!in_string) {
            if (*r == '"') { in_string = 1; *w++ = *r++; continue; }
            *w++ = *r++;
            continue;
        }
        /* inside a JSON string */
        if (prev != '\\' && *r == '"') {
            in_string = 0; *w++ = *r++; prev = '"'; continue;
        }
        if (*r == '\\' && r[1] == 'u' && r[2] && r[3] && r[4] && r[5]) {
            int cp = parse_hex4(r + 2);
            if (cp < 0) { *w++ = *r++; prev = r[-1]; continue; }
            /* Handle UTF-16 surrogate pairs */
            if (cp >= 0xD800 && cp <= 0xDBFF) {
                /* High surrogate; check for low surrogate immediately after */
                if (r[6] == '\\' && r[7] == 'u') {
                    int cp2 = parse_hex4(r + 8);
                    if (cp2 >= 0xDC00 && cp2 <= 0xDFFF) {
                        unsigned int full = (unsigned int)(
                            0x10000 + ((cp - 0xD800) << 10) + (cp2 - 0xDC00));
                        int written = codepoint_to_utf8(full, w);
                        if (written > 0) { w += written; r += 12; prev = 0; continue; }
                    }
                }
            }
            if (cp >= 0xDC00 && cp <= 0xDFFF) {
                /* Lone low surrogate — output replacement char */
                char rep[4]; int rlen = codepoint_to_utf8(0xFFFD, rep);
                if (rlen > 0) { memcpy(w, rep, (size_t)rlen); w += rlen; }
                r += 6; prev = 0; continue;
            }
            int written = codepoint_to_utf8((unsigned int)cp, w);
            if (written > 0) { w += written; r += 6; prev = 0; continue; }
            /* Fallback: copy raw */
        }
        prev = *r;
        *w++ = *r++;
    }
    *w = '\0';
    return out;
}

/* ============================================
 * Timestamp TTS — Segment array parser
 * ============================================ */

static TypecastAlignmentSegment* parse_segment_array(
    cJSON* arr,
    size_t* out_count
) {
    *out_count = 0;
    if (!arr || !cJSON_IsArray(arr)) return NULL;

    int n = cJSON_GetArraySize(arr);
    if (n <= 0) return NULL;

    TypecastAlignmentSegment* segs = (TypecastAlignmentSegment*)calloc(
        (size_t)n, sizeof(TypecastAlignmentSegment));
    if (!segs) return NULL;

    for (int i = 0; i < n; i++) {
        cJSON* item = cJSON_GetArrayItem(arr, i);
        if (!cJSON_IsObject(item)) continue;

        cJSON* jtext  = cJSON_GetObjectItem(item, "text");
        cJSON* jstart = cJSON_GetObjectItem(item, "start");
        cJSON* jend   = cJSON_GetObjectItem(item, "end");

        if (cJSON_IsString(jtext)) {
            segs[i].text = strdup_safe(jtext->valuestring);
        } else {
            /* Missing or non-string text field is malformed — abort. */
            for (int k = 0; k < i; k++) {
                if (segs[k].text) free(segs[k].text);
            }
            free(segs);
            *out_count = 0;
            return NULL;
        }
        if (cJSON_IsNumber(jstart)) {
            segs[i].start = (float)jstart->valuedouble;
        }
        if (cJSON_IsNumber(jend)) {
            segs[i].end = (float)jend->valuedouble;
        }
    }

    *out_count = (size_t)n;
    return segs;
}

/* ============================================
 * Timestamp TTS — Public API Implementation
 * ============================================ */

TYPECAST_API TypecastErrorCode typecast_text_to_speech_with_timestamps(
    TypecastClient* client,
    const TypecastTTSRequestWithTimestamps* request,
    TypecastTTSWithTimestampsResponse** out_response
) {
    if (!client || !request || !out_response) {
        if (client) set_error(client, TYPECAST_ERROR_INVALID_PARAM, "Invalid parameters");
        return TYPECAST_ERROR_INVALID_PARAM;
    }
    if (!request->text || !request->voice_id) {
        set_error(client, TYPECAST_ERROR_INVALID_PARAM, "text and voice_id are required");
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    *out_response = NULL;
    clear_error(client);

    /* Build URL */
    char url[512];
    if (request->granularity && strlen(request->granularity) > 0) {
        snprintf(url, sizeof(url), "%s/v1/text-to-speech/with-timestamps?granularity=%s",
                 client->host, request->granularity);
    } else {
        snprintf(url, sizeof(url), "%s/v1/text-to-speech/with-timestamps", client->host);
    }

    /* Build JSON body */
    cJSON* json = build_tts_with_timestamps_request_json(request);
    if (!json) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to build JSON");
        return TYPECAST_ERROR_OUT_OF_MEMORY;
    }

    char* json_str = cJSON_PrintUnformatted(json);
    cJSON_Delete(json);
    if (!json_str) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to serialize JSON");
        return TYPECAST_ERROR_OUT_OF_MEMORY;
    }

    /* Setup response buffers */
    ResponseBuffer response_buf = {0};

    /* Setup CURL */
    CURL* curl = client->curl;
    curl_easy_reset(curl);

    struct curl_slist* headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");

    char api_key_header[256];
    snprintf(api_key_header, sizeof(api_key_header), "X-API-KEY: %s", client->api_key);
    headers = curl_slist_append(headers, api_key_header);

    curl_easy_setopt(curl, CURLOPT_URL, url);
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_str);
    curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(json_str));
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_callback);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response_buf);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);
    curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L);

    CURLcode res = curl_easy_perform(curl);
    cJSON_free(json_str);
    curl_slist_free_all(headers);

    if (res != CURLE_OK) {
        set_error(client, TYPECAST_ERROR_NETWORK, curl_easy_strerror(res));
        if (response_buf.data) free(response_buf.data);
        return TYPECAST_ERROR_NETWORK;
    }

    long http_code = 0;
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);

    if (http_code != 200) {
        TypecastErrorCode err_code = http_status_to_error(http_code);
        char* err_msg = NULL;
        if (response_buf.data && response_buf.size > 0) {
            cJSON* err_json = cJSON_Parse((const char*)response_buf.data);
            if (err_json) {
                cJSON* detail = cJSON_GetObjectItem(err_json, "detail");
                if (cJSON_IsString(detail)) {
                    err_msg = strdup_safe(detail->valuestring);
                }
                cJSON_Delete(err_json);
            }
        }
        set_error(client, err_code, err_msg ? err_msg : typecast_error_message(err_code));
        if (err_msg) free(err_msg);
        if (response_buf.data) free(response_buf.data);
        return err_code;
    }

    /* Pre-process JSON to convert \uXXXX escapes to UTF-8 before cJSON parse,
     * because the bundled cJSON replaces \uXXXX with '?' (known limitation). */
    char* raw_json = (char*)response_buf.data;
    char* unescaped = json_unescape_unicode(raw_json);
    free(raw_json);

    if (!unescaped) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to unescape JSON");
        return TYPECAST_ERROR_OUT_OF_MEMORY;
    }

    /* Parse JSON */
    cJSON* root = cJSON_Parse(unescaped);
    free(unescaped);

    if (!root) {
        set_error(client, TYPECAST_ERROR_JSON_PARSE, "Failed to parse JSON response");
        return TYPECAST_ERROR_JSON_PARSE;
    }

    TypecastTTSWithTimestampsResponse* resp =
        (TypecastTTSWithTimestampsResponse*)calloc(
            1, sizeof(TypecastTTSWithTimestampsResponse));
    if (!resp) {
        cJSON_Delete(root);
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate response");
        return TYPECAST_ERROR_OUT_OF_MEMORY;
    }

    cJSON* jaudio = cJSON_GetObjectItem(root, "audio");
    if (cJSON_IsString(jaudio)) {
        resp->audio_base64 = strdup_safe(jaudio->valuestring);
    }
    cJSON* jfmt = cJSON_GetObjectItem(root, "audio_format");
    if (cJSON_IsString(jfmt)) {
        resp->audio_format = strdup_safe(jfmt->valuestring);
    }
    cJSON* jdur = cJSON_GetObjectItem(root, "audio_duration");
    if (cJSON_IsNumber(jdur)) {
        resp->audio_duration = (float)jdur->valuedouble;
    }

    cJSON* jwords = cJSON_GetObjectItem(root, "words");
    if (cJSON_IsArray(jwords)) {
        resp->words = parse_segment_array(jwords, &resp->words_count);
    }
    cJSON* jchars = cJSON_GetObjectItem(root, "characters");
    if (cJSON_IsArray(jchars)) {
        resp->characters = parse_segment_array(jchars, &resp->characters_count);
    }

    cJSON_Delete(root);
    *out_response = resp;
    return TYPECAST_OK;
}

/* ---- to_srt ---- */
TYPECAST_API TypecastErrorCode typecast_tts_with_timestamps_response_to_srt(
    const TypecastTTSWithTimestampsResponse* response,
    char** out_string
) {
    if (!response || !out_string) return TYPECAST_ERROR_INVALID_PARAM;
    *out_string = NULL;

    const TypecastAlignmentSegment* segs;
    size_t seg_count;
    int word_mode;
    if (!pick_segments(response, &segs, &seg_count, &word_mode)) {
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    int ncues = 0;
    CaptionCue* cues = group_into_cues(segs, seg_count, word_mode, &ncues);
    if (!cues || ncues == 0) {
        if (cues) cues_free(cues, ncues);
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    StrBuf sb = {0};
    char timebuf[32];
    char linebuf[64];

    for (int i = 0; i < ncues; i++) {
        /* Index line */
        snprintf(linebuf, sizeof(linebuf), "%d\n", i + 1);
        if (!strbuf_append(&sb, linebuf)) goto oom;

        /* Timecode line: start --> end */
        fmt_srt_time(cues[i].start, timebuf, sizeof(timebuf));
        if (!strbuf_append(&sb, timebuf)) goto oom;
        if (!strbuf_append(&sb, " --> ")) goto oom;
        fmt_srt_time(cues[i].end, timebuf, sizeof(timebuf));
        if (!strbuf_append(&sb, timebuf)) goto oom;
        if (!strbuf_append(&sb, "\n")) goto oom;

        /* Text */
        if (!strbuf_append(&sb, cues[i].text)) goto oom;
        if (!strbuf_append(&sb, "\n\n")) goto oom;
    }

    cues_free(cues, ncues);
    *out_string = sb.data;
    return TYPECAST_OK;

oom:
    free(sb.data);
    cues_free(cues, ncues);
    return TYPECAST_ERROR_OUT_OF_MEMORY;
}

/* ---- to_vtt ---- */
TYPECAST_API TypecastErrorCode typecast_tts_with_timestamps_response_to_vtt(
    const TypecastTTSWithTimestampsResponse* response,
    char** out_string
) {
    if (!response || !out_string) return TYPECAST_ERROR_INVALID_PARAM;
    *out_string = NULL;

    const TypecastAlignmentSegment* segs;
    size_t seg_count;
    int word_mode;
    if (!pick_segments(response, &segs, &seg_count, &word_mode)) {
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    int ncues = 0;
    CaptionCue* cues = group_into_cues(segs, seg_count, word_mode, &ncues);
    if (!cues || ncues == 0) {
        if (cues) cues_free(cues, ncues);
        return TYPECAST_ERROR_INVALID_PARAM;
    }

    StrBuf sb = {0};
    char timebuf[32];

    /* VTT header */
    if (!strbuf_append(&sb, "WEBVTT\n\n")) goto oom;

    for (int i = 0; i < ncues; i++) {
        /* Timecode line */
        fmt_vtt_time(cues[i].start, timebuf, sizeof(timebuf));
        if (!strbuf_append(&sb, timebuf)) goto oom;
        if (!strbuf_append(&sb, " --> ")) goto oom;
        fmt_vtt_time(cues[i].end, timebuf, sizeof(timebuf));
        if (!strbuf_append(&sb, timebuf)) goto oom;
        if (!strbuf_append(&sb, "\n")) goto oom;

        /* Text */
        if (!strbuf_append(&sb, cues[i].text)) goto oom;
        if (!strbuf_append(&sb, "\n\n")) goto oom;
    }

    cues_free(cues, ncues);
    *out_string = sb.data;
    return TYPECAST_OK;

oom:
    free(sb.data);
    cues_free(cues, ncues);
    return TYPECAST_ERROR_OUT_OF_MEMORY;
}

/* ---- audio_bytes ---- */
TYPECAST_API TypecastErrorCode typecast_tts_with_timestamps_response_audio_bytes(
    const TypecastTTSWithTimestampsResponse* response,
    uint8_t** out_bytes,
    size_t* out_size
) {
    if (!response || !out_bytes || !out_size) return TYPECAST_ERROR_INVALID_PARAM;
    if (!response->audio_base64) return TYPECAST_ERROR_INVALID_PARAM;

    if (base64_decode(response->audio_base64, out_bytes, out_size) != 0) {
        return TYPECAST_ERROR_JSON_PARSE;
    }
    return TYPECAST_OK;
}

/* ---- save_audio ---- */
TYPECAST_API TypecastErrorCode typecast_tts_with_timestamps_response_save_audio(
    const TypecastTTSWithTimestampsResponse* response,
    const char* path
) {
    if (!response || !path) return TYPECAST_ERROR_INVALID_PARAM;

    uint8_t* bytes = NULL;
    size_t   size  = 0;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_audio_bytes(
        response, &bytes, &size);
    if (rc != TYPECAST_OK) return rc;

    FILE* f = fopen(path, "wb");
    if (!f) {
        free(bytes);
        return TYPECAST_ERROR_NETWORK; /* reuse as I/O error */
    }
    size_t written = fwrite(bytes, 1, size, f);
    fclose(f);
    free(bytes);
    if (written != size) {
        return TYPECAST_ERROR_NETWORK; /* reused as I/O error */
    }
    return TYPECAST_OK;
}

/* ---- free ---- */
TYPECAST_API void typecast_tts_with_timestamps_response_free(
    TypecastTTSWithTimestampsResponse* response
) {
    if (!response) return;

    if (response->audio_base64) free(response->audio_base64);
    if (response->audio_format)  free(response->audio_format);

    if (response->words) {
        for (size_t i = 0; i < response->words_count; i++) {
            if (response->words[i].text) free(response->words[i].text);
        }
        free(response->words);
    }
    if (response->characters) {
        for (size_t i = 0; i < response->characters_count; i++) {
            if (response->characters[i].text) free(response->characters[i].text);
        }
        free(response->characters);
    }

    free(response);
}
