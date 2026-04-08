/**
 * Typecast C SDK - Mock-server-driven coverage tests
 *
 * Drives the SDK against a localhost HTTP mock so we can exercise
 * every code path (request building, response parsing, error handling)
 * without hitting the real Typecast API. Aims for 100% line + function
 * coverage of src/typecast.c.
 *
 * Run: ./test_mock
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdarg.h>
#include <errno.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "typecast.h"

/* ============================================
 * Test bookkeeping
 * ============================================ */

static int tests_run = 0;
static int tests_passed = 0;
static int tests_failed = 0;

#define ASSERT(cond) do { \
    if (!(cond)) { \
        printf("FAILED\n  Assertion failed: %s\n  At %s:%d\n", #cond, __FILE__, __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_EQ(a, b) do { \
    long long _a = (long long)(a), _b = (long long)(b); \
    if (_a != _b) { \
        printf("FAILED\n  Expected %lld, got %lld\n  At %s:%d\n", _b, _a, __FILE__, __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_STREQ(a, b) do { \
    if (!(a) || strcmp((a), (b)) != 0) { \
        printf("FAILED\n  Expected \"%s\", got \"%s\"\n  At %s:%d\n", (b), (a) ? (a) : "(null)", __FILE__, __LINE__); \
        tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_NOT_NULL(p) do { \
    if (!(p)) { printf("FAILED\n  Expected non-NULL\n  At %s:%d\n", __FILE__, __LINE__); tests_failed++; return; } \
} while(0)

#define ASSERT_NULL(p) do { \
    if ((p)) { printf("FAILED\n  Expected NULL\n  At %s:%d\n", __FILE__, __LINE__); tests_failed++; return; } \
} while(0)

#define RUN(name) do { \
    printf("Running %s... ", #name); \
    fflush(stdout); \
    tests_run++; \
    int before = tests_failed; \
    test_##name(); \
    if (tests_failed == before) { tests_passed++; printf("PASSED\n"); } \
} while(0)

/* ============================================
 * Mock HTTP server
 *
 * Single-threaded background server that serves one request at a time.
 * Each test enqueues a response (status code + headers + body) before
 * making an SDK call; the server replies with the next queued response.
 * ============================================ */

#define MAX_QUEUED 16
#define MAX_BODY (256 * 1024)

typedef struct {
    int status;
    char headers[1024];   /* Extra headers, may be empty. Each ends with \r\n */
    uint8_t* body;
    size_t body_len;
    int close_immediately; /* Drop connection after accept (network error) */
} MockResponse;

static struct {
    pthread_mutex_t lock;
    pthread_cond_t cond;
    MockResponse queue[MAX_QUEUED];
    int head;
    int tail;
    int count;

    int listen_fd;
    int port;
    pthread_t thread;
    int running;

    /* Captured request context for assertions */
    char last_method[16];
    char last_path[1024];
    char last_headers[2048];
    char last_body[8192];
    size_t last_body_len;
} g_server;

static void mock_init(void);
static void mock_shutdown(void);
static void mock_enqueue(int status, const char* extra_headers, const uint8_t* body, size_t body_len);
static void mock_enqueue_close(void);

static void* server_thread(void* arg);

/* Read a full HTTP request from a socket. Returns 0 on success, -1 on error. */
static int read_request(int fd) {
    char buf[16384];
    size_t total = 0;
    ssize_t n;
    int header_end = -1;

    /* Zero the capture state so stale bytes from a prior request can't
     * leak through strstr() / strcmp() assertions when the current body
     * is shorter than the previous one. */
    memset(g_server.last_method, 0, sizeof(g_server.last_method));
    memset(g_server.last_path, 0, sizeof(g_server.last_path));
    memset(g_server.last_headers, 0, sizeof(g_server.last_headers));
    memset(g_server.last_body, 0, sizeof(g_server.last_body));
    g_server.last_body_len = 0;

    while (total < sizeof(buf) - 1) {
        n = recv(fd, buf + total, sizeof(buf) - 1 - total, 0);
        if (n <= 0) return -1;
        total += (size_t)n;
        buf[total] = 0;

        char* end = strstr(buf, "\r\n\r\n");
        if (end) {
            header_end = (int)(end - buf) + 4;
            break;
        }
    }
    if (header_end < 0) return -1;

    /* Parse request line */
    char* line_end = strstr(buf, "\r\n");
    if (!line_end) return -1;
    *line_end = 0;

    /* METHOD PATH HTTP/1.1 */
    char method[16] = {0};
    char path[1024] = {0};
    if (sscanf(buf, "%15s %1023s", method, path) != 2) return -1;
    snprintf(g_server.last_method, sizeof(g_server.last_method), "%s", method);
    snprintf(g_server.last_path, sizeof(g_server.last_path), "%s", path);

    /* Headers */
    char* hdr = line_end + 2;
    char* hdr_end = buf + header_end - 4;
    if (hdr_end > hdr) {
        size_t hl = (size_t)(hdr_end - hdr);
        if (hl >= sizeof(g_server.last_headers)) hl = sizeof(g_server.last_headers) - 1;
        memcpy(g_server.last_headers, hdr, hl);
        g_server.last_headers[hl] = 0;
    } else {
        g_server.last_headers[0] = 0;
    }

    /* Determine content length */
    size_t content_length = 0;
    char* lower = strdup(g_server.last_headers);
    if (lower) {
        for (char* p = lower; *p; p++) {
            if (*p >= 'A' && *p <= 'Z') *p += 'a' - 'A';
        }
        char* cl = strstr(lower, "content-length:");
        if (cl) {
            cl += strlen("content-length:");
            while (*cl == ' ') cl++;
            content_length = (size_t)strtoul(cl, NULL, 10);
        }
        free(lower);
    }

    /* Read remaining body */
    size_t body_have = total - (size_t)header_end;
    size_t body_copy = body_have;
    if (body_copy > sizeof(g_server.last_body) - 1) body_copy = sizeof(g_server.last_body) - 1;
    memcpy(g_server.last_body, buf + header_end, body_copy);
    g_server.last_body_len = body_copy;

    while (body_have < content_length) {
        n = recv(fd, buf, sizeof(buf), 0);
        if (n <= 0) break;
        body_have += (size_t)n;
        if (g_server.last_body_len < sizeof(g_server.last_body) - 1) {
            size_t room = sizeof(g_server.last_body) - 1 - g_server.last_body_len;
            size_t copy = ((size_t)n < room) ? (size_t)n : room;
            memcpy(g_server.last_body + g_server.last_body_len, buf, copy);
            g_server.last_body_len += copy;
        }
    }
    g_server.last_body[g_server.last_body_len] = 0;

    return 0;
}

static MockResponse pop_response(void) {
    MockResponse r;
    pthread_mutex_lock(&g_server.lock);
    while (g_server.count == 0 && g_server.running) {
        pthread_cond_wait(&g_server.cond, &g_server.lock);
    }
    r = g_server.queue[g_server.head];
    g_server.head = (g_server.head + 1) % MAX_QUEUED;
    g_server.count--;
    pthread_mutex_unlock(&g_server.lock);
    return r;
}

static void send_all(int fd, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*)data;
    size_t off = 0;
    while (off < len) {
        ssize_t n = send(fd, p + off, len - off, 0);
        if (n <= 0) return;
        off += (size_t)n;
    }
}

static const char* status_text(int code) {
    switch (code) {
        case 200: return "OK";
        case 400: return "Bad Request";
        case 401: return "Unauthorized";
        case 402: return "Payment Required";
        case 404: return "Not Found";
        case 422: return "Unprocessable Entity";
        case 429: return "Too Many Requests";
        case 500: return "Internal Server Error";
        case 503: return "Service Unavailable";
        default:  return "Status";
    }
}

static void* server_thread(void* arg) {
    (void)arg;
    while (g_server.running) {
        struct sockaddr_in cli;
        socklen_t cli_len = sizeof(cli);
        int fd = accept(g_server.listen_fd, (struct sockaddr*)&cli, &cli_len);
        if (fd < 0) {
            if (!g_server.running) break;
            continue;
        }

        if (read_request(fd) != 0) {
            close(fd);
            continue;
        }

        MockResponse resp = pop_response();
        if (resp.close_immediately) {
            close(fd);
            if (resp.body) free(resp.body);
            continue;
        }

        char header[2048];
        int hl = snprintf(header, sizeof(header),
            "HTTP/1.1 %d %s\r\n"
            "Content-Length: %zu\r\n"
            "Connection: close\r\n"
            "%s"
            "\r\n",
            resp.status, status_text(resp.status), resp.body_len,
            resp.headers);
        send_all(fd, header, (size_t)hl);
        if (resp.body && resp.body_len > 0) {
            send_all(fd, resp.body, resp.body_len);
        }
        if (resp.body) free(resp.body);
        close(fd);
    }
    return NULL;
}

static void mock_init(void) {
    memset(&g_server, 0, sizeof(g_server));
    pthread_mutex_init(&g_server.lock, NULL);
    pthread_cond_init(&g_server.cond, NULL);

    g_server.listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (g_server.listen_fd < 0) { perror("socket"); exit(1); }

    int yes = 1;
    setsockopt(g_server.listen_fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;
    if (bind(g_server.listen_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        perror("bind"); exit(1);
    }

    socklen_t addr_len = sizeof(addr);
    if (getsockname(g_server.listen_fd, (struct sockaddr*)&addr, &addr_len) < 0) {
        perror("getsockname"); exit(1);
    }
    g_server.port = ntohs(addr.sin_port);

    if (listen(g_server.listen_fd, 8) < 0) { perror("listen"); exit(1); }

    g_server.running = 1;
    pthread_create(&g_server.thread, NULL, server_thread, NULL);
}

static void mock_shutdown(void) {
    g_server.running = 0;
    /* Wake any blocked accept by connecting to ourselves */
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd >= 0) {
        struct sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        addr.sin_port = htons(g_server.port);
        connect(fd, (struct sockaddr*)&addr, sizeof(addr));
        close(fd);
    }
    /* Wake the consumer too in case it's waiting on the queue */
    pthread_mutex_lock(&g_server.lock);
    pthread_cond_broadcast(&g_server.cond);
    pthread_mutex_unlock(&g_server.lock);

    pthread_join(g_server.thread, NULL);
    close(g_server.listen_fd);
    pthread_mutex_destroy(&g_server.lock);
    pthread_cond_destroy(&g_server.cond);
}

static void mock_enqueue(int status, const char* extra_headers, const uint8_t* body, size_t body_len) {
    pthread_mutex_lock(&g_server.lock);
    MockResponse* r = &g_server.queue[g_server.tail];
    memset(r, 0, sizeof(*r));
    r->status = status;
    if (extra_headers) {
        snprintf(r->headers, sizeof(r->headers), "%s", extra_headers);
    }
    if (body && body_len > 0) {
        r->body = (uint8_t*)malloc(body_len);
        memcpy(r->body, body, body_len);
        r->body_len = body_len;
    }
    g_server.tail = (g_server.tail + 1) % MAX_QUEUED;
    g_server.count++;
    pthread_cond_signal(&g_server.cond);
    pthread_mutex_unlock(&g_server.lock);
}

static void mock_enqueue_close(void) {
    pthread_mutex_lock(&g_server.lock);
    MockResponse* r = &g_server.queue[g_server.tail];
    memset(r, 0, sizeof(*r));
    r->close_immediately = 1;
    g_server.tail = (g_server.tail + 1) % MAX_QUEUED;
    g_server.count++;
    pthread_cond_signal(&g_server.cond);
    pthread_mutex_unlock(&g_server.lock);
}

static void mock_enqueue_text(int status, const char* extra_headers, const char* body) {
    mock_enqueue(status, extra_headers, (const uint8_t*)body, body ? strlen(body) : 0);
}

/* ============================================
 * Helpers
 * ============================================ */

static char g_host[64];

static TypecastClient* new_client(void) {
    snprintf(g_host, sizeof(g_host), "http://127.0.0.1:%d", g_server.port);
    return typecast_client_create_with_host("test_api_key", g_host);
}

/* ============================================
 * Pure utility tests (no HTTP)
 * ============================================ */

static void test_version_string(void) {
    const char* v = typecast_version();
    ASSERT_NOT_NULL(v);
    /* Match TYPECAST_VERSION macro from header */
    ASSERT_STREQ(v, TYPECAST_VERSION);
}

static void test_model_string_round_trip(void) {
    ASSERT_STREQ(typecast_model_to_string(TYPECAST_MODEL_SSFM_V21), "ssfm-v21");
    ASSERT_STREQ(typecast_model_to_string(TYPECAST_MODEL_SSFM_V30), "ssfm-v30");
    ASSERT_STREQ(typecast_model_to_string((TypecastModel)-1), "unknown");
    ASSERT_STREQ(typecast_model_to_string((TypecastModel)999), "unknown");
    ASSERT_EQ(typecast_model_from_string("ssfm-v21"), TYPECAST_MODEL_SSFM_V21);
    ASSERT_EQ(typecast_model_from_string("ssfm-v30"), TYPECAST_MODEL_SSFM_V30);
    ASSERT_EQ(typecast_model_from_string("nope"), -1);
    ASSERT_EQ(typecast_model_from_string(NULL), -1);
}

static void test_emotion_string(void) {
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_NORMAL), "normal");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_HAPPY), "happy");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_SAD), "sad");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_ANGRY), "angry");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_WHISPER), "whisper");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_TONEUP), "toneup");
    ASSERT_STREQ(typecast_emotion_to_string(TYPECAST_EMOTION_TONEDOWN), "tonedown");
    ASSERT_STREQ(typecast_emotion_to_string((TypecastEmotionPreset)-1), "normal");
    ASSERT_STREQ(typecast_emotion_to_string((TypecastEmotionPreset)999), "normal");
}

