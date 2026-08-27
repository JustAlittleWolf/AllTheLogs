package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.data.LogStoreMetadata;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable log-store facts for the browser info button.
 */
final class StoreInfo {
    private StoreInfo() {
    }

    static List<Component> tooltip(LogStoreMetadata metadata) {
        if (metadata.chatLogCount() == 0) {
            return List.of(Component.translatable("allthelogs.meta.empty"));
        }
        List<Component> lines = new ArrayList<>();
        if (metadata.firstLogDate() != null && metadata.lastLogDate() != null) {
            lines.add(Component.translatable("allthelogs.meta.range",
                metadata.firstLogDate().toString(), metadata.lastLogDate().toString()));
        }
        lines.add(Component.translatable("allthelogs.meta.logs", Long.toString(metadata.chatLogCount())));
        lines.add(Component.translatable("allthelogs.meta.entries", Long.toString(metadata.chatEntryCount())));
        lines.add(Component.translatable("allthelogs.meta.size", formatBytes(metadata.databaseSizeBytes())));
        return lines;
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        String unit = "KB";
        if (value >= 1024) {
            value /= 1024;
            unit = "MB";
        }
        if (value >= 1024) {
            value /= 1024;
            unit = "GB";
        }
        return "%.1f %s".formatted(value, unit);
    }
}
