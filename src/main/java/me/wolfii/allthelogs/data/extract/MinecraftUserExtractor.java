package me.wolfii.allthelogs.data.extract;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Minecraft account that produced a log.
 * <p>
 * {@code Setting user:} and In-Game Account Switcher login lines are authoritative and may
 * replace an earlier name when the player switches accounts. A {@code Name[local:E:…] logged in}
 * line is only a fallback for the first such login in the file, because later lines are LAN guests.
 */
public final class MinecraftUserExtractor {
    private static final Pattern SETTING_USER = Pattern.compile("Setting user: (\\S+)");
    private static final Pattern IAS_LOGGING = Pattern.compile(
        "IAS: Logging \\([^)]+\\) as [0-9a-fA-F-]{36}/(\\S+)");
    private static final Pattern IAS_SUCCESS = Pattern.compile(
        "IAS: Successful login as MCProfile\\[uuid=[0-9a-fA-F-]+, name=([^,\\]]+)]");
    private static final Pattern IAS_LOGIN_DATA = Pattern.compile(
        "IAS: Received login request: LoginData\\{name='([^']+)'");
    private static final Pattern LOCAL_LOGIN = Pattern.compile("(\\S+)\\[local:E:[^]]+] logged in");

    private String user;
    private boolean fromAuthoritative;

    /**
     * Observes one log line and updates the detected user when this line is more trustworthy.
     */
    public void accept(String line) {
        String authoritative = authoritativeUser(line);
        if (authoritative != null) {
            user = authoritative;
            fromAuthoritative = true;
            return;
        }
        if (fromAuthoritative || user != null) return;
        Matcher login = LOCAL_LOGIN.matcher(line);
        if (login.find()) user = login.group(1);
    }

    /**
     * @return the detected player name, or {@code null} if none
     */
    public String user() {
        return user;
    }

    private static String authoritativeUser(String line) {
        Matcher setting = SETTING_USER.matcher(line);
        if (setting.find()) return setting.group(1);
        Matcher iasLogging = IAS_LOGGING.matcher(line);
        if (iasLogging.find()) return iasLogging.group(1);
        Matcher iasSuccess = IAS_SUCCESS.matcher(line);
        if (iasSuccess.find()) return iasSuccess.group(1).strip();
        Matcher iasLogin = IAS_LOGIN_DATA.matcher(line);
        if (iasLogin.find()) return iasLogin.group(1);
        return null;
    }
}
