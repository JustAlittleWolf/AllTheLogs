package me.wolfii.allthelogs;

import me.wolfii.allthelogs.data.ImportOptions;
import me.wolfii.allthelogs.data.ImportResult;
import me.wolfii.allthelogs.data.LogStore;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/// Manual debugging helper: imports every top level entry (folder or archive) of a dataset directory one at a time,
/// with a timeout, and logs progress so a hanging entry can be spotted.
///
/// Usage: `gradlew run --args="C:/Users/Wolfi/Downloads/dataset"` or run this class directly with the dataset path
/// as the first argument, optionally followed by a per-entry timeout in seconds (default 60).
public final class DEBUGDatasetProbe {
    private DEBUGDatasetProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: DatasetProbe <dataset-directory> [timeoutSeconds]");
            System.exit(1);
        }
        Path dataset = Path.of(args[0]);
        long timeoutSeconds = args.length > 1 ? Long.parseLong(args[1]) : 60;

        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataset)) {
            for (Path p : stream) entries.add(p);
        }
        entries.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));

        System.out.println("found " + entries.size() + " entries in " + dataset);

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            System.out.println("=== " + name + " starting ===");
            long start = System.currentTimeMillis();

            ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "probe-" + name);
                t.setDaemon(true);
                return t;
            });
            Future<?> future = executor.submit(() -> runOne(entry));
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
                System.out.println("=== " + name + " OK in " + (System.currentTimeMillis() - start) + " ms ===");
            } catch (TimeoutException e) {
                System.out.println("=== " + name + " TIMED OUT after " + timeoutSeconds + "s -- this is the hanging entry ===");
                future.cancel(true);
            } catch (Exception e) {
                System.out.println("=== " + name + " FAILED after " + (System.currentTimeMillis() - start) + " ms: " + e + " ===");
            } finally {
                executor.shutdownNow();
            }
        }

        System.out.println("done");
    }

    private static void runOne(Path entry) {
        try (LogStore store = LogStore.open(Path.of("TEST.db"))) {
            ImportResult result;
            if (Files.isDirectory(entry)) {
                result = store.importDirectory(entry, ImportOptions.defaults().withPathMatcher("**/logs/**"));
            } else {
                result = store.importArchive(entry);
            }
            System.out.println("    files=" + result.importedFiles() + " entries=" + result.importedEntries()
                + " skipped=" + result.skippedFiles() + " failures=" + result.failures().size());
            for (ImportResult.Failure failure : result.failures()) {
                System.out.println("    failure: " + failure.path() + " -> " + failure.reason());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
