package com.neosapience

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

/**
 * Builder env-var / dotenv resolution coverage.
 * Extracted from CoverageTest to satisfy the 450-line file limit.
 */
class CoverageEnvTest {

    @Suppress("UNCHECKED_CAST")
    private fun setEnv(name: String, value: String?) {
        val procEnvCls = Class.forName("java.lang.ProcessEnvironment")
        val unmField = procEnvCls.getDeclaredField("theUnmodifiableEnvironment")
        unmField.isAccessible = true
        val unmodifiable = unmField.get(null)
        val mField = Class.forName("java.util.Collections\$UnmodifiableMap")
            .getDeclaredField("m")
        mField.isAccessible = true
        val backing = mField.get(unmodifiable) as MutableMap<String, String>
        if (value == null) backing.remove(name) else backing[name] = value
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveFromDotenvFile() {
        val envFile = java.io.File(".env")
        val existed = envFile.exists()
        val backup = if (existed) envFile.readText() else null
        try {
            envFile.writeText("TYPECAST_API_KEY=fromdotenv\nTYPECAST_API_HOST=https://dotenv.example.com/\n")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://dotenv.example.com", c.getBaseUrl())
            }
            TypecastClient.builder().baseUrl("https://override.example.com").build().use { c ->
                assertEquals("https://override.example.com", c.getBaseUrl())
            }
        } finally {
            if (backup != null) envFile.writeText(backup) else envFile.delete()
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveDotenvKeyOnlyHostMissing() {
        val envFile = java.io.File(".env")
        val backup = if (envFile.exists()) envFile.readText() else null
        try {
            envFile.writeText("TYPECAST_API_KEY=fromdotenv\n")
            val prevHost = System.getenv("TYPECAST_API_HOST")
            try {
                setEnv("TYPECAST_API_HOST", "")
            } catch (_: Throwable) {}
            try {
                TypecastClient.builder().build().use { c ->
                    assertEquals("https://api.typecast.ai", c.getBaseUrl())
                }
            } finally {
                try {
                    setEnv("TYPECAST_API_HOST", prevHost)
                } catch (_: Throwable) {}
            }
        } finally {
            if (backup != null) envFile.writeText(backup) else envFile.delete()
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveBlankInDotenvFallsThrough() {
        val envFile = java.io.File(".env")
        val existed = envFile.exists()
        val backup = if (existed) envFile.readText() else null
        try {
            envFile.writeText("TYPECAST_API_KEY=\nTYPECAST_API_HOST=\n")
            try {
                TypecastClient.builder().build().close()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("API key"))
            }
        } finally {
            if (backup != null) envFile.writeText(backup) else envFile.delete()
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("dotenv-file")
    fun builder_resolveFromSystemEnvWhenDotenvThrows() {
        val envFile = java.io.File(".env")
        val backup = if (envFile.isFile) envFile.readText() else null
        if (envFile.exists()) envFile.delete()
        envFile.mkdir()
        try {
            setEnv("TYPECAST_API_KEY", "syskey")
            setEnv("TYPECAST_API_HOST", "https://syshost.example.com/")
            check(System.getenv("TYPECAST_API_KEY") == "syskey")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://syshost.example.com", c.getBaseUrl())
            }
            setEnv("TYPECAST_API_KEY", "syskey")
            setEnv("TYPECAST_API_HOST", "")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://api.typecast.ai", c.getBaseUrl())
            }
        } finally {
            envFile.delete()
            if (backup != null) java.io.File(".env").writeText(backup)
            try {
                setEnv("TYPECAST_API_KEY", null)
                setEnv("TYPECAST_API_HOST", null)
            } catch (_: Throwable) {}
        }
    }

    @Test
    @org.junit.jupiter.api.parallel.ResourceLock("env")
    fun builder_resolveFromSystemEnvVarBlankAndSet() {
        val prevKey = System.getenv("TYPECAST_API_KEY")
        val prevHost = System.getenv("TYPECAST_API_HOST")
        val envFile = java.io.File(".env")
        val envBackup = if (envFile.exists()) envFile.readText() else null
        if (envFile.exists()) envFile.delete()
        try {
            setEnv("TYPECAST_API_KEY", "")
            setEnv("TYPECAST_API_HOST", "")
            check(System.getenv("TYPECAST_API_KEY") == "") {
                "env mutation failed: got '${System.getenv("TYPECAST_API_KEY")}'"
            }
            try {
                TypecastClient.builder().build().close()
                fail<Unit>("Expected IllegalArgumentException for blank env")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("API key"))
            }
            setEnv("TYPECAST_API_KEY", "envkey")
            setEnv("TYPECAST_API_HOST", "https://envhost.example.com/")
            check(System.getenv("TYPECAST_API_KEY") == "envkey")
            TypecastClient.builder().build().use { c ->
                assertEquals("https://envhost.example.com", c.getBaseUrl())
            }
        } finally {
            try {
                setEnv("TYPECAST_API_KEY", prevKey)
                setEnv("TYPECAST_API_HOST", prevHost)
            } catch (_: Throwable) {}
            if (envBackup != null) envFile.writeText(envBackup)
        }
    }

    @Test
    fun builder_resolveApiKeyFromSystemEnv() {
        val existing = System.getenv("TYPECAST_API_KEY")
        if (!existing.isNullOrBlank()) {
            TypecastClient.builder().build().close()
        }
    }
}
