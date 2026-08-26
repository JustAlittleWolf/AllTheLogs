package me.wolfii.allthelogs.data;

/// How the calendar date of a log file was determined.
public enum DateSource {
    /// Parsed from the file name, e.g. `2026-08-25-2.log.gz`.
    FILE_NAME,
    /// Taken from the last modification time, used when the name carries no date.
    LAST_MODIFIED,
    /// The file is a client session started with [LogStore#startSession(String)], dated from when that session began
    /// while the Minecraft client was running.
    SESSION
}
