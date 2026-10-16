package me.wolfii.allthelogs.client.ui.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserToolsMenuTest {
    @Test
    void sideMenuGrowsUpSoItsLastRowMeetsTheSourceRow() {
        int rowBottom = 200;
        int padding = 3;
        int height = 48;
        int top = BrowserToolsMenu.submenuTop(rowBottom, padding, height, 240);
        assertEquals(rowBottom, top + height - padding);
    }

    @Test
    void sideMenuMovesDownOnlyWhenItWouldLeaveTheTop() {
        assertEquals(4, BrowserToolsMenu.submenuTop(30, 3, 80, 240));
    }

    @Test
    void leavingAMenuKeepsItOpenForTheCloseDelay() {
        long deadline = BrowserToolsMenu.expire(Long.MAX_VALUE, 1_000);
        assertEquals(1_000 + BrowserToolsMenu.CLOSE_DELAY_MS, deadline);
        assertEquals(deadline, BrowserToolsMenu.expire(deadline, 1_500));
    }

    @Test
    void scrollbarDragBlocksTheHoverMenu() {
        assertTrue(BrowserToolsMenu.blockHoverOpen(false, true, false));
        assertTrue(BrowserToolsMenu.blockHoverOpen(false, true, true));
    }

    @Test
    void releasingTheScrollbarOnTheButtonKeepsTheMenuClosed() {
        assertTrue(BrowserToolsMenu.blockHoverOpen(true, false, true));
    }

    @Test
    void leavingTheButtonAfterAScrollbarDragAllowsHoverAgain() {
        assertFalse(BrowserToolsMenu.blockHoverOpen(true, false, false));
        assertFalse(BrowserToolsMenu.blockHoverOpen(false, false, true));
    }

    @Test
    void sideMenuMovesUpToStayOnScreen() {
        int menuHeight = 80;
        int screenHeight = 220;
        int top = BrowserToolsMenu.submenuTop(250, 3, menuHeight, screenHeight);
        assertTrue(top >= 4);
        assertTrue(top + menuHeight <= screenHeight - 4);
    }
}
