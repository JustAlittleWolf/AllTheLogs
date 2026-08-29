package me.wolfii.allthelogs.data.importer.discover;

/**
 * Deduplicates discovered logs by SHA-256 of their raw bytes, in addition to path-based skips.
 */
public interface ContentTracker {
    ContentTracker NONE = new ContentTracker() {
        @Override
        public boolean skipHash(String contentHash) {
            return false;
        }

        @Override
        public void noteHash(String contentHash) {
        }

        @Override
        public void remember(String sourcePath, String entryPath, String contentHash) {
        }
    };

    boolean skipHash(String contentHash);

    void noteHash(String contentHash);

    void remember(String sourcePath, String entryPath, String contentHash);
}
