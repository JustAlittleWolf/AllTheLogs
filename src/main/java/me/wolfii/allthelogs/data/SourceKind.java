package me.wolfii.allthelogs.data;

/// Where a log file was read from. Client sessions have no source kind: they were captured from a running game rather
/// than read from a directory or archive.
public enum SourceKind {
    /// The log file was read directly from the file system.
    DIRECTORY,
    /// The log file was read out of an archive (zip, 7z, tar, ...).
    ARCHIVE
}
