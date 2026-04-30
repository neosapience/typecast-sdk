/**
 * Typecast C SDK - Timestamp TTS fixture byte-equality tests
 *
 * Loads the shared test-fixtures/with-timestamps/ JSON files, builds
 * TypecastTTSWithTimestampsResponse structs by hand (no HTTP needed),
 * calls to_srt() / to_vtt(), and compares output byte-for-byte against
 * the expected/*.srt / *.vtt files.
 *
 * Run: ./test_timestamps  (from the build directory)
 * Fixture path: resolved relative to the source root via __FILE__ if
 * the binary is in <root>/typecast-c/build/, or via env var
 * TYPECAST_FIXTURE_DIR.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

/* We need to reach into cJSON for fixture parsing.
 * The include path is set by CMake so "cJSON.h" works. */
#include "cJSON.h"
#include "typecast.h"

/* ============================================
 * Test bookkeeping (mirrors test_mock.c style)
 * ============================================ */

static int g_tests_run    = 0;
static int g_tests_passed = 0;
static int g_tests_failed = 0;

#define ASSERT(cond) do { \
    if (!(cond)) { \
        printf("FAILED\n  Assertion failed: %s\n  At %s:%d\n", \
               #cond, __FILE__, __LINE__); \
        g_tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_NOT_NULL(p) do { \
    if (!(p)) { \
        printf("FAILED\n  Expected non-NULL\n  At %s:%d\n", \
               __FILE__, __LINE__); \
        g_tests_failed++; \
        return; \
    } \
} while(0)

#define ASSERT_STREQ(a, b) do { \
    if (!(a) || strcmp((a), (b)) != 0) { \
        printf("FAILED\n  Expected:\n---\n%s\n---\n  Got:\n---\n%s\n---\n  At %s:%d\n", \
               (b), (a) ? (a) : "(null)", __FILE__, __LINE__); \
        g_tests_failed++; \
        return; \
    } \
} while(0)

#define RUN(name) do { \
    printf("Running %-55s", #name "..."); \
    fflush(stdout); \
    g_tests_run++; \
    int _before = g_tests_failed; \
    test_##name(); \
    if (g_tests_failed == _before) { g_tests_passed++; printf("PASSED\n"); } \
} while(0)

/* ============================================
 * Fixture path resolution
 * ============================================ */

static char g_fixture_root[4096] = {0};

/**
 * Try to find the fixture directory.
 * Priority:
 *   1. TYPECAST_FIXTURE_DIR env var
 *   2. Walk up from __FILE__ until we find test-fixtures/
 *   3. Walk up from argv[0]'s directory (set separately)
 */
static void resolve_fixture_root(void) {
    const char* env = getenv("TYPECAST_FIXTURE_DIR");
    if (env) {
        snprintf(g_fixture_root, sizeof(g_fixture_root), "%s", env);
        return;
    }

    /* __FILE__ is typically an absolute path in CMake builds.
     * Walk up until we find a directory that contains test-fixtures/. */
    char path[4096];
    snprintf(path, sizeof(path), "%s", __FILE__);

    /* Strip filename component repeatedly until we hit root or find it */
    for (int attempts = 0; attempts < 10; attempts++) {
        /* Strip last path component */
        char* slash = strrchr(path, '/');
        if (!slash) break;
        *slash = '\0';

        /* Try path/test-fixtures */
        char candidate[4096];
        snprintf(candidate, sizeof(candidate), "%s/test-fixtures/with-timestamps", path);

        FILE* probe = fopen(candidate, "r");
        if (!probe) {
            /* Try with a known file inside */
            char probe_file[4096];
            snprintf(probe_file, sizeof(probe_file),
                     "%s/word_only.json", candidate);
            probe = fopen(probe_file, "r");
        }
        if (probe) {
            fclose(probe);
            snprintf(g_fixture_root, sizeof(g_fixture_root), "%s", candidate);
            return;
        }
    }

    /* Fallback: relative path from CWD (works when ctest runs from build/) */
    snprintf(g_fixture_root, sizeof(g_fixture_root),
             "../../test-fixtures/with-timestamps");
}

static void fixture_path(const char* name, char* out, size_t out_size) {
    snprintf(out, out_size, "%s/%s", g_fixture_root, name);
}

/* ============================================
 * File helpers
 * ============================================ */

/* Read entire file into heap-allocated buffer.  Caller frees. */
static char* read_file(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    rewind(f);
    if (sz <= 0) { fclose(f); return NULL; }
    char* buf = (char*)malloc((size_t)sz + 1);
    if (!buf) { fclose(f); return NULL; }
    size_t rd = fread(buf, 1, (size_t)sz, f);
    fclose(f);
    buf[rd] = '\0';
    return buf;
}

/* ============================================
 * Unicode unescape helper (mirrors typecast.c private version)
 *
 * The bundled cJSON replaces \uXXXX with '?'. Pre-process JSON strings
 * to convert Unicode escapes to UTF-8 before passing to cJSON_Parse.
 * ============================================ */

static int local_codepoint_to_utf8(unsigned int cp, char* buf) {
    if (cp < 0x80) { buf[0] = (char)cp; return 1; }
    if (cp < 0x800) {
        buf[0] = (char)(0xC0 | (cp >> 6));
        buf[1] = (char)(0x80 | (cp & 0x3F));
        return 2;
    }
    if (cp < 0x10000) {
        buf[0] = (char)(0xE0 | (cp >> 12));
        buf[1] = (char)(0x80 | ((cp >> 6) & 0x3F));
        buf[2] = (char)(0x80 | (cp & 0x3F));
        return 3;
    }
    if (cp < 0x110000) {
        buf[0] = (char)(0xF0 | (cp >> 18));
        buf[1] = (char)(0x80 | ((cp >> 12) & 0x3F));
        buf[2] = (char)(0x80 | ((cp >> 6) & 0x3F));
        buf[3] = (char)(0x80 | (cp & 0x3F));
        return 4;
    }
    return 0;
}

static int local_parse_hex4(const char* p) {
    unsigned int cp = 0;
    for (int i = 0; i < 4; i++) {
        cp <<= 4;
        char c = p[i];
        if (c >= '0' && c <= '9') cp |= (unsigned int)(c - '0');
        else if (c >= 'a' && c <= 'f') cp |= (unsigned int)(c - 'a' + 10);
        else if (c >= 'A' && c <= 'F') cp |= (unsigned int)(c - 'A' + 10);
        else return -1;
    }
    return (int)cp;
}

static char* local_json_unescape_unicode(const char* src) {
    size_t src_len = strlen(src);
    char* out = (char*)malloc(src_len + 1);
    if (!out) return NULL;
    const char* r = src;
    char* w = out;
    int in_string = 0;
    char prev = 0;
    while (*r) {
        if (!in_string) {
            if (*r == '"') { in_string = 1; *w++ = *r++; continue; }
            *w++ = *r++;
            continue;
        }
        if (prev != '\\' && *r == '"') {
            in_string = 0; *w++ = *r++; prev = '"'; continue;
        }
        if (*r == '\\' && r[1] == 'u' && r[2] && r[3] && r[4] && r[5]) {
            int cp = local_parse_hex4(r + 2);
            if (cp < 0) { *w++ = *r++; prev = r[-1]; continue; }
            if (cp >= 0xD800 && cp <= 0xDBFF && r[6] == '\\' && r[7] == 'u') {
                int cp2 = local_parse_hex4(r + 8);
                if (cp2 >= 0xDC00 && cp2 <= 0xDFFF) {
                    unsigned int full = (unsigned int)(
                        0x10000 + ((cp - 0xD800) << 10) + (cp2 - 0xDC00));
                    int n = local_codepoint_to_utf8(full, w);
                    if (n > 0) { w += n; r += 12; prev = 0; continue; }
                }
            }
            int n = local_codepoint_to_utf8((unsigned int)cp, w);
            if (n > 0) { w += n; r += 6; prev = 0; continue; }
        }
        prev = *r;
        *w++ = *r++;
    }
    *w = '\0';
    return out;
}

/* ============================================
 * Fixture loader: JSON -> TypecastTTSWithTimestampsResponse
 * ============================================ */

static TypecastAlignmentSegment* parse_seg_array_local(cJSON* arr, size_t* out_n) {
    *out_n = 0;
    if (!arr || !cJSON_IsArray(arr)) return NULL;
    int n = cJSON_GetArraySize(arr);
    if (n <= 0) return NULL;
    TypecastAlignmentSegment* s = (TypecastAlignmentSegment*)calloc(
        (size_t)n, sizeof(TypecastAlignmentSegment));
    if (!s) return NULL;
    for (int i = 0; i < n; i++) {
        cJSON* item  = cJSON_GetArrayItem(arr, i);
        cJSON* jtext = cJSON_GetObjectItem(item, "text");
        cJSON* jst   = cJSON_GetObjectItem(item, "start");
        cJSON* jend  = cJSON_GetObjectItem(item, "end");
        if (cJSON_IsString(jtext)) {
            size_t tl = strlen(jtext->valuestring);
            s[i].text = (char*)malloc(tl + 1);
            if (s[i].text) memcpy(s[i].text, jtext->valuestring, tl + 1);
        }
        if (cJSON_IsNumber(jst))  s[i].start = (float)jst->valuedouble;
        if (cJSON_IsNumber(jend)) s[i].end   = (float)jend->valuedouble;
    }
    *out_n = (size_t)n;
    return s;
}

static TypecastTTSWithTimestampsResponse* load_fixture(const char* json_name) {
    char path[4096];
    fixture_path(json_name, path, sizeof(path));

    char* raw = read_file(path);
    if (!raw) {
        printf("\n  [SKIP] Could not open fixture: %s\n", path);
        return NULL;
    }

    /* Pre-process \uXXXX escapes to UTF-8 before cJSON_Parse (bundled cJSON
     * replaces them with '?', which breaks multi-byte characters). */
    char* unescaped = local_json_unescape_unicode(raw);
    free(raw);
    if (!unescaped) {
        printf("\n  [ERROR] unicode unescape failed for: %s\n", path);
        return NULL;
    }

    cJSON* root = cJSON_Parse(unescaped);
    free(unescaped);
    if (!root) {
        printf("\n  [ERROR] cJSON parse failed for: %s\n", path);
        return NULL;
    }

    TypecastTTSWithTimestampsResponse* resp =
        (TypecastTTSWithTimestampsResponse*)calloc(
            1, sizeof(TypecastTTSWithTimestampsResponse));
    if (!resp) { cJSON_Delete(root); return NULL; }

    cJSON* jaudio = cJSON_GetObjectItem(root, "audio");
    if (cJSON_IsString(jaudio)) {
        size_t l = strlen(jaudio->valuestring);
        resp->audio_base64 = (char*)malloc(l + 1);
        if (resp->audio_base64) memcpy(resp->audio_base64, jaudio->valuestring, l + 1);
    }
    cJSON* jfmt = cJSON_GetObjectItem(root, "audio_format");
    if (cJSON_IsString(jfmt)) {
        size_t l = strlen(jfmt->valuestring);
        resp->audio_format = (char*)malloc(l + 1);
        if (resp->audio_format) memcpy(resp->audio_format, jfmt->valuestring, l + 1);
    }
    cJSON* jdur = cJSON_GetObjectItem(root, "audio_duration");
    if (cJSON_IsNumber(jdur)) resp->audio_duration = (float)jdur->valuedouble;

    cJSON* jwords = cJSON_GetObjectItem(root, "words");
    if (cJSON_IsArray(jwords))
        resp->words = parse_seg_array_local(jwords, &resp->words_count);

    cJSON* jchars = cJSON_GetObjectItem(root, "characters");
    if (cJSON_IsArray(jchars))
        resp->characters = parse_seg_array_local(jchars, &resp->characters_count);

    cJSON_Delete(root);
    return resp;
}

/* ============================================
 * Byte-equality test helper
 * ============================================ */

static void check_srt_vtt(
    const char* fixture_json,
    const char* expected_srt_name,
    const char* expected_vtt_name
) {
    TypecastTTSWithTimestampsResponse* resp = load_fixture(fixture_json);
    ASSERT_NOT_NULL(resp);

    /* Read expected files */
    char srt_path[4096], vtt_path[4096];
    snprintf(srt_path, sizeof(srt_path), "%s/expected/%s", g_fixture_root, expected_srt_name);
    snprintf(vtt_path, sizeof(vtt_path), "%s/expected/%s", g_fixture_root, expected_vtt_name);

    char* expected_srt = read_file(srt_path);
    char* expected_vtt = read_file(vtt_path);
    ASSERT_NOT_NULL(expected_srt);
    ASSERT_NOT_NULL(expected_vtt);

    /* Generate */
    char* got_srt = NULL;
    char* got_vtt = NULL;
    TypecastErrorCode rc_srt = typecast_tts_with_timestamps_response_to_srt(resp, &got_srt);
    TypecastErrorCode rc_vtt = typecast_tts_with_timestamps_response_to_vtt(resp, &got_vtt);

    ASSERT(rc_srt == TYPECAST_OK);
    ASSERT(rc_vtt == TYPECAST_OK);
    ASSERT_NOT_NULL(got_srt);
    ASSERT_NOT_NULL(got_vtt);

    ASSERT_STREQ(got_srt, expected_srt);
    ASSERT_STREQ(got_vtt, expected_vtt);

    free(got_srt);
    free(got_vtt);
    free(expected_srt);
    free(expected_vtt);
    typecast_tts_with_timestamps_response_free(resp);
}

/* ============================================
 * Fixture tests
 * ============================================ */

static void test_word_only_srt_vtt(void) {
    check_srt_vtt("word_only.json", "word_only.srt", "word_only.vtt");
}

static void test_char_only_srt_vtt(void) {
    check_srt_vtt("char_only.json", "char_only.srt", "char_only.vtt");
}

static void test_both_srt_vtt(void) {
    check_srt_vtt("both.json", "both.srt", "both.vtt");
}

static void test_jpn_char_srt_vtt(void) {
    check_srt_vtt("jpn_char.json", "jpn_char.srt", "jpn_char.vtt");
}

/* ============================================
 * Unit tests for individual helper paths
 * ============================================ */

/* NULL response / out_string -> INVALID_PARAM */
static void test_srt_null_params(void) {
    char* out = NULL;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_to_srt(NULL, &out);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
    ASSERT(out == NULL);

    TypecastTTSWithTimestampsResponse resp = {0};
    rc = typecast_tts_with_timestamps_response_to_srt(&resp, NULL);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
}

static void test_vtt_null_params(void) {
    char* out = NULL;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_to_vtt(NULL, &out);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
    ASSERT(out == NULL);

    TypecastTTSWithTimestampsResponse resp = {0};
    rc = typecast_tts_with_timestamps_response_to_vtt(&resp, NULL);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
}

/* No segments at all -> INVALID_PARAM */
static void test_srt_no_segments(void) {
    TypecastTTSWithTimestampsResponse resp = {0};
    char* out = NULL;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_to_srt(&resp, &out);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
    ASSERT(out == NULL);
}

static void test_vtt_no_segments(void) {
    TypecastTTSWithTimestampsResponse resp = {0};
    char* out = NULL;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_to_vtt(&resp, &out);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
    ASSERT(out == NULL);
}

/* audio_bytes: NULL input -> INVALID_PARAM */
static void test_audio_bytes_null(void) {
    uint8_t* bytes = NULL;
    size_t sz = 0;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_audio_bytes(NULL, &bytes, &sz);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);

    TypecastTTSWithTimestampsResponse resp = {0};
    rc = typecast_tts_with_timestamps_response_audio_bytes(&resp, &bytes, &sz);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
}

