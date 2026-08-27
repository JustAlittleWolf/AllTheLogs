package me.wolfii.allthelogs.client.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {
    @Test
    void contextUsesASingleMediumGrey() {
        assertEquals(0xFFA4A4A4, Colors.CONTEXT_TEXT);
    }

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
    }

    @Test
    void multiplyStacksChannelsAndWhiteIsANoOp() {
        assertEquals(Colors.CONTEXT_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.CONTEXT_TEXT));
        assertEquals(Colors.MATCH_TEXT, Colors.multiply(Colors.MATCH_TEXT, Colors.MATCH_TEXT));
    }
}
