/**
 * Typecast C/C++ SDK
 *
 * Text-to-Speech API client library for Typecast AI.
 * Supports both C and C++ with easy integration for Unreal Engine
 * and embedded systems.
 *
 * MIT License
 * Copyright (c) 2025 Typecast
 * See LICENSE file for details.
 *
 * Platform support:
 * - Windows: MSVC, MinGW
 * - Linux: GCC, Clang
 * - macOS: Clang
 *
 * Dependencies:
 * - libcurl (for HTTP requests)
 * - cJSON (bundled, for JSON parsing)
 *
 * Usage example (C):
 *
 *   #include "typecast.h"
 *
 *   int main() {
 *       // Initialize client
 *       TypecastClient* client = typecast_client_create("your-api-key");
 *       if (!client) return 1;
 *
 *       // Text-to-Speech
 *       TypecastTTSRequest req = {0};
 *       req.text = "Hello, world!";
 *       req.voice_id = "tc_60e5426de8b95f1d3000d7b5";
 *       req.model = TYPECAST_MODEL_SSFM_V30;
 *
 *       TypecastTTSResponse* resp = typecast_text_to_speech(client, &req);
 *       if (resp) {
 *           // Save audio data
 *           FILE* f = fopen("output.wav", "wb");
 *           fwrite(resp->audio_data, 1, resp->audio_size, f);
 *           fclose(f);
 *           typecast_tts_response_free(resp);
 *       }
 *
 *       typecast_client_destroy(client);
 *       return 0;
 *   }
 *
 * Usage example (C++):
 *
 *   #include "typecast.h"
 *
 *   int main() {
 *       auto client = typecast::Client("your-api-key");
 *       auto response = client.textToSpeech({
 *           .text = "Hello, world!",
 *           .voice_id = "tc_60e5426de8b95f1d3000d7b5",
 *           .model = typecast::Model::SSFM_V30
 *       });
 *       // ... handle response
 *   }
 */

#ifndef TYPECAST_H
#define TYPECAST_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>
#include <stdint.h>

/* Library version */
#define TYPECAST_VERSION_MAJOR 1
#define TYPECAST_VERSION_MINOR 0
#define TYPECAST_VERSION_PATCH 0
#define TYPECAST_VERSION "1.0.0"

/*
 * DLL Export/Import macros for Windows
 */
#if defined(_WIN32) || defined(_WIN64)
    #ifdef TYPECAST_STATIC
        #define TYPECAST_API
    #elif defined(TYPECAST_BUILDING_DLL)
        #define TYPECAST_API __declspec(dllexport)
    #else
        #define TYPECAST_API __declspec(dllimport)
    #endif
#else
    #if defined(__GNUC__) && __GNUC__ >= 4
        #define TYPECAST_API __attribute__((visibility("default")))
    #else
        #define TYPECAST_API
    #endif
#endif

/* ============================================
 * Error Codes
 * ============================================ */

typedef enum {
    TYPECAST_OK = 0,
    TYPECAST_ERROR_INVALID_PARAM = -1,
    TYPECAST_ERROR_OUT_OF_MEMORY = -2,
    TYPECAST_ERROR_CURL_INIT = -3,
    TYPECAST_ERROR_NETWORK = -4,
    TYPECAST_ERROR_JSON_PARSE = -5,
    TYPECAST_ERROR_BAD_REQUEST = 400,
    TYPECAST_ERROR_UNAUTHORIZED = 401,
    TYPECAST_ERROR_PAYMENT_REQUIRED = 402,
    TYPECAST_ERROR_NOT_FOUND = 404,
    TYPECAST_ERROR_UNPROCESSABLE_ENTITY = 422,
    TYPECAST_ERROR_RATE_LIMIT = 429,
    TYPECAST_ERROR_INTERNAL_SERVER = 500
} TypecastErrorCode;

/* ============================================
 * TTS Model
 * ============================================ */

typedef enum {
    TYPECAST_MODEL_SSFM_V21,
    TYPECAST_MODEL_SSFM_V30
} TypecastModel;