/* save_audio: NULL params -> INVALID_PARAM */
static void test_save_audio_null(void) {
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_save_audio(NULL, "x");
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);

    TypecastTTSWithTimestampsResponse resp = {0};
    rc = typecast_tts_with_timestamps_response_save_audio(&resp, NULL);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);
}

/* free NULL -> no crash */
static void test_free_null(void) {
    typecast_tts_with_timestamps_response_free(NULL);
    ASSERT(1); /* just prove no crash */
}

/* audio_bytes from fixture -> non-zero length */
static void test_audio_bytes_from_fixture(void) {
    TypecastTTSWithTimestampsResponse* resp = load_fixture("word_only.json");
    ASSERT_NOT_NULL(resp);

    uint8_t* bytes = NULL;
    size_t sz = 0;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_audio_bytes(resp, &bytes, &sz);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(bytes);
    ASSERT(sz > 0);

    free(bytes);
    typecast_tts_with_timestamps_response_free(resp);
}

/* Single-word response (words_count == 1, no characters) */
static void test_single_word_response(void) {
    TypecastAlignmentSegment w;
    w.text  = (char*)"Hello.";
    w.start = 0.1f;
    w.end   = 0.5f;

    TypecastTTSWithTimestampsResponse resp = {0};
    resp.words       = &w;
    resp.words_count = 1;

    char* srt = NULL;
    TypecastErrorCode rc = typecast_tts_with_timestamps_response_to_srt(&resp, &srt);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(srt);
    ASSERT(strstr(srt, "Hello.") != NULL);
    free(srt);

    char* vtt = NULL;
    rc = typecast_tts_with_timestamps_response_to_vtt(&resp, &vtt);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(vtt);
    ASSERT(strstr(vtt, "WEBVTT") != NULL);
    free(vtt);
}

