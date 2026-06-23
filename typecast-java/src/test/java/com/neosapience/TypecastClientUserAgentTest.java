package com.neosapience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypecastClientUserAgentTest {

    @Test
    void buildUserAgent_marksDefaultBaseUrl() {
        TypecastClient client = new TypecastClient("key");
        try {
            assertTrue(client.buildUserAgent().endsWith(" (base=default; timeout=30-60-60)"));
        } finally {
            client.close();
        }
    }

    @Test
    void versionOrFallback_handlesPackageMetadata() {
        assertEquals("dev", TypecastClient.versionOrFallback(null, "dev"));
        assertEquals("1.2.3", TypecastClient.versionOrFallback("1.2.3", "dev"));
    }

    @Test
    void majorJavaVersion_handlesLegacyAndModernFormats() {
        assertEquals("8", TypecastClient.majorJavaVersion("1.8.0_392"));
        assertEquals("17", TypecastClient.majorJavaVersion("17.0.10"));
        assertEquals("unknown", TypecastClient.majorJavaVersion("unknown"));
    }
}
