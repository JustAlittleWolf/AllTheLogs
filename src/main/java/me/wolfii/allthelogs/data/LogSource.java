package me.wolfii.allthelogs.data;

import java.util.Objects;

/// Where a [ChatLog] was read from: a file on disk, an archive entry, or a running client session.
public sealed interface LogSource permits LogSource.Directory, LogSource.Archive, LogSource.Session {

    /// The log was read directly from the file system.
    ///
    /// @param fileName  the bare file name, e.g. `2026-08-25-2.log.gz`
    /// @param path      absolute path of the imported directory
    /// @param entryPath path of the log relative to that directory, always `/` separated
    record Directory(String fileName, String path, String entryPath) implements LogSource {
        public Directory {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entryPath, "entryPath");
        }
    }

    /// The log was read out of an archive (zip, 7z, tar, ...).
    ///
    /// @param fileName  the bare file name, e.g. `2026-08-25-2.log.gz`
    /// @param path      absolute path of the outermost archive that was imported
    /// @param entryPath path of the log inside that archive, always `/` separated, with nested archives separated by
    ///                  `!/`
    record Archive(String fileName, String path, String entryPath) implements LogSource {
        public Archive {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(entryPath, "entryPath");
        }
    }

    /// Captured from a running Minecraft client with [LogStore#startSession(String)]. A session is not a file, so it
    /// has no name or path.
    record Session() implements LogSource {
    }
}
