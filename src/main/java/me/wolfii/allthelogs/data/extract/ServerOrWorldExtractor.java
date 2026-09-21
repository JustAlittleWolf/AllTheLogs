package me.wolfii.allthelogs.data.extract;

import java.util.Locale;

/**
 * Finds the remote server or local world a log line was recorded on, and when the player left it.
 * <p>
 * Remote servers use the address with the default port {@code 25565} dropped. Local worlds use
 * {@code world/{worldname}}. Place switches when the player <em>joins</em> something: {@code Connecting to}
 * for multiplayer, or an integrated-server start for singleplayer. Chat only arrives while in a
 * server or world, so resource-pack reloads (including {@code Reloading ResourceManager} without a
 * {@code server/} pack) are not treated as leaves.
 * <p>
 * World names are often only logged on save, after chat from that session, so
 * {@link me.wolfii.allthelogs.data.parse.LogParser} backfills earlier untagged chat while
 * {@link #inSession()} is true. Joining singleplayer while still tagged with a remote host drops
 * that host immediately so the next chat is not stuck on the previous server. World-save lines do
 * not override a remote place (a leftover integrated-server save after disconnect).
 * Disconnect lines still clear the current value when they appear. {@link #current()} is what was
 * in effect at the latest line; {@link #last()} is the last non-null value seen in the file.
 */
public final class ServerOrWorldExtractor {
    public static final String LOCAL_PREFIX = "world/";
    private static final int DEFAULT_PORT = 25565;
    private static final String DEFAULT_PORT_SUFFIX = ":" + DEFAULT_PORT;

    private static final String CONNECTING_TO = "Connecting to ";
    private static final String SAVING_CHUNKS = "Saving chunks for level '";
    private static final String LOADING_DIMENSION = "Loading dimension ";
    private static final String INTEGRATED_SERVER_AT =
        " (net.minecraft.server.integrated.IntegratedServer@";
    private static final String SERVER_LEVEL = "ServerLevel[";

    private String current;
    private String last;
    private boolean inSession;
    /** After a leave, ignore world-save lines until a new connect / integrated-server start. */
    private boolean placeAllowed = true;

    /**
     * Observes one log line: sets the current place, or clears it on disconnect.
     */
    public void accept(String line) {
        if (isLeave(line)) {
            clearCurrent();
            return;
        }
        boolean singleplayerJoin = isSingleplayerJoin(line);
        if (singleplayerJoin && isRemote(current)) {
            current = null;
        }
        Connecting connecting = connectingMatch(line);
        if (connecting != null || singleplayerJoin) {
            inSession = true;
            placeAllowed = true;
        }
        String found = placeFrom(line, connecting);
        if (found != null && found.startsWith(LOCAL_PREFIX) && isRemote(current)) {
            found = null;
        }
        if (found != null && (inSession || placeAllowed)) {
            current = found;
            last = found;
            inSession = true;
        }
    }

    private void clearCurrent() {
        current = null;
        inSession = false;
        placeAllowed = false;
    }

    private static boolean isRemote(String place) {
        return place != null && !place.startsWith(LOCAL_PREFIX);
    }

    /**
     * Place in effect after the latest {@link #accept}, or {@code null} after a disconnect (or if none).
     */
    public String current() {
        return current;
    }

    /**
     * Last non-null place seen in the file, kept after disconnect for log-level metadata.
     */
    public String last() {
        return last;
    }

    /**
     * Whether the player is in a world or server session that may still learn a place name.
     * Menu chat before this is true is not backfilled when a later world name appears.
     */
    public boolean inSession() {
        return inSession;
    }

    /**
     * Whether {@code line} is a real disconnect: client shutdown, vanilla disconnect, or
     * singleplayer logout. Chunk/renderer {@code Stopping worker threads} and resource-pack
     * reloads are not leaves.
     */
    public static boolean isLeave(String line) {
        return line.indexOf("Stopping singleplayer server") >= 0
            || trailingAfter(line, "]: Stopping!")
            || line.indexOf("Client disconnected with reason:") >= 0;
    }

    /**
     * Whether {@code line} starts a multiplayer connect or an integrated-server session, even when
     * the world name is not known yet.
     */
    public static boolean isSessionStart(String line) {
        return connectingMatch(line) != null || isSingleplayerJoin(line);
    }

    /**
     * Integrated-server join: the client is now in a local world, even if the world name is logged
     * only later on save.
     */
    public static boolean isSingleplayerJoin(String line) {
        return line.indexOf("Starting integrated minecraft server") >= 0
            || trailingAfter(line, "]: Generating keypair")
            || localLoginAt(line) >= 0
            || loadingDimensionWorld(line) != null;
    }

    static String find(String line) {
        return placeFrom(line, connectingMatch(line));
    }

    private static String placeFrom(String line, Connecting connecting) {
        if (connecting != null) {
            String host = stripTrailingDots(connecting.host().strip());
            int port;
            try {
                port = Integer.parseInt(connecting.port());
            } catch (NumberFormatException e) {
                return remote(host);
            }
            return port > 0 && port != DEFAULT_PORT ? remote(host + ":" + port) : remote(host);
        }
        String saving = savingLevelWorld(line);
        if (saving != null) return localWorld(saving);
        String loading = loadingDimensionWorld(line);
        if (loading != null) return localWorld(loading);
        return null;
    }

