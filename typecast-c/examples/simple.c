/**
 * Typecast C SDK - Simple Example
 *
 * This example demonstrates basic usage of the Typecast TTS API:
 * 1. Create a client with API key
 * 2. List available voices
 * 3. Generate speech from text
 * 4. Save audio to a file
 *
 * Build:
 *   Compile with CMake (see CMakeLists.txt)
 *
 * Usage:
 *   ./example_simple <api_key>
 *   or set TYPECAST_API_KEY environment variable
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "typecast.h"

/* Helper function to get API key from args or environment */
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

/* Example: List available voices */
static void example_list_voices(TypecastClient* client) {
    printf("\n=== Listing Available Voices ===\n");
    
    TypecastVoicesResponse* voices = typecast_get_voices(client, NULL);
    if (!voices) {
        const TypecastError* err = typecast_client_get_error(client);
        printf("Error: %s (code: %d)\n", err->message, err->code);
        return;
    }
    
    printf("Found %zu voices:\n", voices->count);
    for (size_t i = 0; i < voices->count && i < 5; i++) {
        TypecastVoice* v = &voices->voices[i];
        printf("  - %s (%s)\n", v->voice_name, v->voice_id);
        
        /* Show supported models and emotions */
        for (size_t j = 0; j < v->models_count; j++) {
            printf("    Model: %s, Emotions: ", 
                typecast_model_to_string(v->models[j].version));
            for (size_t k = 0; k < v->models[j].emotions_count; k++) {
                printf("%s%s", v->models[j].emotions[k],
                    k < v->models[j].emotions_count - 1 ? ", " : "");
            }
            printf("\n");
        }
    }
    
    if (voices->count > 5) {
        printf("  ... and %zu more voices\n", voices->count - 5);
    }
    
    typecast_voices_response_free(voices);
}

/* Example: Generate speech */
static void example_text_to_speech(TypecastClient* client, const char* voice_id) {
    printf("\n=== Text-to-Speech Example ===\n");
    
    /* Prepare the TTS request */
    TypecastTTSRequest request = {0};
    request.text = "Hello! Welcome to Typecast. This is a demonstration of the C SDK.";
    request.voice_id = voice_id;
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    
    /* Optional: Configure emotion */
    TypecastPrompt prompt = TYPECAST_PROMPT_DEFAULT();
    prompt.emotion_type = TYPECAST_EMOTION_TYPE_PRESET;
    prompt.emotion_preset = TYPECAST_EMOTION_HAPPY;
    prompt.emotion_intensity = 1.2f;
    request.prompt = &prompt;
    
    /* Optional: Configure output */
    TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
    output.volume = 100;
    output.audio_format = TYPECAST_AUDIO_FORMAT_WAV;
    request.output = &output;
    
    printf("Generating speech...\n");
    printf("  Text: \"%s\"\n", request.text);
    printf("  Voice: %s\n", request.voice_id);
    printf("  Model: %s\n", typecast_model_to_string(request.model));
    printf("  Emotion: %s (intensity: %.1f)\n", 
        typecast_emotion_to_string(prompt.emotion_preset),
        prompt.emotion_intensity);
    
    /* Call TTS API */
    TypecastTTSResponse* response = typecast_text_to_speech(client, &request);
    if (!response) {
        const TypecastError* err = typecast_client_get_error(client);
        printf("Error: %s (code: %d)\n", err->message, err->code);
        return;
    }
    
    printf("\nSuccess!\n");
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    printf("  Format: %s\n", typecast_audio_format_to_string(response->format));
    
    /* Save to file */
    const char* filename = "output.wav";
    FILE* fp = fopen(filename, "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: %s\n", filename);
    } else {
        printf("  Warning: Could not save file\n");
    }
    
    typecast_tts_response_free(response);
}

/* Example: Smart emotion (context-aware) */
static void example_smart_emotion(TypecastClient* client, const char* voice_id) {
    printf("\n=== Smart Emotion Example ===\n");
    
    TypecastTTSRequest request = {0};
    request.text = "Everything is so incredibly perfect that I feel like I'm dreaming.";
    request.voice_id = voice_id;
    request.model = TYPECAST_MODEL_SSFM_V30;
    request.language = "eng";
    
    /* Use smart emotion with context */
    TypecastPrompt prompt = {0};
    prompt.emotion_type = TYPECAST_EMOTION_TYPE_SMART;
    prompt.previous_text = "I feel like I'm walking on air and I just want to scream with joy!";
    prompt.next_text = "I am literally bursting with happiness and I never want this feeling to end!";
    request.prompt = &prompt;
    
    TypecastOutput output = TYPECAST_OUTPUT_DEFAULT();
    request.output = &output;
    
    printf("Using smart emotion inference...\n");
    printf("  Previous: \"%s\"\n", prompt.previous_text);
    printf("  Current:  \"%s\"\n", request.text);
    printf("  Next:     \"%s\"\n", prompt.next_text);
    
    TypecastTTSResponse* response = typecast_text_to_speech(client, &request);
    if (!response) {
        const TypecastError* err = typecast_client_get_error(client);
        printf("Error: %s (code: %d)\n", err->message, err->code);
        return;
    }
    
    printf("\nSuccess!\n");
    printf("  Audio size: %zu bytes\n", response->audio_size);
    printf("  Duration: %.2f seconds\n", response->duration);
    
    /* Save to file */
    const char* filename = "output_smart.wav";
    FILE* fp = fopen(filename, "wb");
    if (fp) {
        fwrite(response->audio_data, 1, response->audio_size, fp);
        fclose(fp);
        printf("  Saved to: %s\n", filename);
    }
    
    typecast_tts_response_free(response);
}

int main(int argc, char* argv[]) {
    printf("Typecast C SDK Example\n");
    printf("Version: %s\n", typecast_version());
    printf("================================\n");
    
    /* Get API key */
    const char* api_key = get_api_key(argc, argv);
    if (!api_key) {
        fprintf(stderr, "Error: API key required\n");
        fprintf(stderr, "Usage: %s <api_key>\n", argv[0]);
        fprintf(stderr, "Or set TYPECAST_API_KEY environment variable\n");
        return 1;
    }
    
    /* Create client */
    TypecastClient* client = typecast_client_create(api_key);
    if (!client) {
        fprintf(stderr, "Error: Failed to create client\n");
        return 1;
    }
    
    printf("Client created successfully\n");
    
    /* Run examples */
    example_list_voices(client);
    
    /* Use a default voice for TTS examples */
    const char* default_voice = "tc_60e5426de8b95f1d3000d7b5";  /* Olivia */
    example_text_to_speech(client, default_voice);
    example_smart_emotion(client, default_voice);
    
    /* Cleanup */
    typecast_client_destroy(client);
    printf("\n=== Done ===\n");
    
    return 0;
}
