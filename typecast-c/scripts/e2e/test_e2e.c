/**
 * Typecast C SDK E2E Test Program
 *
 * Tests library loading and basic functionality.
 * This program is compiled and executed inside Docker containers
 * or locally on macOS.
 *
 * Copyright (c) 2025 Typecast
 * MIT License
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32) || defined(_WIN64)
    #include <windows.h>
    #define LOAD_LIBRARY(path) LoadLibraryA(path)
    #define GET_PROC(handle, name) GetProcAddress(handle, name)
    #define CLOSE_LIBRARY(handle) FreeLibrary(handle)
    #define LIBRARY_ERROR() "LoadLibrary failed"
    typedef HMODULE LibHandle;
#else
    #include <dlfcn.h>
    #define LOAD_LIBRARY(path) dlopen(path, RTLD_NOW)
    #define GET_PROC(handle, name) dlsym(handle, name)
    #define CLOSE_LIBRARY(handle) dlclose(handle)
    #define LIBRARY_ERROR() dlerror()
    typedef void* LibHandle;
#endif

/* Test result structure */
typedef struct {
    int total;
    int passed;
    int failed;
} TestResult;

/* Forward declaration of opaque types */
typedef struct TypecastClient TypecastClient;

/* Function pointer types */
typedef TypecastClient* (*client_create_fn)(const char*);
typedef TypecastClient* (*client_create_with_host_fn)(const char*, const char*);
typedef void (*client_destroy_fn)(TypecastClient*);
typedef const char* (*version_fn)(void);
typedef const char* (*model_to_string_fn)(int);
typedef int (*model_from_string_fn)(const char*);
typedef const char* (*emotion_to_string_fn)(int);
typedef const char* (*audio_format_to_string_fn)(int);
typedef const char* (*error_message_fn)(int);

/* Global function pointers */
static client_create_fn typecast_client_create;
static client_create_with_host_fn typecast_client_create_with_host;
static client_destroy_fn typecast_client_destroy;
static version_fn typecast_version;
static model_to_string_fn typecast_model_to_string;
static model_from_string_fn typecast_model_from_string;
static emotion_to_string_fn typecast_emotion_to_string;
static audio_format_to_string_fn typecast_audio_format_to_string;
static error_message_fn typecast_error_message;

/* Test helper macros */
#define TEST_ASSERT(result, test_name, condition, msg) do { \
    (result)->total++; \
    if (condition) { \
        printf("  [PASS] %s\n", test_name); \
        (result)->passed++; \
    } else { \
        printf("  [FAIL] %s: %s\n", test_name, msg); \
        (result)->failed++; \
    } \
} while(0)

/* Get library path from command line or use default */
const char* get_library_path(int argc, char* argv[]) {
#if defined(_WIN32) || defined(_WIN64)
    static const char* default_path = "typecast.dll";
#elif defined(__APPLE__)
    static const char* default_path = "libtypecast.dylib";
#else
    static const char* default_path = "/lib_check/libtypecast.so";
#endif
    
    if (argc > 1) {
        return argv[1];
    }
    return default_path;
}

