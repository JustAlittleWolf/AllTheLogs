package me.wolfii.allthelogs.data;

/// How the calendar date of a log file was determined.
public enum DateSource {
    /// Parsed from the file name, e.g. `2026-08-25-2.log.gz`.
    FILE_NAME,
    /// Taken from the last modification time, used when the name carries no date.
    LAST_MODIFIED
}
