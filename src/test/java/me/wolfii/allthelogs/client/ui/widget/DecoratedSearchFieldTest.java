package me.wolfii.allthelogs.client.ui.widget;

import org.junit.jupiter.api.Test;

import me.wolfii.allthelogs.client.search.SearchDecorations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecoratedSearchFieldTest {
    @Test
    void aScrollOffsetAtTheEndHidesTheRestoredQuery() {
        assertTrue(DecoratedSearchField.hidesText("session-query", "session-query".length()));
        assertTrue(DecoratedSearchField.hidesText("session-query", "session-query".length() + 4));
    }

    @Test
    void aVisibleWindowAndAnEmptyBoxAreNotHidden() {
        assertFalse(DecoratedSearchField.hidesText("session-query", 0));
        assertFalse(DecoratedSearchField.hidesText("session-query", 3));
        assertFalse(DecoratedSearchField.hidesText("", 0));
        assertFalse(DecoratedSearchField.hidesText(null, 1));
    }

    @Test
    void closingMarkFollowsTheVisibleQuery() {
        int textX = 40;
        int gap = SearchDecorations.GAP;
        assertEquals(textX + 18 + gap, DecoratedSearchField.suffixX(textX, 18, gap, 3, 0, 3));
    }

    @Test
    void closingMarkStaysHiddenWhileTheEndIsScrolledAway() {
        assertEquals(-1, DecoratedSearchField.suffixX(40, 80, SearchDecorations.GAP, 40, 0, 12));
    }

    @Test
    void closingMarkReturnsWhenTheEndScrollsIntoView() {
        assertEquals(40 + 24 + SearchDecorations.GAP, DecoratedSearchField.suffixX(40, 24, SearchDecorations.GAP, 40, 28, 12));
    }
}
