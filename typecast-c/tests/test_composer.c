#include <arpa/inet.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

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
    int status;
    pthread_t thread;
    char request[16384];
} TinyServer;

static void* tiny_server_thread(void* arg) {
    TinyServer* server = (TinyServer*)arg;
    int fd = accept(server->listen_fd, NULL, NULL);
    if (fd < 0) return NULL;
    size_t used = 0;
    ssize_t received;
    while ((received = recv(fd, server->request + used, sizeof(server->request) - used - 1, 0)) > 0) {
        used += (size_t)received;
        server->request[used] = '\0';
        char* header_end = strstr(server->request, "\r\n\r\n");
        char* length_header = strstr(server->request, "Content-Length:");
        if (header_end && length_header) {
            size_t content_length = (size_t)strtoul(length_header + strlen("Content-Length:"), NULL, 10);
            if (used >= (size_t)(header_end + 4 - server->request) + content_length) break;
        }
    }
    const char* body = "composed-audio";
    char header[256];
    int header_len = snprintf(header, sizeof(header),
        "HTTP/1.1 %d Test\r\nContent-Type: audio/mpeg\r\nX-Audio-Duration: 1.25\r\nContent-Length: %zu\r\nConnection: close\r\n\r\n",
        server->status, strlen(body));
    (void)send(fd, header, (size_t)header_len, 0);
    (void)send(fd, body, strlen(body), 0);
    close(fd);
    return NULL;
}

static int tiny_server_start(TinyServer* server) {
    memset(server, 0, sizeof(*server));
    server->status = 200;
    server->listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server->listen_fd < 0) return 0;
    int opt = 1;
    setsockopt(server->listen_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    struct sockaddr_in addr = {0};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    if (bind(server->listen_fd, (struct sockaddr*)&addr, sizeof(addr)) != 0 || listen(server->listen_fd, 1) != 0) return 0;
    socklen_t len = sizeof(addr);
    if (getsockname(server->listen_fd, (struct sockaddr*)&addr, &len) != 0) return 0;
    server->port = ntohs(addr.sin_port);
    return pthread_create(&server->thread, NULL, tiny_server_thread, server) == 0;
}

static void tiny_server_stop(TinyServer* server) {
    shutdown(server->listen_fd, SHUT_RDWR);
    close(server->listen_fd);
    pthread_join(server->thread, NULL);
}

static void test_parse_pause_markup_preserves_invalid_tokens(void) {
    TypecastSpeechPart* parts = NULL;
    size_t count = 0;
    ASSERT_EQ(typecast_parse_pause_markup("a<|0.3s|>b<|abc|>c<|3s|>", &parts, &count), TYPECAST_OK);
    ASSERT_EQ(count, 5);
    ASSERT_STREQ(parts[0].text, "a");
    ASSERT(parts[1].is_pause);
    ASSERT_STREQ(parts[2].text, "b<|abc|>c");
    ASSERT(parts[3].is_pause);
    typecast_speech_parts_free(parts, count);
}