static void test_audio_format_string(void) {
    ASSERT_STREQ(typecast_audio_format_to_string(TYPECAST_AUDIO_FORMAT_WAV), "wav");
    ASSERT_STREQ(typecast_audio_format_to_string(TYPECAST_AUDIO_FORMAT_MP3), "mp3");
    ASSERT_STREQ(typecast_audio_format_to_string((TypecastAudioFormat)-1), "wav");
    ASSERT_STREQ(typecast_audio_format_to_string((TypecastAudioFormat)999), "wav");
}

static void test_error_messages_all(void) {
    ASSERT_STREQ(typecast_error_message(TYPECAST_OK), "Success");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_INVALID_PARAM), "Invalid parameter");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_OUT_OF_MEMORY), "Out of memory");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_CURL_INIT), "Failed to initialize CURL");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_NETWORK), "Network error");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_JSON_PARSE), "JSON parse error");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_BAD_REQUEST), "Bad request");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_UNAUTHORIZED), "Unauthorized");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_PAYMENT_REQUIRED), "Payment required");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_NOT_FOUND), "Not found");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_UNPROCESSABLE_ENTITY), "Unprocessable entity");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_RATE_LIMIT), "Rate limit exceeded");
    ASSERT_STREQ(typecast_error_message(TYPECAST_ERROR_INTERNAL_SERVER), "Internal server error");
    ASSERT_STREQ(typecast_error_message((TypecastErrorCode)12345), "Unknown error");
}

