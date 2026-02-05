/**
 * Typecast C SDK - Unit Tests
 *
 * Basic unit tests for the SDK functionality.
 * Run: ./test_typecast
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "typecast.h"

/* Test counters */
static int tests_run = 0;
static int tests_passed = 0;
static int tests_failed = 0;

/* Test macros */
#define TEST(name) static void test_##name(void)
#define RUN_TEST(name) do { \
    printf("Running %s... ", #name); \
    tests_run++; \
    test_##name(); \
} while(0)

#define ASSERT(cond) do { \
    if (!(cond)) { \
        printf("FAILED\n  Assertion failed: %s\n  At line %d\n", #cond, __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_EQ(a, b) do { \
    if ((a) != (b)) { \
        printf("FAILED\n  Expected: %d, Got: %d\n  At line %d\n", (int)(b), (int)(a), __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_STREQ(a, b) do { \
    if (strcmp((a), (b)) != 0) { \
        printf("FAILED\n  Expected: \"%s\", Got: \"%s\"\n  At line %d\n", (b), (a), __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_NULL(ptr) do { \
    if ((ptr) != NULL) { \
        printf("FAILED\n  Expected NULL\n  At line %d\n", __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_NOT_NULL(ptr) do { \
    if ((ptr) == NULL) { \
        printf("FAILED\n  Expected not NULL\n  At line %d\n", __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define TEST_PASS() do { \
    printf("PASSED\n"); \
    tests_passed++; \
} while(0)

/* ============================================
 * Version Tests
 * ============================================ */

TEST(version) {
    const char* version = typecast_version();
    ASSERT_NOT_NULL(version);
    ASSERT_STREQ(version, "1.0.0");
    TEST_PASS();
}

/* ============================================
 * Model String Tests
 * ============================================ */

TEST(model_to_string) {
    ASSERT_STREQ(typecast_model_to_string(TYPECAST_MODEL_SSFM_V21), "ssfm-v21");
    ASSERT_STREQ(typecast_model_to_string(TYPECAST_MODEL_SSFM_V30), "ssfm-v30");
    TEST_PASS();
}

TEST(model_from_string) {
    ASSERT_EQ(typecast_model_from_string("ssfm-v21"), TYPECAST_MODEL_SSFM_V21);
    ASSERT_EQ(typecast_model_from_string("ssfm-v30"), TYPECAST_MODEL_SSFM_V30);
    ASSERT_EQ(typecast_model_from_string("invalid"), -1);
    ASSERT_EQ(typecast_model_from_string(NULL), -1);
    TEST_PASS();
}

/* ============================================
 * Emotion String Tests
 * ============================================ */

TEST(emotion_to_string) {
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_NORMAL), "normal");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_HAPPY), "happy");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_SAD), "sad");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_ANGRY), "angry");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_WHISPER), "whisper");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_TONEUP), "toneup");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_TONEDOWN), "tonedown");
    TEST_PASS();
}

/* ============================================
 * Audio Format String Tests
 * ============================================ */

TEST(audio_format_to_string) {
    ASSERT_STREQ(typecast_audio_format_to_string(TYPECAST_AUDIO_FORMAT_WAV), "wav");
    ASSERT_STREQ(typecast_audio_format_to_string(TYPECAST_AUDIO_FORMAT_MP3), "mp3");
    TEST_PASS();
}

/* ============================================
 * Error Message Tests
 * ============================================ */

TEST(error_messages) {
    ASSERT_STREQ(typecast_error_message(TYPECAST_OK), "Success");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_INVALID_PARAM), "Invalid parameter");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_UNAUTHORIZED), "Unauthorized");
    ASSERT_NOT_NULL(typecast_error_message(999));  /* Unknown error should return something */
    TEST_PASS();
}

/* ============================================
 * Client Creation Tests
 * ============================================ */

TEST(client_create_null_key) {
    TypecastClient* client = typecast_client_create(NULL);
    ASSERT_NULL(client);
    TEST_PASS();
}

TEST(client_create_empty_key) {
    TypecastClient* client = typecast_client_create("");
    ASSERT_NULL(client);
    TEST_PASS();
}

TEST(client_create_valid) {
    TypecastClient* client = typecast_client_create("test_api_key");
    ASSERT_NOT_NULL(client);
    typecast_client_destroy(client);
    TEST_PASS();
}

TEST(client_create_with_host) {
    TypecastClient* client = typecast_client_create_with_host(
        "test_api_key",
        "https://custom.api.host"
    );
    ASSERT_NOT_NULL(client);
    typecast_client_destroy(client);
    TEST_PASS();
}

TEST(client_destroy_null) {
    /* Should not crash */
    typecast_client_destroy(NULL);
    TEST_PASS();
}

/* ============================================
 * TTS Request Validation Tests
 * ============================================ */

TEST(tts_null_client) {
    TypecastTTSRequest req = {0};
    req.text = "Hello";
    req.voice_id = "tc_test";
    req.model = TYPECAST_MODEL_SSFM_V30;
    
    TypecastTTSResponse* resp = typecast_text_to_speech(NULL, &req);
    ASSERT_NULL(resp);
    TEST_PASS();
}

