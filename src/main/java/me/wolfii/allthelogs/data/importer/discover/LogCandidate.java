package me.wolfii.allthelogs.data.importer.discover;

import me.wolfii.allthelogs.data.store.SourceKind;

import java.time.Instant;

/**
 * A log file that was discovered and is ready to be parsed, with its contents already materialised in memory.
 * Archive entries cannot be read lazily from several threads, so discovery reads the raw bytes up front.
 * Files already stored are skipped by path before this object is created, so a boot re-import does not open them.
 * Copies of a file already handled are skipped by {@link #contentHash()} after the bytes are read.
 *
 * @param fileName     bare file name
 * @param sourceKind   whether it came from a file on disk or an archive
 * @param sourcePath   absolute path of the log file, or of the archive file for an archive entry
 * @param entryPath    path within the archive, always {@code /} separated, nested archives separated by {@code !/};
 *                     empty for a file on disk
 * @param lastModified last modification instant, or {@code null} if the source does not report one
 * @param content      raw file bytes, still gzip compressed if the file name ends in {@code .gz}
 * @param contentHash  SHA-256 of {@link #content()}
 */
public record LogCandidate(
    String fileName,
    SourceKind sourceKind,
    String sourcePath,
    String entryPath,
    Instant lastModified,
    byte[] content,
    String contentHash
) {
}
