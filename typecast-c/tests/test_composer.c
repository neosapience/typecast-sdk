#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "typecast.h"

static int tests_run = 0;
static int tests_failed = 0;

#define ASSERT(cond) do { \
    if (!(cond)) { \
        printf("FAILED\n  Assertion failed: %s at %s:%d\n", #cond, __FILE__, __LINE__); \
        tests_failed++; \
        return; \
    } \
} while (0)

#define ASSERT_EQ(a, b) ASSERT((a) == (b))
#define ASSERT_STREQ(a, b) ASSERT((a) && strcmp((a), (b)) == 0)

#define RUN(name) do { \
    printf("Running %s... ", #name); \
    tests_run++; \
    int before = tests_failed; \
    test_##name(); \
    if (tests_failed == before) printf("PASSED\n"); \
} while (0)

static void test_parse_pause_markup_preserves_invalid_tokens(void) {
    TypecastSpeechPart* parts = NULL;
    size_t count = 0;
    TypecastErrorCode err = typecast_parse_pause_markup("Hello <|0.3s|>world <|bad|> <|3000s|>", &parts, &count);

    ASSERT_EQ(err, TYPECAST_OK);
    ASSERT_EQ(count, 5);
    ASSERT_STREQ(parts[0].text, "Hello ");
    ASSERT(parts[1].is_pause);
    ASSERT(parts[1].pause_seconds > 0.299f && parts[1].pause_seconds < 0.301f);
    ASSERT_STREQ(parts[2].text, "world <|bad|> ");
    ASSERT(parts[3].is_pause);
    ASSERT(parts[3].pause_seconds > 2999.9f && parts[3].pause_seconds < 3000.1f);
    ASSERT_STREQ(parts[4].text, "");

    typecast_speech_parts_free(parts, count);
}

static void test_segment_requests_merge_defaults_and_overrides(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);

    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "default-voice";
    defaults.use_model = 1;
    defaults.model = TYPECAST_MODEL_SSFM_V30;
    defaults.language = "eng";
    defaults.use_output = 1;
    defaults.output.use_audio_pitch = 1;
    defaults.output.audio_pitch = 1;
    defaults.output.use_audio_tempo = 1;
    defaults.output.audio_tempo = 0.9f;
    defaults.output.use_audio_format = 1;
    defaults.output.audio_format = TYPECAST_AUDIO_FORMAT_MP3;
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);

    ASSERT_EQ(typecast_speech_composer_say(composer, "First", NULL), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_pause(composer, 0.25f), TYPECAST_OK);

    TypecastComposerSettings overrides = {0};
    overrides.voice_id = "second-voice";
    overrides.use_output = 1;
    overrides.output.use_audio_pitch = 1;
    overrides.output.audio_pitch = -2;
    overrides.output.use_audio_tempo = 1;
    overrides.output.audio_tempo = 1.1f;
    ASSERT_EQ(typecast_speech_composer_say(composer, "Second", &overrides), TYPECAST_OK);

    TypecastTTSRequest* requests = NULL;
    size_t count = 0;
    ASSERT_EQ(typecast_speech_composer_segment_requests(composer, &requests, &count), TYPECAST_OK);
    ASSERT_EQ(count, 2);
    ASSERT_STREQ(requests[0].voice_id, "default-voice");
    ASSERT_STREQ(requests[0].text, "First");
    ASSERT_EQ(requests[0].output->audio_format, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT_EQ(requests[0].output->audio_pitch, 1);
    ASSERT(requests[0].output->audio_tempo > 0.89f && requests[0].output->audio_tempo < 0.91f);
    ASSERT_STREQ(requests[1].voice_id, "second-voice");
    ASSERT_STREQ(requests[1].text, "Second");
    ASSERT_EQ(requests[1].output->audio_format, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT_EQ(requests[1].output->audio_pitch, -2);
    ASSERT(requests[1].output->audio_tempo > 1.09f && requests[1].output->audio_tempo < 1.11f);

    typecast_speech_composer_segment_requests_free(requests, count);
    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_rejects_mp3_conversion(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_MP3);
    ASSERT(response == NULL);
    const TypecastError* error = typecast_client_get_error(client);
    ASSERT(error != NULL);
    ASSERT_EQ(error->code, TYPECAST_ERROR_INVALID_PARAM);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

int main(void) {
    RUN(parse_pause_markup_preserves_invalid_tokens);
    RUN(segment_requests_merge_defaults_and_overrides);
    RUN(generate_rejects_mp3_conversion);

    printf("\nComposer tests: %d run, %d failed\n", tests_run, tests_failed);
    return tests_failed == 0 ? 0 : 1;
}
