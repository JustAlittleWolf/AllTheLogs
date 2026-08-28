package me.wolfii.allthelogs.api;

import java.nio.file.Path;

/**
 * Where a {@link ChatLog} was read from: a file on disk, an archive entry, or a running client session.
 */
public interface LogSource {
    /**
     * The log was read from a file on disk, such as {@code 2026-08-25-2.log.gz}.
     */
    interface File extends LogSource {
        /**
         * Absolute path of the log file itself, not the directory that was imported.
         */
        Path path();
    }

    /**
     * The log was read out of an archive (zip, 7z, tar, ...).
     */
    interface Archive extends LogSource {
        /**
         * Absolute path of the archive file.
         */
        Path path();

        /**
         * Path of the log inside that archive, always {@code /} separated, with nested archives
         * separated by {@code !/}.
         */
        String entryPath();
    }

    /**
     * Captured from a running Minecraft client. A session is not a file, so it has no name or path.
     */
    interface Session extends LogSource {
        /**
         * Unique id of this capture session, or {@code null} when none is stored.
         */
        String id();
    }
}
