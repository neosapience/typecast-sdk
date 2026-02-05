/**
 * Typecast C SDK - Integration Tests
 *
 * These tests make actual API calls to verify the SDK works correctly.
 * Requires a valid API key.
 *
 * Usage:
 *   ./test_integration <api_key>
 *   or set TYPECAST_API_KEY environment variable
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "typecast.h"

/* Test counters */
static int tests_run = 0;
static int tests_passed = 0;
static int tests_failed = 0;

/* Global client for all tests */
static TypecastClient* g_client = NULL;

/* Test macros */
#define TEST(name) static void test_##name(void)
#define RUN_TEST(name) do { \
    printf("\n[TEST] %s\n", #name); \
    tests_run++; \
    test_##name(); \
} while(0)

#define ASSERT(cond, msg) do { \
    if (!(cond)) { \
        printf("  FAILED: %s\n", msg); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define TEST_PASS() do { \
    printf("  PASSED\n"); \
    tests_passed++; \
} while(0)

/* ============================================
 * API Integration Tests
 * ============================================ */

TEST(get_voices) {
    printf("  Fetching voices list...\n");
    
    TypecastVoicesResponse* voices = typecast_get_voices(g_client, NULL);
    
    if (!voices) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to get voices");
    }
    
    printf("  Found %zu voices\n", voices->count);
    ASSERT(voices->count > 0, "Expected at least one voice");
    
    /* Verify first voice has required fields */
    ASSERT(voices->voices[0].voice_id != NULL, "voice_id should not be NULL");
    ASSERT(voices->voices[0].voice_name != NULL, "voice_name should not be NULL");
    
    printf("  First voice: %s (%s)\n", 
        voices->voices[0].voice_name, 
        voices->voices[0].voice_id);
    
    typecast_voices_response_free(voices);
    TEST_PASS();
}

TEST(get_voices_with_filter) {
    printf("  Fetching voices with model filter...\n");
    
    TypecastModel model = TYPECAST_MODEL_SSFM_V30;
    TypecastVoicesFilter filter = {0};
    filter.model = &model;
    
    TypecastVoicesResponse* voices = typecast_get_voices(g_client, &filter);
    
    if (!voices) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to get voices with filter");
    }
    
    printf("  Found %zu voices with ssfm-v30 support\n", voices->count);
    ASSERT(voices->count > 0, "Expected at least one voice");
    
    typecast_voices_response_free(voices);
    TEST_PASS();
}

TEST(get_single_voice) {
    printf("  Fetching a specific voice...\n");
    
    const char* voice_id = "tc_60e5426de8b95f1d3000d7b5";  /* Olivia */
    
    TypecastVoice* voice = typecast_get_voice(g_client, voice_id);
    
    if (!voice) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to get voice");
    }
    
    printf("  Voice: %s (%s)\n", voice->voice_name, voice->voice_id);
    printf("  Gender: %s\n", voice->gender == TYPECAST_GENDER_MALE ? "male" : 
                             voice->gender == TYPECAST_GENDER_FEMALE ? "female" : "unknown");
    printf("  Models: %zu\n", voice->models_count);
    
    ASSERT(strcmp(voice->voice_id, voice_id) == 0, "voice_id should match");
    ASSERT(voice->voice_name != NULL, "voice_name should not be NULL");
    
    typecast_voice_free(voice);
    TEST_PASS();
}

TEST(get_invalid_voice) {
    printf("  Fetching an invalid voice (should fail)...\n");
    
    TypecastVoice* voice = typecast_get_voice(g_client, "tc_invalid_voice_id");
    
    ASSERT(voice == NULL, "Expected NULL for invalid voice");
    
    const TypecastError* err = typecast_client_get_error(g_client);
    printf("  Expected error: %s (code: %d)\n", err->message, err->code);
    ASSERT(err->code == TYPECAST_ERROR_NOT_FOUND, "Expected 404 error");
    
    TEST_PASS();
}