static void test_default_macros(void) {
    TypecastOutput o = TYPECAST_OUTPUT_DEFAULT();
    ASSERT_EQ(o.volume, 100);
    ASSERT_EQ(o.audio_pitch, 0);
    ASSERT(o.audio_tempo == 1.0f);
    ASSERT_EQ(o.audio_format, TYPECAST_AUDIO_FORMAT_WAV);

    TypecastPrompt p = TYPECAST_PROMPT_DEFAULT();
    ASSERT_EQ(p.emotion_type, TYPECAST_EMOTION_TYPE_NONE);
    ASSERT_EQ(p.emotion_preset, TYPECAST_EMOTION_NORMAL);
    ASSERT(p.emotion_intensity == 1.0f);
    ASSERT_NULL(p.previous_text);
    ASSERT_NULL(p.next_text);
}

/* ============================================
 * Client lifecycle
 * ============================================ */

static void test_client_create_null(void) {
    ASSERT_NULL(typecast_client_create(NULL));
    ASSERT_NULL(typecast_client_create(""));
}

static void test_client_create_default_host(void) {
    TypecastClient* c = typecast_client_create("key");
    ASSERT_NOT_NULL(c);
    typecast_client_destroy(c);
}

static void test_client_create_custom_host(void) {
    TypecastClient* c = typecast_client_create_with_host("key", "https://example.org");
    ASSERT_NOT_NULL(c);
    typecast_client_destroy(c);
}

