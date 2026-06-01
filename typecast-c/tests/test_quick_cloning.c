/**
 * Typecast C SDK - Instant cloning tests
 *
 * Covers typecast_clone_voice() and typecast_delete_voice() using the same
 * in-process mock HTTP server pattern as test_mock.c.
 *
 * Run: ./test_quick_cloning
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include "typecast.h"

/* ============================================
 * Test bookkeeping (mirrors test_mock.c)
 * ============================================ */

static int tests_run    = 0;
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
 * Minimal mock HTTP server
 * (same design as test_mock.c, self-contained here)
 * ============================================ */

#define MAX_QUEUED 16

typedef struct {
    int        status;
    char       headers[512];
    uint8_t*   body;
    size_t     body_len;
    int        close_immediately;
} MockResponse;

static struct {
    pthread_mutex_t lock;
    pthread_cond_t  cond;
    MockResponse    queue[MAX_QUEUED];
    int             head, tail, count;

    int             listen_fd;
    int             port;
    pthread_t       thread;
    int             running;

    /* captured for assertions */
    char  last_method[16];
    char  last_path[512];
    char  last_headers[2048];
    char  last_body[8192];
    size_t last_body_len;
} g_srv;

/* ---- helpers ---- */

static void send_all_s(int fd, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*)data;
    size_t off = 0;
    while (off < len) {
        ssize_t n = send(fd, p + off, len - off, 0);
        if (n <= 0) return;
        off += (size_t)n;
    }
}

static const char* status_txt(int code) {
    switch (code) {
        case 200: return "OK";
        case 201: return "Created";
        case 204: return "No Content";
        case 400: return "Bad Request";
        case 401: return "Unauthorized";
        case 404: return "Not Found";
        case 422: return "Unprocessable Entity";
        case 500: return "Internal Server Error";
        default:  return "Status";
    }
}

static int read_req(int fd) {
    char buf[65536];
    size_t total = 0;
    ssize_t n;
    int header_end = -1;

    memset(g_srv.last_method,  0, sizeof(g_srv.last_method));
    memset(g_srv.last_path,    0, sizeof(g_srv.last_path));
    memset(g_srv.last_headers, 0, sizeof(g_srv.last_headers));
    memset(g_srv.last_body,    0, sizeof(g_srv.last_body));
    g_srv.last_body_len = 0;

    while (total < sizeof(buf) - 1) {
        n = recv(fd, buf + total, sizeof(buf) - 1 - total, 0);
        if (n <= 0) return -1;
        total += (size_t)n;
        buf[total] = 0;
        char* end = strstr(buf, "\r\n\r\n");
        if (end) { header_end = (int)(end - buf) + 4; break; }
    }
    if (header_end < 0) return -1;

    char* line_end = strstr(buf, "\r\n");
    if (!line_end) return -1;
    *line_end = 0;
    sscanf(buf, "%15s %511s", g_srv.last_method, g_srv.last_path);

    char* hdr = line_end + 2;
    char* hdr_end = buf + header_end - 4;
    if (hdr_end > hdr) {
        size_t hl = (size_t)(hdr_end - hdr);
        if (hl >= sizeof(g_srv.last_headers)) hl = sizeof(g_srv.last_headers) - 1;
        memcpy(g_srv.last_headers, hdr, hl);
        g_srv.last_headers[hl] = 0;
    }

    /* determine content-length */
    size_t content_length = 0;
    char* lower = strdup(g_srv.last_headers);
    if (lower) {
        for (char* p = lower; *p; p++) if (*p >= 'A' && *p <= 'Z') *p += 'a' - 'A';
        char* cl = strstr(lower, "content-length:");
        if (cl) {
            cl += strlen("content-length:");
            while (*cl == ' ') cl++;
            content_length = (size_t)strtoul(cl, NULL, 10);
        }
        free(lower);
    }

    /* read body */
    size_t body_have = total - (size_t)header_end;
    if (body_have > content_length) body_have = content_length;
    size_t copy = body_have;
    if (copy > sizeof(g_srv.last_body) - 1) copy = sizeof(g_srv.last_body) - 1;
    memcpy(g_srv.last_body, buf + header_end, copy);
    g_srv.last_body_len = copy;

    while (body_have < content_length) {
        n = recv(fd, buf, sizeof(buf), 0);
        if (n <= 0) break;
        size_t take = (size_t)n;
        if (body_have + take > content_length) take = content_length - body_have;
        body_have += take;
        if (g_srv.last_body_len < sizeof(g_srv.last_body) - 1) {
            size_t room = sizeof(g_srv.last_body) - 1 - g_srv.last_body_len;
            size_t c2 = (take < room) ? take : room;
            memcpy(g_srv.last_body + g_srv.last_body_len, buf, c2);
            g_srv.last_body_len += c2;
        }
    }
    g_srv.last_body[g_srv.last_body_len] = 0;
    return 0;
}