TEST(text_to_speech_basic) {
    printf("  Generating basic TTS...\n");
    
    TypecastTTSRequest request = {0};
    request.text = "Hello world. This is a test.";
    request.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    
    TypecastTTSResponse* response = typecast_text_to_speech(g_client, &request);
    
    if (!response) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to generate TTS");
    }
    
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    printf("  Format: %s\n", typecast_audio_format_to_string(response->format));
    
    ASSERT(response->audio_size > 0, "Audio data should not be empty");
    ASSERT(response->audio_data != NULL, "Audio data should not be NULL");
    
    /* Verify WAV header (RIFF...WAVE) */
    ASSERT(response->audio_size >= 12, "Audio should be at least 12 bytes");
    ASSERT(memcmp(response->audio_data, "RIFF", 4) == 0, "Should start with RIFF");
    ASSERT(memcmp(response->audio_data + 8, "WAVE", 4) == 0, "Should be WAVE format");
    
    /* Save to file for verification */
    FILE* fp = fopen("test_output_basic.wav", "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: test_output_basic.wav\n");
    }
    
    typecast_tts_response_free(response);
    TEST_PASS();
}

TEST(text_to_speech_with_emotion) {
    printf("  Generating TTS with emotion...\n");
    
    TypecastTTSRequest request = {0};
    request.text = "I am so happy today!";
    request.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    
    TypecastPrompt prompt = TYPECAST_PROMPT_DEFAULT();
    prompt.emotion_type = TYPECAST_EMOTION_TYPE_PRESET;
    prompt.emotion_preset = TYPECAST_EMOTION_HAPPY;
    prompt.emotion_intensity = 1.5f;
    request.prompt = &prompt;
    
    TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
    output.volume = 100;
    output.audio_format = TYPECAST_AUDIO_FORMAT_WAV;
    request.output = &output;
    
    TypecastTTSResponse* response = typecast_text_to_speech(g_client, &request);
    
    if (!response) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to generate TTS");
    }
    
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    
    ASSERT(response->audio_size > 0, "Audio data should not be empty");
    
    FILE* fp = fopen("test_output_emotion.wav", "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: test_output_emotion.wav\n");
    }
    
    typecast_tts_response_free(response);
    TEST_PASS();
}

TEST(text_to_speech_smart_emotion) {
    printf("  Generating TTS with smart emotion...\n");
    
    TypecastTTSRequest request = {0};
    request.text = "Everything is so incredibly perfect.";
    request.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    
    TypecastPrompt prompt = {0};
    prompt.emotion_type = TYPECAST_EMOTION_TYPE_SMART;
    prompt.previous_text = "I feel like I'm walking on air!";
    prompt.next_text = "I never want this feeling to end!";
    request.prompt = &prompt;
    
    TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
    request.output = &output;
    
    TypecastTTSResponse* response = typecast_text_to_speech(g_client, &request);
    
    if (!response) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to generate TTS");
    }
    
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    
    ASSERT(response->audio_size > 0, "Audio data should not be empty");
    
    FILE* fp = fopen("test_output_smart.wav", "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: test_output_smart.wav\n");
    }
    
    typecast_tts_response_free(response);
    TEST_PASS();
}

TEST(text_to_speech_mp3) {
    printf("  Generating TTS with MP3 format...\n");
    
    TypecastTTSRequest request = {0};
    request.text = "This audio is in MP3 format.";
    request.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    
    TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
    output.audio_format = TYPECAST_AUDIO_FORMAT_MP3;
    request.output = &output;
    
    TypecastTTSResponse* response = typecast_text_to_speech(g_client, &request);
    
    if (!response) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to generate TTS");
    }
    
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    printf("  Format: %s\n", typecast_audio_format_to_string(response->format));
    
    ASSERT(response->audio_size > 0, "Audio data should not be empty");
    ASSERT(response->format == TYPECAST_AUDIO_FORMAT_MP3, "Format should be MP3");
    
    /* Verify MP3 header (ID3 or sync word 0xFFxx) */
    ASSERT(response->audio_size >= 3, "Audio should be at least 3 bytes");
    int is_mp3 = (memcmp(response->audio_data, "ID3", 3) == 0) || 
                 (response->audio_data[0] == 0xFF && (response->audio_data[1] & 0xE0) == 0xE0);
    ASSERT(is_mp3, "Should be valid MP3 data");
    
    FILE* fp = fopen("test_output.mp3", "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: test_output.mp3\n");
    }
    
    typecast_tts_response_free(response);
    TEST_PASS();
}