    /**
     * {@code Connecting to ([^,]+), (\\d+)\\s*$}. Host cannot contain a comma; a greedy
     * {@code .+} backtracks badly on chat that mentions connecting.
     */
    private static Connecting connectingMatch(String line) {
        if (line.indexOf("Connecting to") < 0) return null;
        int from = 0;
        while (true) {
            int at = line.indexOf(CONNECTING_TO, from);
            if (at < 0) return null;
            int hostStart = at + CONNECTING_TO.length();
            int comma = indexOfChar(line, ',', hostStart);
            if (comma > hostStart) {
                int i = comma + 1;
                if (i < line.length() && line.charAt(i) == ' ') {
                    int portStart = i + 1;
                    int portEnd = portStart;
                    while (portEnd < line.length() && ExtractChars.isDigit(line.charAt(portEnd))) portEnd++;
                    if (portEnd > portStart && ExtractChars.onlyWhitespaceFrom(line, portEnd)) {
                        return new Connecting(line.substring(hostStart, comma),
                            line.substring(portStart, portEnd));
                    }
                }
            }
            from = at + 1;
        }
    }

    /** {@code Saving chunks for level '(?:ServerLevel\\[([^]]+)]|([^']+))'} */
    static String savingLevelWorld(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(SAVING_CHUNKS, from);
            if (at < 0) return null;
            int i = at + SAVING_CHUNKS.length();
            if (line.startsWith(SERVER_LEVEL, i)) {
                int nameStart = i + SERVER_LEVEL.length();
                int close = indexOfChar(line, ']', nameStart);
                if (close > nameStart) return line.substring(nameStart, close);
            }
            int quote = indexOfChar(line, '\'', i);
            if (quote > i) return line.substring(i, quote);
            from = at + 1;
        }
    }

    /**
     * {@code Loading dimension -?\\d+ \\(([^)]+)\\) \\(net\\.minecraft\\.server\\.integrated\\.IntegratedServer@}
     */
    static String loadingDimensionWorld(String line) {
        if (line.indexOf("Loading dimension") < 0 || line.indexOf("IntegratedServer@") < 0) return null;
        int from = 0;
        while (true) {
            int at = line.indexOf(LOADING_DIMENSION, from);
            if (at < 0) return null;
            int i = at + LOADING_DIMENSION.length();
            int length = line.length();
            if (i < length && line.charAt(i) == '-') i++;
            int digits = i;
            while (i < length && ExtractChars.isDigit(line.charAt(i))) i++;
            if (i > digits && i + 1 < length && line.charAt(i) == ' ' && line.charAt(i + 1) == '(') {
                int nameStart = i + 2;
                int close = indexOfChar(line, ')', nameStart);
                if (close > nameStart && line.startsWith(INTEGRATED_SERVER_AT, close + 1)) {
                    return line.substring(nameStart, close);
                }
            }
            from = at + 1;
        }
    }

    private static int indexOfChar(String line, char c, int from) {
        int at = line.indexOf(c, from);
        return at < 0 ? -1 : at;
    }

    private record Connecting(String host, String port) {
    }

    /**
     * Index of {@code [local:E:…]} when it is followed by {@code logged in}, or {@code -1}.
     */
    static int localLoginAt(String line) {
        int start = line.indexOf("[local:E:");
        if (start < 0) return -1;
        int close = line.indexOf(']', start + 9);
        if (close < 0 || !line.startsWith(" logged in", close + 1)) return -1;
        return start;
    }

    /**
     * {@code token} occurs, and only whitespace follows the last occurrence (same as {@code token\\s*$}).
     */
    private static boolean trailingAfter(String line, String token) {
        int at = line.lastIndexOf(token);
        if (at < 0) return false;
        for (int i = at + token.length(); i < line.length(); i++) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') return false;
        }
        return true;
    }

    /**
     * Stable id for a local world: {@code world/{worldname}}.
     */
    public static String localWorld(String worldName) {
        String cleaned = sanitize(worldName);
        if (cleaned.isEmpty()) cleaned = "unnamed";
        return LOCAL_PREFIX + cleaned;
    }

    /**
     * Stable id for a remote address. Drops {@code :25565}; IPv6 literals keep their brackets.
     *
     * @return the normalised address, or {@code null} when {@code address} is blank
     */
    public static String remote(String address) {
        String trimmed = sanitize(address);
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("[")) {
            int close = trimmed.indexOf(']');
            if (close > 0) {
                String host = trimmed.substring(0, close + 1).toLowerCase(Locale.ROOT);
                String rest = trimmed.substring(close + 1);
                if (rest.isEmpty() || DEFAULT_PORT_SUFFIX.equals(rest)) return host;
                return host + rest;
            }
        }
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && trimmed.indexOf(':') == colon) {
            String host = stripTrailingDots(trimmed.substring(0, colon).toLowerCase(Locale.ROOT));
            String port = trimmed.substring(colon);
            return DEFAULT_PORT_SUFFIX.equals(port) ? host : host + port;
        }
        return stripTrailingDots(trimmed.toLowerCase(Locale.ROOT));
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').strip();
    }

    private static String stripTrailingDots(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '.') end--;
        return end == value.length() ? value : value.substring(0, end);
    }
}
