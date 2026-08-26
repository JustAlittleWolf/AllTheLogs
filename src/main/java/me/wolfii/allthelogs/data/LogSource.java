package me.wolfii.allthelogs.data;

import java.nio.file.Path;
import java.util.Objects;

/// Where a [ChatLog] was read from: a file on disk, an archive entry, or a running client session.
public sealed interface LogSource permits LogSource.File, LogSource.Archive, LogSource.Session {

    /// The log was read from a file on disk, such as `2026-08-25-2.log.gz`.
    ///
    /// @param path absolute path of the log file itself, not the directory that was imported
    record File(Path path) implements LogSource {
        public File {
            Objects.requireNonNull(path, "path");
        }
    }

    /// The log was read out of an archive (zip, 7z, tar, ...).
    ///
    /// @param path      absolute path of the archive file
    /// @param entryPath path of the log inside that archive, always `/` separated, with nested archives separated by
    ///                  `!/`
    record Archive(Path path, String entryPath) implements LogSource {
        public Archive {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entryPath, "entryPath");
        }
    }

    /// Captured from a running Minecraft client with [LogStore#startSession(String)]. A session is not a file, so it
    /// has no name or path.
    record Session() implements LogSource {
    }
}
