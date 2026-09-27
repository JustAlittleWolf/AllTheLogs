package me.wolfii.allthelogs.data.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * Compressed copies of the DuckDB database file, taken immediately before a schema migration.
 * <p>
 * This is a copy of {@code logs.duckdb} itself, not a dump of the schema. The archive sits beside
 * the database as {@code <name>.v<schemaVersion>.<utc>.gz}: the version is the schema that file
 * was still on, so it can be restored by decompressing it back over the database. Gzip at the
 * fastest level is used because the DuckDB file is already compressed internally; a framed LZ4
 * archive from Commons Compress encodes through a Java LZ77 matcher and rewrites a database this
 * size at well under a megabyte per second. Older {@code .lz4} copies are still recognised so a
 * later upgrade can prune them. Old copies are only considered when another upgrade runs; the
 * newest copy is always kept.
 */
public final class DatabaseBackup {
    static final String EXTENSION = ".gz";
    private static final String LEGACY_EXTENSION = ".lz4";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC);

    private DatabaseBackup() {
    }

    /**
     * Copies {@code database} and then drops copies older than three months.
     * The database must already be checkpointed and closed, so the file includes the WAL.
     */
    public static Path copyBeforeMigration(Path database, int schemaVersion) throws IOException {
        Instant now = Instant.now();
        Path copy = create(database, schemaVersion, now);
        pruneExpired(database, now);
        return copy;
    }

    /**
     * Writes {@code database} to a sibling {@code <name>.v<schemaVersion>.<utc>.gz} file.
     * A failed write leaves no archive behind.
     */
    public static Path create(Path database, int schemaVersion, Instant at) throws IOException {
        if (StoreCancellation.stoppedOnThisThread()) {
            throw new IOException(StoreCancellation.CLOSED_MESSAGE);
        }
        Path target = backupPath(database, schemaVersion, at);
        Path partial = target.resolveSibling(target.getFileName() + ".tmp");
        System.out.println("[AllTheLogs] Backing up database to " + target.getFileName()
            + " before migrating from schema version " + schemaVersion);
        try {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(database));
                 OutputStream file = new BufferedOutputStream(Files.newOutputStream(partial));
                 OutputStream gzip = new FastGzipOutputStream(file)) {
                byte[] buffer = new byte[256 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (StoreCancellation.stoppedOnThisThread()) {
                        throw new IOException(StoreCancellation.CLOSED_MESSAGE);
                    }
                    gzip.write(buffer, 0, read);
                }
            }
            moveIntoPlace(partial, target);
        } catch (IOException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        System.out.println("[AllTheLogs] Backed up database to " + target.getFileName()
            + " before migrating from schema version " + schemaVersion);
        return target;
    }

    /**
     * Deletes copies older than three months, except the most recent one.
     */
    public static void pruneExpired(Path database, Instant now) throws IOException {
        List<Copy> backups = list(database);
        if (backups.size() < 2) return;
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(3).toInstant();
        for (int i = 0; i < backups.size() - 1; i++) {
            Copy backup = backups.get(i);
            if (backup.createdAt().isBefore(cutoff)) {
                Files.deleteIfExists(backup.path());
            }
        }
    }

    static Path backupPath(Path database, int schemaVersion, Instant at) {
        return database.resolveSibling(database.getFileName() + ".v" + schemaVersion + "."
            + STAMP.format(at) + EXTENSION);
    }

    static List<Copy> list(Path database) throws IOException {
        Path parent = database.getParent();
        if (parent == null || !Files.isDirectory(parent)) return List.of();
        Pattern pattern = pattern(database);
        List<Copy> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            for (Path path : stream) {
                parse(path, pattern).ifPresent(backups::add);
            }
        }
        backups.sort(Comparator.comparing(Copy::createdAt).thenComparing(file -> file.path().getFileName().toString()));
        return backups;
    }

    private static void moveIntoPlace(Path partial, Path target) throws IOException {
        try {
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Pattern pattern(Path database) {
        return Pattern.compile(Pattern.quote(database.getFileName().toString())
            + "\\.v\\d+\\.(\\d{8}T\\d{6}Z)(?:"
            + Pattern.quote(EXTENSION) + "|" + Pattern.quote(LEGACY_EXTENSION) + ")");
    }

    /**
     * Gzip at {@link Deflater#BEST_SPEED}. The header is written by the superclass constructor,
     * before the level change, and the header is not compressed.
     */
    private static final class FastGzipOutputStream extends GZIPOutputStream {
        private FastGzipOutputStream(OutputStream out) throws IOException {
            super(out, 1 << 16);
            def.setLevel(Deflater.BEST_SPEED);
        }
    }

    private static Optional<Copy> parse(Path path, Pattern pattern) {
        Matcher matcher = pattern.matcher(path.getFileName().toString());
        if (!matcher.matches()) return Optional.empty();
        try {
            Instant createdAt = Instant.from(STAMP.parse(matcher.group(1)));
            return Optional.of(new Copy(path, createdAt));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * One compressed database file and the time encoded in its name.
     */
    record Copy(Path path, Instant createdAt) {
    }
}