/* ============================================
 * Mock-server tests for typecast_text_to_speech_with_timestamps
 * (reuse the mini mock server from test_mock.c style)
 * ============================================ */

#include <pthread.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define MOCK_MAX_QUEUED 8

typedef struct {
    int     status;
    char    headers[512];
    char*   body;
    size_t  body_len;
    int     close_immediately;
} MiniMockResp;

static struct {
    pthread_mutex_t lock;
    pthread_cond_t  cond;
    MiniMockResp    queue[MOCK_MAX_QUEUED];
    int head, tail, count;
    int listen_fd, port;
    pthread_t thread;
    int running;
    char last_path[256];
    char last_body[1024];
} g_mock;

static void mini_send_all(int fd, const void* d, size_t l) {
    const uint8_t* p = (const uint8_t*)d;
    size_t off = 0;
    while (off < l) {
        ssize_t n = send(fd, p + off, l - off, 0);
        if (n <= 0) return;
        off += (size_t)n;
    }
}

static void* mini_server_thread(void* arg) {
    (void)arg;
    while (g_mock.running) {
        struct sockaddr_in cli;
        socklen_t cl = sizeof(cli);
        int fd = accept(g_mock.listen_fd, (struct sockaddr*)&cli, &cl);
        if (fd < 0) {
            if (!g_mock.running) break;
            continue;
        }

        /* Read request headers (stop at \r\n\r\n) */
        char buf[8192] = {0};
        size_t total = 0;
        while (total < sizeof(buf) - 1) {
            ssize_t n = recv(fd, buf + total, sizeof(buf) - 1 - total, 0);
            if (n <= 0) break;
            total += (size_t)n;
            buf[total] = 0;
            if (strstr(buf, "\r\n\r\n")) break;
        }
        /* Capture path */
        char method[16], path[256];
        if (sscanf(buf, "%15s %255s", method, path) == 2) {
            snprintf(g_mock.last_path, sizeof(g_mock.last_path), "%s", path);
        }
        /* Parse Content-Length from request headers */
        int content_length = 0;
        {
            char* cl_hdr = strcasestr(buf, "Content-Length:");
            if (cl_hdr) {
                content_length = atoi(cl_hdr + 15);
            }
        }
        /* Find body start and continue reading until full body is received */
        char* body_start = strstr(buf, "\r\n\r\n");
        if (body_start) {
            body_start += 4;
            int header_len = (int)(body_start - buf);
            int body_recvd = (int)total - header_len;
            /* Continue reading until expected body length or connection close */
            while (content_length > 0 && body_recvd < content_length
                   && total < sizeof(buf) - 1) {
                ssize_t n = recv(fd, buf + total, sizeof(buf) - 1 - total, 0);
                if (n <= 0) break;
                total += (size_t)n;
                buf[total] = 0;
                body_recvd += (int)n;
            }
            /* body_start may have been invalidated if buf was not reallocated,
             * but buf is a fixed-size stack array so the pointer is still valid */
            snprintf(g_mock.last_body, sizeof(g_mock.last_body), "%s", body_start);
        }

        /* Pop response */
        pthread_mutex_lock(&g_mock.lock);
        while (g_mock.count == 0 && g_mock.running)
            pthread_cond_wait(&g_mock.cond, &g_mock.lock);
        if (g_mock.count == 0) {
            /* running flipped to 0 and queue is empty — exit thread */
            pthread_mutex_unlock(&g_mock.lock);
            close(fd);
            break;
        }
        MiniMockResp r = g_mock.queue[g_mock.head];
        g_mock.head = (g_mock.head + 1) % MOCK_MAX_QUEUED;
        g_mock.count--;
        pthread_mutex_unlock(&g_mock.lock);

        if (r.close_immediately) {
            close(fd);
            if (r.body) free(r.body);
            continue;
        }

        char hdr[1024];
        int hl = snprintf(hdr, sizeof(hdr),
            "HTTP/1.1 %d OK\r\nContent-Length: %zu\r\nConnection: close\r\n%s\r\n",
            r.status, r.body_len, r.headers);
        mini_send_all(fd, hdr, (size_t)hl);
        if (r.body && r.body_len > 0) mini_send_all(fd, r.body, r.body_len);
        if (r.body) free(r.body);
        close(fd);
    }
    return NULL;
}

