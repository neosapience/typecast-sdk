package com.neosapience;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JavaInteropTest {
    @Test
    void staticFactoryIsCallableFromJava() {
        TypecastClient client = TypecastClient.create("test-key");
        client.close();
    }

    @Test
    void pauseParserIsCallableFromJava() {
        List<SpeechPart> parts = SpeechComposer.parsePauseMarkup("hello");

        assertEquals(1, parts.size());
        assertEquals("hello", parts.get(0).getText());
        assertFalse(parts.get(0).isPause());
    }
}
