package me.wolfii.allthelogs.data;

/// Unchecked wrapper for the IO and SQL failures that can happen while importing or querying.
public class LogDataException extends RuntimeException {
    public LogDataException(String message) {
        super(message);
    }

    public LogDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
