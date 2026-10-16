package me.wolfii.allthelogs.client.playtime;

/**
 * Texture laid over a series' color so series with similar colors still look different. Coordinates are
 * screen pixels divided by a unit that grows with the GUI scale, so patterns keep their size on screen.
 */
public enum PlaytimePattern {
    SOLID,
    DIAGONAL,
    HORIZONTAL,
    VERTICAL,
    CHECKER,
    DIAMOND,
    DOTS;

    /**
     * Whether the pixel at {@code x}, {@code y} is drawn in the darker pattern color.
     */
    public boolean on(int x, int y, int unit) {
        int u = Math.max(1, unit);
        int px = Math.floorDiv(x, u);
        int py = Math.floorDiv(y, u);
        return switch (this) {
            case SOLID -> false;
            case DIAGONAL -> Math.floorMod(px + py, 6) < 2;
            case HORIZONTAL -> Math.floorMod(py, 5) < 2;
            case VERTICAL -> Math.floorMod(px, 5) < 2;
            case CHECKER -> (Math.floorDiv(px, 3) + Math.floorDiv(py, 3)) % 2 == 0;
            case DIAMOND -> (Math.floorDiv(px + py, 4) + Math.floorDiv(px - py, 4)) % 2 == 0;
            case DOTS -> Math.floorMod(px, 5) < 2 && Math.floorMod(py, 5) < 2;
        };
    }
}
