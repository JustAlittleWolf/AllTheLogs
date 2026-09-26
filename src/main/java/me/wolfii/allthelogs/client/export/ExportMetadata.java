package me.wolfii.allthelogs.client.export;

import java.time.LocalDateTime;

/**
 * Search and export scope written after the messages in a JSON export, and used to name every export file.
 * A blank {@code query} is stored as {@code null} and becomes {@code none} in the file name.
 *
 * @param scope         {@code selection}, {@code visible}, or {@code query}
 * @param query         search text, or {@code null} when the box is empty
 * @param regex         whether {@code query} is a regular expression
 * @param caseSensitive whether the search matches case
 * @param contextLines  context lines the browser was showing around each hit
 * @param sort          {@code ascending} or {@code descending}
 * @param startingAt    inclusive range start, or {@code null}
 * @param upUntil       exclusive range end, or {@code null}
 * @param version       Minecraft version filter, or {@code null}
 * @param server        server or world substring, or {@code null}
 */
public record ExportMetadata(
    String scope,
    String query,
    boolean regex,
    boolean caseSensitive,
    int contextLines,
    String sort,
    LocalDateTime startingAt,
    LocalDateTime upUntil,
    String version,
    String server
) {
    /** Longest search fragment kept in an export file name. */
    static final int FILE_TOKEN_LIMIT = 40;

    public ExportMetadata {
        if (query != null && query.isBlank()) query = null;
        if (scope != null && scope.isBlank()) scope = null;
        if (sort != null && sort.isBlank()) sort = null;
        if (version != null && version.isBlank()) version = null;
        if (server != null && server.isBlank()) server = null;
        if (contextLines < 0) contextLines = 0;
    }

    /**
     * File-name fragment for {@code query}. Letters and digits are kept; everything else becomes a hyphen.
     * An empty result is {@code none}.
     */
    public static String fileToken(String query) {
        if (query == null || query.isBlank()) return "none";
        StringBuilder token = new StringBuilder();
        boolean hyphen = false;
        for (int i = 0; i < query.length(); ) {
            int codePoint = query.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                if (hyphen && !token.isEmpty()) token.append('-');
                hyphen = false;
                token.appendCodePoint(codePoint);
                if (token.length() >= FILE_TOKEN_LIMIT) break;
            } else {
                hyphen = true;
            }
        }
        if (token.isEmpty()) return "none";
        return token.toString();
    }

    public String fileToken() {
        return fileToken(query);
    }
}
