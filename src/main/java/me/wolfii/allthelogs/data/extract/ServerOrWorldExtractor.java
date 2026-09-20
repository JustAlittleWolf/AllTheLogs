package me.wolfii.allthelogs.data.extract;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the remote server or local world a log line was recorded on, and when the player left it.
 * <p>
 * Remote servers use the address with the default port {@code 25565} dropped. Local worlds use
 * {@code world/{worldname}}. {@code Connecting to} and world-save lines set the current value.
 * Disconnect lines ({@code Stopping worker threads} / {@code Stopping [n] Worker Daemon threads})
 * clear it so later chat is not tagged with the previous server. {@link #current()} is what was in
 * effect at the latest line; {@link #last()} is the last non-null value seen in the file.
 */
public final class ServerOrWorldExtractor {
    public static final String LOCAL_PREFIX = "world/";
    public static final int DEFAULT_PORT = 25565;
    private static final String DEFAULT_PORT_SUFFIX = ":" + DEFAULT_PORT;

    private static final Pattern CONNECTING = Pattern.compile("Connecting to (.+), (\\d+)\\s*$");
    private static final Pattern SAVING_LEVEL = Pattern.compile(
        "Saving chunks for level '(?:ServerLevel\\[([^]]+)]|([^']+))'");
    private static final Pattern LOADING_DIMENSION = Pattern.compile(
        "Loading dimension -?\\d+ \\(([^)]+)\\) \\(net\\.minecraft\\.server\\.integrated\\.IntegratedServer@");
    private static final Pattern WORKER_DAEMON_STOP = Pattern.compile(
        "Stopping \\[\\d+] Worker Daemon threads");

    private String current;
    private String last;

    /**
     * Observes one log line: sets the current place, or clears it on disconnect.
     */
    public void accept(String line) {
        if (isLeave(line)) {
            current = null;
            return;
        }
        String found = find(line);
        if (found != null) {
            current = found;
            last = found;
        }
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
     * Whether {@code line} is a client disconnect from a remote server.
     */
    public static boolean isLeave(String line) {
        return line.contains("Stopping worker threads") || WORKER_DAEMON_STOP.matcher(line).find();
    }

    static String find(String line) {
        Matcher connecting = CONNECTING.matcher(line);
        if (connecting.find()) {
            String host = connecting.group(1).strip();
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
            String host = trimmed.substring(0, colon).toLowerCase(Locale.ROOT);
            String port = trimmed.substring(colon);
            return DEFAULT_PORT_SUFFIX.equals(port) ? host : host + port;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').strip();
    }
}