static void test_client_destroy_null(void) {
    typecast_client_destroy(NULL);
}

static void test_client_get_error_null(void) {
    ASSERT_NULL(typecast_client_get_error(NULL));
}

static void test_voice_free_null(void) {
    typecast_voice_free(NULL);
    typecast_voices_response_free(NULL);
    typecast_tts_response_free(NULL);
}

/* ============================================
 * TTS validation
 * ============================================ */

static void test_tts_null_client(void) {
    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;
    ASSERT_NULL(typecast_text_to_speech(NULL, &req));
}

static void test_tts_null_request(void) {
    TypecastClient* c = new_client();
    ASSERT_NOT_NULL(c);
    ASSERT_NULL(typecast_text_to_speech(c, NULL));
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_NOT_NULL(e);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_tts_missing_text(void) {
    TypecastClient* c = new_client();
    TypecastTTSRequest req = {0};
    req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;
    ASSERT_NULL(typecast_text_to_speech(c, &req));
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_tts_missing_voice_id(void) {
    TypecastClient* c = new_client();
    TypecastTTSRequest req = {0};
    req.text = "hi"; req.model = TYPECAST_MODEL_SSFM_V30;
    ASSERT_NULL(typecast_text_to_speech(c, &req));
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

/* ============================================
 * TTS happy paths
 * ============================================ */

static void test_tts_basic_wav(void) {
    TypecastClient* c = new_client();

    /* 8 KiB body to force write_callback growth past initial 4096 capacity. */
    size_t body_len = 8192;
    uint8_t* body = (uint8_t*)malloc(body_len);
    for (size_t i = 0; i < body_len; i++) body[i] = (uint8_t)(i & 0xff);

    mock_enqueue(200, "X-Audio-Duration: 1.5\r\n", body, body_len);
    free(body);

    TypecastTTSRequest req = {0};
    req.text = "hello world"; req.voice_id = "tc_xx"; req.model = TYPECAST_MODEL_SSFM_V21;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    ASSERT_EQ(r->audio_size, body_len);
    ASSERT(r->duration > 1.4f && r->duration < 1.6f);
    ASSERT_EQ(r->format, TYPECAST_AUDIO_FORMAT_WAV);

    /* Verify request body and headers were sent */
    ASSERT(strstr(g_server.last_path, "/v1/text-to-speech") != NULL);
    ASSERT(strstr(g_server.last_body, "\"text\":\"hello world\"") != NULL);
    ASSERT(strstr(g_server.last_body, "\"voice_id\":\"tc_xx\"") != NULL);
    ASSERT(strstr(g_server.last_body, "\"model\":\"ssfm-v21\"") != NULL);

    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_tts_with_output_volume_mp3(void) {
    TypecastClient* c = new_client();

    /* Use lowercase header to hit the strstr branch */
    mock_enqueue_text(200, "x-audio-duration: 2.25\r\n", "MP3DATA");

    TypecastOutput out = TYPECAST_OUTPUT_DEFAULT();
    out.volume = 80; out.audio_pitch = 3; out.audio_tempo = 1.2f;
    out.audio_format = TYPECAST_AUDIO_FORMAT_MP3;

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "tc_y"; req.model = TYPECAST_MODEL_SSFM_V30;
    req.language = "eng"; req.output = &out; req.seed = 42;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    ASSERT_EQ(r->format, TYPECAST_AUDIO_FORMAT_MP3);
    ASSERT(r->duration > 2.2f && r->duration < 2.3f);

    ASSERT(strstr(g_server.last_body, "\"language\":\"eng\"") != NULL);
    ASSERT(strstr(g_server.last_body, "\"volume\":80") != NULL);
    ASSERT(strstr(g_server.last_body, "\"audio_format\":\"mp3\"") != NULL);
    ASSERT(strstr(g_server.last_body, "\"seed\":42") != NULL);

    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_tts_with_output_lufs(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "WAV");

    TypecastOutput out = TYPECAST_OUTPUT_DEFAULT();
    out.use_target_lufs = 1; out.target_lufs = -16.0f;

    TypecastTTSRequest req = {0};
    req.text = "x"; req.voice_id = "y"; req.model = TYPECAST_MODEL_SSFM_V30;
    req.output = &out;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    ASSERT_EQ(r->duration, 0); /* No header */
    ASSERT(strstr(g_server.last_body, "\"target_lufs\":-16") != NULL);

    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_tts_prompt_preset(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "AUDIO");

    TypecastPrompt p = TYPECAST_PROMPT_DEFAULT();
    p.emotion_type = TYPECAST_EMOTION_TYPE_PRESET;
    p.emotion_preset = TYPECAST_EMOTION_HAPPY;
    p.emotion_intensity = 1.5f;

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;
    req.prompt = &p;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_body, "\"emotion_type\":\"preset\"") != NULL);
    ASSERT(strstr(g_server.last_body, "\"emotion_preset\":\"happy\"") != NULL);
    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_tts_prompt_smart(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "AUDIO");

    TypecastPrompt p = TYPECAST_PROMPT_DEFAULT();
    p.emotion_type = TYPECAST_EMOTION_TYPE_SMART;
    p.previous_text = "Once upon a time";
    p.next_text = "And they lived happily ever after.";

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;
    req.prompt = &p;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_body, "\"emotion_type\":\"smart\"") != NULL);
    ASSERT(strstr(g_server.last_body, "\"previous_text\":") != NULL);
    ASSERT(strstr(g_server.last_body, "\"next_text\":") != NULL);
    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_tts_prompt_smart_no_context(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "AUDIO");

    /* Explicit zero-init of every field, then promote to smart. We don't
     * use TYPECAST_PROMPT_DEFAULT() here because the compound-literal
     * macro has historically tripped strict-aliasing edge cases on some
     * gcc versions, leaving previous_text/next_text non-null. */
    TypecastPrompt p;
    memset(&p, 0, sizeof(p));
    p.emotion_type = TYPECAST_EMOTION_TYPE_SMART;
    p.emotion_preset = TYPECAST_EMOTION_NORMAL;
    p.emotion_intensity = 1.0f;
    /* previous_text and next_text remain NULL */

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;
    req.prompt = &p;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    /* Use length-bounded checks so we cannot read past the actual body
     * into the rest of the 8 KB g_server.last_body buffer. */
    ASSERT(g_server.last_body_len > 0);
    {
        char body[8192];
        size_t n = g_server.last_body_len < sizeof(body) - 1
                       ? g_server.last_body_len
                       : sizeof(body) - 1;
        memcpy(body, g_server.last_body, n);
        body[n] = 0;
        ASSERT(strstr(body, "\"previous_text\"") == NULL);
        ASSERT(strstr(body, "\"next_text\"") == NULL);
    }
    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_tts_prompt_none_basic(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "AUDIO");

    TypecastPrompt p = TYPECAST_PROMPT_DEFAULT();
    /* emotion_type stays NONE -> basic prompt branch */

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V21;
    req.prompt = &p;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_body, "\"emotion_preset\":\"normal\"") != NULL);
    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

