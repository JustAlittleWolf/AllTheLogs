package me.wolfii.allthelogs.data.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Minecraft account that produced a log.
 * <p>
 * {@code Setting user:} is authoritative. A {@code Name[local:E:…] logged in} line is only a fallback
 * for the first such login in the file, because later lines are LAN guests.
 */
public final class MinecraftUserExtractor {
    private static final Pattern SETTING_USER = Pattern.compile("Setting user: (\\S+)");
    private static final Pattern LOCAL_LOGIN = Pattern.compile("(\\S+)\\[local:E:[^]]+] logged in");

    private String user;
    private boolean fromSettingUser;

    /**
     * Observes one log line and updates the detected user when this line is more trustworthy.
     */
    public void accept(String line) {
        Matcher setting = SETTING_USER.matcher(line);
        if (setting.find()) {
            user = setting.group(1);
            fromSettingUser = true;
            return;
        }
        if (fromSettingUser || user != null) return;
        Matcher login = LOCAL_LOGIN.matcher(line);
        if (login.find()) user = login.group(1);
    }

    /**
     * @return the detected player name, or {@code null} if none
     */
    public String user() {
        return user;
    }
}
