package me.wolfii.allthelogs.client.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {

    @Test
    void highlightIsTheSameGreenAtOneThirdOpacity() {
        assertEquals(0x54A8DC9C, Colors.MATCH_HIGHLIGHT);
        assertEquals(0xFFFFFFFF, Colors.MATCH_TEXT);
        assertEquals(0xA8DC9C, Colors.MATCH_HIGHLIGHT & 0xFFFFFF);
        assertEquals(84, (Colors.MATCH_HIGHLIGHT >>> 24) & 0xFF);
    }

    @Test
    void contextTimestampsAreSlightlyDarker() {
        assertEquals(0xFFA0A0A0, Colors.TIMESTAMP);
        assertEquals(0xFF7E7E7E, Colors.CONTEXT_TIMESTAMP);
        assertEquals(0xFF3A3A3A, Colors.SEPARATOR);
        assertEquals(0xFFE8A8A8, Colors.SEARCH_INVALID);
    }

    @Test
    void multiplyStacksChannelsAndWhiteIsANoOp() {
        assertEquals(Colors.CONTEXT_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.CONTEXT_TEXT));
        assertEquals(Colors.MATCH_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.MATCH_TEXT));
    }
}
