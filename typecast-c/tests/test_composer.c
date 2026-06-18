#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

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

typedef struct {
    int listen_fd;
    int port;
    pthread_t thread;
    const uint8_t** bodies;
    const size_t* sizes;
    const int* statuses;
    size_t count;
} TinyServer;

static void write_u16(uint8_t* p, uint16_t v) {
    p[0] = (uint8_t)(v & 0xff);
    p[1] = (uint8_t)((v >> 8) & 0xff);
}

static void write_u32(uint8_t* p, uint32_t v) {
    p[0] = (uint8_t)(v & 0xff);
    p[1] = (uint8_t)((v >> 8) & 0xff);
    p[2] = (uint8_t)((v >> 16) & 0xff);
    p[3] = (uint8_t)((v >> 24) & 0xff);
}

static uint16_t read_u16(const uint8_t* p) {
    return (uint16_t)p[0] | ((uint16_t)p[1] << 8);
}

static uint8_t* make_wav(const int16_t* samples, size_t count, uint32_t sample_rate, size_t* out_size) {
    *out_size = 44 + count * 2;
    uint8_t* wav = (uint8_t*)calloc(1, *out_size);
    if (!wav) return NULL;
    memcpy(wav, "RIFF", 4);
    write_u32(wav + 4, (uint32_t)(36 + count * 2));
    memcpy(wav + 8, "WAVEfmt ", 8);
    write_u32(wav + 16, 16);
    write_u16(wav + 20, 1);
    write_u16(wav + 22, 1);
    write_u32(wav + 24, sample_rate);
    write_u32(wav + 28, sample_rate * 2);
    write_u16(wav + 32, 2);
    write_u16(wav + 34, 16);
    memcpy(wav + 36, "data", 4);
    write_u32(wav + 40, (uint32_t)(count * 2));
    for (size_t i = 0; i < count; i++) write_u16(wav + 44 + i * 2, (uint16_t)samples[i]);
    return wav;
}

static uint8_t* make_wav_without_data(size_t* out_size) {
    *out_size = 48;
    uint8_t* wav = (uint8_t*)calloc(1, *out_size);
    if (!wav) return NULL;
    memcpy(wav, "RIFF", 4);
    write_u32(wav + 4, 40);
    memcpy(wav + 8, "WAVEfmt ", 8);
    write_u32(wav + 16, 16);
    write_u16(wav + 20, 1);
    write_u16(wav + 22, 1);
    write_u32(wav + 24, 10);
    write_u32(wav + 28, 20);
    write_u16(wav + 32, 2);
    write_u16(wav + 34, 16);
    memcpy(wav + 36, "JUNK", 4);
    write_u32(wav + 40, 4);
    write_u32(wav + 44, 123);
    return wav;
}

static void* tiny_server_thread(void* arg) {
    TinyServer* server = (TinyServer*)arg;
    for (size_t i = 0; i < server->count; i++) {
        int fd = accept(server->listen_fd, NULL, NULL);
        if (fd < 0) continue;
        char buf[4096];
        (void)recv(fd, buf, sizeof(buf), 0);
        char header[256];
        int status = server->statuses ? server->statuses[i] : 200;
        int header_len = snprintf(header, sizeof(header),
            "HTTP/1.1 %d %s\r\nContent-Type: audio/wav\r\nContent-Length: %zu\r\nConnection: close\r\n\r\n",
            status, status == 200 ? "OK" : "Error", server->sizes[i]);
        (void)send(fd, header, (size_t)header_len, 0);
        (void)send(fd, server->bodies[i], server->sizes[i], 0);
        close(fd);
    }
    return NULL;
}

static int tiny_server_start_with_statuses(TinyServer* server, const uint8_t** bodies, const size_t* sizes, const int* statuses, size_t count) {
    memset(server, 0, sizeof(*server));
    server->bodies = bodies;
    server->sizes = sizes;
    server->statuses = statuses;
    server->count = count;
    server->listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server->listen_fd < 0) return 0;
    int opt = 1;
    setsockopt(server->listen_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;
    if (bind(server->listen_fd, (struct sockaddr*)&addr, sizeof(addr)) != 0) return 0;
    if (listen(server->listen_fd, 8) != 0) return 0;
    socklen_t len = sizeof(addr);
    if (getsockname(server->listen_fd, (struct sockaddr*)&addr, &len) != 0) return 0;
    server->port = ntohs(addr.sin_port);
    return pthread_create(&server->thread, NULL, tiny_server_thread, server) == 0;
}

static int tiny_server_start(TinyServer* server, const uint8_t** bodies, const size_t* sizes, size_t count) {
    return tiny_server_start_with_statuses(server, bodies, sizes, NULL, count);
}