/* ============================================
 * TTS error paths (HTTP status codes)
 * ============================================ */

static void check_tts_error(int http_status, TypecastErrorCode want, const char* detail_body) {
    TypecastClient* c = new_client();
    mock_enqueue_text(http_status, NULL, detail_body);

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    if (r) { typecast_tts_response_free(r); }
    ASSERT_NULL(r);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_NOT_NULL(e);
    ASSERT_EQ(e->code, want);

    typecast_client_destroy(c);
}

static void test_tts_400(void) { check_tts_error(400, TYPECAST_ERROR_BAD_REQUEST, "{\"detail\":\"bad input\"}"); }
static void test_tts_401(void) { check_tts_error(401, TYPECAST_ERROR_UNAUTHORIZED, "{\"detail\":\"no key\"}"); }
static void test_tts_402(void) { check_tts_error(402, TYPECAST_ERROR_PAYMENT_REQUIRED, "{}"); }
static void test_tts_404(void) { check_tts_error(404, TYPECAST_ERROR_NOT_FOUND, ""); }
static void test_tts_422(void) { check_tts_error(422, TYPECAST_ERROR_UNPROCESSABLE_ENTITY, "not-json"); }
static void test_tts_429(void) { check_tts_error(429, TYPECAST_ERROR_RATE_LIMIT, "{\"detail\":42}"); }
static void test_tts_500(void) { check_tts_error(500, TYPECAST_ERROR_INTERNAL_SERVER, "{\"detail\":\"oops\"}"); }
static void test_tts_503(void) { check_tts_error(503, TYPECAST_ERROR_NETWORK, NULL); }

