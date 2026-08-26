package me.wolfii.allthelogs.data.store;

/**
 * Discriminator stored in {@code log_file.source_kind}. Callers of the public API match on
 * {@link me.wolfii.allthelogs.data.LogSource} instead.
 */
public enum SourceKind {
    FILE,
    ARCHIVE,
    SESSION
}
