package me.wolfii.allthelogs.data;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot of what a {@link LogStore} currently holds.
 *
 * @param minecraftVersions distinct {@link ChatLog#minecraftVersion()} values, ordered by the earliest log date of
 *                          each version, then by name
 * @param firstLogDate      earliest {@link ChatLog#date()} among stored logs, or {@code null} when the store is empty
 * @param lastLogDate       latest {@link ChatLog#date()} among stored logs, or {@code null} when the store is empty
 * @param chatLogCount      number of stored chat logs, including those with no chat entries
 * @param chatEntryCount    number of stored chat entries
 * @param databaseSizeBytes size of the database in bytes. For a file-backed store this is the on-disk file, including
 *                          its write-ahead log when present. For an in-memory store this is DuckDB's current memory
 *                          usage for that database
 */
public record LogStoreMetadata(
    List<String> minecraftVersions,
    LocalDate firstLogDate,
    LocalDate lastLogDate,
    long chatLogCount,
    long chatEntryCount,
    long databaseSizeBytes
) implements me.wolfii.allthelogs.api.LogStoreMetadata {
    public LogStoreMetadata {
        Objects.requireNonNull(minecraftVersions, "minecraftVersions");
        minecraftVersions = List.copyOf(minecraftVersions);
        if (chatLogCount < 0) throw new IllegalArgumentException("chatLogCount must not be negative");
        if (chatEntryCount < 0) throw new IllegalArgumentException("chatEntryCount must not be negative");
        if (databaseSizeBytes < 0) throw new IllegalArgumentException("databaseSizeBytes must not be negative");
        if ((firstLogDate == null) != (lastLogDate == null)) {
            throw new IllegalArgumentException("firstLogDate and lastLogDate must both be set or both be null");
        }
        if (firstLogDate != null && firstLogDate.isAfter(lastLogDate)) {
            throw new IllegalArgumentException("firstLogDate " + firstLogDate + " is after lastLogDate " + lastLogDate);
        }
    }
}