static void test_tts_curl_network_error(void) {
    /* Bad host = curl_easy_perform fails. */
    TypecastClient* c = typecast_client_create_with_host(
        "key", "http://127.0.0.1:1");  /* port 1 should refuse */
    ASSERT_NOT_NULL(c);

    TypecastTTSRequest req = {0};
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NULL(r);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_NETWORK);
    typecast_client_destroy(c);
}

/* ============================================
 * Voices V2 (list)
 * ============================================ */

static const char* VOICES_JSON =
"[{"
  "\"voice_id\":\"tc_aaa\","
  "\"voice_name\":\"Alice\","
  "\"gender\":\"female\","
  "\"age\":\"young_adult\","
  "\"models\":["
    "{\"version\":\"ssfm-v30\",\"emotions\":[\"normal\",\"happy\",\"sad\"]},"
    "{\"version\":\"ssfm-v21\",\"emotions\":[\"normal\"]}"
  "],"
  "\"use_cases\":[\"narration\",\"dialogue\"]"
"},"
"{"
  "\"voice_id\":\"tc_bbb\","
  "\"voice_name\":\"Bob\","
  "\"gender\":\"male\","
  "\"age\":\"middle_age\","
  "\"models\":[{\"version\":\"unknown\",\"emotions\":[]}]"
"}]";

static void test_voices_full_filter(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, VOICES_JSON);

    TypecastModel model = TYPECAST_MODEL_SSFM_V30;
    TypecastGender gender = TYPECAST_GENDER_FEMALE;
    TypecastAge age = TYPECAST_AGE_YOUNG_ADULT;
    TypecastVoicesFilter f = {0};
    f.model = &model; f.gender = &gender; f.age = &age; f.use_cases = "narration";

    TypecastVoicesResponse* r = typecast_get_voices(c, &f);
    ASSERT_NOT_NULL(r);
    ASSERT_EQ(r->count, 2);
    ASSERT_STREQ(r->voices[0].voice_id, "tc_aaa");
    ASSERT_STREQ(r->voices[0].voice_name, "Alice");
    ASSERT_EQ(r->voices[0].gender, TYPECAST_GENDER_FEMALE);
    ASSERT_EQ(r->voices[0].age, TYPECAST_AGE_YOUNG_ADULT);
    ASSERT_EQ(r->voices[0].models_count, 2);
    ASSERT_EQ(r->voices[0].models[0].version, TYPECAST_MODEL_SSFM_V30);
    ASSERT_EQ(r->voices[0].models[0].emotions_count, 3);
    ASSERT_STREQ(r->voices[0].models[0].emotions[1], "happy");
    ASSERT_EQ(r->voices[0].use_cases_count, 2);
    ASSERT_STREQ(r->voices[0].use_cases[0], "narration");

    /* URL must contain all params */
    ASSERT(strstr(g_server.last_path, "model=ssfm-v30") != NULL);
    ASSERT(strstr(g_server.last_path, "gender=female") != NULL);
    ASSERT(strstr(g_server.last_path, "age=young_adult") != NULL);
    ASSERT(strstr(g_server.last_path, "use_cases=narration") != NULL);

    typecast_voices_response_free(r);
    typecast_client_destroy(c);
}

static void test_voices_no_filter(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "[]");

    TypecastVoicesResponse* r = typecast_get_voices(c, NULL);
    ASSERT_NOT_NULL(r);
    ASSERT_EQ(r->count, 0);
    /* No query string */
    ASSERT(strstr(g_server.last_path, "?") == NULL);
    typecast_voices_response_free(r);
    typecast_client_destroy(c);
}

static void test_voices_null_client(void) {
    ASSERT_NULL(typecast_get_voices(NULL, NULL));
}

static void test_voices_http_error(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(401, NULL, "{}");
    TypecastVoicesResponse* r = typecast_get_voices(c, NULL);
    ASSERT_NULL(r);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_UNAUTHORIZED);
    typecast_client_destroy(c);
}

static void test_voices_invalid_json(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "not-json[");
    TypecastVoicesResponse* r = typecast_get_voices(c, NULL);
    ASSERT_NULL(r);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_JSON_PARSE);
    typecast_client_destroy(c);
}

static void test_voices_not_array(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "{\"oops\":1}");
    TypecastVoicesResponse* r = typecast_get_voices(c, NULL);
    ASSERT_NULL(r);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_JSON_PARSE);
    typecast_client_destroy(c);
}

static void test_voices_network_error(void) {
    TypecastClient* c = typecast_client_create_with_host("key", "http://127.0.0.1:1");
    ASSERT_NOT_NULL(c);
    TypecastVoicesResponse* r = typecast_get_voices(c, NULL);
    ASSERT_NULL(r);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_NETWORK);
    typecast_client_destroy(c);
}

