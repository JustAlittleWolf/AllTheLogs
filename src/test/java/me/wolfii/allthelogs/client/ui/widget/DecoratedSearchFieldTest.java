package me.wolfii.allthelogs.client.ui.widget;

import org.junit.jupiter.api.Test;

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
}
