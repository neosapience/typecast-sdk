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
#if !defined(_WIN32) && !defined(_WIN64)
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
            cJSON_AddNumberToObject(output, "volume", request->output->volume);
            cJSON_AddNumberToObject(output, "audio_pitch", request->output->audio_pitch);
            cJSON_AddNumberToObject(output, "audio_tempo", request->output->audio_tempo);
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
        case 200: return TYPECAST_OK;
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
    
    if (!client->api_key || !client->host) {
        typecast_client_destroy(client);
        return NULL;
    }
    
    /* Initialize CURL globally (safe to call multiple times) */
    curl_global_init(CURL_GLOBAL_DEFAULT);
    
    client->curl = curl_easy_init();
    if (!client->curl) {
        typecast_client_destroy(client);
        return NULL;
    }
    
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
    if (!json) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to build JSON");
        return NULL;
    }
    
    char* json_str = cJSON_PrintUnformatted(json);
    cJSON_Delete(json);
    
    if (!json_str) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to serialize JSON");
        return NULL;
    }
    
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
    if (!resp) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate response");
        if (response_buf.data) free(response_buf.data);
        if (header_buf.data) free(header_buf.data);
        return NULL;
    }
    
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
    if (!resp) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate response");
        cJSON_Delete(json);
        return NULL;
    }
    
    resp->count = cJSON_GetArraySize(json);
    resp->voices = (TypecastVoice*)calloc(resp->count, sizeof(TypecastVoice));
    
    if (!resp->voices) {
        set_error(client, TYPECAST_ERROR_OUT_OF_MEMORY, "Failed to allocate voices array");
        free(resp);
        cJSON_Delete(json);
        return NULL;
    }
    
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