static void mini_mock_init(void) {
    memset(&g_mock, 0, sizeof(g_mock));
    pthread_mutex_init(&g_mock.lock, NULL);
    pthread_cond_init(&g_mock.cond, NULL);

    g_mock.listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    int yes = 1;
    setsockopt(g_mock.listen_fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof(yes));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;
    bind(g_mock.listen_fd, (struct sockaddr*)&addr, sizeof(addr));

    socklen_t al = sizeof(addr);
    getsockname(g_mock.listen_fd, (struct sockaddr*)&addr, &al);
    g_mock.port = ntohs(addr.sin_port);
    listen(g_mock.listen_fd, 4);
    g_mock.running = 1;
    pthread_create(&g_mock.thread, NULL, mini_server_thread, NULL);
}

static void mini_mock_shutdown(void) {
    g_mock.running = 0;
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd >= 0) {
        struct sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        addr.sin_port = htons(g_mock.port);
        connect(fd, (struct sockaddr*)&addr, sizeof(addr));
        close(fd);
    }
    pthread_mutex_lock(&g_mock.lock);
    pthread_cond_broadcast(&g_mock.cond);
    pthread_mutex_unlock(&g_mock.lock);
    pthread_join(g_mock.thread, NULL);
    close(g_mock.listen_fd);
    pthread_mutex_destroy(&g_mock.lock);
    pthread_cond_destroy(&g_mock.cond);
}

