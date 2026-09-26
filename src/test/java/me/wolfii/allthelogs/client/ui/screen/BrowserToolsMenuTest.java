package me.wolfii.allthelogs.client.ui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserToolsMenuTest {
    @Test
    void sideMenuFirstRowLinesUpWithTheSourceRow() {
        int rowY = 100;
        int padding = 3;
        int top = BrowserToolsMenu.submenuTop(rowY, padding, 48, 240);
        assertEquals(rowY, top + padding);
    }

    @Test
    void sideMenuMovesUpToStayOnScreen() {
        int menuHeight = 80;
        int screenHeight = 220;
        int top = BrowserToolsMenu.submenuTop(200, 3, menuHeight, screenHeight);
        assertTrue(top >= 4);
        assertTrue(top + menuHeight <= screenHeight - 4);
    }
}
