/**
 * Example: text-to-speech with word/character timestamps, SRT and VTT export.
 *
 * Build: see CMakeLists.txt or use the Makefile
 * Usage: TYPECAST_API_KEY=your-key ./with_timestamps
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "typecast.h"

int main(void) {
    const char* api_key = getenv("TYPECAST_API_KEY");
    if (!api_key || strlen(api_key) == 0) {
        fprintf(stderr, "Error: TYPECAST_API_KEY not set\n");
        return 1;
    }

    TypecastClient* client = typecast_client_create(api_key);
    if (!client) {
        fprintf(stderr, "Error: Failed to create client\n");
        return 1;
    }

    TypecastTTSRequestWithTimestamps request = {0};
    request.voice_id  = "tc_60e5426de8b95f1d3000d7b5";
    request.text      = "Hello. How are you?";
    request.model     = TYPECAST_MODEL_SSFM_V30;
    request.language  = "eng";
    request.granularity = NULL; /* return both words and characters */

    TypecastTTSWithTimestampsResponse* resp = NULL;
    TypecastErrorCode code = typecast_text_to_speech_with_timestamps(client, &request, &resp);
    if (code != TYPECAST_OK || !resp) {
        const TypecastError* err = typecast_client_get_error(client);
        fprintf(stderr, "Error: %s (code: %d)\n", err->message, err->code);
        typecast_client_destroy(client);
        return 1;
    }

    /* Save audio */
    TypecastErrorCode save_code = typecast_tts_with_timestamps_response_save_audio(
        resp, "/tmp/with_timestamps_c.wav");
    if (save_code != TYPECAST_OK) {
        fprintf(stderr, "Error saving audio: %d\n", save_code);
    }

    /* Get SRT */
    char* srt = NULL;
    typecast_tts_with_timestamps_response_to_srt(resp, &srt);
    if (srt) {
        FILE* f = fopen("/tmp/with_timestamps_c.srt", "w");
        if (f) { fputs(srt, f); fclose(f); }
        free(srt);
    }

    /* Get VTT */
    char* vtt = NULL;
    typecast_tts_with_timestamps_response_to_vtt(resp, &vtt);
    if (vtt) {
        FILE* f = fopen("/tmp/with_timestamps_c.vtt", "w");
        if (f) { fputs(vtt, f); fclose(f); }
        free(vtt);
    }

    printf("audio: /tmp/with_timestamps_c.wav (%.2fs, format=%s)\n",
           resp->audio_duration, resp->audio_format);
    printf("words: %zu, characters: %zu\n",
           resp->words_count, resp->characters_count);

    /* Print first SRT cue: re-generate to print */
    char* srt2 = NULL;
    typecast_tts_with_timestamps_response_to_srt(resp, &srt2);
    if (srt2) {
        printf("SRT first cue:\n");
        /* Print up to first double-newline */
        int newlines = 0;
        for (char* p = srt2; *p; p++) {
            putchar(*p);
            if (*p == '\n') {
                newlines++;
                if (newlines >= 3) break; /* cue_index\ntimeline\ntext */
            }
        }
        printf("\n");
        free(srt2);
    }

    typecast_tts_with_timestamps_response_free(resp);
    typecast_client_destroy(client);
    return 0;
}
