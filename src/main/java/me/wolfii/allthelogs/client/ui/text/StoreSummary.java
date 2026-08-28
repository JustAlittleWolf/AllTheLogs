package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.data.LogStoreMetadata;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable log-store facts for the browser info button.
 */
public final class StoreSummary {
    private StoreSummary() {
    }

    public static List<Component> tooltip(LogStoreMetadata metadata) {
        if (metadata.chatLogCount() == 0) {
            return List.of(muted(Component.translatable("allthelogs.meta.empty")));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(labeled("allthelogs.meta.hint"));
        if (metadata.firstLogDate() != null && metadata.lastLogDate() != null) {
            lines.add(labeled("allthelogs.meta.range",
                value(metadata.firstLogDate().toString()),
                value(metadata.lastLogDate().toString())));
        }
        lines.add(labeled("allthelogs.meta.logs", number(Long.toString(metadata.chatLogCount()))));
        lines.add(labeled("allthelogs.meta.entries", number(Long.toString(metadata.chatEntryCount()))));
        lines.add(labeled("allthelogs.meta.size", size(formatBytes(metadata.databaseSizeBytes()))));
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

    private static Component labeled(String key, Component... args) {
        return muted(Component.translatable(key, (Object[]) args));
    }

    private static Component value(String text) {
        return colored(text, Colors.META_VALUE);
    }

    private static Component number(String text) {
        return colored(text, Colors.META_NUMBER);
    }

    private static Component size(String text) {
        return colored(text, Colors.META_SIZE);
    }

    private static Component muted(Component component) {
        return component.copy().withStyle(Style.EMPTY.withColor(Colors.META_LABEL & 0xFFFFFF));
    }

    private static Component colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }
}
