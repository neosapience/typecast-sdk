package com.neosapience

import com.neosapience.models.TTSModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SSFMV31Test {
    @Test fun modelRoundTrip() {
        assertEquals("\"ssfm-v31\"", Json.encodeToString(TTSModel.SSFM_V31))
        assertEquals(TTSModel.SSFM_V31, Json.decodeFromString<TTSModel>("\"ssfm-v31\""))
        assertEquals("ssfm-v30", TTSModel.SSFM_V30.value)
        assertEquals("ssfm-v21", TTSModel.SSFM_V21.value)
    }
}
