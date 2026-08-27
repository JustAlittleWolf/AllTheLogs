package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.LogSource;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * How a {@link LogSource} is stored in {@code log_file.source_kind}, {@code source_path}, and {@code entry_path}.
 * Query and write paths share this mapping so a later lookup can find the same row that was written.
 */
public final class StoredSources {
    /** Placeholder path for live capture rows, which are not files. */
    public static final String SESSION_PATH = "<session>";

    private StoredSources() {
    }

    public static String sourcePath(LogSource source) {
        return switch (source) {
            case LogSource.File file -> file.path().toString();
            case LogSource.Archive archive -> archive.path().toString();
            case LogSource.Session ignored -> SESSION_PATH;
        };
    }

    public static String entryPath(LogSource source) {
        return switch (source) {
            case LogSource.File ignored -> "";
            case LogSource.Archive archive -> archive.entryPath();
            case LogSource.Session session -> SessionMarker.entryPath(session.id());
        };
    }

    public static SourceKind kind(LogSource source) {
        return switch (source) {
            case LogSource.File ignored -> SourceKind.FILE;
            case LogSource.Archive ignored -> SourceKind.ARCHIVE;
            case LogSource.Session ignored -> SourceKind.SESSION;
        };
    }

    /**
     * Rebuilds a {@link LogSource} from stored columns.
     *
     * @throws SQLException if {@code kind} is not a known {@link SourceKind}
     */
    public static LogSource fromStored(String kind, String path, String entryPath) throws SQLException {
        SourceKind sourceKind;
        try {
            sourceKind = SourceKind.valueOf(kind);
        } catch (IllegalArgumentException e) {
            throw new SQLException("unknown source kind: " + kind, e);
        }
        return switch (sourceKind) {
            case FILE -> new LogSource.File(Path.of(path));
            case ARCHIVE -> new LogSource.Archive(Path.of(path), entryPath);
            case SESSION -> new LogSource.Session(SessionMarker.idFromEntryPath(entryPath));
        };
    }

    public static LogSource fromPrepared(PreparedLog log) {
        return switch (log.sourceKind()) {
            case FILE -> new LogSource.File(Path.of(log.sourcePath()));
            case ARCHIVE -> new LogSource.Archive(Path.of(log.sourcePath()), log.entryPath());
            case SESSION -> new LogSource.Session(log.sessionId());
        };
    }
}
