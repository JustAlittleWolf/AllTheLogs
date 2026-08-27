package me.wolfii.allthelogs.client.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextColorsTest {
    @Test
    void contextUsesASingleMediumGrey() {
        assertEquals(0xFFA0A0A0, ContextColors.CONTEXT_TEXT);
        assertEquals(ContextColors.TIMESTAMP, ContextColors.CONTEXT_TEXT);
    }

    @Test
    void highlightIsASlightlyDarkerGreen() {
        assertEquals(0xFFA8DC9C, ContextColors.MATCH_HIGHLIGHT);
        assertEquals(0xFFFFFFFF, ContextColors.MATCH_TEXT);
    }
}