/* Filters with only one parameter at a time exercise the has_params=0 branch
 * for the second/third/fourth filter. */
static void test_voices_filter_single_model(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "[]");
    TypecastModel m = TYPECAST_MODEL_SSFM_V21;
    TypecastVoicesFilter f = {0};
    f.model = &m;
    TypecastVoicesResponse* r = typecast_get_voices(c, &f);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_path, "?model=ssfm-v21") != NULL);
    typecast_voices_response_free(r);
    typecast_client_destroy(c);
}

static void test_voices_filter_single_gender(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "[]");
    TypecastGender g = TYPECAST_GENDER_MALE;
    TypecastVoicesFilter f = {0}; f.gender = &g;
    TypecastVoicesResponse* r = typecast_get_voices(c, &f);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_path, "?gender=male") != NULL);
    typecast_voices_response_free(r);
    typecast_client_destroy(c);
}

static void test_voices_filter_single_age(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "[]");
    TypecastAge a = TYPECAST_AGE_ELDER;
    TypecastVoicesFilter f = {0}; f.age = &a;
    TypecastVoicesResponse* r = typecast_get_voices(c, &f);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_path, "?age=elder") != NULL);
    typecast_voices_response_free(r);
    typecast_client_destroy(c);
}

static void test_voices_filter_single_use_case(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "[]");
    TypecastVoicesFilter f = {0}; f.use_cases = "audiobook";
    TypecastVoicesResponse* r = typecast_get_voices(c, &f);
    ASSERT_NOT_NULL(r);
    ASSERT(strstr(g_server.last_path, "?use_cases=audiobook") != NULL);
    typecast_voices_response_free(r);
    typecast_client_destroy(c);
}

/* ============================================
 * get_voice (single voice)
 * ============================================ */

static const char* SINGLE_VOICE_JSON =
"{"
  "\"voice_id\":\"tc_one\","
  "\"voice_name\":\"Solo\","
  "\"gender\":\"unknown\","
  "\"age\":\"child\","
  "\"models\":[{\"version\":\"ssfm-v21\",\"emotions\":[\"normal\"]}],"
  "\"use_cases\":[\"narration\"]"
"}";

static void test_get_voice_happy(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, SINGLE_VOICE_JSON);
    TypecastVoice* v = typecast_get_voice(c, "tc_one");
    ASSERT_NOT_NULL(v);
    ASSERT_STREQ(v->voice_id, "tc_one");
    ASSERT_EQ(v->gender, TYPECAST_GENDER_UNKNOWN);
    ASSERT_EQ(v->age, TYPECAST_AGE_CHILD);
    ASSERT(strstr(g_server.last_path, "/v2/voices/tc_one") != NULL);
    typecast_voice_free(v);
    typecast_client_destroy(c);
}

static void test_get_voice_age_variants(void) {
    /* Drives parse_age branches: teenager + middle_age + young_adult done above */
    const char* json =
    "{\"voice_id\":\"tc_t\",\"voice_name\":\"T\","
    "\"gender\":\"female\",\"age\":\"teenager\",\"models\":[],\"use_cases\":[]}";
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, json);
    TypecastVoice* v = typecast_get_voice(c, "tc_t");
    ASSERT_NOT_NULL(v);
    ASSERT_EQ(v->age, TYPECAST_AGE_TEENAGER);
    typecast_voice_free(v);
    typecast_client_destroy(c);

    c = new_client();
    mock_enqueue_text(200, NULL,
        "{\"voice_id\":\"tc_m\",\"voice_name\":\"M\","
        "\"gender\":\"male\",\"age\":\"middle_age\",\"models\":[],\"use_cases\":[]}");
    v = typecast_get_voice(c, "tc_m");
    ASSERT_NOT_NULL(v);
    ASSERT_EQ(v->age, TYPECAST_AGE_MIDDLE_AGE);
    typecast_voice_free(v);
    typecast_client_destroy(c);

    c = new_client();
    mock_enqueue_text(200, NULL,
        "{\"voice_id\":\"tc_u\",\"voice_name\":\"U\","
        "\"gender\":\"male\",\"age\":\"unknown\",\"models\":[],\"use_cases\":[]}");
    v = typecast_get_voice(c, "tc_u");
    ASSERT_NOT_NULL(v);
    ASSERT_EQ(v->age, TYPECAST_AGE_UNKNOWN);
    typecast_voice_free(v);
    typecast_client_destroy(c);
}

static void test_get_voice_null_args(void) {
    TypecastClient* c = new_client();
    ASSERT_NULL(typecast_get_voice(c, NULL));
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);

    ASSERT_NULL(typecast_get_voice(NULL, "tc_x"));
}

static void test_get_voice_http_error(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(404, NULL, "{}");
    TypecastVoice* v = typecast_get_voice(c, "tc_x");
    ASSERT_NULL(v);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_NOT_FOUND);
    typecast_client_destroy(c);
}

static void test_get_voice_invalid_json(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "<<not-json>>");
    TypecastVoice* v = typecast_get_voice(c, "tc_x");
    ASSERT_NULL(v);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_JSON_PARSE);
    typecast_client_destroy(c);
}

