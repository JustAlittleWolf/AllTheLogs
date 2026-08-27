package me.wolfii.allthelogs.client.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorsTest {
    @Test
    void contextUsesASingleMediumGrey() {
        assertEquals(0xFFA4A4A4, Colors.CONTEXT_TEXT);
    }

    @Test
    void highlightIsASlightlyDarkerGreen() {
        assertEquals(0xFFA8DC9C, Colors.MATCH_HIGHLIGHT);
        assertEquals(0xFFFFFFFF, Colors.MATCH_TEXT);
    }
}
