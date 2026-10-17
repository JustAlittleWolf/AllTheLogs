package me.wolfii.allthelogs.client.ui.screen.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchBarTest {
    @Test
    void acceptsPatternsUpToTwoToTheSixteenthCharacters() {
        assertEquals(1 << 16, SearchBar.MAX_LENGTH);
    }
}
