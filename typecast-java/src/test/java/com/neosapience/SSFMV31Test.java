package com.neosapience;

import com.neosapience.models.TTSModel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SSFMV31Test {
    @Test void roundTripPreservesLegacyModels() {
        assertEquals("ssfm-v31", TTSModel.SSFM_V31.getValue());
        assertEquals(TTSModel.SSFM_V31, TTSModel.fromValue("ssfm-v31"));
        assertEquals("ssfm-v21", TTSModel.SSFM_V21.getValue());
        assertEquals("ssfm-v30", TTSModel.SSFM_V30.getValue());
    }
}
