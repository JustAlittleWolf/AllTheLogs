package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.data.LogStoreMetadata;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreSummaryTest {
    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    @Test
    void formatBytesPicksTheSmallestFittingUnit() {
        assertEquals("512 B", StoreSummary.formatBytes(512));
        assertEquals("1.5 KB", StoreSummary.formatBytes(1536));
        assertEquals("1.0 MB", StoreSummary.formatBytes(1024 * 1024));
        assertEquals("1.0 GB", StoreSummary.formatBytes(1024L * 1024 * 1024));
    }

    @Test
    void tooltipOmitsMinecraftVersionsAndUsesDateBounds() {
        LogStoreMetadata metadata = new LogStoreMetadata(
            List.of("1.8.9", "26.2"),
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2026, 8, 1),
            3, 40, 2048);
        List<Component> lines = StoreSummary.tooltip(metadata);
        assertEquals("allthelogs.meta.hint", key(lines.getFirst()));
        assertEquals("allthelogs.meta.range", key(lines.get(1)));
        assertTrue(lines.stream().noneMatch(line -> "allthelogs.meta.versions".equals(key(line))));
        assertEquals(5, lines.size());
    }

    @Test
    void emptyStoreHasASingleLine() {
        LogStoreMetadata metadata = new LogStoreMetadata(List.of(), null, null, 0, 0, 0);
        assertEquals("allthelogs.meta.empty", key(StoreSummary.tooltip(metadata).getFirst()));
    }

    @Test
    void tooltipValuesAreColoured() {
        LogStoreMetadata metadata = new LogStoreMetadata(
            List.of("1.8.9"),
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2026, 8, 1),
            3, 40, 2048);
        List<Component> lines = StoreSummary.tooltip(metadata);
        Object[] rangeArgs = ((TranslatableContents) lines.get(1).getContents()).getArgs();
        assertEquals("2020-01-01", ((Component) rangeArgs[0]).getString());
        assertTrue(((Component) rangeArgs[0]).getStyle().getColor().getValue() != 0);
        Object[] sizeArgs = ((TranslatableContents) lines.getLast().getContents()).getArgs();
        assertEquals("2.0 KB", ((Component) sizeArgs[0]).getString());
    }
}