static void test_get_voice_not_object(void) {
    /* parse_voice_json returns NULL for arrays -> sets JSON_PARSE error */
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "[]");
    TypecastVoice* v = typecast_get_voice(c, "tc_x");
    ASSERT_NULL(v);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_JSON_PARSE);
    typecast_client_destroy(c);
}

static void test_get_voice_network_error(void) {
    TypecastClient* c = typecast_client_create_with_host("key", "http://127.0.0.1:1");
    ASSERT_NOT_NULL(c);
    TypecastVoice* v = typecast_get_voice(c, "tc_x");
    ASSERT_NULL(v);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_NETWORK);
    typecast_client_destroy(c);
}

/* ============================================
 * Edge cases for parse_voice_json (sparse fields)
 * ============================================ */

/* Reuses a client across failing and successful calls to drive the
 * "previous error message free" branches in set_error and clear_error. */
static void test_error_reuse_and_clear(void) {
    TypecastClient* c = new_client();

    /* First failing call - sets an error message */
    TypecastTTSRequest req = {0};
    ASSERT_NULL(typecast_text_to_speech(c, NULL));
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    ASSERT_NOT_NULL(e->message);

    /* Second failing call - set_error must free the prior message */
    ASSERT_NULL(typecast_text_to_speech(c, NULL));
    e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    ASSERT_NOT_NULL(e->message);

    /* Now trigger a success path so clear_error frees the prior message */
    mock_enqueue_text(200, NULL, "WAV");
    req.text = "hi"; req.voice_id = "v"; req.model = TYPECAST_MODEL_SSFM_V30;
    TypecastTTSResponse* r = typecast_text_to_speech(c, &req);
    ASSERT_NOT_NULL(r);
    e = typecast_client_get_error(c);
    ASSERT_EQ(e->code, TYPECAST_OK);
    ASSERT_NULL(e->message);
    typecast_tts_response_free(r);
    typecast_client_destroy(c);
}

static void test_voice_sparse(void) {
    /* Empty object - all fields default */
    TypecastClient* c = new_client();
    mock_enqueue_text(200, NULL, "{}");
    TypecastVoice* v = typecast_get_voice(c, "tc_x");
    ASSERT_NOT_NULL(v);
    ASSERT_NULL(v->voice_id);
    ASSERT_EQ(v->gender, TYPECAST_GENDER_UNKNOWN);
    ASSERT_EQ(v->age, TYPECAST_AGE_UNKNOWN);
    typecast_voice_free(v);
    typecast_client_destroy(c);
}

/* ============================================
 * Main
 * ============================================ */

int main(void) {
    printf("===========================================\n");
    printf("Typecast C SDK Mock Coverage Tests\n");
    printf("===========================================\n\n");

    mock_init();

    /* Pure utilities */
    RUN(version_string);
    RUN(model_string_round_trip);
    RUN(emotion_string);
    RUN(audio_format_string);
    RUN(error_messages_all);
    RUN(default_macros);

    /* Client lifecycle */
    RUN(client_create_null);
    RUN(client_create_default_host);
    RUN(client_create_custom_host);
    RUN(client_destroy_null);
    RUN(client_get_error_null);
    RUN(voice_free_null);

    /* TTS validation */
    RUN(tts_null_client);
    RUN(tts_null_request);
    RUN(tts_missing_text);
    RUN(tts_missing_voice_id);

    /* TTS happy paths */
    RUN(tts_basic_wav);
    RUN(tts_with_output_volume_mp3);
    RUN(tts_with_output_lufs);
    RUN(tts_prompt_preset);
    RUN(tts_prompt_smart);
    RUN(tts_prompt_smart_no_context);
    RUN(tts_prompt_none_basic);

    /* TTS HTTP errors */
    RUN(tts_400);
    RUN(tts_401);
    RUN(tts_402);
    RUN(tts_404);
    RUN(tts_422);
    RUN(tts_429);
    RUN(tts_500);
    RUN(tts_503);
    RUN(tts_curl_network_error);

    /* Voices V2 list */
    RUN(voices_full_filter);
    RUN(voices_no_filter);
    RUN(voices_null_client);
    RUN(voices_http_error);
    RUN(voices_invalid_json);
    RUN(voices_not_array);
    RUN(voices_network_error);
    RUN(voices_filter_single_model);
    RUN(voices_filter_single_gender);
    RUN(voices_filter_single_age);
    RUN(voices_filter_single_use_case);

    /* get_voice */
    RUN(get_voice_happy);
    RUN(get_voice_age_variants);
    RUN(get_voice_null_args);
    RUN(get_voice_http_error);
    RUN(get_voice_invalid_json);
    RUN(get_voice_not_object);
    RUN(get_voice_network_error);
    RUN(error_reuse_and_clear);
    RUN(voice_sparse);

    mock_shutdown();

    printf("\n===========================================\n");
    printf("Tests run: %d  passed: %d  failed: %d\n", tests_run, tests_passed, tests_failed);
    printf("===========================================\n");
    return tests_failed > 0 ? 1 : 0;
}
