/* Streaming TTS integration test for typecast-c SDK. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include "typecast.h"

#define API_KEY  "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3"
#define HOST     "https://api.icepeak.in"
#define VOICE_ID "tc_68d259f809700d8ac76e8567"
#define OUTPUT_FILE "/tmp/streaming_test_c.wav"

static FILE *g_file = NULL;
static size_t g_total_bytes = 0;
static int g_chunk_count = 0;

static int stream_callback(const uint8_t *data, size_t len, void *user_data) {
    (void)user_data;
    if (g_file && len > 0) {
        fwrite(data, 1, len, g_file);
        g_total_bytes += len;
        g_chunk_count++;
    }
    return 0; /* 0 = continue streaming */
}

int main(void) {
    TypecastClient *client = typecast_client_create_with_host(API_KEY, HOST);
    if (!client) {
        fprintf(stderr, "[C] FAILED: Could not create client\n");
        return 1;
    }

    TypecastOutputStream output = TYPECAST_OUTPUT_STREAM_DEFAULT();

    TypecastTTSRequestStream request = {0};
    request.voice_id = VOICE_ID;
    request.text = "Hello, this is a streaming integration test from the C SDK.";
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    request.output = &output;

    g_file = fopen(OUTPUT_FILE, "wb");
    if (!g_file) {
        fprintf(stderr, "[C] FAILED: Could not open output file\n");
        typecast_client_destroy(client);
        return 1;
    }

    printf("[C] Calling typecast_text_to_speech_stream...\n");
    TypecastErrorCode err = typecast_text_to_speech_stream(client, &request, stream_callback, NULL);
    fclose(g_file);

    if (err != TYPECAST_OK) {
        fprintf(stderr, "[C] FAILED: error code %d\n", err);
        typecast_client_destroy(client);
        return 1;
    }

    printf("[C] SUCCESS - %d chunks, %zu bytes -> %s\n", g_chunk_count, g_total_bytes, OUTPUT_FILE);
    typecast_client_destroy(client);

    if (g_total_bytes == 0) {
        fprintf(stderr, "[C] FAILED: No audio data received\n");
        return 1;
    }
    return 0;
}
