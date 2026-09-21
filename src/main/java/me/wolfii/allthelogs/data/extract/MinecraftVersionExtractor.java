package me.wolfii.allthelogs.data.extract;

/**
 * Finds the Minecraft version from loader / OptiFine / mods-list lines.
 * <p>
 * Lower source index wins when several version lines appear in one file.
 * {@code Starting integrated minecraft server version …} is last: that line is often the only
 * version in vanilla logs, but it has been observed to disagree with Fabric/Forge when both exist.
 * Each source is a hand parse of the old regex so a mods-list or integrated-server line does not
 * run a matcher on every Minecraft mention.
 */
public final class MinecraftVersionExtractor {
    private static final int SOURCE_COUNT = 9;
    private static final String LOADING_MINECRAFT = "Loading Minecraft ";
    private static final String LOADING_FOR_GAME = "Loading for game Minecraft ";
    private static final String FOR_MINECRAFT = "for Minecraft ";
    private static final String MINECRAFT_AT = "minecraft@";
    private static final String MINECRAFT_VERSION = "Minecraft Version:";
    private static final String DASH_VERSION = "--version";
    private static final String INTEGRATED = "Starting integrated minecraft server version ";

    private String version;
    private int priority = Integer.MAX_VALUE;

    /**
     * Observes one log line and keeps it when it is a more trustworthy version source.
     */
    public void accept(String line) {
        if (priority <= 0) return;
        if (line.indexOf("inecraft") < 0 && line.indexOf("ptiFine") < 0 && line.indexOf(DASH_VERSION) < 0) {
            return;
        }
        for (int i = 0; i < SOURCE_COUNT && i < priority; i++) {
            String found = extract(i, line);
            if (found == null) continue;
            found = found.strip();
            if (found.isEmpty()) continue;
            version = found;
            priority = i;
            return;
        }
    }

    /**
     * @return the detected version, or {@code null} if none
     */
    public String version() {
        return version;
    }

    static String extract(int index, String line) {
        return switch (index) {
            case 0 -> loadingMinecraft(line);
            case 1 -> afterNeedleToken(line, LOADING_FOR_GAME);
            case 2 -> forMinecraftLoading(line);
            case 3 -> optiFine(line);
            case 4 -> modsListDash(line);
            case 5 -> minecraftAt(line);
            case 6 -> minecraftVersionColon(line);
            case 7 -> dashVersion(line);
            case 8 -> integratedServer(line);
            default -> null;
        };
    }

