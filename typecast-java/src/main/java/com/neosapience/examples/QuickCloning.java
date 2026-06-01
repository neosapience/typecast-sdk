package com.neosapience.examples;

import com.neosapience.TypecastClient;
import com.neosapience.models.CustomVoice;
import com.neosapience.models.TTSModel;
import com.neosapience.models.TTSRequest;
import com.neosapience.models.TTSResponse;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Example: instant cloning — clone a voice from an audio file, synthesize
 * speech with it, then delete the voice.
 *
 * <p>Usage:</p>
 * <pre>
 *   export TYPECAST_API_KEY=your_api_key
 *   mvn compile exec:java \
 *       -Dexec.mainClass=com.neosapience.examples.QuickCloning \
 *       -Dexec.args="path/to/sample.wav"
 * </pre>
 */
public class QuickCloning {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("TYPECAST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("TYPECAST_API_KEY environment variable is not set.");
            System.exit(1);
        }

        if (args.length < 1) {
            System.err.println("Usage: QuickCloning <path-to-audio-file>");
            System.err.println("  Supported formats: WAV, MP3 (max 25 MB)");
            System.exit(1);
        }

        File audioFile = new File(args[0]);
        if (!audioFile.exists()) {
            System.err.println("File not found: " + audioFile.getAbsolutePath());
            System.exit(1);
        }

        TypecastClient client = new TypecastClient(apiKey);

        try {
            // Step 1: Clone the voice from the audio file
            System.out.println("Cloning voice from: " + audioFile.getName());
            CustomVoice cloned = client.cloneVoice(audioFile, "My Cloned Voice", "ssfm-v30");
            System.out.println("  Voice ID : " + cloned.getVoiceId());
            System.out.println("  Name     : " + cloned.getName());
            System.out.println("  Model    : " + cloned.getModel());

            // Step 2: Synthesize speech with the cloned voice
            System.out.println("\nSynthesizing speech...");
            TTSRequest request = TTSRequest.builder()
                    .voiceId(cloned.getVoiceId())
                    .text("Hello! This is my custom voice (created via instant cloning) speaking.")
                    .model(TTSModel.SSFM_V30)
                    .build();

            TTSResponse ttsResponse = client.textToSpeech(request);
            String outputPath = "cloned_output.wav";
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                fos.write(ttsResponse.getAudioData());
            }
            System.out.println("  Saved " + ttsResponse.getAudioData().length
                    + " bytes to " + outputPath
                    + " (duration: " + ttsResponse.getDuration() + "s)");

            // Step 3: Delete the voice when no longer needed
            System.out.println("\nDeleting cloned voice...");
            client.deleteVoice(cloned.getVoiceId());
            System.out.println("  Voice " + cloned.getVoiceId() + " deleted.");

        } finally {
            client.close();
        }
    }
}
