package me.wolfii.allthelogs.data.internal;

/// Discriminator stored in `log_file.source_kind`. Callers of the public API match on [me.wolfii.allthelogs.data.LogSource] instead.
public enum SourceKind {
    DIRECTORY,
    ARCHIVE,
    SESSION
}
