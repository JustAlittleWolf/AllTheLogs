package me.wolfii.allthelogs.client.ui.widget;

import org.joml.Vector2ic;
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
        int gap = SearchDecorations.suffixGap();
        assertEquals(textX + 18 + gap, DecoratedSearchField.suffixX(textX, 18, gap, 3, 0, 3));
    }

    @Test
    void placeholderSitsBetweenTheRegexMarks() {
        int textX = 40;
        int hintWidth = 108;
        int gap = SearchDecorations.suffixGap();
        int content = DecoratedSearchField.contentWidth(0, true, hintWidth);
        assertEquals(hintWidth, content);
        assertEquals(textX + hintWidth + gap, DecoratedSearchField.suffixX(textX, content, gap, 0, 0, 0));
    }

    @Test
    void typedQueryKeepsTheClosingMarkOnTheText() {
        assertEquals(18, DecoratedSearchField.contentWidth(18, false, 108));
    }

    @Test
    void closingMarkStaysHiddenWhileTheEndIsScrolledAway() {
        assertEquals(-1, DecoratedSearchField.suffixX(40, 80, SearchDecorations.GAP, 40, 0, 12));
    }

    @Test
    void closingMarkReturnsWhenTheEndScrollsIntoView() {
        assertEquals(40 + 24 + SearchDecorations.GAP, DecoratedSearchField.suffixX(40, 24, SearchDecorations.GAP, 40, 28, 12));
    }

    @Test
    void regexTooltipSitsAboveTheSearchField() {
        Vector2ic at = DecoratedSearchField.placeTooltip(400, 20, 20, 800, 450, 180, 30);
        assertEquals(20, at.x());
        assertEquals(400 - 30 - 4, at.y());
    }

    @Test
    void regexTooltipDropsBelowWhenTheTopHasNoRoom() {
        Vector2ic at = DecoratedSearchField.placeTooltip(8, 20, 20, 800, 450, 180, 30);
        assertEquals(20, at.x());
        assertEquals(8 + 20 + 4, at.y());
    }

    @Test
    void regexTooltipShiftsLeftToStayOnScreen() {
        Vector2ic at = DecoratedSearchField.placeTooltip(400, 20, 700, 800, 450, 180, 30);
        assertEquals(800 - 180 - 2, at.x());
        assertEquals(400 - 30 - 4, at.y());
    }
}
