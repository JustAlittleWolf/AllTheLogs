package me.wolfii.allthelogs.data.extract;

/**
 * Finds the Minecraft account that produced a log.
 * <p>
 * {@code Setting user:} and In-Game Account Switcher login lines are authoritative and may
 * replace an earlier name when the player switches accounts. A {@code Name[local:E:…] logged in}
 * line is only a fallback for the first such login in the file, because later lines are LAN guests.
 */
public final class MinecraftUserExtractor {
    private static final String SETTING_USER = "Setting user: ";
    private static final String IAS_LOGGING = "IAS: Logging";
    private static final String IAS_SUCCESS = "IAS: Successful login as MCProfile[uuid=";
    private static final String IAS_LOGIN_DATA = "IAS: Received login request: LoginData{name='";

    private String user;
    private boolean fromAuthoritative;

    /**
     * Observes one log line and updates the detected user when this line is more trustworthy.
     */
    public void accept(String line) {
        if (line.indexOf("Setting user:") >= 0 || line.indexOf("IAS:") >= 0) {
            String authoritative = authoritativeUser(line);
            if (authoritative != null) {
                user = authoritative;
                fromAuthoritative = true;
                return;
            }
        }
        if (fromAuthoritative || user != null) return;
        int loginAt = ServerOrWorldExtractor.localLoginAt(line);
        if (loginAt <= 0) return;
        int nameStart = loginAt;
        while (nameStart > 0 && !ExtractChars.isWhitespace(line.charAt(nameStart - 1))) nameStart--;
        if (nameStart < loginAt) user = line.substring(nameStart, loginAt);
    }

    /**
     * @return the detected player name, or {@code null} if none
     */
    public String user() {
        return user;
    }

    private static String authoritativeUser(String line) {
        if (line.indexOf("Setting user:") >= 0) {
            String setting = settingUser(line);
            if (setting != null) return setting;
        }
        if (line.indexOf("IAS:") < 0) return null;
        if (line.indexOf(IAS_LOGGING) >= 0) {
            String logging = iasLogging(line);
            if (logging != null) return logging;
        }
        if (line.indexOf("IAS: Successful login") >= 0) {
            String success = iasSuccess(line);
            if (success != null) return success;
        }
        if (line.indexOf("IAS: Received login request") >= 0) {
            return iasLoginData(line);
        }
        return null;
    }

    /** {@code Setting user: (\\S+)} */
    static String settingUser(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(SETTING_USER, from);
            if (at < 0) return null;
            int start = at + SETTING_USER.length();
            int end = ExtractChars.tokenEnd(line, start);
            if (end > start) return line.substring(start, end);
            from = at + 1;
        }
    }

    /** {@code IAS: Logging \\([^)]+\\) as [0-9a-fA-F-]{36}/(\\S+)} */
    static String iasLogging(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(IAS_LOGGING, from);
            if (at < 0) return null;
            int i = at + IAS_LOGGING.length();
            int length = line.length();
            if (i < length && line.charAt(i) == ' ' && i + 1 < length && line.charAt(i + 1) == '(') {
                int close = line.indexOf(')', i + 2);
                if (close > i + 2 && line.startsWith(" as ", close + 1)) {
                    int uuid = close + 5;
                    if (uuid + 37 <= length && hexDash36(line, uuid) && line.charAt(uuid + 36) == '/') {
                        int start = uuid + 37;
                        int end = ExtractChars.tokenEnd(line, start);
                        if (end > start) return line.substring(start, end);
                    }
                }
            }
            from = at + 1;
        }
    }

    /** {@code IAS: Successful login as MCProfile\\[uuid=[0-9a-fA-F-]+, name=([^,\\]]+)]} */
    static String iasSuccess(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(IAS_SUCCESS, from);
            if (at < 0) return null;
            int i = at + IAS_SUCCESS.length();
            int length = line.length();
            int uuidEnd = i;
            while (uuidEnd < length && ExtractChars.isHexOrDash(line.charAt(uuidEnd))) uuidEnd++;
            if (uuidEnd > i && line.startsWith(", name=", uuidEnd)) {
                int start = uuidEnd + 7;
                int end = start;
                while (end < length) {
                    char c = line.charAt(end);
                    if (c == ',' || c == ']') break;
                    end++;
                }
                if (end > start && end < length && line.charAt(end) == ']') {
                    return line.substring(start, end).strip();
                }
            }
            from = at + 1;
        }
    }

    /** {@code IAS: Received login request: LoginData\\{name='([^']+)'} */
    static String iasLoginData(String line) {
        int from = 0;
        while (true) {
            int at = line.indexOf(IAS_LOGIN_DATA, from);
            if (at < 0) return null;
            int start = at + IAS_LOGIN_DATA.length();
            int end = line.indexOf('\'', start);
            if (end > start) return line.substring(start, end);
            from = at + 1;
        }
    }

    private static boolean hexDash36(String line, int start) {
        for (int i = 0; i < 36; i++) {
            if (!ExtractChars.isHexOrDash(line.charAt(start + i))) return false;
        }
        return true;
    }
}
