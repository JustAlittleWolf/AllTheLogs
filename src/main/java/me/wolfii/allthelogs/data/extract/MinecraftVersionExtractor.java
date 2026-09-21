package me.wolfii.allthelogs.data.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Minecraft version from loader / OptiFine / mods-list lines.
 * <p>
 * Lower pattern index wins when several version lines appear in one file.
 * {@code Starting integrated minecraft server version …} is last: that line is often the only
 * version in vanilla logs, but it has been observed to disagree with Fabric/Forge when both exist.
 */
public final class MinecraftVersionExtractor {
    private static final Pattern[] PATTERNS = {
        Pattern.compile("Loading Minecraft (\\S+) with (?:Fabric|Quilt) Loader"),
        Pattern.compile("Loading for game Minecraft (\\S+)"),
        Pattern.compile("for Minecraft (\\S+) loading"),
        Pattern.compile("OptiFine[_ ](\\d+\\.\\d+(?:\\.\\d+)?)"),
        Pattern.compile("(?i)^\\s*-\\s*minecraft\\s+(\\S+)"),
        Pattern.compile("(?:^|[\\s,])minecraft@([^,\\s]+)"),
        Pattern.compile("Minecraft Version: (\\S+)"),
        Pattern.compile("--version,? (\\S+)"),
        Pattern.compile("Starting integrated minecraft server version (.+?)\\s*$")
    };

    private String version;
    private int priority = Integer.MAX_VALUE;

    /**
     * Observes one log line and keeps it when it is a more trustworthy version source.
     */
    public void accept(String line) {
        if (priority <= 0) return;
        for (int i = 0; i < PATTERNS.length && i < priority; i++) {
            Matcher versionMatcher = PATTERNS[i].matcher(line);
            if (versionMatcher.find()) {
                String found = versionMatcher.group(1).strip();
                if (found.isEmpty()) continue;
                version = found;
                priority = i;
                return;
            }
        }
    }

    /**
     * @return the detected version, or {@code null} if none
     */
    public String version() {
        return version;
    }
}
