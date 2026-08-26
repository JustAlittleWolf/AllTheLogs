package me.wolfii.allthelogs.data.discover;

import me.wolfii.allthelogs.data.ImportOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;

/**
 * Cheap file-count guesses used to keep the import progress bar moving before discovery has finished.
 */
public final class FileCountEstimator {
    static final int LOGS_PER_ARCHIVE = 4;
    static final int DEFAULT_ARCHIVE_LOGS = 8;

    private FileCountEstimator() {
    }

    /**
     * Counts log files on disk and assumes a handful of logs inside each archive, without opening those
     * archives except for zip files where listing is cheap.
     */
    public static int estimateDirectory(Path root, ImportOptions options) {
        if (root == null || !Files.isDirectory(root)) return 1;
        int estimate = 0;
        try (Stream<Path> stream = options.recursive() ? Files.walk(root) : Files.list(root)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path)) continue;
                String name = path.getFileName().toString();
                if (LogDiscovery.isLogFile(name)) {
                    estimate++;
                } else if (options.nestedArchives() && LogDiscovery.isArchive(name)) {
                    estimate += estimateArchive(path, options);
                }
            }
        } catch (IOException ignored) {
            return Math.max(1, estimate);
        }
        return Math.max(1, estimate);
    }

    /**
     * Guesses how many log files {@code archive} contains. Zip files are listed; other formats use a constant.
     */
    public static int estimateArchive(Path archive, ImportOptions options) {
        if (archive == null || !Files.isRegularFile(archive)) return DEFAULT_ARCHIVE_LOGS;
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) {
            int count = countZipLogs(archive, options);
            if (count > 0) return count;
        }
        return DEFAULT_ARCHIVE_LOGS;
    }

    private static int countZipLogs(Path archive, ImportOptions options) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            int count = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String entryName = entry.getName().replace('\\', '/');
                if (entryName.contains("/") && !options.recursive()) continue;
                String leaf = entryName.substring(entryName.lastIndexOf('/') + 1);
                if (LogDiscovery.isLogFile(leaf)) {
                    count++;
                } else if (options.nestedArchives() && LogDiscovery.isArchive(leaf)) {
                    count += LOGS_PER_ARCHIVE;
                }
            }
            return count;
        } catch (IOException ignored) {
            return 0;
        }
    }
}
