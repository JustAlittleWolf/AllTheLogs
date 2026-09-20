package me.wolfii.allthelogs.data.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Minecraft version from loader / OptiFine / mods-list lines.
 * <p>
 * Lower pattern index wins when several version lines appear in one file.
 * {@code Starting integrated minecraft server version …} is ignored: that line has been observed
 * to disagree with the actual game version.
 */
public final class MinecraftVersionExtractor {
    private static final Pattern[] PATTERNS = {
        Pattern.compile("Loading Minecraft (\\S+) with (?:Fabric|Quilt) Loader"),
        Pattern.compile("for Minecraft (\\S+) loading"),
        Pattern.compile("OptiFine[_ ](\\d+\\.\\d+(?:\\.\\d+)?)"),
        Pattern.compile("(?i)^\\s*-\\s*minecraft\\s+(\\S+)"),
        Pattern.compile("Minecraft Version: (\\S+)"),
        Pattern.compile("--version,? (\\S+)")
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
                version = versionMatcher.group(1);
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
