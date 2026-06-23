package com.neosapience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypecastClientUserAgentTest {

    @Test
    void buildUserAgent_marksDefaultBaseUrl() {
        TypecastClient client = new TypecastClient("key");
        try {
            assertTrue(client.buildUserAgent().contains(" (base=default; timeout=30-60-60; os="));
            assertTrue(client.buildUserAgent().endsWith("; sdk_env=java; platform=server)"));
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

    @Test
    void normalizedOs_handlesCommonValues() {
        assertEquals("macos", TypecastClient.normalizedOs("Mac OS X"));
        assertEquals("macos", TypecastClient.normalizedOs("Darwin"));
        assertEquals("windows", TypecastClient.normalizedOs("Windows 11"));
        assertEquals("linux", TypecastClient.normalizedOs("Linux"));
        assertEquals("freebsd", TypecastClient.normalizedOs("FreeBSD"));
        assertEquals("unknown", TypecastClient.normalizedOs(null));
    }

    @Test
    void normalizedArch_handlesCommonValues() {
        assertEquals("x64", TypecastClient.normalizedArch("x86_64"));
        assertEquals("x64", TypecastClient.normalizedArch("amd64"));
        assertEquals("arm64", TypecastClient.normalizedArch("aarch64"));
        assertEquals("arm64", TypecastClient.normalizedArch("arm64"));
        assertEquals("x86", TypecastClient.normalizedArch("x86"));
        assertEquals("x86", TypecastClient.normalizedArch("i386"));
        assertEquals("x86", TypecastClient.normalizedArch("i686"));
        assertEquals("ppc64le", TypecastClient.normalizedArch("ppc64le"));
        assertEquals("unknown", TypecastClient.normalizedArch(null));
    }
}
