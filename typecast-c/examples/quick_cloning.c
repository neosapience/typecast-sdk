/**
 * Typecast C SDK - Quick Voice Cloning Example
 *
 * Demonstrates how to clone a custom voice from an audio file and then
 * use it for text-to-speech synthesis.
 *
 * Build: compile with CMake (see CMakeLists.txt)
 *
 * Usage:
 *   ./example_quick_cloning <audio_file> [voice_name]
 *   or set TYPECAST_API_KEY environment variable
 *
 * Example:
 *   TYPECAST_API_KEY=your_key ./example_quick_cloning sample.wav "My Voice"
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "typecast.h"

static const char* get_api_key(int argc, char* argv[]) {
    (void)argc; (void)argv;
    const char* key = getenv("TYPECAST_API_KEY");
    if (key && strlen(key) > 0) return key;
    return NULL;
}

/* Read an entire file into a heap buffer.  Returns NULL on error. */
static unsigned char* read_file(const char* path, size_t* out_len) {
    FILE* f = fopen(path, "rb");
    if (!f) { fprintf(stderr, "Cannot open %s\n", path); return NULL; }

    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);

    if (size <= 0 || (size_t)size > (size_t)TYPECAST_CLONING_MAX_FILE_SIZE) {
        fprintf(stderr, "File size out of range (0 < size <= 25 MB)\n");
        fclose(f);
        return NULL;
    }

    unsigned char* buf = (unsigned char*)malloc((size_t)size);
    if (!buf) { fclose(f); return NULL; }

    if (fread(buf, 1, (size_t)size, f) != (size_t)size) {
        free(buf); fclose(f); return NULL;
    }
    fclose(f);
    *out_len = (size_t)size;
    return buf;
}

int main(int argc, char* argv[]) {
    if (argc < 2) {
        fprintf(stderr, "Usage: %s <audio_file> [voice_name]\n", argv[0]);
        return 1;
    }

    const char* audio_path = argv[1];
    const char* voice_name = (argc >= 3) ? argv[2] : "My Cloned Voice";

    const char* api_key = get_api_key(argc, argv);
    if (!api_key) {
        fprintf(stderr, "Set TYPECAST_API_KEY environment variable.\n");
        return 1;
    }

    /* --- Create client --- */
    TypecastClient* client = typecast_client_create(api_key);
    if (!client) {
        fprintf(stderr, "Failed to create client.\n");
        return 1;
    }

    /* --- Read audio file --- */
    size_t audio_len = 0;
    unsigned char* audio = read_file(audio_path, &audio_len);
    if (!audio) {
        typecast_client_destroy(client);
        return 1;
    }
    printf("Loaded audio: %s (%zu bytes)\n", audio_path, audio_len);

    /* --- Clone voice --- */
    printf("Cloning voice \"%s\" ...\n", voice_name);
    TypecastCustomVoice custom_voice;
    TypecastErrorCode rc = typecast_clone_voice(
        client,
        audio, audio_len,
        audio_path,       /* filename hint for MIME type detection */
        voice_name,
        "ssfm-v30",
        &custom_voice
    );
    free(audio);

    if (rc != TYPECAST_OK) {
        const TypecastError* err = typecast_client_get_error(client);
        fprintf(stderr, "Clone failed: %s (code %d)\n",
                err && err->message ? err->message : "unknown", rc);
        typecast_client_destroy(client);
        return 1;
    }

    printf("Clone successful!\n");
    printf("  voice_id : %s\n", custom_voice.voice_id);
    printf("  name     : %s\n", custom_voice.name);
    printf("  model    : %s\n", custom_voice.model);

    /* --- Use the cloned voice for TTS --- */
    printf("\nGenerating speech with the cloned voice...\n");

    TypecastTTSRequest tts_req;
    memset(&tts_req, 0, sizeof(tts_req));
    tts_req.text     = "Hello, this is my custom cloned voice speaking!";
    tts_req.voice_id = custom_voice.voice_id;
    tts_req.model    = TYPECAST_MODEL_SSFM_V30;

    TypecastTTSResponse* tts_resp = typecast_text_to_speech(client, &tts_req);
    if (tts_resp) {
        const char* out_path = "cloned_voice_output.wav";
        FILE* f = fopen(out_path, "wb");
        if (f) {
            fwrite(tts_resp->audio_data, 1, tts_resp->audio_size, f);
            fclose(f);
            printf("Audio saved to %s (%.2f seconds)\n",
                   out_path, tts_resp->duration);
        }
        typecast_tts_response_free(tts_resp);
    } else {
        const TypecastError* err = typecast_client_get_error(client);
        fprintf(stderr, "TTS failed: %s\n",
                err && err->message ? err->message : "unknown");
    }

    /* --- Delete the cloned voice --- */
    printf("\nDeleting cloned voice %s ...\n", custom_voice.voice_id);
    rc = typecast_delete_voice(client, custom_voice.voice_id);
    if (rc == TYPECAST_OK) {
        printf("Voice deleted.\n");
    } else {
        const TypecastError* err = typecast_client_get_error(client);
        fprintf(stderr, "Delete failed: %s (code %d)\n",
                err && err->message ? err->message : "unknown", rc);
    }

    typecast_client_destroy(client);
    return 0;
}
