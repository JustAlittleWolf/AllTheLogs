package me.wolfii.allthelogs.data.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryMatchTest {
    @Test
    void windowIsThreeSeconds() {
        assertEquals(3, EntryMatch.WINDOW_SECONDS);
        assertTrue(EntryMatch.withinWindow("a", "b").contains("3000"));
    }

    @Test
    void sameTextNormalisesBackslashNOnBothSides() {
        String sql = EntryMatch.sameText("left.message", "right.message");
        assertTrue(sql.contains("chr(92) || 'n'"));
        assertTrue(sql.contains("left.message"));
        assertTrue(sql.contains("right.message"));
    }

    @Test
    void sameAsNormalizedOnlyRewritesTheRawColumn() {
        String sql = EntryMatch.sameAsNormalized("e.message", "p.text");
        assertEquals("replace(e.message, chr(92) || 'n', chr(10)) = p.text", sql);
    }
}
