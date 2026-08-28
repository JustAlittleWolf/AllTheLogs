package me.wolfii.allthelogs.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot of what the AllTheLogs database currently holds.
 */
public interface LogStoreMetadata {
    /**
     * Distinct {@link ChatLog#minecraftVersion()} values, ordered by the earliest log date of each version,
     * then by name.
     */
    List<String> minecraftVersions();

    /**
     * Earliest {@link ChatLog#date()} among stored logs, or {@code null} when the store is empty.
     */
    LocalDate firstLogDate();

    /**
     * Latest {@link ChatLog#date()} among stored logs, or {@code null} when the store is empty.
     */
    LocalDate lastLogDate();

    /**
     * Number of stored chat logs, including those with no chat entries.
     */
    long chatLogCount();

    /**
     * Number of stored chat entries.
     */
    long chatEntryCount();

    /**
     * Size of the database in bytes. For a file-backed store this is the on-disk file, including its
     * write-ahead log when present.
     */
    long databaseSizeBytes();
}