static void mini_mock_enqueue_json(int status, const char* json_body) {
    pthread_mutex_lock(&g_mock.lock);
    if (g_mock.count >= MOCK_MAX_QUEUED) {
        pthread_mutex_unlock(&g_mock.lock);
        fprintf(stderr, "mini_mock_enqueue_json: queue full (MOCK_MAX_QUEUED=%d)\n",
                MOCK_MAX_QUEUED);
        abort();
    }
    MiniMockResp* r = &g_mock.queue[g_mock.tail];
    memset(r, 0, sizeof(*r));
    r->status = status;
    if (json_body) {
        r->body_len = strlen(json_body);
        r->body = (char*)malloc(r->body_len);
        if (r->body) memcpy(r->body, json_body, r->body_len);
    }
    g_mock.tail = (g_mock.tail + 1) % MOCK_MAX_QUEUED;
    g_mock.count++;
    pthread_cond_signal(&g_mock.cond);
    pthread_mutex_unlock(&g_mock.lock);
}

static void mini_mock_enqueue_close(void) {
    pthread_mutex_lock(&g_mock.lock);
    if (g_mock.count >= MOCK_MAX_QUEUED) {
        pthread_mutex_unlock(&g_mock.lock);
        fprintf(stderr, "mini_mock_enqueue_close: queue full (MOCK_MAX_QUEUED=%d)\n",
                MOCK_MAX_QUEUED);
        abort();
    }
    MiniMockResp* r = &g_mock.queue[g_mock.tail];
    memset(r, 0, sizeof(*r));
    r->close_immediately = 1;
    g_mock.tail = (g_mock.tail + 1) % MOCK_MAX_QUEUED;
    g_mock.count++;
    pthread_cond_signal(&g_mock.cond);
    pthread_mutex_unlock(&g_mock.lock);
}

