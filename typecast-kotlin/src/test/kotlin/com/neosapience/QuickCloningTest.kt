package com.neosapience

import com.neosapience.exceptions.NotFoundException
import com.neosapience.models.CustomVoice
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for cloneVoice and deleteVoice using MockWebServer.
 */
class QuickCloningTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: TypecastClient

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        client = TypecastClient.builder()
            .apiKey("test-api-key")
            .baseUrl(mockServer.url("/").toString())
            .build()
    }

    @AfterEach
    fun tearDown() {
        client.close()
        mockServer.shutdown()
    }

    // ==================== cloneVoice Tests ====================

    @Test
    @DisplayName("cloneVoice returns a CustomVoice with correct fields")
    fun cloneVoiceReturnsCustomVoice() {
        val responseJson = """
            {"voice_id": "uc_abc123", "name": "My Voice", "model": "ssfm-v30"}
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson)
        )

        val audio = "fake-wav-data".toByteArray()
        val result = client.cloneVoice(audio, "sample.wav", "My Voice", "ssfm-v30")

        assertEquals("uc_abc123", result.voiceId)
        assertEquals("My Voice", result.name)
        assertEquals("ssfm-v30", result.model)
    }

    @Test
    @DisplayName("cloneVoice sends a multipart body with name, model, and file parts")
    fun cloneVoiceSendsMultipartBody() {
        val responseJson = """
            {"voice_id": "uc_abc123", "name": "TestVoice", "model": "ssfm-v30"}
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson)
        )

        val audio = "fake-audio".toByteArray()
        client.cloneVoice(audio, "voice.wav", "TestVoice", "ssfm-v30")

        val recorded = mockServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/voices/clone", recorded.path)
        assertEquals("test-api-key", recorded.getHeader("X-API-KEY"))

        val contentType = recorded.getHeader("Content-Type") ?: ""
        assertTrue(
            contentType.startsWith("multipart/"),
            "Expected multipart Content-Type, got: $contentType"
        )

        val bodyText = recorded.body.readUtf8()
        assertTrue(bodyText.contains("TestVoice"), "Body should contain name")
        assertTrue(bodyText.contains("ssfm-v30"), "Body should contain model")
        assertTrue(bodyText.contains("voice.wav"), "Body should contain filename")
    }

    @Test
    @DisplayName("cloneVoice rejects audio that exceeds the 25MB limit")
    fun cloneVoiceRejectsOversizedAudio() {
        val oversized = ByteArray((CustomVoice.CLONING_MAX_FILE_SIZE + 1).toInt())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            client.cloneVoice(oversized, "big.wav", "Voice", "ssfm-v30")
        }
        assertTrue(ex.message!!.contains("25MB"), "Error should mention the 25MB limit")

        // Verify no request was actually sent
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    @DisplayName("cloneVoice rejects names that are empty or exceed 30 characters")
    fun cloneVoiceRejectsBadNameLength() {
        val audio = "small".toByteArray()

        // Empty name (length < NAME_MIN_LENGTH)
        val emptyNameEx = assertThrows(IllegalArgumentException::class.java) {
            client.cloneVoice(audio, "sample.wav", "", "ssfm-v30")
        }
        assertTrue(emptyNameEx.message!!.isNotBlank())

        // Name that is 31 characters long (length > NAME_MAX_LENGTH)
        val longName = "a".repeat(CustomVoice.NAME_MAX_LENGTH + 1)
        val longNameEx = assertThrows(IllegalArgumentException::class.java) {
            client.cloneVoice(audio, "sample.wav", longName, "ssfm-v30")
        }
        assertTrue(longNameEx.message!!.isNotBlank())

        // No requests should have been sent
        assertEquals(0, mockServer.requestCount)
    }

    @Test
    @DisplayName("cloneVoice File overload reads bytes and delegates correctly")
    fun cloneVoiceFileOverloadDelegates() {
        val responseJson = """
            {"voice_id": "uc_file123", "name": "FileVoice", "model": "ssfm-v30"}
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson)
        )

        val tmp = File.createTempFile("voice_sample", ".wav")
        try {
            Files.write(tmp.toPath(), "fake-wav-bytes".toByteArray())
            val result = client.cloneVoice(tmp, "FileVoice", "ssfm-v30")
            assertEquals("uc_file123", result.voiceId)

            val recorded = mockServer.takeRequest()
            assertEquals("POST", recorded.method)
            val ct = recorded.getHeader("Content-Type") ?: ""
            assertTrue(ct.startsWith("multipart/"))
        } finally {
            tmp.delete()
        }
    }

    // ==================== deleteVoice Tests ====================

    @Test
    @DisplayName("deleteVoice succeeds on a 204 response")
    fun deleteVoiceSucceedsOn204() {
        mockServer.enqueue(
            MockResponse().setResponseCode(204)
        )

        assertDoesNotThrow {
            client.deleteVoice("uc_abc123")
        }

        val recorded = mockServer.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/v1/voices/uc_abc123", recorded.path)
        assertEquals("test-api-key", recorded.getHeader("X-API-KEY"))
    }

    @Test
    @DisplayName("deleteVoice throws NotFoundException on a 404 response")
    fun deleteVoiceThrowsOn404() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"detail": "Voice not found"}""")
        )

        assertThrows(NotFoundException::class.java) {
            client.deleteVoice("uc_nonexistent")
        }
    }

    // ==================== CustomVoice model coverage ====================

    @Test
    @DisplayName("CustomVoice data class methods work correctly")
    fun customVoiceDataClassMethods() {
        val v1 = CustomVoice("uc_abc", "My Voice", "ssfm-v30")
        val v2 = v1.copy()

        assertEquals(v1, v2)
        assertEquals(v1.hashCode(), v2.hashCode())
        assertTrue(v1.toString().contains("uc_abc"))
        assertEquals("uc_abc", v1.component1())
        assertEquals("My Voice", v1.component2())
        assertEquals("ssfm-v30", v1.component3())

        val different = v1.copy(voiceId = "uc_xyz")
        assertNotEquals(v1, different)
        assertFalse(v1.equals(null))
        assertFalse(v1.equals("string"))
    }

    @Test
    @DisplayName("CustomVoice companion object constants have expected values")
    fun customVoiceCompanionConstants() {
        assertEquals(25L * 1024 * 1024, CustomVoice.CLONING_MAX_FILE_SIZE)
        assertEquals(1, CustomVoice.NAME_MIN_LENGTH)
        assertEquals(30, CustomVoice.NAME_MAX_LENGTH)
    }

    @Test
    @DisplayName("cloneVoice sends audio/wav MIME type for .wav file")
    fun cloneVoiceWavMime() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"voice_id":"uc_1","name":"V","model":"ssfm-v30"}""")
        )
        client.cloneVoice("data".toByteArray(), "sample.wav", "V", "ssfm-v30")
        val body = mockServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("audio/wav"))
    }

    @Test
    @DisplayName("cloneVoice sends audio/mpeg MIME type for .mp3 file")
    fun cloneVoiceMp3Mime() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"voice_id":"uc_2","name":"V","model":"ssfm-v30"}""")
        )
        client.cloneVoice("data".toByteArray(), "sample.mp3", "V", "ssfm-v30")
        val body = mockServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("audio/mpeg"))
    }

    @Test
    @DisplayName("cloneVoice sends application/octet-stream for unknown extension")
    fun cloneVoiceUnknownMime() {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"voice_id":"uc_3","name":"V","model":"ssfm-v30"}""")
        )
        client.cloneVoice("data".toByteArray(), "sample.ogg", "V", "ssfm-v30")
        val body = mockServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("application/octet-stream"))
    }

    @Test
    @DisplayName("cloneVoice accepts a name exactly at the boundary lengths (1 and 30 chars)")
    fun cloneVoiceBoundaryNames() {
        // Min length = 1
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"voice_id":"uc_x","name":"V","model":"ssfm-v30"}""")
        )
        assertDoesNotThrow {
            client.cloneVoice("d".toByteArray(), "f.wav", "V", "ssfm-v30")
        }
        mockServer.takeRequest()

        // Max length = 30
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"voice_id":"uc_y","name":"VoiceName","model":"ssfm-v30"}""")
        )
        assertDoesNotThrow {
            client.cloneVoice("d".toByteArray(), "f.wav", "a".repeat(30), "ssfm-v30")
        }
        mockServer.takeRequest()
    }
}
