package me.wolfii.allthelogs.data.store;

import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

/**
 * Compressed copies of {@code logs.duckdb} taken immediately before a schema migration.
 * Framed LZ4 is used because Commons Compress ships it and it is fast on already-compressed
 * DuckDB files. Old copies are only considered when another upgrade runs; the newest backup
 * is always kept.
 */
public final class SchemaBackup {
    static final String EXTENSION = ".lz4";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC);

    private SchemaBackup() {
    }

    /**
     * Writes {@code database} to a sibling {@code <name>.v<fromVersion>.<utc>.lz4} file.
     */
    public static Path create(Path database, int fromVersion, Instant at) throws IOException {
        Path target = backupPath(database, fromVersion, at);
        try (InputStream in = Files.newInputStream(database);
             OutputStream file = Files.newOutputStream(target);
             FramedLZ4CompressorOutputStream lz4 = new FramedLZ4CompressorOutputStream(file)) {
            in.transferTo(lz4);
        }
        System.out.println("[AllTheLogs] Backed up database to " + target.getFileName()
            + " before migrating schema version " + fromVersion);
        return target;
    }

    /**
     * Deletes backups older than three months, except the most recent one.
     */
    public static void pruneExpired(Path database, Instant now) throws IOException {
        List<BackupFile> backups = list(database);
        if (backups.size() < 2) return;
        Instant cutoff = now.atZone(ZoneOffset.UTC).minusMonths(3).toInstant();
        BackupFile newest = backups.getLast();
        for (BackupFile backup : backups) {
            if (backup.equals(newest)) continue;
            if (backup.createdAt().isBefore(cutoff)) {
                Files.deleteIfExists(backup.path());
            }
        }
    }

    static Path backupPath(Path database, int fromVersion, Instant at) {
        return database.resolveSibling(database.getFileName() + ".v" + fromVersion + "."
            + STAMP.format(at) + EXTENSION);
    }

    static List<BackupFile> list(Path database) throws IOException {
        Path parent = database.getParent();
        if (parent == null || !Files.isDirectory(parent)) return List.of();
        Pattern pattern = pattern(database);
        List<BackupFile> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            for (Path path : stream) {
                parse(path, pattern).ifPresent(backups::add);
            }
        }
        backups.sort(Comparator.comparing(BackupFile::createdAt).thenComparing(file -> file.path().getFileName().toString()));
        return backups;
    }

    private static Pattern pattern(Path database) {
        return Pattern.compile(Pattern.quote(database.getFileName().toString())
            + "\\.v\\d+\\.(\\d{8}T\\d{6}Z)" + Pattern.quote(EXTENSION));
    }

    private static Optional<BackupFile> parse(Path path, Pattern pattern) {
        Matcher matcher = pattern.matcher(path.getFileName().toString());
        if (!matcher.matches()) return Optional.empty();
        try {
            Instant createdAt = Instant.from(STAMP.parse(matcher.group(1)));
            return Optional.of(new BackupFile(path, createdAt));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    record BackupFile(Path path, Instant createdAt) {
    }
}