static TypecastClient* mini_new_client(void) {
    char host[64];
    snprintf(host, sizeof(host), "http://127.0.0.1:%d", g_mock.port);
    return typecast_client_create_with_host("test_api_key", host);
}

/* Build a minimal valid JSON response body for the timestamps endpoint */
static char* build_word_only_response_json(void) {
    /* A short but valid response: 2 words + audio=base64("AUDIO") */
    static const char* tmpl =
        "{"
        "\"audio\":\"QVVESU8=\","   /* base64 of "AUDIO" */
        "\"audio_format\":\"wav\","
        "\"audio_duration\":2.6,"
        "\"words\":["
            "{\"text\":\"Hello.\",\"start\":0.121,\"end\":0.403},"
            "{\"text\":\"How\",\"start\":1.512,\"end\":1.834},"
            "{\"text\":\"are\",\"start\":1.915,\"end\":2.076},"
            "{\"text\":\"you?\",\"start\":2.076,\"end\":2.600}"
        "],"
        "\"characters\":null"
        "}";
    return (char*)tmpl;
}

/* Test: happy path via mock HTTP server */
static void test_http_with_timestamps_happy(void) {
    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    mini_mock_enqueue_json(200, build_word_only_response_json());

    TypecastTTSRequestWithTimestamps req = {0};
    req.text     = "Hello. How are you?";
    req.voice_id = "tc_test";
    req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);

    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(resp);
    ASSERT(resp->words_count == 4);
    ASSERT(resp->audio_duration > 0.0f);

    /* Verify SRT output */
    char* srt = NULL;
    rc = typecast_tts_with_timestamps_response_to_srt(resp, &srt);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(srt);
    ASSERT(strstr(srt, "Hello.") != NULL);
    ASSERT(strstr(srt, "How are you?") != NULL);
    free(srt);

    /* Verify VTT output */
    char* vtt = NULL;
    rc = typecast_tts_with_timestamps_response_to_vtt(resp, &vtt);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(vtt);
    ASSERT(strstr(vtt, "WEBVTT") != NULL);
    free(vtt);

    /* audio_bytes */
    uint8_t* bytes = NULL;
    size_t sz = 0;
    rc = typecast_tts_with_timestamps_response_audio_bytes(resp, &bytes, &sz);
    ASSERT(rc == TYPECAST_OK);
    ASSERT(sz == 5); /* "AUDIO" */
    ASSERT(memcmp(bytes, "AUDIO", 5) == 0);
    free(bytes);

    typecast_tts_with_timestamps_response_free(resp);
    typecast_client_destroy(client);
}

