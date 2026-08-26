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
    /// @param path the archive file, then `!/`, then the path of the log inside it. Nested archives are also
    ///             separated by `!/`, e.g. `/backups/outer.zip!/inner.zip!/logs/2026-08-25-2.log.gz`
    record Archive(Path path) implements LogSource {
        public Archive {
            Objects.requireNonNull(path, "path");
        }
    }

    /// Captured from a running Minecraft client with [LogStore#startSession(String)]. A session is not a file, so it
    /// has no name or path.
    record Session() implements LogSource {
    }
}
