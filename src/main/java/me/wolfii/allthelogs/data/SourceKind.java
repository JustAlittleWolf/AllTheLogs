package me.wolfii.allthelogs.data;

/// Where a log file was read from.
public enum SourceKind {
    /// The log file was read directly from the file system.
    DIRECTORY,
    /// The log file was read out of an archive (zip, 7z, tar, ...).
    ARCHIVE,
    /// The entry was captured live from a running game rather than read from a file on disk.
    LIVE
}