/* Test: null client / null request -> INVALID_PARAM */
static void test_http_null_params(void) {
    TypecastTTSWithTimestampsResponse* resp = NULL;

    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(NULL, NULL, NULL);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);

    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    rc = typecast_text_to_speech_with_timestamps(client, NULL, &resp);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);

    TypecastTTSRequestWithTimestamps req = {0};
    rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    ASSERT(rc == TYPECAST_ERROR_INVALID_PARAM);

    typecast_client_destroy(client);
}

/* Test: HTTP 401 -> TYPECAST_ERROR_UNAUTHORIZED */
static void test_http_401(void) {
    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    mini_mock_enqueue_json(401, "{\"detail\":\"Unauthorized\"}");

    TypecastTTSRequestWithTimestamps req = {0};
    req.text     = "test";
    req.voice_id = "tc_test";
    req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    ASSERT(rc == TYPECAST_ERROR_UNAUTHORIZED);
    ASSERT(resp == NULL);

    typecast_client_destroy(client);
}

/* Test: Network error (connection drop) */
static void test_http_network_error(void) {
    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    mini_mock_enqueue_close();

    TypecastTTSRequestWithTimestamps req = {0};
    req.text     = "test";
    req.voice_id = "tc_test";
    req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    ASSERT(rc == TYPECAST_ERROR_NETWORK);
    ASSERT(resp == NULL);

    typecast_client_destroy(client);
}

/* Test: Invalid JSON response */
static void test_http_invalid_json(void) {
    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    mini_mock_enqueue_json(200, "not-json{{{{");

    TypecastTTSRequestWithTimestamps req = {0};
    req.text     = "test";
    req.voice_id = "tc_test";
    req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    ASSERT(rc == TYPECAST_ERROR_JSON_PARSE);
    ASSERT(resp == NULL);

    typecast_client_destroy(client);
}