/* ============================================
 * Emotion Preset
 * ============================================ */

typedef enum {
    TYPECAST_EMOTION_NORMAL,
    TYPECAST_EMOTION_HAPPY,
    TYPECAST_EMOTION_SAD,
    TYPECAST_EMOTION_ANGRY,
    TYPECAST_EMOTION_WHISPER,    /* ssfm-v30 only */
    TYPECAST_EMOTION_TONEUP,     /* ssfm-v30 only */
    TYPECAST_EMOTION_TONEDOWN    /* ssfm-v30 only */
} TypecastEmotionPreset;

/* ============================================
 * Emotion Type (for ssfm-v30)
 * ============================================ */

typedef enum {
    TYPECAST_EMOTION_TYPE_NONE,   /* Use basic prompt (ssfm-v21 style) */
    TYPECAST_EMOTION_TYPE_PRESET, /* Preset-based emotion control */
    TYPECAST_EMOTION_TYPE_SMART   /* Context-aware emotion inference */
} TypecastEmotionType;

/* ============================================
 * Audio Format
 * ============================================ */

typedef enum {
    TYPECAST_AUDIO_FORMAT_WAV,
    TYPECAST_AUDIO_FORMAT_MP3
} TypecastAudioFormat;

/* ============================================
 * Gender Enum
 * ============================================ */

typedef enum {
    TYPECAST_GENDER_UNKNOWN,
    TYPECAST_GENDER_MALE,
    TYPECAST_GENDER_FEMALE
} TypecastGender;

/* ============================================
 * Age Enum
 * ============================================ */

typedef enum {
    TYPECAST_AGE_UNKNOWN,
    TYPECAST_AGE_CHILD,
    TYPECAST_AGE_TEENAGER,
    TYPECAST_AGE_YOUNG_ADULT,
    TYPECAST_AGE_MIDDLE_AGE,
    TYPECAST_AGE_ELDER
} TypecastAge;

/* ============================================
 * Forward Declarations
 * ============================================ */

typedef struct TypecastClient TypecastClient;

/* ============================================
 * TTS Request
 * ============================================ */

/**
 * Output settings for TTS request
 */
typedef struct {
    int volume;              /* 0-200, default 100 */
    int audio_pitch;         /* -12 to 12, default 0 */
    float audio_tempo;       /* 0.5 to 2.0, default 1.0 */
    TypecastAudioFormat audio_format; /* wav or mp3, default wav */
} TypecastOutput;

/**
 * Prompt settings for TTS request (emotion control)
 */
typedef struct {
    TypecastEmotionType emotion_type;      /* none, preset, or smart */
    TypecastEmotionPreset emotion_preset;  /* For preset type */
    float emotion_intensity;               /* 0.0 to 2.0, default 1.0 */
    const char* previous_text;             /* For smart type (max 2000 chars) */
    const char* next_text;                 /* For smart type (max 2000 chars) */
} TypecastPrompt;

/**
 * TTS Request structure
 */
typedef struct {
    const char* text;        /* Required: Text to convert (max 2000 chars) */
    const char* voice_id;    /* Required: Voice ID (e.g., "tc_xxx") */
    TypecastModel model;     /* Required: ssfm-v21 or ssfm-v30 */
    const char* language;    /* Optional: ISO 639-3 code (e.g., "eng", "kor") */
    TypecastPrompt* prompt;  /* Optional: Emotion settings */
    TypecastOutput* output;  /* Optional: Audio output settings */
    int seed;                /* Optional: Random seed (0 = not set) */
} TypecastTTSRequest;

/* ============================================
 * TTS Response
 * ============================================ */

/**
 * TTS Response structure
 */
typedef struct {
    uint8_t* audio_data;     /* Audio binary data */
    size_t audio_size;       /* Size of audio data in bytes */
    float duration;          /* Audio duration in seconds */
    TypecastAudioFormat format; /* Audio format (wav/mp3) */
} TypecastTTSResponse;

