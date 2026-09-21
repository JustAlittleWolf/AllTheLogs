package me.wolfii.allthelogs.data.extract;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern CONNECTING = Pattern.compile("Connecting to (.+), (\\d+)\\s*$");
    private static final Pattern SAVING_LEVEL = Pattern.compile(
        "Saving chunks for level '(?:ServerLevel\\[([^]]+)]|([^']+))'");
    private static final Pattern LOADING_DIMENSION = Pattern.compile(
        "Loading dimension -?\\d+ \\(([^)]+)\\) \\(net\\.minecraft\\.server\\.integrated\\.IntegratedServer@");
    private static final Pattern CLIENT_STOP = Pattern.compile("]: Stopping!\\s*$");
    private static final Pattern CLIENT_DISCONNECTED = Pattern.compile("]: Client disconnected with reason:");
    private static final Pattern SINGLEPLAYER_STOP = Pattern.compile("Stopping singleplayer server");
    private static final Pattern INTEGRATED_START = Pattern.compile("Starting integrated minecraft server");
    private static final Pattern LOCAL_LOGIN = Pattern.compile("\\[local:E:[^]]+] logged in");
    private static final Pattern GENERATING_KEYPAIR = Pattern.compile("]: Generating keypair\\s*$");

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
        if (isSingleplayerJoin(line) && isRemote(current)) {
            current = null;
        }
        if (isSessionStart(line)) {
            inSession = true;
            placeAllowed = true;
        }
        String found = find(line);
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
        return SINGLEPLAYER_STOP.matcher(line).find()
            || CLIENT_STOP.matcher(line).find()
            || CLIENT_DISCONNECTED.matcher(line).find();
    }

    /**
     * Whether {@code line} starts a multiplayer connect or an integrated-server session, even when
     * the world name is not known yet.
     */
    public static boolean isSessionStart(String line) {
        return CONNECTING.matcher(line).find() || isSingleplayerJoin(line);
    }

    /**
     * Integrated-server join: the client is now in a local world, even if the world name is logged
     * only later on save.
     */
    public static boolean isSingleplayerJoin(String line) {
        return INTEGRATED_START.matcher(line).find()
            || LOADING_DIMENSION.matcher(line).find()
            || LOCAL_LOGIN.matcher(line).find()
            || GENERATING_KEYPAIR.matcher(line).find();
    }

    static String find(String line) {
        Matcher connecting = CONNECTING.matcher(line);
        if (connecting.find()) {
            String host = stripTrailingDots(connecting.group(1).strip());
            int port;
            try {
                port = Integer.parseInt(connecting.group(2));
            } catch (NumberFormatException e) {
                return remote(host);
            }
            return port > 0 && port != DEFAULT_PORT ? remote(host + ":" + port) : remote(host);
        }
        Matcher saving = SAVING_LEVEL.matcher(line);
        if (saving.find()) {
            String name = saving.group(1) != null ? saving.group(1) : saving.group(2);
            return localWorld(name);
        }
        Matcher loading = LOADING_DIMENSION.matcher(line);
        if (loading.find()) return localWorld(loading.group(1));
        return null;
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
