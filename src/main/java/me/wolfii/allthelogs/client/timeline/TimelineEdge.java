package me.wolfii.allthelogs.client.timeline;

/**
 * Which end of the loaded page a request is for: older messages ({@link #BEFORE}) or newer ones ({@link #AFTER}).
 * The list draws newest at the bottom, so {@code BEFORE} is toward the top of the viewport.
 */
public enum TimelineEdge {
    BEFORE, AFTER
}