static MockResponse pop_resp(void) {
    MockResponse r;
    pthread_mutex_lock(&g_srv.lock);
    while (g_srv.count == 0 && g_srv.running)
        pthread_cond_wait(&g_srv.cond, &g_srv.lock);
    r = g_srv.queue[g_srv.head];
    g_srv.head = (g_srv.head + 1) % MAX_QUEUED;
    g_srv.count--;
    pthread_mutex_unlock(&g_srv.lock);
    return r;
}

static void* srv_thread(void* arg) {
    (void)arg;
    while (g_srv.running) {
        struct sockaddr_in cli;
        socklen_t cli_len = sizeof(cli);
        int fd = accept(g_srv.listen_fd, (struct sockaddr*)&cli, &cli_len);
        if (fd < 0) { if (!g_srv.running) break; continue; }
        if (read_req(fd) != 0) { close(fd); continue; }

        MockResponse resp = pop_resp();
        if (resp.close_immediately) { close(fd); if (resp.body) free(resp.body); continue; }

        char hdr[1024];
        int hl = snprintf(hdr, sizeof(hdr),
            "HTTP/1.1 %d %s\r\n"
            "Content-Length: %zu\r\n"
            "Connection: close\r\n"
            "%s"
            "\r\n",
            resp.status, status_txt(resp.status), resp.body_len, resp.headers);
        send_all_s(fd, hdr, (size_t)hl);
        if (resp.body && resp.body_len > 0) send_all_s(fd, resp.body, resp.body_len);
        if (resp.body) free(resp.body);
        close(fd);
    }
    return NULL;
}

static void mock_init(void) {
    memset(&g_srv, 0, sizeof(g_srv));
    pthread_mutex_init(&g_srv.lock, NULL);
    pthread_cond_init(&g_srv.cond, NULL);

    g_srv.listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    int yes = 1;
    setsockopt(g_srv.listen_fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;
    bind(g_srv.listen_fd, (struct sockaddr*)&addr, sizeof(addr));

    socklen_t al = sizeof(addr);
    getsockname(g_srv.listen_fd, (struct sockaddr*)&addr, &al);
    g_srv.port = ntohs(addr.sin_port);

    listen(g_srv.listen_fd, 8);
    g_srv.running = 1;
    pthread_create(&g_srv.thread, NULL, srv_thread, NULL);
}

static void mock_shutdown(void) {
    g_srv.running = 0;
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd >= 0) {
        struct sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        addr.sin_port = htons(g_srv.port);
        connect(fd, (struct sockaddr*)&addr, sizeof(addr));
        close(fd);
    }
    pthread_mutex_lock(&g_srv.lock);
    pthread_cond_broadcast(&g_srv.cond);
    pthread_mutex_unlock(&g_srv.lock);
    pthread_join(g_srv.thread, NULL);
    close(g_srv.listen_fd);
    pthread_mutex_destroy(&g_srv.lock);
    pthread_cond_destroy(&g_srv.cond);
}

