package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.ChatEntry;

import java.util.List;

/**
 * What a cleanup would touch: how many entries match, the oldest and newest of them, and a random sample of
 * the rest. Each list is oldest-first, and no entry appears in two of them.
 */
public record CleanupPreview(long matches, List<ChatEntry> oldest, List<ChatEntry> newest, List<ChatEntry> sample) {
    public static final CleanupPreview NONE = new CleanupPreview(0, List.of(), List.of(), List.of());

    public CleanupPreview {
        oldest = List.copyOf(oldest);
        newest = List.copyOf(newest);
        sample = List.copyOf(sample);
    }
}
