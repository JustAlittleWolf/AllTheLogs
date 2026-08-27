package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogSource;

import java.nio.file.Path;

/**
 * Text shown on the import progress screen.
 */
public final class ImportProgressText {
    private ImportProgressText() {
    }

    /**
     * Path of the file currently being imported, relative to {@code importPath} when possible. Archive work
     * appends {@code !/} and the entry (or nested archive) being read.
     */
    public static String currentFile(LogSource source, Path importPath) {
        if (source == null) return "";
        return switch (source) {
            case LogSource.File file -> relative(importPath, file.path());
            case LogSource.Archive archive -> {
                String archivePath = relative(importPath, archive.path());
                if (archive.entryPath().isEmpty()) yield archivePath;
                yield archivePath + "!/" + archive.entryPath();
            }
            case LogSource.Session ignored -> "";
        };
    }

    static String relative(Path importPath, Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (importPath == null) return slash(absolute);
        Path base = importPath.toAbsolutePath().normalize();
        if (absolute.equals(base)) {
            Path name = absolute.getFileName();
            return name == null ? slash(absolute) : name.toString();
        }
        if (absolute.startsWith(base)) {
            return slash(base.relativize(absolute));
        }
        Path parent = base.getParent();
        if (parent != null && absolute.startsWith(parent)) {
            return slash(parent.relativize(absolute));
        }
        return slash(absolute);
    }

    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }

    public static int percent(ImportProgress progress) {
        return (int) Math.clamp(Math.round(progress.fraction() * 100), 0, 100);
    }
}
