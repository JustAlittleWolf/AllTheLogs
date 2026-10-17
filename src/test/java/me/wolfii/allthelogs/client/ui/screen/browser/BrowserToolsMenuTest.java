package me.wolfii.allthelogs.client.ui.screen.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserToolsMenuTest {
    @Test
    void toolsMenuDropsDownUnderTheHamburger() {
        assertEquals(220 + 53 - 90, BrowserToolsMenu.dropdownX(220, 53, 90));
        assertEquals(4, BrowserToolsMenu.dropdownX(6, 20, 80));
    }

    @Test
    void sideMenuOpensToTheLeftOfItsRow() {
        assertEquals(220 - 90 - 2, BrowserToolsMenu.sideLeft(220, 20, 90, 400));
    }

    @Test
    void sideMenuUsesTheRightSideOnlyWhenTheLeftSideDoesNotFit() {
        assertEquals(6 + 20 + 2, BrowserToolsMenu.sideLeft(6, 20, 80, 400));
    }

    @Test
    void anchoredNoticeDropsDownBelowItsButton() {
        assertEquals(30, BrowserToolsMenu.dropdownTop(8, 20, 2, 70, 240));
    }

    @Test
    void toolsMenuMovesUpOnlyWhenItWouldLeaveTheBottom() {
        assertEquals(120 - 4 - 70, BrowserToolsMenu.dropdownTop(80, 20, 2, 70, 120));
    }

    @Test
    void sideMenuDropsDownFromTheTopOfItsRow() {
        int rowTop = 40;
        int padding = 3;
        int height = 48;
        int top = BrowserToolsMenu.submenuTop(rowTop, padding, height, 240);
        assertEquals(rowTop, top + padding);
    }

    @Test
    void exportMenusAreCenteredOnTheirRowAndKeptOnScreen() {
        assertEquals(100 - 30, BrowserToolsMenu.centeredTop(100, 60, 240));
        assertEquals(4, BrowserToolsMenu.centeredTop(10, 60, 240));
        assertEquals(240 - 4 - 60, BrowserToolsMenu.centeredTop(230, 60, 240));
    }

    @Test
    void sideMenuStaysPutWhenItFitsBelowTheRow() {
        assertEquals(27, BrowserToolsMenu.submenuTop(30, 3, 80, 240));
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
    void outsideClickHidesTheOpenMenu() {
        assertTrue(BrowserToolsMenu.dismissOnClick(true, false));
        assertFalse(BrowserToolsMenu.dismissOnClick(true, true));
        assertFalse(BrowserToolsMenu.dismissOnClick(false, false));
    }

    @Test
    void sideMenuMovesUpOnlyWhenItWouldLeaveTheBottom() {
        int menuHeight = 80;
        int screenHeight = 220;
        int top = BrowserToolsMenu.submenuTop(180, 3, menuHeight, screenHeight);
        assertEquals(screenHeight - 4 - menuHeight, top);
    }
}