int main(int argc, char* argv[]) {
    TestResult result = {0, 0, 0};
    const char* lib_path = get_library_path(argc, argv);
    
    printf("===========================================\n");
    printf("  Typecast C SDK E2E Test\n");
    printf("===========================================\n\n");
    printf("Library path: %s\n\n", lib_path);
    
    /* Load library */
    LibHandle handle = LOAD_LIBRARY(lib_path);
    if (!handle) {
        fprintf(stderr, "Failed to load library: %s\n", LIBRARY_ERROR());
        return 1;
    }
    
    printf("Library loaded successfully\n\n");
    
    /* ============================================
     * 1. Load and verify all exported symbols
     * ============================================ */
    printf("=== Testing Symbol Loading ===\n");
    
    /* Client functions */
    typecast_client_create = (client_create_fn)GET_PROC(handle, "typecast_client_create");
    TEST_ASSERT(&result, "typecast_client_create symbol", typecast_client_create != NULL, "Symbol not found");
    
    typecast_client_create_with_host = (client_create_with_host_fn)GET_PROC(handle, "typecast_client_create_with_host");
    TEST_ASSERT(&result, "typecast_client_create_with_host symbol", typecast_client_create_with_host != NULL, "Symbol not found");
    
    typecast_client_destroy = (client_destroy_fn)GET_PROC(handle, "typecast_client_destroy");
    TEST_ASSERT(&result, "typecast_client_destroy symbol", typecast_client_destroy != NULL, "Symbol not found");
    
    /* Version function */
    typecast_version = (version_fn)GET_PROC(handle, "typecast_version");
    TEST_ASSERT(&result, "typecast_version symbol", typecast_version != NULL, "Symbol not found");
    
    /* Utility functions */
    typecast_model_to_string = (model_to_string_fn)GET_PROC(handle, "typecast_model_to_string");
    TEST_ASSERT(&result, "typecast_model_to_string symbol", typecast_model_to_string != NULL, "Symbol not found");
    
    typecast_model_from_string = (model_from_string_fn)GET_PROC(handle, "typecast_model_from_string");
    TEST_ASSERT(&result, "typecast_model_from_string symbol", typecast_model_from_string != NULL, "Symbol not found");
    
    typecast_emotion_to_string = (emotion_to_string_fn)GET_PROC(handle, "typecast_emotion_to_string");
    TEST_ASSERT(&result, "typecast_emotion_to_string symbol", typecast_emotion_to_string != NULL, "Symbol not found");
    
    typecast_audio_format_to_string = (audio_format_to_string_fn)GET_PROC(handle, "typecast_audio_format_to_string");
    TEST_ASSERT(&result, "typecast_audio_format_to_string symbol", typecast_audio_format_to_string != NULL, "Symbol not found");
    
    typecast_error_message = (error_message_fn)GET_PROC(handle, "typecast_error_message");
    TEST_ASSERT(&result, "typecast_error_message symbol", typecast_error_message != NULL, "Symbol not found");
    
    /* TTS functions */
    void* tts_fn = GET_PROC(handle, "typecast_text_to_speech");
    TEST_ASSERT(&result, "typecast_text_to_speech symbol", tts_fn != NULL, "Symbol not found");
    
    void* tts_free_fn = GET_PROC(handle, "typecast_tts_response_free");
    TEST_ASSERT(&result, "typecast_tts_response_free symbol", tts_free_fn != NULL, "Symbol not found");
    
    /* Voice functions */
    void* get_voices_fn = GET_PROC(handle, "typecast_get_voices");
    TEST_ASSERT(&result, "typecast_get_voices symbol", get_voices_fn != NULL, "Symbol not found");
    
    void* get_voice_fn = GET_PROC(handle, "typecast_get_voice");
    TEST_ASSERT(&result, "typecast_get_voice symbol", get_voice_fn != NULL, "Symbol not found");
    
    void* voices_free_fn = GET_PROC(handle, "typecast_voices_response_free");
    TEST_ASSERT(&result, "typecast_voices_response_free symbol", voices_free_fn != NULL, "Symbol not found");
    
    void* voice_free_fn = GET_PROC(handle, "typecast_voice_free");
    TEST_ASSERT(&result, "typecast_voice_free symbol", voice_free_fn != NULL, "Symbol not found");
    
    /* Error function */
    void* get_error_fn = GET_PROC(handle, "typecast_client_get_error");
    TEST_ASSERT(&result, "typecast_client_get_error symbol", get_error_fn != NULL, "Symbol not found");
    
    printf("\n");
    
    /* ============================================
     * 2. Test version function
     * ============================================ */
    printf("=== Testing Version Function ===\n");
    
    if (typecast_version) {
        const char* version = typecast_version();
        TEST_ASSERT(&result, "typecast_version returns non-NULL", version != NULL, "NULL version");
        
        if (version) {
            printf("  Library version: %s\n", version);
            TEST_ASSERT(&result, "typecast_version returns valid string", strlen(version) > 0, "Empty version string");
        }
    }
    
    printf("\n");
    
    /* ============================================
     * 3. Test model conversion functions
     * ============================================ */
    printf("=== Testing Model Conversion ===\n");
    
    if (typecast_model_to_string) {
        /* TYPECAST_MODEL_SSFM_V21 = 0 */
        const char* v21 = typecast_model_to_string(0);
        TEST_ASSERT(&result, "model_to_string(SSFM_V21)", v21 != NULL && strstr(v21, "ssfm") != NULL, "Invalid model string");
        if (v21) printf("  Model V21: %s\n", v21);
        
        /* TYPECAST_MODEL_SSFM_V30 = 1 */
        const char* v30 = typecast_model_to_string(1);
        TEST_ASSERT(&result, "model_to_string(SSFM_V30)", v30 != NULL && strstr(v30, "ssfm") != NULL, "Invalid model string");
        if (v30) printf("  Model V30: %s\n", v30);
    }
    
    if (typecast_model_from_string) {
        int model = typecast_model_from_string("ssfm-v30");
        TEST_ASSERT(&result, "model_from_string(ssfm-v30)", model == 1, "Invalid model enum");
        
        model = typecast_model_from_string("ssfm-v21");
        TEST_ASSERT(&result, "model_from_string(ssfm-v21)", model == 0, "Invalid model enum");
    }
    
    printf("\n");
    
    /* ============================================
     * 4. Test emotion conversion function
     * ============================================ */
    printf("=== Testing Emotion Conversion ===\n");
    
    if (typecast_emotion_to_string) {
        /* TYPECAST_EMOTION_NORMAL = 0 */
        const char* normal = typecast_emotion_to_string(0);
        TEST_ASSERT(&result, "emotion_to_string(NORMAL)", normal != NULL, "NULL emotion string");
        if (normal) printf("  Emotion 0: %s\n", normal);
        
        /* TYPECAST_EMOTION_HAPPY = 1 */
        const char* happy = typecast_emotion_to_string(1);
        TEST_ASSERT(&result, "emotion_to_string(HAPPY)", happy != NULL && strstr(happy, "happy") != NULL, "Invalid emotion string");
        if (happy) printf("  Emotion 1: %s\n", happy);
    }
    
    printf("\n");
    
    /* ============================================
     * 5. Test audio format conversion
     * ============================================ */
    printf("=== Testing Audio Format Conversion ===\n");
    
    if (typecast_audio_format_to_string) {
        /* TYPECAST_AUDIO_FORMAT_WAV = 0 */
        const char* wav = typecast_audio_format_to_string(0);
        TEST_ASSERT(&result, "audio_format_to_string(WAV)", wav != NULL && strstr(wav, "wav") != NULL, "Invalid format string");
        if (wav) printf("  Format 0: %s\n", wav);
        
        /* TYPECAST_AUDIO_FORMAT_MP3 = 1 */
        const char* mp3 = typecast_audio_format_to_string(1);
        TEST_ASSERT(&result, "audio_format_to_string(MP3)", mp3 != NULL && strstr(mp3, "mp3") != NULL, "Invalid format string");
        if (mp3) printf("  Format 1: %s\n", mp3);
    }
    
    printf("\n");
    
    /* ============================================
     * 6. Test error message function
     * ============================================ */
    printf("=== Testing Error Messages ===\n");
    
    if (typecast_error_message) {
        /* TYPECAST_OK = 0 */
        const char* ok_msg = typecast_error_message(0);
        TEST_ASSERT(&result, "error_message(OK)", ok_msg != NULL, "NULL error message");
        if (ok_msg) printf("  Error 0: %s\n", ok_msg);
        
        /* TYPECAST_ERROR_UNAUTHORIZED = 401 */
        const char* unauth_msg = typecast_error_message(401);
        TEST_ASSERT(&result, "error_message(UNAUTHORIZED)", unauth_msg != NULL, "NULL error message");
        if (unauth_msg) printf("  Error 401: %s\n", unauth_msg);
    }
    
    printf("\n");
    
    /* ============================================
     * 7. Test client creation (with dummy API key)
     * ============================================ */
    printf("=== Testing Client Creation ===\n");
    
    if (typecast_client_create && typecast_client_destroy) {
        /* Create client with test API key */
        TypecastClient* client = typecast_client_create("test-api-key-for-e2e");
        TEST_ASSERT(&result, "typecast_client_create", client != NULL, "Failed to create client");
        
        if (client) {
            printf("  Client created successfully\n");
            
            /* Destroy client */
            typecast_client_destroy(client);
            printf("  Client destroyed successfully\n");
            result.total++;
            result.passed++;
            printf("  [PASS] typecast_client_destroy\n");
        }
    }
    
    if (typecast_client_create_with_host && typecast_client_destroy) {
        /* Create client with custom host */
        TypecastClient* client = typecast_client_create_with_host("test-api-key", "https://api.typecast.ai");
        TEST_ASSERT(&result, "typecast_client_create_with_host", client != NULL, "Failed to create client with host");
        
        if (client) {
            typecast_client_destroy(client);
        }
    }
    
    printf("\n");
    
    /* ============================================
     * 8. Test with NULL parameters (robustness)
     * ============================================ */
    printf("=== Testing NULL Parameter Handling ===\n");
    
    if (typecast_client_create) {
        /* NULL API key should return NULL */
        TypecastClient* client = typecast_client_create(NULL);
        TEST_ASSERT(&result, "client_create(NULL) returns NULL", client == NULL, "Should return NULL for NULL API key");
    }
    
    if (typecast_client_destroy) {
        /* Should not crash with NULL */
        typecast_client_destroy(NULL);
        result.total++;
        result.passed++;
        printf("  [PASS] client_destroy(NULL) does not crash\n");
    }
    
    printf("\n");
    
    /* ============================================
     * Results
     * ============================================ */
    printf("===========================================\n");
    printf("Test Results:\n");
    printf("  Total:  %d\n", result.total);
    printf("  Passed: %d\n", result.passed);
    printf("  Failed: %d\n", result.failed);
    printf("===========================================\n");
    
    /* Cleanup */
    CLOSE_LIBRARY(handle);
    
    /* Return 1 if any tests failed */
    return result.failed > 0 ? 1 : 0;
}
