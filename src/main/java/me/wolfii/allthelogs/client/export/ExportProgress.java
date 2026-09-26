package me.wolfii.allthelogs.client.export;

/**
 * Messages written so far in one export. {@code total} is how many messages the file will contain.
 * <p>
 * Delivered from the export thread. Calls are never concurrent and should return quickly.
 */
public record ExportProgress(long written, long total) {
    public ExportProgress {
        written = Math.max(0, written);
        total = Math.max(0, total);
        if (total > 0) written = Math.min(written, total);
    }

    /**
     * {@code written / total} as a percent in {@code [0, 100]}. An empty export stays at 0 until it finishes.
     */
    public int percent() {
        if (total <= 0) return 0;
        return (int) Math.clamp(Math.round(written * 100.0 / total), 0, 100);
    }
}
