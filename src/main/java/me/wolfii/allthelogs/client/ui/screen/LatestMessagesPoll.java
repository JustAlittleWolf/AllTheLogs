package me.wolfii.allthelogs.client.ui.screen;

import java.util.concurrent.TimeUnit;

/** Throttles live queries and discards a result if following stopped while it was in flight. */
final class LatestMessagesPoll {
    private static final long INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private long lastStarted;
    private boolean started;
    private boolean pending;
    private boolean valid;

    boolean begin(long now, boolean following) {
        if (!following) {
            pause();
            return false;
        }
        if (pending || started && now - lastStarted < INTERVAL) return false;
        lastStarted = now;
        started = true;
        pending = true;
        valid = true;
        return true;
    }

    boolean finish(boolean following) {
        boolean apply = pending && valid && following;
        pending = false;
        valid = false;
        return apply;
    }

    void pause() {
        valid = false;
    }
}
