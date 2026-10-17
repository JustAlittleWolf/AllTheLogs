package me.wolfii.allthelogs.client.ui.screen.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchBarTest {
    @Test
    void acceptsPatternsUpToTwoToTheSixteenthCharacters() {
        assertEquals(1 << 16, SearchBar.MAX_LENGTH);
    }

    @Test
    void filterButtonFaceLinesUpWithTheSearchField() {
        int fieldHeight = 20;
        int frame = 1;
        int faceTop = SearchBar.FILTER_BUTTON_LIFT + frame;
        int faceHeight = SearchBar.FILTER_BUTTON_HEIGHT - frame * 2;
        assertEquals(0, faceTop);
        assertEquals(fieldHeight, faceHeight);
    }
}