static void tiny_server_stop(TinyServer* server) {
    pthread_join(server->thread, NULL);
    close(server->listen_fd);
}

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
    defaults.use_seed = 1;
    defaults.seed = 123;
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
    overrides.output.use_volume = 1;
    overrides.output.volume = 80;
    overrides.output.use_target_lufs = 1;
    overrides.output.target_lufs = -16.0f;
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
    ASSERT_EQ(requests[1].output->volume, 80);
    ASSERT(requests[1].output->target_lufs > -16.1f && requests[1].output->target_lufs < -15.9f);
    ASSERT_EQ(requests[1].seed, 123);

    typecast_speech_composer_segment_requests_free(requests, count);
    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_trims_segments_and_inserts_pause(void) {
    int16_t samples1[] = {0, 100, 0};
    int16_t samples2[] = {0, -200, 0};
    size_t wav1_size = 0;
    size_t wav2_size = 0;
    uint8_t* wav1 = make_wav(samples1, 3, 10, &wav1_size);
    uint8_t* wav2 = make_wav(samples2, 3, 10, &wav2_size);
    ASSERT(wav1 != NULL && wav2 != NULL);

    const uint8_t* bodies[] = {wav1, wav2};
    size_t sizes[] = {wav1_size, wav2_size};
    TinyServer server;
    ASSERT(tiny_server_start(&server, bodies, sizes, 2));

    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* client = typecast_client_create_with_host("test-key", host);
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);

    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice-a";
    defaults.use_model = 1;
    defaults.model = TYPECAST_MODEL_SSFM_V30;
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "First", NULL), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_pause(composer, 0.2f), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Second", NULL), TYPECAST_OK);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response != NULL);
    ASSERT_EQ(response->format, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response->duration > 0.39f && response->duration < 0.41f);
    ASSERT_EQ(response->audio_size, 52);
    ASSERT_EQ((int16_t)read_u16(response->audio_data + 44), 100);
    ASSERT_EQ((int16_t)read_u16(response->audio_data + 46), 0);
    ASSERT_EQ((int16_t)read_u16(response->audio_data + 48), 0);
    ASSERT_EQ((int16_t)read_u16(response->audio_data + 50), -200);

    typecast_tts_response_free(response);
    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
    tiny_server_stop(&server);
    free(wav1);
    free(wav2);
}

static void test_segment_requests_require_voice_id(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);

    ASSERT_EQ(typecast_speech_composer_say(composer, "Hello", NULL), TYPECAST_OK);
    TypecastTTSRequest* requests = NULL;
    size_t count = 0;
    ASSERT_EQ(typecast_speech_composer_segment_requests(composer, &requests, &count), TYPECAST_ERROR_INVALID_PARAM);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_requires_speech_segment(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response == NULL);
    const TypecastError* error = typecast_client_get_error(client);
    ASSERT(error != NULL);
    ASSERT_EQ(error->code, TYPECAST_ERROR_INVALID_PARAM);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_rejects_malformed_wav(void) {
    uint8_t bad[] = {1, 2, 3, 4};
    const uint8_t* bodies[] = {bad};
    size_t sizes[] = {sizeof(bad)};
    TinyServer server;
    ASSERT(tiny_server_start(&server, bodies, sizes, 1));

    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* client = typecast_client_create_with_host("test-key", host);
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice-a";
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Hello", NULL), TYPECAST_OK);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response == NULL);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
    tiny_server_stop(&server);
}

static void test_generate_rejects_mismatched_sample_rates(void) {
    int16_t sample[] = {100};
    size_t wav1_size = 0;
    size_t wav2_size = 0;
    uint8_t* wav1 = make_wav(sample, 1, 10, &wav1_size);
    uint8_t* wav2 = make_wav(sample, 1, 20, &wav2_size);
    ASSERT(wav1 != NULL && wav2 != NULL);
    const uint8_t* bodies[] = {wav1, wav2};
    size_t sizes[] = {wav1_size, wav2_size};
    TinyServer server;
    ASSERT(tiny_server_start(&server, bodies, sizes, 2));

    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* client = typecast_client_create_with_host("test-key", host);
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice-a";
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "First", NULL), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Second", NULL), TYPECAST_OK);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response == NULL);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
    tiny_server_stop(&server);
    free(wav1);
    free(wav2);
}

static void test_generate_rejects_missing_data_chunk(void) {
    size_t wav_size = 0;
    uint8_t* wav = make_wav_without_data(&wav_size);
    ASSERT(wav != NULL);
    const uint8_t* bodies[] = {wav};
    size_t sizes[] = {wav_size};
    TinyServer server;
    ASSERT(tiny_server_start(&server, bodies, sizes, 1));

    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* client = typecast_client_create_with_host("test-key", host);
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice-a";
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Hello", NULL), TYPECAST_OK);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response == NULL);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
    tiny_server_stop(&server);
    free(wav);
}

static void test_generate_cleans_up_after_segment_failure(void) {
    int16_t sample[] = {100};
    size_t wav_size = 0;
    uint8_t* wav = make_wav(sample, 1, 10, &wav_size);
    ASSERT(wav != NULL);
    uint8_t error_body[] = "{\"detail\":\"boom\"}";
    const uint8_t* bodies[] = {wav, error_body};
    size_t sizes[] = {wav_size, sizeof(error_body) - 1};
    int statuses[] = {200, 500};
    TinyServer server;
    ASSERT(tiny_server_start_with_statuses(&server, bodies, sizes, statuses, 2));

    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* client = typecast_client_create_with_host("test-key", host);
    ASSERT(client != NULL);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(composer != NULL);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice-a";
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "First", NULL), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Second", NULL), TYPECAST_OK);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV);
    ASSERT(response == NULL);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
    tiny_server_stop(&server);
    free(wav);
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
    RUN(segment_requests_require_voice_id);
    RUN(generate_trims_segments_and_inserts_pause);
    RUN(generate_requires_speech_segment);
    RUN(generate_rejects_malformed_wav);
    RUN(generate_rejects_mismatched_sample_rates);
    RUN(generate_rejects_missing_data_chunk);
    RUN(generate_cleans_up_after_segment_failure);
    RUN(generate_rejects_mp3_conversion);

    printf("\nComposer tests: %d run, %d failed\n", tests_run, tests_failed);
    return tests_failed == 0 ? 0 : 1;
}
