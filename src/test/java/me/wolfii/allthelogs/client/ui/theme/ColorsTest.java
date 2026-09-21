package me.wolfii.allthelogs.client.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {
    @Test
    void multiplyStacksChannelsAndWhiteIsANoOp() {
        assertEquals(Colors.ESCAPE_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.ESCAPE_TEXT));
        assertEquals(Colors.MATCH_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.MATCH_TEXT));
    }
}
