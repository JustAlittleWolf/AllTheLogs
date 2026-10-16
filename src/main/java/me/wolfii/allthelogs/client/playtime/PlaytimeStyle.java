package me.wolfii.allthelogs.client.playtime;

/**
 * How bucket totals are drawn. Independent of {@link PlaytimeBreakdown}.
 */
public enum PlaytimeStyle {
    /** Playtime that falls inside the bucket. */
    BARS,
    /** Running total of this bucket and every earlier one. */
    CUMULATIVE,
    /** Share of the bucket. A bucket with no playtime is left empty. */
    PERCENT
}