static void test_segment_requests_merge_defaults_and_overrides(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "default-voice";
    defaults.use_model = 1;
    defaults.model = TYPECAST_MODEL_SSFM_V30;
    defaults.use_output = 1;
    defaults.output.use_audio_pitch = 1;
    defaults.output.audio_pitch = 1;
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    TypecastComposerSettings overrides = {0};
    overrides.use_output = 1;
    overrides.output.use_volume = 1;
    overrides.output.volume = 80;
    overrides.output.use_target_lufs = 1;
    overrides.output.target_lufs = -18.0f;
    overrides.output.use_audio_tempo = 1;
    overrides.output.audio_tempo = 1.1f;
    overrides.use_seed = 1;
    overrides.seed = 77;
    ASSERT_EQ(typecast_speech_composer_say(composer, "First", &overrides), TYPECAST_OK);

    TypecastTTSRequest* requests = NULL;
    size_t count = 0;
    ASSERT_EQ(typecast_speech_composer_segment_requests(composer, &requests, &count), TYPECAST_OK);
    ASSERT_EQ(count, 1);
    ASSERT_STREQ(requests[0].voice_id, "default-voice");
    ASSERT_EQ(requests[0].output->audio_pitch, 1);
    ASSERT_EQ(requests[0].output->volume, 80);
    ASSERT(requests[0].output->target_lufs < -17.9f && requests[0].output->target_lufs > -18.1f);
    ASSERT(requests[0].output->audio_tempo > 1.09f && requests[0].output->audio_tempo < 1.11f);
    ASSERT_EQ(requests[0].seed, 77);
    typecast_speech_composer_segment_requests_free(requests, count);
    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_uses_compose_api_once(void) {
    TinyServer server;
    ASSERT(tiny_server_start(&server));
    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* client = typecast_client_create_with_host("test-key", host);
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice-a";
    defaults.use_model = 1;
    defaults.model = TYPECAST_MODEL_SSFM_V30;
    defaults.use_output = 1;
    defaults.output.use_audio_format = 1;
    defaults.output.audio_format = TYPECAST_AUDIO_FORMAT_MP3;
    ASSERT_EQ(typecast_speech_composer_defaults(composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_pause(composer, 0.25f), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Hello<|0.3s|>world", NULL), TYPECAST_OK);

    TypecastTTSResponse* response = typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_MP3);
    ASSERT(response != NULL);
    ASSERT_EQ(response->format, TYPECAST_AUDIO_FORMAT_MP3);
    ASSERT(response->duration > 1.24f && response->duration < 1.26f);
    ASSERT_EQ(response->audio_size, strlen("composed-audio"));
    ASSERT(memcmp(response->audio_data, "composed-audio", response->audio_size) == 0);
    tiny_server_stop(&server);
    ASSERT(strstr(server.request, "POST /v1/text-to-speech/compose") != NULL);
    ASSERT(strstr(server.request, "\"type\":\"tts\"") != NULL);
    ASSERT(strstr(server.request, "\"type\":\"pause\"") != NULL);
    ASSERT(strstr(server.request, "\"duration_seconds\":0.3") != NULL);
    ASSERT(strstr(server.request, "\"duration_seconds\":0.25") != NULL);
    ASSERT(strstr(server.request, "\"text\":\"Hello\"") != NULL);
    ASSERT(strstr(server.request, "\"text\":\"world\"") != NULL);

    typecast_tts_response_free(response);
    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_validates_before_network(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT(typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV) == NULL);
    ASSERT_EQ(typecast_client_get_error(client)->code, TYPECAST_ERROR_INVALID_PARAM);
    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_composer_validation_edges(void) {
    TypecastClient* client = typecast_client_create_with_host("test-key", "http://localhost:1");
    TypecastSpeechComposer* composer = typecast_speech_composer_create(client);
    ASSERT_EQ(typecast_speech_composer_pause(NULL, 0.1f), TYPECAST_ERROR_INVALID_PARAM);
    ASSERT_EQ(typecast_speech_composer_pause(composer, -0.1f), TYPECAST_ERROR_INVALID_PARAM);
    ASSERT_EQ(typecast_speech_composer_say(composer, "Hello", NULL), TYPECAST_OK);

    TypecastTTSRequest* requests = NULL;
    size_t count = 0;
    ASSERT_EQ(
        typecast_speech_composer_segment_requests(composer, &requests, &count),
        TYPECAST_ERROR_INVALID_PARAM
    );
    ASSERT(requests == NULL);
    ASSERT(typecast_speech_composer_generate(composer, TYPECAST_AUDIO_FORMAT_WAV) == NULL);
    ASSERT_EQ(typecast_client_get_error(client)->code, TYPECAST_ERROR_INVALID_PARAM);

    typecast_speech_composer_destroy(composer);
    typecast_client_destroy(client);
}

static void test_generate_propagates_network_and_http_errors(void) {
    TypecastClient* network_client = typecast_client_create_with_host("test-key", "http://127.0.0.1:1");
    TypecastSpeechComposer* network_composer = typecast_speech_composer_create(network_client);
    TypecastComposerSettings defaults = {0};
    defaults.voice_id = "voice";
    defaults.use_model = 1;
    defaults.model = TYPECAST_MODEL_SSFM_V30;
    ASSERT_EQ(typecast_speech_composer_defaults(network_composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(network_composer, "Hello", NULL), TYPECAST_OK);
    ASSERT(typecast_speech_composer_generate(network_composer, TYPECAST_AUDIO_FORMAT_WAV) == NULL);
    ASSERT_EQ(typecast_client_get_error(network_client)->code, TYPECAST_ERROR_NETWORK);
    typecast_speech_composer_destroy(network_composer);
    typecast_client_destroy(network_client);

    TinyServer server;
    ASSERT(tiny_server_start(&server));
    server.status = 422;
    char host[128];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", server.port);
    TypecastClient* http_client = typecast_client_create_with_host("test-key", host);
    TypecastSpeechComposer* http_composer = typecast_speech_composer_create(http_client);
    ASSERT_EQ(typecast_speech_composer_defaults(http_composer, &defaults), TYPECAST_OK);
    ASSERT_EQ(typecast_speech_composer_say(http_composer, "Hello", NULL), TYPECAST_OK);
    ASSERT(typecast_speech_composer_generate(http_composer, TYPECAST_AUDIO_FORMAT_WAV) == NULL);
    ASSERT_EQ(typecast_client_get_error(http_client)->code, TYPECAST_ERROR_UNPROCESSABLE_ENTITY);
    tiny_server_stop(&server);
    typecast_speech_composer_destroy(http_composer);
    typecast_client_destroy(http_client);
}

int main(void) {
    RUN(parse_pause_markup_preserves_invalid_tokens);
    RUN(segment_requests_merge_defaults_and_overrides);
    RUN(generate_uses_compose_api_once);
    RUN(generate_validates_before_network);
    RUN(composer_validation_edges);
    RUN(generate_propagates_network_and_http_errors);
    printf("\nComposer tests: %d run, %d failed\n", tests_run, tests_failed);
    return tests_failed == 0 ? 0 : 1;
}