/* Test: granularity is sent as a query parameter in the request URL */
static void test_http_granularity_field(void) {
    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    /* Reset path to avoid false positives */
    g_mock.last_path[0] = '\0';

    /* Serve the same simple response */
    mini_mock_enqueue_json(200, build_word_only_response_json());

    TypecastTTSRequestWithTimestamps req = {0};
    req.text        = "test";
    req.voice_id    = "tc_test";
    req.model       = TYPECAST_MODEL_SSFM_V30;
    req.granularity = "word";

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(resp);

    /* granularity is sent as a query parameter, not in the JSON body */
    ASSERT(strstr(g_mock.last_path, "granularity") != NULL);

    typecast_tts_with_timestamps_response_free(resp);
    typecast_client_destroy(client);
}

/* Test: path is /v1/text-to-speech/with-timestamps */
static void test_http_endpoint_path(void) {
    g_mock.last_path[0] = '\0';  /* reset to avoid false positives from prior test */

    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    mini_mock_enqueue_json(200, build_word_only_response_json());

    TypecastTTSRequestWithTimestamps req = {0};
    req.text     = "test";
    req.voice_id = "tc_test";
    req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    ASSERT(rc == TYPECAST_OK);
    ASSERT_NOT_NULL(resp);
    typecast_tts_with_timestamps_response_free(resp);

    /* Now check the path was actually recorded */
    ASSERT(strcmp(g_mock.last_path, "/v1/text-to-speech/with-timestamps") == 0);

    typecast_client_destroy(client);
}

/* Test: segment with missing text field returns NULL / safe result (no crash) */
static void test_http_malformed_segment_no_text(void) {
    TypecastClient* client = mini_new_client();
    ASSERT_NOT_NULL(client);

    /* Malformed: word segment has no "text" field */
    const char* bad_json =
        "{"
        "\"audio\":\"QVVESU8=\","
        "\"audio_format\":\"wav\","
        "\"audio_duration\":1.0,"
        "\"words\":["
            "{\"start\":0.1,\"end\":0.5}"  /* missing "text" */
        "],"
        "\"characters\":null"
        "}";
    mini_mock_enqueue_json(200, bad_json);

    TypecastTTSRequestWithTimestamps req = {0};
    req.text     = "test";
    req.voice_id = "tc_test";
    req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode rc = typecast_text_to_speech_with_timestamps(client, &req, &resp);
    /* Either an error is returned or resp has zero words — either way no crash */
    if (rc == TYPECAST_OK && resp != NULL) {
        ASSERT(resp->words_count == 0);
        typecast_tts_with_timestamps_response_free(resp);
    }

    typecast_client_destroy(client);
}

/* ============================================
 * main
 * ============================================ */

int main(void) {
    printf("===========================================\n");
    printf("Typecast C SDK - Timestamp TTS Tests\n");
    printf("===========================================\n\n");

    resolve_fixture_root();
    printf("Fixture root: %s\n\n", g_fixture_root);

    /* Start mini mock server */
    mini_mock_init();

    /* Byte-equality fixture tests */
    RUN(word_only_srt_vtt);
    RUN(char_only_srt_vtt);
    RUN(both_srt_vtt);
    RUN(jpn_char_srt_vtt);

    /* Unit / edge-case tests */
    RUN(srt_null_params);
    RUN(vtt_null_params);
    RUN(srt_no_segments);
    RUN(vtt_no_segments);
    RUN(audio_bytes_null);
    RUN(save_audio_null);
    RUN(free_null);
    RUN(audio_bytes_from_fixture);
    RUN(single_word_response);

    /* HTTP (mock) tests */
    RUN(http_with_timestamps_happy);
    RUN(http_null_params);
    RUN(http_401);
    RUN(http_network_error);
    RUN(http_invalid_json);
    RUN(http_granularity_field);
    RUN(http_endpoint_path);
    RUN(http_malformed_segment_no_text);

    mini_mock_shutdown();

    printf("\n===========================================\n");
    printf("Tests run: %d  passed: %d  failed: %d\n",
           g_tests_run, g_tests_passed, g_tests_failed);
    printf("===========================================\n");
    return g_tests_failed > 0 ? 1 : 0;
}
