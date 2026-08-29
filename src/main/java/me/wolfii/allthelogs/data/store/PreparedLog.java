package me.wolfii.allthelogs.data.store;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A parsed log file, carrying everything the writer needs to store it.
 *
 * @param entryTimes               timestamps of the chat entries, parallel to {@link #messages()}
 * @param formattings              packed formatting per entry, parallel to {@link #messages()}; a null
 *                                 element means that line is unformatted
 * @param resourceManagerReloaded  whether the file contains a {@code Reloading ResourceManager} line; such files are
 *                                 kept even when they have no chat entries
 * @param firstLineTime            timestamp of the first logged line of the file, not just chat entries
 * @param lastLineTime             timestamp of the last logged line of the file, not just chat entries
 * @param sessionId                id from an AllTheLogs session marker in the file, or {@code null}
 * @param minecraftUser            the player from a {@code Setting user:} line, or {@code null}
 * @param contentHash              SHA-256 of the discovered raw bytes, or {@code null}
 */
public record PreparedLog(
    String fileName,
    SourceKind sourceKind,
    String sourcePath,
    String entryPath,
    LocalDate date,
    String minecraftVersion,
    List<LocalDateTime> entryTimes,
    List<String> messages,
    List<long[]> formattings,
    boolean resourceManagerReloaded,
    LocalDateTime firstLineTime,
    LocalDateTime lastLineTime,
    String sessionId,
    String minecraftUser,
    String contentHash
) {
}
