package com.neosapience.examples;

import com.neosapience.TypecastClient;
import com.neosapience.models.*;

import java.nio.file.Paths;
import java.nio.file.Files;

/**
 * Example: text-to-speech with word/character timestamps, SRT and VTT export.
 *
 * Run: mvn compile exec:java -Dexec.mainClass=com.neosapience.examples.WithTimestamps
 */
public class WithTimestamps {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("TYPECAST_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("TYPECAST_API_KEY not set");
            System.exit(1);
        }

        TypecastClient client = new TypecastClient(apiKey);

        TTSRequestWithTimestamps request = TTSRequestWithTimestamps.builder()
                .voiceId("tc_60e5426de8b95f1d3000d7b5")
                .text("Hello. How are you?")
                .model(TTSModel.SSFM_V30)
                .language(LanguageCode.ENG)
                .build();

        TTSWithTimestampsResponse resp = client.textToSpeechWithTimestamps(request, null);

        resp.saveAudio(Paths.get("/tmp/with_timestamps_java.wav"));
        Files.writeString(Paths.get("/tmp/with_timestamps_java.srt"), resp.toSrt());
        Files.writeString(Paths.get("/tmp/with_timestamps_java.vtt"), resp.toVtt());

        System.out.printf("audio: /tmp/with_timestamps_java.wav (%.2fs, format=%s)%n",
                resp.getAudioDuration(), resp.getAudioFormat());
        int wordCount = resp.getWords() != null ? resp.getWords().size() : 0;
        int charCount = resp.getCharacters() != null ? resp.getCharacters().size() : 0;
        System.out.printf("words: %d, characters: %d%n", wordCount, charCount);
        // Print first SRT cue
        String srt = resp.toSrt();
        String[] lines = srt.split("\n");
        int printCount = Math.min(4, lines.length);
        System.out.println("SRT first cue:");
        for (int i = 0; i < printCount; i++) System.out.println(lines[i]);

        client.close();
    }
}
