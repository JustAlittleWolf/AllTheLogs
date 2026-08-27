package me.wolfii.allthelogs.data;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where a {@link ChatLog} was read from: a file on disk, an archive entry, or a running client session.
 */
public sealed interface LogSource permits LogSource.File, LogSource.Archive, LogSource.Session {

    /**
     * Full path for hover details: the log file, {@code archive!/entry}, or {@code null} for a live session.
     */
    default String fullPath() {
        return switch (this) {
            case File file -> file.path().toAbsolutePath().normalize().toString();
            case Archive archive -> archive.path().toAbsolutePath().normalize() + "!/" + archive.entryPath();
            case Session ignored -> null;
        };
    }

    /**
     * Short label for tooltips: the log file name, archive entry name, or {@code session}.
     */
    default String label() {
        return switch (this) {
            case File file -> {
                Path name = file.path().getFileName();
                yield name == null ? file.path().toString() : name.toString();
            }
            case Archive archive -> {
                String entry = archive.entryPath();
                int slash = entry.lastIndexOf('/');
                yield slash < 0 ? entry : entry.substring(slash + 1);
            }
            case Session ignored -> "session";
        };
    }

    /**
     * The log was read from a file on disk, such as {@code 2026-08-25-2.log.gz}.
     *
     * @param path absolute path of the log file itself, not the directory that was imported
     */
    record File(Path path) implements LogSource {
        public File {
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * The log was read out of an archive (zip, 7z, tar, ...).
     *
     * @param path      absolute path of the archive file
     * @param entryPath path of the log inside that archive, always {@code /} separated, with nested archives
     *                  separated by {@code !/}
     */
    record Archive(Path path, String entryPath) implements LogSource {
        public Archive {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entryPath, "entryPath");
        }
    }

    /**
     * Captured from a running Minecraft client with {@link LogStore#startSession(String)}. A session is not a file,
     * so it has no name or path. {@code id} is a UUID written to the Minecraft log as a
     * {@link me.wolfii.allthelogs.data.store.SessionMarker} so a later import of that log can skip the file.
     *
     * @param id unique id of this capture session, or {@code null} when none is stored.
     *           {@link LogStore#startSession(String)} always assigns one
     */
    record Session(String id) implements LogSource {
    }
}
