package me.wolfii.allthelogs.data.importer.discover;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a discovered log's raw bytes (still gzip-compressed when the name ends in {@code .gz}).
 */
public final class ContentHashes {
    private ContentHashes() {
    }

    public static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content == null ? new byte[0] : content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}