TEST(tts_null_request) {
    TypecastClient* client = typecast_client_create("test_key");
    ASSERT_NOT_NULL(client);
    
    TypecastTTSResponse* resp = typecast_text_to_speech(client, NULL);
    ASSERT_NULL(resp);
    
    const TypecastError* err = typecast_client_get_error(client);
    ASSERT_NOT_NULL(err);
    ASSERT_EQ(err->code, TYPECAST_ERROR_INVALID_PARAM);
    
    typecast_client_destroy(client);
    TEST_PASS();
}

TEST(tts_missing_text) {
    TypecastClient* client = typecast_client_create("test_key");
    ASSERT_NOT_NULL(client);
    
    TypecastTTSRequest req = {0};
    req.text = NULL;
    req.voice_id = "tc_test";
    req.model = TYPECAST_MODEL_SSFM_V30;
    
    TypecastTTSResponse* resp = typecast_text_to_speech(client, &req);
    ASSERT_NULL(resp);
    
    const TypecastError* err = typecast_client_get_error(client);
    ASSERT_NOT_NULL(err);
    ASSERT_EQ(err->code, TYPECAST_ERROR_INVALID_PARAM);
    
    typecast_client_destroy(client);
    TEST_PASS();
}

TEST(tts_missing_voice_id) {
    TypecastClient* client = typecast_client_create("test_key");
    ASSERT_NOT_NULL(client);
    
    TypecastTTSRequest req = {0};
    req.text = "Hello";
    req.voice_id = NULL;
    req.model = TYPECAST_MODEL_SSFM_V30;
    
    TypecastTTSResponse* resp = typecast_text_to_speech(client, &req);
    ASSERT_NULL(resp);
    
    const TypecastError* err = typecast_client_get_error(client);
    ASSERT_NOT_NULL(err);
    ASSERT_EQ(err->code, TYPECAST_ERROR_INVALID_PARAM);
    
    typecast_client_destroy(client);
    TEST_PASS();
}

/* ============================================
 * Default Value Tests
 * ============================================ */

TEST(output_default) {
    TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
    ASSERT_EQ(output.volume, 100);
    ASSERT_EQ(output.audio_pitch, 0);
    ASSERT(output.audio_tempo == 1.0f);
    ASSERT_EQ(output.audio_format, TYPECAST_AUDIO_FORMAT_WAV);
    TEST_PASS();
}

TEST(prompt_default) {
    TypecastPrompt prompt = TYPECAST_PROMPT_DEFAULT();
    ASSERT_EQ(prompt.emotion_type, TYPECAST_EMOTION_TYPE_NONE);
    ASSERT_EQ(prompt.emotion_preset, TYPECAST_EMOTION_NORMAL);
    ASSERT(prompt.emotion_intensity == 1.0f);
    ASSERT_NULL(prompt.previous_text);
    ASSERT_NULL(prompt.next_text);
    TEST_PASS();
}

/* ============================================
 * Voice Response Free Tests
 * ============================================ */

TEST(voice_free_null) {
    /* Should not crash */
    typecast_voice_free(NULL);
    typecast_voices_response_free(NULL);
    TEST_PASS();
}

TEST(tts_response_free_null) {
    /* Should not crash */
    typecast_tts_response_free(NULL);
    TEST_PASS();
}

/* ============================================
 * Main
 * ============================================ */

int main(void) {
    printf("===========================================\n");
    printf("Typecast C SDK Unit Tests\n");
    printf("Version: %s\n", typecast_version());
    printf("===========================================\n\n");
    
    /* Version tests */
    RUN_TEST(version);
    
    /* String conversion tests */
    RUN_TEST(model_to_string);
    RUN_TEST(model_from_string);
    RUN_TEST(emotion_to_string);
    RUN_TEST(audio_format_to_string);
    RUN_TEST(error_messages);
    
    /* Client tests */
    RUN_TEST(client_create_null_key);
    RUN_TEST(client_create_empty_key);
    RUN_TEST(client_create_valid);
    RUN_TEST(client_create_with_host);
    RUN_TEST(client_destroy_null);
    
    /* TTS validation tests */
    RUN_TEST(tts_null_client);
    RUN_TEST(tts_null_request);
    RUN_TEST(tts_missing_text);
    RUN_TEST(tts_missing_voice_id);
    
    /* Default value tests */
    RUN_TEST(output_default);
    RUN_TEST(prompt_default);
    
    /* Memory free tests */
    RUN_TEST(voice_free_null);
    RUN_TEST(tts_response_free_null);
    
    /* Summary */
    printf("\n===========================================\n");
    printf("Tests run: %d\n", tests_run);
    printf("Tests passed: %d\n", tests_passed);
    printf("Tests failed: %d\n", tests_failed);
    printf("===========================================\n");
    
    return tests_failed > 0 ? 1 : 0;
}