/* ============================================
 * Voice Model Info
 * ============================================ */

typedef struct {
    TypecastModel version;
    char** emotions;         /* Array of emotion strings */
    size_t emotions_count;
} TypecastModelInfo;

/* ============================================
 * Voice Response (V2)
 * ============================================ */

typedef struct {
    char* voice_id;
    char* voice_name;
    TypecastModelInfo* models;
    size_t models_count;
    TypecastGender gender;
    TypecastAge age;
    char** use_cases;
    size_t use_cases_count;
} TypecastVoice;

/**
 * Voices list response
 */
typedef struct {
    TypecastVoice* voices;
    size_t count;
} TypecastVoicesResponse;

/* ============================================
 * Voices Filter (for V2 API)
 * ============================================ */

typedef struct {
    TypecastModel* model;    /* Optional: Filter by model */
    TypecastGender* gender;  /* Optional: Filter by gender */
    TypecastAge* age;        /* Optional: Filter by age */
    const char* use_cases;   /* Optional: Filter by use case */
} TypecastVoicesFilter;

/* ============================================
 * Error Response
 * ============================================ */

typedef struct {
    TypecastErrorCode code;
    char* message;
} TypecastError;

/* ============================================
 * Client API
 * ============================================ */

/**
 * Create a new Typecast client
 *
 * @param api_key API key for authentication (required)
 * @return Pointer to TypecastClient, or NULL on failure
 */
TYPECAST_API TypecastClient* typecast_client_create(const char* api_key);

/**
 * Create a new Typecast client with custom host
 *
 * @param api_key API key for authentication (required)
 * @param host Custom API host URL (e.g., "https://api.typecast.ai")
 * @return Pointer to TypecastClient, or NULL on failure
 */
TYPECAST_API TypecastClient* typecast_client_create_with_host(
    const char* api_key,
    const char* host
);

/**
 * Destroy the Typecast client and free resources
 *
 * @param client Pointer to TypecastClient
 */
TYPECAST_API void typecast_client_destroy(TypecastClient* client);

/**
 * Get the last error from the client
 *
 * @param client Pointer to TypecastClient
 * @return Pointer to TypecastError (valid until next API call)
 */
TYPECAST_API const TypecastError* typecast_client_get_error(
    const TypecastClient* client
);

/* ============================================
 * Text-to-Speech API
 * ============================================ */

/**
 * Convert text to speech
 *
 * @param client Pointer to TypecastClient
 * @param request Pointer to TTSRequest
 * @return Pointer to TTSResponse, or NULL on failure
 *         (must be freed with typecast_tts_response_free)
 */
TYPECAST_API TypecastTTSResponse* typecast_text_to_speech(
    TypecastClient* client,
    const TypecastTTSRequest* request
);

/**
 * Free TTS response
 *
 * @param response Pointer to TTSResponse
 */
TYPECAST_API void typecast_tts_response_free(TypecastTTSResponse* response);

/* ============================================
 * Voices API
 * ============================================ */

/**
 * Get available voices (V2 API)
 *
 * @param client Pointer to TypecastClient
 * @param filter Optional filter (can be NULL)
 * @return Pointer to VoicesResponse, or NULL on failure
 *         (must be freed with typecast_voices_response_free)
 */
TYPECAST_API TypecastVoicesResponse* typecast_get_voices(
    TypecastClient* client,
    const TypecastVoicesFilter* filter
);

/**
 * Get a specific voice by ID (V2 API)
 *
 * @param client Pointer to TypecastClient
 * @param voice_id Voice ID to retrieve
 * @return Pointer to Voice, or NULL on failure
 *         (must be freed with typecast_voice_free)
 */
TYPECAST_API TypecastVoice* typecast_get_voice(
    TypecastClient* client,
    const char* voice_id
);

/**
 * Free voices response
 *
 * @param response Pointer to VoicesResponse
 */
TYPECAST_API void typecast_voices_response_free(TypecastVoicesResponse* response);

/**
 * Free single voice
 *
 * @param voice Pointer to Voice
 */
