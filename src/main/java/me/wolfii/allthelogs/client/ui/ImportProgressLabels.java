package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogSource;

/**
 * Text shown on the import progress screen.
 */
final class ImportProgressLabels {
    private ImportProgressLabels() {
    }

    static String currentFile(LogSource source) {
        if (source == null) return "";
        return switch (source) {
            case LogSource.File file -> file.path().getFileName().toString();
            case LogSource.Archive archive -> archive.entryPath().isEmpty()
                ? archive.path().getFileName().toString()
                : archive.entryPath();
            case LogSource.Session ignored -> "";
        };
    }

    static int percent(ImportProgress progress) {
        return (int) Math.clamp(Math.round(progress.fraction() * 100), 0, 100);
    }
}
