package me.wolfii.allthelogs.data;

/// Where a [ChatLog] was read from.
public enum SourceKind {
    /// The log was read directly from the file system.
    DIRECTORY,
    /// The log was read out of an archive (zip, 7z, tar, ...).
    ARCHIVE,
    /// The log is a [LogStore#startSession(String) client session], captured while the Minecraft client was running.
    SESSION
}
