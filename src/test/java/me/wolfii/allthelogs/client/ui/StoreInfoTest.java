package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.data.LogStoreMetadata;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreInfoTest {
    @Test
    void formatBytesPicksTheSmallestFittingUnit() {
        assertEquals("512 B", StoreInfo.formatBytes(512));
        assertEquals("1.5 KB", StoreInfo.formatBytes(1536));
        assertEquals("1.0 MB", StoreInfo.formatBytes(1024 * 1024));
        assertEquals("1.0 GB", StoreInfo.formatBytes(1024L * 1024 * 1024));
    }

    @Test
    void tooltipOmitsMinecraftVersionsAndUsesDateBounds() {
        LogStoreMetadata metadata = new LogStoreMetadata(
            List.of("1.8.9", "26.2"),
            LocalDate.of(2020, 1, 1),
            LocalDate.of(2026, 8, 1),
            3, 40, 2048);
        List<Component> lines = StoreInfo.tooltip(metadata);
        assertEquals("allthelogs.meta.range", key(lines.getFirst()));
        assertTrue(lines.stream().noneMatch(line -> "allthelogs.meta.versions".equals(key(line))));
        assertEquals(4, lines.size());
    }

    @Test
    void emptyStoreHasASingleLine() {
        LogStoreMetadata metadata = new LogStoreMetadata(List.of(), null, null, 0, 0, 0);
        assertEquals("allthelogs.meta.empty", key(StoreInfo.tooltip(metadata).getFirst()));
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }
}
