import com.neosapience.TypecastClient;
import com.neosapience.models.*;
import java.io.*;

public class TestJava {
    static final String API_KEY = "__pltWfi6S3QGbfLYmNtbF82DiNNxQ7LVNbaEvA6pnCH3";
    static final String HOST = "https://api.icepeak.in";
    static final String VOICE_ID = "tc_68d259f809700d8ac76e8567";
    static final String OUTPUT_FILE = "/tmp/streaming_test_java.wav";

    public static void main(String[] args) throws Exception {
        TypecastClient client = new TypecastClient(API_KEY, HOST);

        TTSRequestStream request = TTSRequestStream.builder()
            .voiceId(VOICE_ID)
            .text("Hello, this is a streaming integration test from the Java SDK.")
            .model(TTSModel.SSFM_V30)
            .language(LanguageCode.ENG)
            .output(com.neosapience.models.OutputStream.builder()
                .audioFormat(AudioFormat.WAV)
                .build())
            .build();

        System.out.println("[Java] Calling textToSpeechStream...");
        try (InputStream stream = client.textToSpeechStream(request);
             FileOutputStream fos = new FileOutputStream(OUTPUT_FILE)) {

            byte[] buffer = new byte[8192];
            int totalBytes = 0;
            int chunkCount = 0;
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                chunkCount++;
            }

            System.out.printf("[Java] SUCCESS - %d chunks, %d bytes -> %s%n", chunkCount, totalBytes, OUTPUT_FILE);
            if (totalBytes == 0) throw new RuntimeException("No audio data received");
        }
    }
}
