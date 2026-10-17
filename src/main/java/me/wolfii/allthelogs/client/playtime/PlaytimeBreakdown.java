package me.wolfii.allthelogs.client.playtime;

/**
 * How a playtime bar is split into colored sections: by version, server or world, player, or a pair of them.
 * The order is the order of the menu, version groupings first, then server, then player.
 */
public enum PlaytimeBreakdown {
    VERSION(true, false, false),
    VERSION_SERVER(true, true, false),
    VERSION_PLAYER(true, false, true),
    SERVER(false, true, false),
    SERVER_PLAYER(false, true, true),
    PLAYER(false, false, true);

    private final boolean version;
    private final boolean server;
    private final boolean player;

    PlaytimeBreakdown(boolean version, boolean server, boolean player) {
        this.version = version;
        this.server = server;
        this.player = player;
    }

    public boolean byVersion() {
        return version;
    }

    public boolean byServer() {
        return server;
    }

    public boolean byPlayer() {
        return player;
    }
}
