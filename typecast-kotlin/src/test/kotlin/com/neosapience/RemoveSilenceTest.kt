package com.neosapience

import com.neosapience.models.Output
import com.neosapience.models.OutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RemoveSilenceTest {
    @Test fun optionalZeroAndRange() {
        assertFalse(Json.encodeToString(Output()).contains("remove_silence_ms"))
        assertFalse(Json.encodeToString(OutputStream()).contains("remove_silence_ms"))
        for (value in listOf(0, 300, 1000)) {
            val output = Output.builder().removeSilenceMs(value).build()
            val stream = OutputStream.builder().removeSilenceMs(value).build()
            assertEquals(value, stream.removeSilenceMs)
            assertTrue(Json.encodeToString(output).contains("\"remove_silence_ms\":$value"))
            assertTrue(Json.encodeToString(stream).contains("\"remove_silence_ms\":$value"))
        }
        for (value in listOf(-1, 1001)) {
            assertThrows(IllegalArgumentException::class.java) { Output(removeSilenceMs = value) }
            assertThrows(IllegalArgumentException::class.java) { OutputStream(removeSilenceMs = value) }
        }
    }
}
