package me.wolfii.allthelogs.client.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(0xFF686868, Colors.CONTEXT_TEXT);
        assertEquals(0xFF5A5A5A, Colors.CONTEXT_BAR);
        assertTrue((Colors.CONTEXT_TEXT & 0xFF) < (Colors.CONTEXT_TIMESTAMP & 0xFF));
        assertTrue((Colors.CONTEXT_BAR & 0xFF) < (Colors.CONTEXT_TIMESTAMP & 0xFF));
        assertEquals(2, Colors.CONTEXT_BAR_WIDTH);
        assertEquals(0xFF3A3A3A, Colors.SEPARATOR);
        assertEquals(0xFFE8A8A8, Colors.SEARCH_INVALID);
    }

    @Test
    void multiplyStacksChannelsAndWhiteIsANoOp() {
        assertEquals(Colors.ESCAPE_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.ESCAPE_TEXT));
        assertEquals(Colors.MATCH_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.MATCH_TEXT));
    }
}
