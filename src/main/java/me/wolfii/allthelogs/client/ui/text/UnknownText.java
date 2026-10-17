package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.client.playtime.PlaytimeFilter;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import me.wolfii.allthelogs.client.search.MinecraftVersions;
import net.minecraft.network.chat.Component;

/**
 * The word shown when a Minecraft version, server, world, or player is not known.
 * Versions store {@code unknown}; a missing place is an empty string or {@link PlaytimeFilter#UNKNOWN_SERVER}.
 * Both are labelled with {@link #KEY}.
 */
public final class UnknownText {
    public static final String KEY = "allthelogs.unknown";

    private UnknownText() {
    }

    public static String get() {
        return Component.translatable(KEY).getString();
    }

    /**
     * {@code version} as shown in a menu, or {@link #get()} when it is not a real release.
     */
    public static String version(String version) {
        return MinecraftVersions.isUnknown(version) ? get() : version;
    }

    /**
     * Server or world name without formatting codes, or {@link #get()} when the log names none.
     */
    public static String place(String place) {
        if (place == null || place.isEmpty() || PlaytimeFilter.UNKNOWN_SERVER.equals(place)) return get();
        return PlaytimeSeries.displayName(place);
    }
}
