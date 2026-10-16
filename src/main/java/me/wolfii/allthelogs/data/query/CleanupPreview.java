package me.wolfii.allthelogs.data.query;

import me.wolfii.allthelogs.data.ChatEntry;
import java.util.List;

/**
 * What a cleanup would touch: how many entries match, the oldest and newest of them, and a few picked at
 * random. {@code first} and {@code last} are null when nothing matches.
 */
public record CleanupPreview(long matches, ChatEntry first, ChatEntry last, List<ChatEntry> sample) {
    public CleanupPreview {
        sample = List.copyOf(sample);
    }
}