TYPECAST_API void typecast_voice_free(TypecastVoice* voice);

/* ============================================
 * Utility Functions
 * ============================================ */

/**
 * Get library version string
 *
 * @return Version string (e.g., "1.0.0")
 */
TYPECAST_API const char* typecast_version(void);

/**
 * Get model string representation
 *
 * @param model Model enum value
 * @return Model string (e.g., "ssfm-v30")
 */
TYPECAST_API const char* typecast_model_to_string(TypecastModel model);

/**
 * Parse model from string
 *
 * @param str Model string (e.g., "ssfm-v30")
 * @return Model enum value, or -1 on invalid input
 */
TYPECAST_API int typecast_model_from_string(const char* str);

/**
 * Get emotion preset string representation
 *
 * @param preset Emotion preset enum value
 * @return Emotion string (e.g., "happy")
 */
TYPECAST_API const char* typecast_emotion_to_string(TypecastEmotionPreset preset);

/**
 * Get audio format string representation
 *
 * @param format Audio format enum value
 * @return Format string (e.g., "wav")
 */
TYPECAST_API const char* typecast_audio_format_to_string(TypecastAudioFormat format);

/**
 * Get error message for error code
 *
 * @param code Error code
 * @return Error message string
 */
TYPECAST_API const char* typecast_error_message(TypecastErrorCode code);

/* ============================================
 * Helper Macros
 * ============================================ */

/** Create default output settings */
#define TYPECAST_OUTPUT_DEFAULT() \
    ((TypecastOutput){ \
        .volume = 100, \
        .audio_pitch = 0, \
        .audio_tempo = 1.0f, \
        .audio_format = TYPECAST_AUDIO_FORMAT_WAV \
    })

/** Create default prompt settings */
#define TYPECAST_PROMPT_DEFAULT() \
    ((TypecastPrompt){ \
        .emotion_type = TYPECAST_EMOTION_TYPE_NONE, \
        .emotion_preset = TYPECAST_EMOTION_NORMAL, \
        .emotion_intensity = 1.0f, \
        .previous_text = NULL, \
        .next_text = NULL \
    })

#ifdef __cplusplus
}

/* ============================================
 * C++ Wrapper (optional, for convenience)
 * ============================================ */

#ifdef TYPECAST_CPP_WRAPPER

#include <string>
#include <vector>
#include <memory>
#include <stdexcept>

namespace typecast {

enum class Model {
    SSFM_V21 = TYPECAST_MODEL_SSFM_V21,
    SSFM_V30 = TYPECAST_MODEL_SSFM_V30
};

enum class EmotionPreset {
    Normal = TYPECAST_EMOTION_NORMAL,
    Happy = TYPECAST_EMOTION_HAPPY,
    Sad = TYPECAST_EMOTION_SAD,
    Angry = TYPECAST_EMOTION_ANGRY,
    Whisper = TYPECAST_EMOTION_WHISPER,
    ToneUp = TYPECAST_EMOTION_TONEUP,
    ToneDown = TYPECAST_EMOTION_TONEDOWN
};

enum class AudioFormat {
    WAV = TYPECAST_AUDIO_FORMAT_WAV,
    MP3 = TYPECAST_AUDIO_FORMAT_MP3
};

struct Output {
    int volume = 100;
    int audioPitch = 0;
    float audioTempo = 1.0f;
    AudioFormat audioFormat = AudioFormat::WAV;
};

struct Prompt {
    EmotionPreset emotionPreset = EmotionPreset::Normal;
    float emotionIntensity = 1.0f;
    std::string previousText;
    std::string nextText;
    bool useSmart = false;
};

struct TTSRequest {
    std::string text;
    std::string voiceId;
    Model model = Model::SSFM_V30;
    std::string language;
    Prompt prompt;
    Output output;
    int seed = 0;
};

struct TTSResponse {
    std::vector<uint8_t> audioData;
    float duration = 0.0f;
    AudioFormat format = AudioFormat::WAV;
};

struct Voice {
    std::string voiceId;
    std::string voiceName;
    std::vector<std::string> emotions;
};

class TypecastException : public std::runtime_error {
public:
    TypecastErrorCode code;
    TypecastException(TypecastErrorCode c, const std::string& msg)
        : std::runtime_error(msg), code(c) {}
};

class Client {
public:
    explicit Client(const std::string& apiKey,
                   const std::string& host = "https://api.typecast.ai")
        : client_(typecast_client_create_with_host(apiKey.c_str(), host.c_str()))
    {
        if (!client_) {
            throw TypecastException(TYPECAST_ERROR_CURL_INIT,
                "Failed to create Typecast client");
        }
    }

