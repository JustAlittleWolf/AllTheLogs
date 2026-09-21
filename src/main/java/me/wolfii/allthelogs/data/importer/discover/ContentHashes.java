package me.wolfii.allthelogs.data.importer.discover;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a discovered log's raw bytes (still gzip-compressed when the name ends in {@code .gz}).
 */
public final class ContentHashes {
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    });

    private ContentHashes() {
    }

    public static String sha256(byte[] content) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        byte[] hash = digest.digest(content == null ? new byte[0] : content);
        return HexFormat.of().formatHex(hash);
    }
}
