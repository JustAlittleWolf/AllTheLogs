package me.wolfii.allthelogs.client.ui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListStatusChipTest {
    @Test
    void pageCountsDoNotReplaceAnExactTotal() {
        ListStatusChip chip = new ListStatusChip();
        chip.showMatchCount(100, 12, false);
        assertEquals(100, chip.matchCount());
        assertFalse(chip.exactMatchCount());

        chip.showTotalMatchCount(1500, 80);
        assertEquals(1500, chip.matchCount());
        assertTrue(chip.exactMatchCount());

        chip.offerPageMatchCount(100, 0, false);
        assertEquals(1500, chip.matchCount());
        assertTrue(chip.exactMatchCount());
        assertEquals(80, chip.elapsedMs());
    }

    @Test
    void aNewSearchCanShowACappedPageCountAgain() {
        ListStatusChip chip = new ListStatusChip();
        chip.showTotalMatchCount(1500, 80);
        chip.beginNewSearchCount();
        assertFalse(chip.exactMatchCount());

        chip.offerPageMatchCount(100, 5, false);
        assertEquals(100, chip.matchCount());
        assertFalse(chip.exactMatchCount());
        assertEquals(5, chip.elapsedMs());
    }
}