TEST(text_to_speech_korean) {
    printf("  Generating TTS with Korean text...\n");
    
    TypecastTTSRequest request = {0};
    request.text = "안녕하세요. 타입캐스트 SDK 테스트입니다.";
    request.voice_id = "tc_60e5426de8b95f1d3000d7b5";
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "kor";
    
    TypecastTTSResponse* response = typecast_text_to_speech(g_client, &request);
    
    if (!response) {
        const TypecastError* err = typecast_client_get_error(g_client);
        printf("  Error: %s (code: %d)\n", err->message, err->code);
        ASSERT(0, "Failed to generate TTS");
    }
    
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    
    ASSERT(response->audio_size > 0, "Audio data should not be empty");
    
    FILE* fp = fopen("test_output_korean.wav", "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: test_output_korean.wav\n");
    }
    
    typecast_tts_response_free(response);
    TEST_PASS();
}

TEST(text_to_speech_invalid_voice) {
    printf("  Testing TTS with invalid voice (should fail)...\n");
    
    TypecastTTSRequest request = {0};
    request.text = "Hello";
    request.voice_id = "tc_invalid_voice";
    request.model = TYPECAST_MODEL_SSFM_V30;
    
    TypecastTTSResponse* response = typecast_text_to_speech(g_client, &request);
    
    ASSERT(response == NULL, "Expected NULL for invalid voice");
    
    const TypecastError* err = typecast_client_get_error(g_client);
    printf("  Expected error: %s (code: %d)\n", err->message, err->code);
    ASSERT(err->code == TYPECAST_ERROR_BAD_REQUEST || 
           err->code == TYPECAST_ERROR_NOT_FOUND ||
           err->code == TYPECAST_ERROR_UNPROCESSABLE_ENTITY, 
           "Expected 400, 404, or 422 error");
    
    TEST_PASS();
}

TEST(unauthorized_request) {
    printf("  Testing with invalid API key (should fail)...\n");
    
    TypecastClient* bad_client = typecast_client_create("invalid_api_key");
    ASSERT(bad_client != NULL, "Client creation should succeed");
    
    TypecastVoicesResponse* voices = typecast_get_voices(bad_client, NULL);
    ASSERT(voices == NULL, "Expected NULL for unauthorized request");
    
    const TypecastError* err = typecast_client_get_error(bad_client);
    printf("  Expected error: %s (code: %d)\n", err->message, err->code);
    ASSERT(err->code == TYPECAST_ERROR_UNAUTHORIZED, "Expected 401 error");
    
    typecast_client_destroy(bad_client);
    TEST_PASS();
}

/* ============================================
 * Main
 * ============================================ */

static const char* get_api_key(int argc, char* argv[]) {
    if (argc > 1) {
        return argv[1];
    }
    const char* env_key = getenv("TYPECAST_API_KEY");
    if (env_key && strlen(env_key) > 0) {
        return env_key;
    }
    return NULL;
}

int main(int argc, char* argv[]) {
    printf("===========================================\n");
    printf("Typecast C SDK Integration Tests\n");
    printf("Version: %s\n", typecast_version());
    printf("===========================================\n");
    
    /* Get API key */
    const char* api_key = get_api_key(argc, argv);
    if (!api_key) {
        fprintf(stderr, "\nError: API key required for integration tests\n");
        fprintf(stderr, "Usage: %s <api_key>\n", argv[0]);
        fprintf(stderr, "Or set TYPECAST_API_KEY environment variable\n");
        return 1;
    }
    
    /* Create global client */
    g_client = typecast_client_create(api_key);
    if (!g_client) {
        fprintf(stderr, "Error: Failed to create client\n");
        return 1;
    }
    
    printf("\nClient created, running tests...\n");
    
    /* Run integration tests */
    RUN_TEST(get_voices);
    RUN_TEST(get_voices_with_filter);
    RUN_TEST(get_single_voice);
    RUN_TEST(get_invalid_voice);
    RUN_TEST(text_to_speech_basic);
    RUN_TEST(text_to_speech_with_emotion);
    RUN_TEST(text_to_speech_smart_emotion);
    RUN_TEST(text_to_speech_mp3);
    RUN_TEST(text_to_speech_korean);
    RUN_TEST(text_to_speech_invalid_voice);
    RUN_TEST(unauthorized_request);
    
    /* Cleanup */
    typecast_client_destroy(g_client);
    
    /* Summary */
    printf("\n===========================================\n");
    printf("Integration Tests Summary\n");
    printf("===========================================\n");
    printf("Tests run: %d\n", tests_run);
    printf("Tests passed: %d\n", tests_passed);
    printf("Tests failed: %d\n", tests_failed);
    printf("===========================================\n");
    
    if (tests_failed > 0) {
        printf("\nSome tests FAILED!\n");
        return 1;
    }
    
    printf("\nAll tests PASSED!\n");
    return 0;
}