    /** {@code Loading Minecraft (\\S+) with (?:Fabric|Quilt) Loader} */
    private static String loadingMinecraft(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(LOADING_MINECRAFT, from);
            if (at < 0) return null;
            int start = at + LOADING_MINECRAFT.length();
            int end = ExtractChars.tokenEnd(line, start);
            if (end > start && line.startsWith(" with ", end)) {
                int loader = end + 6;
                if (line.startsWith("Fabric Loader", loader) || line.startsWith("Quilt Loader", loader)) {
                    return line.substring(start, end);
                }
            }
            from = at + 1;
        }
    }

    /** {@code for Minecraft (\\S+) loading} */
    private static String forMinecraftLoading(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(FOR_MINECRAFT, from);
            if (at < 0) return null;
            int start = at + FOR_MINECRAFT.length();
            int end = ExtractChars.tokenEnd(line, start);
            if (end > start && line.startsWith(" loading", end)) {
                return line.substring(start, end);
            }
            from = at + 1;
        }
    }

    /** {@code OptiFine[_ ](\\d+\\.\\d+(?:\\.\\d+)?)} */
    private static String optiFine(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf("OptiFine", from);
            if (at < 0) return null;
            int i = at + 8;
            if (i < line.length()) {
                char sep = line.charAt(i);
                if (sep == '_' || sep == ' ') {
                    int start = i + 1;
                    int end = optiFineVersionEnd(line, start);
                    if (end > start) return line.substring(start, end);
                }
            }
            from = at + 1;
        }
    }

    private static int optiFineVersionEnd(String line, int start) {
        int i = start;
        int length = line.length();
        int first = i;
        while (i < length && ExtractChars.isDigit(line.charAt(i))) i++;
        if (i == first || i >= length || line.charAt(i) != '.') return -1;
        i++;
        int second = i;
        while (i < length && ExtractChars.isDigit(line.charAt(i))) i++;
        if (i == second) return -1;
        if (i < length && line.charAt(i) == '.') {
            int dot = i;
            i++;
            int third = i;
            while (i < length && ExtractChars.isDigit(line.charAt(i))) i++;
            if (i == third) return dot;
        }
        return i;
    }

    /** {@code (?i)^\\s*-\\s*minecraft\\s+(\\S+)} */
    private static String modsListDash(String line) {
        int i = 0;
        int length = line.length();
        while (i < length && ExtractChars.isWhitespace(line.charAt(i))) i++;
        if (i >= length || line.charAt(i) != '-') return null;
        i++;
        while (i < length && ExtractChars.isWhitespace(line.charAt(i))) i++;
        if (i + 9 >= length || !line.regionMatches(true, i, "minecraft", 0, 9)) return null;
        i += 9;
        int afterName = i;
        while (i < length && ExtractChars.isWhitespace(line.charAt(i))) i++;
        if (i == afterName) return null;
        int end = ExtractChars.tokenEnd(line, i);
        return end > i ? line.substring(i, end) : null;
    }

    /** {@code (?:^|[\\s,])minecraft@([^,\\s]+)} */
    private static String minecraftAt(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(MINECRAFT_AT, from);
            if (at < 0) return null;
            if (at == 0 || isWsOrComma(line.charAt(at - 1))) {
                int start = at + MINECRAFT_AT.length();
                int end = start;
                int length = line.length();
                while (end < length) {
                    char c = line.charAt(end);
                    if (c == ',' || ExtractChars.isWhitespace(c)) break;
                    end++;
                }
                if (end > start) return line.substring(start, end);
            }
            from = at + 1;
        }
    }

    /** {@code --version,? (\\S+)} */
    private static String dashVersion(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(DASH_VERSION, from);
            if (at < 0) return null;
            int i = at + DASH_VERSION.length();
            if (i < line.length() && line.charAt(i) == ',') i++;
            if (i < line.length() && line.charAt(i) == ' ') {
                int start = i + 1;
                int end = ExtractChars.tokenEnd(line, start);
                if (end > start) return line.substring(start, end);
            }
            from = at + 1;
        }
    }

    /** {@code Starting integrated minecraft server version (.+)\\s*$} */
    private static String integratedServer(String line) {
        int at = line.indexOf(INTEGRATED);
        if (at < 0) return null;
        int start = at + INTEGRATED.length();
        if (start >= line.length()) return null;
        int end = line.length();
        while (end > start && ExtractChars.isWhitespace(line.charAt(end - 1))) end--;
        return end > start ? line.substring(start, end) : null;
    }

    /** {@code Minecraft Version: (\\S+)} */
    private static String minecraftVersionColon(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(MINECRAFT_VERSION, from);
            if (at < 0) return null;
            int start = at + MINECRAFT_VERSION.length();
            if (start < line.length() && line.charAt(start) == ' ') {
                start++;
                int end = ExtractChars.tokenEnd(line, start);
                if (end > start) return line.substring(start, end);
            }
            from = at + 1;
        }
    }

    private static String afterNeedleToken(String line, String needle) {
        int from = 0;
        while (true) {
            int at = line.indexOf(needle, from);
            if (at < 0) return null;
            int start = at + needle.length();
            int end = ExtractChars.tokenEnd(line, start);
            if (end > start) return line.substring(start, end);
            from = at + 1;
        }
    }

    private static boolean isWsOrComma(char c) {
        return c == ',' || ExtractChars.isWhitespace(c);
    }
}
