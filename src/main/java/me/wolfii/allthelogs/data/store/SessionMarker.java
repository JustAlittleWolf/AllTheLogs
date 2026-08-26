package me.wolfii.allthelogs.data.store;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identity of a live capture session, written to the Minecraft log so a later file import can recognise that the
 * same play session is already stored.
 * <p>
 * The line is {@code AllTheLogs session <uuid>}. A UUID is unique enough that two clients will not collide, and the
 * prefix is easy to grep for in {@code latest.log}.
 */
public final class SessionMarker {
    public static final String PREFIX = "AllTheLogs session ";
    private static final String SESSION_PATH_PREFIX = "session/";

    private static final String UUID_PATTERN =
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern ID = Pattern.compile(UUID_PATTERN, Pattern.CASE_INSENSITIVE);
    private static final Pattern IN_LINE = Pattern.compile(
        Pattern.quote(PREFIX) + "(" + UUID_PATTERN + ")", Pattern.CASE_INSENSITIVE);

    private SessionMarker() {
    }

    /**
     * A fresh session id. Canonical lowercase UUID, 122 bits of randomness.
     */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * The message body to write to the Minecraft log when a session starts. Not a chat line.
     */
    public static String message(String sessionId) {
        return PREFIX + Objects.requireNonNull(sessionId, "sessionId");
    }

    /**
     * Path stored on {@code log_file.entry_path} for a session row.
     */
    public static String entryPath(String sessionId) {
        return SESSION_PATH_PREFIX + Objects.requireNonNull(sessionId, "sessionId");
    }

    /**
     * Recovers the session UUID from a stored {@code entry_path}, or {@code null} if that path is not a session id.
     */
    public static String idFromEntryPath(String entryPath) {
        if (entryPath == null || !entryPath.startsWith(SESSION_PATH_PREFIX)) return null;
        String id = entryPath.substring(SESSION_PATH_PREFIX.length()).toLowerCase(Locale.ROOT);
        return isId(id) ? id : null;
    }

    /**
     * The session id embedded in a Minecraft log line, if this is an AllTheLogs session marker.
     */
    public static Optional<String> find(String logLine) {
        if (logLine == null || logLine.indexOf(PREFIX) < 0) return Optional.empty();
        Matcher matcher = IN_LINE.matcher(logLine);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(matcher.group(1).toLowerCase(Locale.ROOT));
    }

    /**
     * Whether {@code value} is a session UUID.
     */
    public static boolean isId(String value) {
        return value != null && ID.matcher(value).matches();
    }
}
