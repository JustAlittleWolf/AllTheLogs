package me.wolfii.allthelogs.data.store;

import me.wolfii.allthelogs.data.LogSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StoredSourcesTest {
    @Test
    void fileSourceUsesThePathAndAnEmptyEntry() throws SQLException {
        LogSource.File file = new LogSource.File(Path.of("/tmp/2026-01-01-1.log.gz"));
        assertEquals(file.path().toString(), StoredSources.sourcePath(file));
        assertEquals("", StoredSources.entryPath(file));
        assertEquals(SourceKind.FILE, StoredSources.kind(file));
        assertEquals(file, StoredSources.fromStored("FILE", file.path().toString(), ""));
    }

    @Test
    void archiveSourceKeepsTheEntryPath() throws SQLException {
        LogSource.Archive archive = new LogSource.Archive(Path.of("/tmp/logs.zip"), "nested/2026-01-01-1.log.gz");
        assertEquals(archive.path().toString(), StoredSources.sourcePath(archive));
        assertEquals(archive.entryPath(), StoredSources.entryPath(archive));
        assertEquals(SourceKind.ARCHIVE, StoredSources.kind(archive));
        assertEquals(archive, StoredSources.fromStored("ARCHIVE", archive.path().toString(), archive.entryPath()));
    }

    @Test
    void sessionSourceUsesTheSharedPlaceholderPath() throws SQLException {
        String id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        LogSource.Session session = new LogSource.Session(id);
        assertEquals("<session>", StoredSources.sourcePath(session));
        assertEquals(StoredSources.SESSION_PATH, StoredSources.sourcePath(session));
        assertEquals(SessionMarker.entryPath(id), StoredSources.entryPath(session));
        assertEquals(SourceKind.SESSION, StoredSources.kind(session));
        LogSource restored = StoredSources.fromStored("SESSION", StoredSources.SESSION_PATH, SessionMarker.entryPath(id));
        assertInstanceOf(LogSource.Session.class, restored);
        assertEquals(id, ((LogSource.Session) restored).id());
    }

    @Test
    void preparedLogsRoundTripToTheSameSourceKind() {
        PreparedLog file = new PreparedLog("latest.log", SourceKind.FILE, "/tmp/a.log", "",
            LocalDate.of(2026, 1, 1), "26.2", List.of(), List.of(), List.of(), false, null, null, null, null, null);
        assertInstanceOf(LogSource.File.class, StoredSources.fromPrepared(file));
        PreparedLog archive = new PreparedLog("a.log", SourceKind.ARCHIVE, "/tmp/a.zip", "inside/a.log",
            LocalDate.of(2026, 1, 1), "26.2", List.of(), List.of(), List.of(), false, null, null, null, null, null);
        assertInstanceOf(LogSource.Archive.class, StoredSources.fromPrepared(archive));
        PreparedLog session = new PreparedLog("", SourceKind.SESSION, StoredSources.SESSION_PATH, "session/id",
            LocalDate.of(2026, 1, 1), "26.2", List.of(), List.of(), List.of(), false, null, null, "id", null, null);
        assertInstanceOf(LogSource.Session.class, StoredSources.fromPrepared(session));
    }
}
