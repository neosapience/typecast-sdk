package com.neosapience;

import com.neosapience.exceptions.NotFoundException;
import com.neosapience.models.CustomVoice;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for cloneVoice and deleteVoice using MockWebServer.
 *
 * Test order:
 *   1. cloneVoiceReturnsCustomVoice
 *   2. cloneVoiceSendsMultipartBody
 *   3. cloneVoiceRejectsOversizedAudio
 *   4. cloneVoiceRejectsBadNameLength
 *   5. deleteVoiceSucceedsOn204
 *   6. deleteVoiceThrowsOn404
 */
class QuickCloningTest {

    private MockWebServer mockServer;
    private TypecastClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        String baseUrl = mockServer.url("/").toString();
        baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        client = new TypecastClient("test-api-key", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        mockServer.shutdown();
    }

    // ==================== Test 1: cloneVoiceReturnsCustomVoice ====================

    @Test
    @DisplayName("cloneVoice returns a CustomVoice parsed from 200 JSON response")
    void cloneVoiceReturnsCustomVoice() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_abc123\",\"name\":\"My Voice\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[1024]; // 1 KB — well under 25 MB limit
        CustomVoice result = client.cloneVoice(audio, "sample.wav", "My Voice", "ssfm-v30");

        assertNotNull(result);
        assertEquals("uc_abc123", result.getVoiceId());
        assertEquals("My Voice", result.getName());
        assertEquals("ssfm-v30", result.getModel());
    }

    // ==================== Test 2: cloneVoiceSendsMultipartBody ====================

    @Test
    @DisplayName("cloneVoice sends POST to /v1/voices/clone with multipart/form-data body")
    void cloneVoiceSendsMultipartBody() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_abc\",\"name\":\"Test\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[512];
        client.cloneVoice(audio, "voice.wav", "Test", "ssfm-v30");

        RecordedRequest request = mockServer.takeRequest();

        assertEquals("POST", request.getMethod());
        assertEquals("/v1/voices/clone", request.getPath());

        String contentType = request.getHeader("Content-Type");
        assertNotNull(contentType);
        assertTrue(contentType.startsWith("multipart/form-data; boundary="),
                "Content-Type should start with multipart/form-data; boundary=, got: " + contentType);

        String body = request.getBody().readUtf8();
        // Verify multipart part names are present
        assertTrue(body.contains("name=\"name\""),
                "Body should contain form field 'name'");
        assertTrue(body.contains("name=\"model\""),
                "Body should contain form field 'model'");
        assertTrue(body.contains("name=\"file\""),
                "Body should contain form field 'file'");

        // Verify API key header
        assertEquals("test-api-key", request.getHeader("X-API-KEY"));
    }

    // ==================== Test 3: cloneVoiceRejectsOversizedAudio ====================

    @Test
    @DisplayName("cloneVoice rejects audio exceeding 25 MB without sending any request")
    void cloneVoiceRejectsOversizedAudio() {
        int requestCountBefore = mockServer.getRequestCount();

        // 26 MB — one byte over the 25 MB limit
        byte[] oversized = new byte[26 * 1024 * 1024];

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> client.cloneVoice(oversized, "big.wav", "Big Voice", "ssfm-v30")
        );

        assertTrue(ex.getMessage().contains("25MB"),
                "Exception message should mention 25MB limit");

        // No HTTP request should have been made
        assertEquals(requestCountBefore, mockServer.getRequestCount(),
                "No HTTP request should be sent when audio exceeds size limit");
    }

    // ==================== Test 4: cloneVoiceRejectsBadNameLength ====================

    @Test
    @DisplayName("cloneVoice rejects empty name and name exceeding 30 characters")
    void cloneVoiceRejectsBadNameLength() {
        byte[] audio = new byte[1024];

        // Empty name (length 0 — below NAME_MIN_LENGTH of 1)
        IllegalArgumentException emptyEx = assertThrows(
                IllegalArgumentException.class,
                () -> client.cloneVoice(audio, "sample.wav", "", "ssfm-v30")
        );
        assertTrue(emptyEx.getMessage().contains("1"),
                "Exception should mention minimum length 1");

        // 31-character name (above NAME_MAX_LENGTH of 30)
        String longName = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"; // 31 chars
        assertEquals(31, longName.length());
        IllegalArgumentException longEx = assertThrows(
                IllegalArgumentException.class,
                () -> client.cloneVoice(audio, "sample.wav", longName, "ssfm-v30")
        );
        assertTrue(longEx.getMessage().contains("30"),
                "Exception should mention maximum length 30");

        // No requests should have been sent
        assertEquals(0, mockServer.getRequestCount());
    }

    // ==================== Test 5: deleteVoiceSucceedsOn204 ====================

    @Test
    @DisplayName("deleteVoice succeeds on 204 and sends DELETE to correct path")
    void deleteVoiceSucceedsOn204() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(204));

        // Should not throw
        assertDoesNotThrow(() -> client.deleteVoice("uc_xxx"));

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("DELETE", request.getMethod());
        assertTrue(request.getPath().endsWith("/v1/voices/uc_xxx"),
                "Path should end with /v1/voices/uc_xxx, got: " + request.getPath());
        assertEquals("test-api-key", request.getHeader("X-API-KEY"));
    }

    // ==================== Test 6: deleteVoiceThrowsOn404 ====================

    @Test
    @DisplayName("deleteVoice throws NotFoundException on 404")
    void deleteVoiceThrowsOn404() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("{\"detail\": \"Voice not found\"}"));

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> client.deleteVoice("uc_nonexistent")
        );

        assertEquals(404, ex.getStatusCode());
    }

    // ==================== Additional coverage tests ====================

    @Test
    @DisplayName("cloneVoice sends correct MIME type for .mp3 files")
    void cloneVoiceSendsMp3MimeType() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_mp3\",\"name\":\"MP3 Voice\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[512];
        CustomVoice result = client.cloneVoice(audio, "voice.mp3", "MP3 Voice", "ssfm-v30");

        assertNotNull(result);
        assertEquals("uc_mp3", result.getVoiceId());

        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        // MP3 MIME type should be present in multipart body
        assertTrue(body.contains("audio/mpeg"),
                "Body should contain audio/mpeg content type for .mp3 file");
    }

    @Test
    @DisplayName("cloneVoice uses application/octet-stream for unknown extensions")
    void cloneVoiceSendsOctetStreamForUnknownExtension() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_ogg\",\"name\":\"Ogg Voice\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[512];
        CustomVoice result = client.cloneVoice(audio, "voice.ogg", "Ogg Voice", "ssfm-v30");

        assertNotNull(result);
        RecordedRequest request = mockServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("application/octet-stream"),
                "Body should contain application/octet-stream for unknown file type");
    }

    @Test
    @DisplayName("cloneVoice File overload reads file bytes and delegates correctly")
    void cloneVoiceFileOverload() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_file\",\"name\":\"File Voice\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        // Create a small temp WAV file
        File tempFile = File.createTempFile("cloning_test_", ".wav");
        tempFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(new byte[]{0x52, 0x49, 0x46, 0x46}); // RIFF header stub
        }

        CustomVoice result = client.cloneVoice(tempFile, "File Voice", "ssfm-v30");

        assertNotNull(result);
        assertEquals("uc_file", result.getVoiceId());

        RecordedRequest request = mockServer.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("/v1/voices/clone", request.getPath());
        // Filename from File object should be used
        String body = request.getBody().readUtf8();
        assertTrue(body.contains(tempFile.getName()),
                "Body should contain the file name: " + tempFile.getName());
    }

    @Test
    @DisplayName("CustomVoice model class has correct constants and accessors")
    void customVoiceModelAndConstants() {
        CustomVoice voice = new CustomVoice("uc_test", "Test Name", "ssfm-v30");

        assertEquals("uc_test", voice.getVoiceId());
        assertEquals("Test Name", voice.getName());
        assertEquals("ssfm-v30", voice.getModel());

        // Verify constants
        assertEquals(25L * 1024 * 1024, CustomVoice.CLONING_MAX_FILE_SIZE);
        assertEquals(1, CustomVoice.NAME_MIN_LENGTH);
        assertEquals(30, CustomVoice.NAME_MAX_LENGTH);

        // toString and setters
        assertNotNull(voice.toString());

        // No-arg constructor + setters
        CustomVoice voice2 = new CustomVoice();
        voice2.setVoiceId("uc_2");
        voice2.setName("Name2");
        voice2.setModel("ssfm-v21");
        assertEquals("uc_2", voice2.getVoiceId());
        assertEquals("Name2", voice2.getName());
        assertEquals("ssfm-v21", voice2.getModel());
        assertNotNull(voice2.toString());
    }

    @Test
    @DisplayName("cloneVoice accepts name of exactly 1 character (min boundary)")
    void cloneVoiceAcceptsMinLengthName() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_min\",\"name\":\"A\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[512];
        CustomVoice result = client.cloneVoice(audio, "sample.wav", "A", "ssfm-v30");
        assertEquals("uc_min", result.getVoiceId());
    }

    @Test
    @DisplayName("cloneVoice accepts name of exactly 30 characters (max boundary)")
    void cloneVoiceAcceptsMaxLengthName() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_max\",\"name\":\"123456789012345678901234567890\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[512];
        String maxName = "123456789012345678901234567890"; // exactly 30 chars
        assertEquals(30, maxName.length());
        CustomVoice result = client.cloneVoice(audio, "sample.wav", maxName, "ssfm-v30");
        assertEquals("uc_max", result.getVoiceId());
    }

    @Test
    @DisplayName("cloneVoice accepts audio of exactly 25 MB (max boundary)")
    void cloneVoiceAcceptsMaxSizeAudio() throws Exception {
        String responseJson = "{\"voice_id\":\"uc_25mb\",\"name\":\"Big\",\"model\":\"ssfm-v30\"}";
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        byte[] audio = new byte[25 * 1024 * 1024]; // exactly 25 MB
        CustomVoice result = client.cloneVoice(audio, "sample.wav", "Big", "ssfm-v30");
        assertEquals("uc_25mb", result.getVoiceId());
    }

    @Test
    @DisplayName("deleteVoice succeeds on 200 response")
    void deleteVoiceSucceedsOn200() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"voice_id\":\"uc_del\"}"));

        // Should not throw
        assertDoesNotThrow(() -> client.deleteVoice("uc_del"));
    }

    @Test
    @DisplayName("cloneVoice throws TypecastException on 401 response")
    void cloneVoiceThrowsOnErrorResponse() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"detail\": \"Unauthorized\"}"));

        byte[] audio = new byte[512];
        assertThrows(
                com.neosapience.exceptions.UnauthorizedException.class,
                () -> client.cloneVoice(audio, "sample.wav", "My Voice", "ssfm-v30")
        );
    }

    @Test
    @DisplayName("cloneVoice throws TypecastException when server is unreachable")
    void cloneVoiceThrowsOnIoException() throws IOException {
        mockServer.shutdown();

        byte[] audio = new byte[512];
        assertThrows(
                com.neosapience.exceptions.TypecastException.class,
                () -> client.cloneVoice(audio, "sample.wav", "My Voice", "ssfm-v30")
        );
    }

    @Test
    @DisplayName("deleteVoice throws TypecastException when server is unreachable")
    void deleteVoiceThrowsOnIoException() throws IOException {
        mockServer.shutdown();

        assertThrows(
                com.neosapience.exceptions.TypecastException.class,
                () -> client.deleteVoice("uc_xxx")
        );
    }
}
