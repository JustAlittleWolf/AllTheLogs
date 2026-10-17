package me.wolfii.allthelogs.client.files;

import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.importer.discover.LogDiscovery;

import java.nio.file.Path;

/**
 * Filesystem path a log can be shown at. A log read out of an archive shows that archive, because the
 * path inside a zip is not a file the file manager can select. A path that continues past an archive
 * segment stops at the archive too.
 */
public final class LogPaths {
    private LogPaths() {
    }

    /**
     * Absolute path to show for {@code source}, or {@code null} when the log is not a file (a live session).
     * Archive entries resolve to the archive file itself.
     */
    public static Path openable(LogSource source) {
        String truncated = throughArchive(storedPath(source));
        if (truncated == null) return null;
        return Path.of(truncated).toAbsolutePath().normalize();
    }

    /**
     * Cuts {@code path} at the first zip or other archive. {@code backup.zip/logs/latest.log} and
     * {@code backup.zip!/logs/latest.log} both become {@code backup.zip}. A Windows path uses
     * {@code !\} for that same separator, because {@link Path#toString()} rewrites the slash.
     * A path with no archive in it is returned unchanged. {@code null} and blank paths stay empty.
     */
    public static String throughArchive(String path) {
        if (path == null || path.isBlank()) return null;
        int cut = path.length();
        int segmentEnd = archiveSegmentEnd(path);
        if (segmentEnd >= 0) cut = segmentEnd;
        int bang = nextArchiveBang(path, 0);
        while (bang >= 0 && bang < cut) {
            if (endsWithArchive(path.substring(0, bang))) {
                cut = bang;
                break;
            }
            bang = nextArchiveBang(path, bang + LogDiscovery.ARCHIVE_SEPARATOR.length());
        }
        String result = path.substring(0, cut);
        return result.isBlank() ? null : result;
    }

    /**
     * Index of {@code !} where an archive separator starts, or {@code -1}.
     * {@link LogDiscovery#ARCHIVE_SEPARATOR} is {@code !/}. {@code !\} is the same mark after a Windows path
     * is turned into a string.
     */
    private static int nextArchiveBang(String path, int from) {
        int slash = path.indexOf(LogDiscovery.ARCHIVE_SEPARATOR, from);
        int backslash = path.indexOf("!\\", from);
        if (slash < 0) return backslash;
        if (backslash < 0) return slash;
        return Math.min(slash, backslash);
    }

    private static String storedPath(LogSource source) {
        if (source == null) return null;
        return switch (source) {
            case LogSource.File file -> file.path().toString();
            case LogSource.Archive archive -> archive.path().toString();
            case LogSource.Session ignored -> null;
        };
    }

    /**
     * Index just after the first archive file name that is followed by more path, or {@code -1}.
     */
    private static int archiveSegmentEnd(String path) {
        int start = 0;
        for (int i = 0; i <= path.length(); i++) {
            boolean boundary = i == path.length() || isSeparator(path.charAt(i));
            if (!boundary) continue;
            if (i > start && i < path.length() && isArchiveName(path.substring(start, i))) {
                return i;
            }
            start = i + 1;
        }
        return -1;
    }

    private static boolean endsWithArchive(String path) {
        int start = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1;
        return isArchiveName(path.substring(start));
    }

    private static boolean isArchiveName(String name) {
        return name != null && !name.isEmpty() && ImportPaths.isArchive(Path.of(name));
    }

    private static boolean isSeparator(char c) {
        return c == '/' || c == '\\';
    }
}