static void mock_enqueue_text(int status, const char* body) {
    pthread_mutex_lock(&g_srv.lock);
    MockResponse* r = &g_srv.queue[g_srv.tail];
    memset(r, 0, sizeof(*r));
    r->status = status;
    if (body && *body) {
        r->body_len = strlen(body);
        r->body = (uint8_t*)malloc(r->body_len);
        memcpy(r->body, body, r->body_len);
    }
    g_srv.tail = (g_srv.tail + 1) % MAX_QUEUED;
    g_srv.count++;
    pthread_cond_signal(&g_srv.cond);
    pthread_mutex_unlock(&g_srv.lock);
}

static void mock_enqueue_empty(int status) {
    mock_enqueue_text(status, "");
}

static void mock_enqueue_close(void) {
    pthread_mutex_lock(&g_srv.lock);
    MockResponse* r = &g_srv.queue[g_srv.tail];
    memset(r, 0, sizeof(*r));
    r->close_immediately = 1;
    g_srv.tail = (g_srv.tail + 1) % MAX_QUEUED;
    g_srv.count++;
    pthread_cond_signal(&g_srv.cond);
    pthread_mutex_unlock(&g_srv.lock);
}

/* ---- client factory ---- */
static char g_host[64];

static TypecastClient* new_client(void) {
    snprintf(g_host, sizeof(g_host), "http://127.0.0.1:%d", g_srv.port);
    return typecast_client_create_with_host("test_key", g_host);
}

/* ============================================
 * Validation tests (no HTTP needed)
 * ============================================ */

