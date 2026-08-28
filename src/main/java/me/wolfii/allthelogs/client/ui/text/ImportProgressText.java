package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.data.ImportProgress;
import me.wolfii.allthelogs.data.LogDataException;
import me.wolfii.allthelogs.data.LogSource;

import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Text shown on the import progress screen.
 */
public final class ImportProgressText {
    private ImportProgressText() {
    }

    /**
     * User-facing reason for a failed import. Unwraps worker {@link CompletionException}s and prefers the
     * {@link LogDataException} message when one is present.
     */
    public static String failureReason(Throwable error) {
        if (error == null) return "";
        Throwable current = error;
        while (current != null) {
            if (current instanceof LogDataException && hasText(current.getMessage())) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        current = unwrap(error);
        if (hasText(current.getMessage())) return current.getMessage();
        return current.getClass().getSimpleName();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean hasText(String message) {
        return message != null && !message.isBlank();
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
