package me.wolfii.allthelogs.data;

import java.util.Objects;

/// Where a [ChatLog] was read from: a file on disk, an archive entry, or a running client session.
///
/// @param fileName  the bare file name, e.g. `2026-08-25-2.log.gz`, or a synthetic name for a session
/// @param kind      whether this came from a directory, an archive, or a client session
/// @param path      absolute path of the import root (the directory or the outermost archive), or a sentinel for a
///                  client session
/// @param entryPath path of the log inside that root; for directory imports this is the path relative to the imported
///                  directory, for archives the slash separated path inside the archive, with nested archives
///                  separated by `!/`
public record LogSource(
    String fileName,
    SourceKind kind,
    String path,
    String entryPath
) {
    public LogSource {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(entryPath, "entryPath");
    }
}