static void test_clone_null_client(void) {
    unsigned char audio[16] = {0};
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(NULL, audio, sizeof(audio),
                                                "a.wav", "MyVoice", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
}

static void test_clone_null_audio(void) {
    TypecastClient* c = new_client();
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(c, NULL, 0,
                                                "a.wav", "MyVoice", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_clone_rejects_oversized_audio(void) {
    TypecastClient* c = new_client();
    TypecastCustomVoice out;
    /* Use a fake large size; we never actually allocate it */
    size_t oversized = (size_t)TYPECAST_CLONING_MAX_FILE_SIZE + 1;
    unsigned char stub[1] = {0};
    /* We pass audio_len > limit; the function must reject before HTTP */
    TypecastErrorCode rc = typecast_clone_voice(c, stub, oversized,
                                                "a.wav", "MyVoice", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_NOT_NULL(e);
    ASSERT_EQ(e->code, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_clone_rejects_empty_name(void) {
    TypecastClient* c = new_client();
    TypecastCustomVoice out;
    unsigned char audio[16] = {0};
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "a.wav", "", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_clone_rejects_null_name(void) {
    TypecastClient* c = new_client();
    TypecastCustomVoice out;
    unsigned char audio[16] = {0};
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "a.wav", NULL, "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_clone_rejects_long_name(void) {
    TypecastClient* c = new_client();
    TypecastCustomVoice out;
    unsigned char audio[16] = {0};
    /* 31 characters — one over the limit */
    const char* long_name = "1234567890123456789012345678901";
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "a.wav", long_name, "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_clone_rejects_null_model(void) {
    TypecastClient* c = new_client();
    TypecastCustomVoice out;
    unsigned char audio[16] = {0};
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "a.wav", "MyVoice", NULL, &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_clone_rejects_null_out(void) {
    TypecastClient* c = new_client();
    unsigned char audio[16] = {0};
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "a.wav", "MyVoice", "ssfm-v30", NULL);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

/* ============================================
 * delete_voice validation
 * ============================================ */

static void test_delete_null_client(void) {
    TypecastErrorCode rc = typecast_delete_voice(NULL, "uc_abc");
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
}

static void test_delete_null_voice_id(void) {
    TypecastClient* c = new_client();
    TypecastErrorCode rc = typecast_delete_voice(c, NULL);
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

static void test_delete_empty_voice_id(void) {
    TypecastClient* c = new_client();
    TypecastErrorCode rc = typecast_delete_voice(c, "");
    ASSERT_EQ(rc, TYPECAST_ERROR_INVALID_PARAM);
    typecast_client_destroy(c);
}

/* ============================================
 * Happy-path HTTP tests
 * ============================================ */

static void test_clone_voice_returns_custom_voice(void) {
    TypecastClient* c = new_client();

    const char* body =
        "{\"voice_id\":\"uc_aabbccdd1122334455667788\","
        "\"name\":\"My Clone\","
        "\"model\":\"ssfm-v30\"}";
    mock_enqueue_text(200, body);

    unsigned char audio[64];
    memset(audio, 0xAB, sizeof(audio));

    TypecastCustomVoice out;
    memset(&out, 0, sizeof(out));

    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "sample.wav", "My Clone", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_OK);
    ASSERT_STREQ(out.voice_id, "uc_aabbccdd1122334455667788");
    ASSERT_STREQ(out.name,     "My Clone");
    ASSERT_STREQ(out.model,    "ssfm-v30");

    /* Verify endpoint and multipart fields were sent */
    ASSERT(strstr(g_srv.last_path, "/v1/voices/clone") != NULL);
    ASSERT(strcmp(g_srv.last_method, "POST") == 0);
    ASSERT(strstr(g_srv.last_headers, "X-API-KEY: test_key") != NULL);
    /* multipart body should contain the field names */
    ASSERT(strstr(g_srv.last_body, "name") != NULL);
    ASSERT(strstr(g_srv.last_body, "model") != NULL);
    ASSERT(strstr(g_srv.last_body, "file") != NULL);

    typecast_client_destroy(c);
}

static void test_clone_voice_201_response(void) {
    /* Some server implementations return 201 Created */
    TypecastClient* c = new_client();
    const char* body =
        "{\"voice_id\":\"uc_0000\",\"name\":\"V\",\"model\":\"ssfm-v21\"}";
    mock_enqueue_text(201, body);

    unsigned char audio[8] = {1, 2, 3, 4, 5, 6, 7, 8};
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "clip.mp3", "V", "ssfm-v21", &out);
    ASSERT_EQ(rc, TYPECAST_OK);
    ASSERT_STREQ(out.voice_id, "uc_0000");
    ASSERT_STREQ(out.model,    "ssfm-v21");
    typecast_client_destroy(c);
}

static void test_clone_voice_sets_supported_mime_types(void) {
    struct {
        const char* filename;
        const char* expected_mime;
    } cases[] = {
        {"clip.ogg",  "audio/ogg"},
        {"clip.flac", "audio/flac"},
        {"clip.m4a",  "audio/mp4"},
        {"clip.webm", "audio/webm"},
        {"clip.bin",  "application/octet-stream"},
        {NULL,        "application/octet-stream"},
    };

    TypecastClient* c = new_client();
    unsigned char audio[8] = {1, 2, 3, 4, 5, 6, 7, 8};

    for (size_t i = 0; i < sizeof(cases) / sizeof(cases[0]); i++) {
        mock_enqueue_text(200, "{\"voice_id\":\"uc_mime\",\"name\":\"V\",\"model\":\"ssfm-v30\"}");

        TypecastCustomVoice out;
        TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                    cases[i].filename, "V", "ssfm-v30", &out);
        ASSERT_EQ(rc, TYPECAST_OK);
        ASSERT(strstr(g_srv.last_body, cases[i].expected_mime) != NULL);
    }

    typecast_client_destroy(c);
}

static void test_clone_voice_http_error(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(422, "{\"detail\":\"invalid audio format\"}");

    unsigned char audio[8] = {0};
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "x.wav", "Name", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_UNPROCESSABLE_ENTITY);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_NOT_NULL(e);
    ASSERT_EQ(e->code, TYPECAST_ERROR_UNPROCESSABLE_ENTITY);
    typecast_client_destroy(c);
}

static void test_clone_voice_404(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(404, "{}");
    unsigned char audio[8] = {0};
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "x.wav", "N", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_NOT_FOUND);
    typecast_client_destroy(c);
}

static void test_clone_voice_network_error(void) {
    TypecastClient* c = new_client();
    mock_enqueue_close();

    unsigned char audio[8] = {0};
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "x.wav", "Name", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_NETWORK);
    typecast_client_destroy(c);
}

static void test_clone_voice_invalid_json(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, "not-json");

    unsigned char audio[8] = {0};
    TypecastCustomVoice out;
    TypecastErrorCode rc = typecast_clone_voice(c, audio, sizeof(audio),
                                                "x.wav", "Name", "ssfm-v30", &out);
    ASSERT_EQ(rc, TYPECAST_ERROR_JSON_PARSE);
    typecast_client_destroy(c);
}

/* ---- delete_voice happy path ---- */

static void test_delete_voice_succeeds_on_204(void) {
    TypecastClient* c = new_client();
    mock_enqueue_empty(204);

    TypecastErrorCode rc = typecast_delete_voice(c, "uc_aabbccdd");
    ASSERT_EQ(rc, TYPECAST_OK);

    ASSERT(strstr(g_srv.last_path, "/v1/voices/uc_aabbccdd") != NULL);
    ASSERT(strcmp(g_srv.last_method, "DELETE") == 0);
    ASSERT(strstr(g_srv.last_headers, "X-API-KEY: test_key") != NULL);

    typecast_client_destroy(c);
}

static void test_delete_voice_succeeds_on_200(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(200, "{}");
    TypecastErrorCode rc = typecast_delete_voice(c, "uc_xyz");
    ASSERT_EQ(rc, TYPECAST_OK);
    typecast_client_destroy(c);
}

static void test_delete_voice_returns_error_on_404(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(404, "{\"detail\":\"voice not found\"}");

    TypecastErrorCode rc = typecast_delete_voice(c, "uc_nonexistent");
    ASSERT_EQ(rc, TYPECAST_ERROR_NOT_FOUND);
    const TypecastError* e = typecast_client_get_error(c);
    ASSERT_NOT_NULL(e);
    ASSERT_EQ(e->code, TYPECAST_ERROR_NOT_FOUND);

    typecast_client_destroy(c);
}

static void test_delete_voice_returns_error_on_401(void) {
    TypecastClient* c = new_client();
    mock_enqueue_text(401, "{}");
    TypecastErrorCode rc = typecast_delete_voice(c, "uc_abc");
    ASSERT_EQ(rc, TYPECAST_ERROR_UNAUTHORIZED);
    typecast_client_destroy(c);
}

static void test_delete_voice_network_error(void) {
    TypecastClient* c = new_client();
    mock_enqueue_close();
    TypecastErrorCode rc = typecast_delete_voice(c, "uc_abc");
    ASSERT_EQ(rc, TYPECAST_ERROR_NETWORK);
    typecast_client_destroy(c);
}

/* ============================================
 * main
 * ============================================ */

int main(void) {
    mock_init();

    printf("=== Instant cloning Tests ===\n\n");

    /* clone_voice validation (no HTTP) */
    RUN(clone_null_client);
    RUN(clone_null_audio);
    RUN(clone_rejects_oversized_audio);
    RUN(clone_rejects_empty_name);
    RUN(clone_rejects_null_name);
    RUN(clone_rejects_long_name);
    RUN(clone_rejects_null_model);
    RUN(clone_rejects_null_out);

    /* delete_voice validation (no HTTP) */
    RUN(delete_null_client);
    RUN(delete_null_voice_id);
    RUN(delete_empty_voice_id);

    /* clone_voice HTTP paths */
    RUN(clone_voice_returns_custom_voice);
    RUN(clone_voice_201_response);
    RUN(clone_voice_sets_supported_mime_types);
    RUN(clone_voice_http_error);
    RUN(clone_voice_404);
    RUN(clone_voice_network_error);
    RUN(clone_voice_invalid_json);

    /* delete_voice HTTP paths */
    RUN(delete_voice_succeeds_on_204);
    RUN(delete_voice_succeeds_on_200);
    RUN(delete_voice_returns_error_on_404);
    RUN(delete_voice_returns_error_on_401);
    RUN(delete_voice_network_error);

    mock_shutdown();

    printf("\n===========================================\n");
    printf("Tests run: %d  passed: %d  failed: %d\n", tests_run, tests_passed, tests_failed);
    printf("===========================================\n");
    return tests_failed > 0 ? 1 : 0;
}