    ~Client() {
        if (client_) {
            typecast_client_destroy(client_);
        }
    }

    // Non-copyable
    Client(const Client&) = delete;
    Client& operator=(const Client&) = delete;

    // Movable
    Client(Client&& other) noexcept : client_(other.client_) {
        other.client_ = nullptr;
    }

    Client& operator=(Client&& other) noexcept {
        if (this != &other) {
            if (client_) typecast_client_destroy(client_);
            client_ = other.client_;
            other.client_ = nullptr;
        }
        return *this;
    }

    TTSResponse textToSpeech(const TTSRequest& request) {
        TypecastTTSRequest req = {0};
        req.text = request.text.c_str();
        req.voice_id = request.voiceId.c_str();
        req.model = static_cast<TypecastModel>(request.model);

        if (!request.language.empty()) {
            req.language = request.language.c_str();
        }

        TypecastPrompt prompt = {0};
        prompt.emotion_preset = static_cast<TypecastEmotionPreset>(
            request.prompt.emotionPreset);
        prompt.emotion_intensity = request.prompt.emotionIntensity;
        if (request.prompt.useSmart) {
            prompt.emotion_type = TYPECAST_EMOTION_TYPE_SMART;
            if (!request.prompt.previousText.empty()) {
                prompt.previous_text = request.prompt.previousText.c_str();
            }
            if (!request.prompt.nextText.empty()) {
                prompt.next_text = request.prompt.nextText.c_str();
            }
        } else {
            prompt.emotion_type = TYPECAST_EMOTION_TYPE_PRESET;
        }
        req.prompt = &prompt;

        TypecastOutput output = {0};
        output.volume = request.output.volume;
        output.audio_pitch = request.output.audioPitch;
        output.audio_tempo = request.output.audioTempo;
        output.audio_format = static_cast<TypecastAudioFormat>(
            request.output.audioFormat);
        req.output = &output;

        req.seed = request.seed;

        TypecastTTSResponse* resp = typecast_text_to_speech(client_, &req);
        if (!resp) {
            const TypecastError* err = typecast_client_get_error(client_);
            throw TypecastException(err->code, err->message ? err->message : "Unknown error");
        }

        TTSResponse result;
        result.audioData.assign(resp->audio_data, resp->audio_data + resp->audio_size);
        result.duration = resp->duration;
        result.format = static_cast<AudioFormat>(resp->format);

        typecast_tts_response_free(resp);
        return result;
    }

    std::vector<Voice> getVoices() {
        TypecastVoicesResponse* resp = typecast_get_voices(client_, nullptr);
        if (!resp) {
            const TypecastError* err = typecast_client_get_error(client_);
            throw TypecastException(err->code, err->message ? err->message : "Unknown error");
        }

        std::vector<Voice> result;
        result.reserve(resp->count);

        for (size_t i = 0; i < resp->count; i++) {
            Voice v;
            v.voiceId = resp->voices[i].voice_id ? resp->voices[i].voice_id : "";
            v.voiceName = resp->voices[i].voice_name ? resp->voices[i].voice_name : "";
            result.push_back(std::move(v));
        }

        typecast_voices_response_free(resp);
        return result;
    }

private:
    TypecastClient* client_;
};

} // namespace typecast

#endif // TYPECAST_CPP_WRAPPER

#endif // __cplusplus

#endif // TYPECAST_H
