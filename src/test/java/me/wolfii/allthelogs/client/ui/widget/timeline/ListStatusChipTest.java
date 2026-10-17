package me.wolfii.allthelogs.client.ui.widget.timeline;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListStatusChipTest {
    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static Object[] args(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs();
    }
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

    @Test
    void theCountStaysUpAndLoadingWaitsASecondWithoutALiveTimer() {
        ListStatusChip chip = new ListStatusChip();
        chip.showTotalMatchCount(4, 80);
        assertEquals("allthelogs.status.messages", key(chip.text(60_000)));
        assertEquals(1, args(chip.text(60_000)).length);

        chip.showTotalMatchCount(4, 1_600);
        Component slow = chip.text(60_000);
        assertEquals("allthelogs.status.messages.timed", key(slow));
        assertEquals("1.6", args(slow)[1]);

        chip.showTotalMatchCount(4, 80);
        chip.setLoading(true, 10_000);
        assertEquals("allthelogs.status.messages", key(chip.text(10_050)));
        assertEquals("allthelogs.status.messages", key(chip.text(11_000)));
        Component loading = chip.text(11_001);
        assertEquals("allthelogs.status.loading", key(loading));

        chip.setLoading(false, 11_200);
        Component settled = chip.text(11_300);
        assertEquals("allthelogs.status.messages", key(settled));
        assertEquals("4", args(settled)[0]);
        assertEquals(80, chip.elapsedMs());

        chip.beginNewSearchCount();
        assertEquals("", chip.text(11_300).getString());
        assertFalse(chip.text(11_300).getContents() instanceof TranslatableContents);
        chip.setLoading(true, 20_000);
        assertEquals("", chip.text(21_000).getString());
        Component waiting = chip.text(21_001);
        assertEquals("allthelogs.status.loading", key(waiting));
    }
}
