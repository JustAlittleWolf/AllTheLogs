package me.wolfii.allthelogs;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background work for this mod must not keep Minecraft alive after the window closes.
 * Every thread we start is a daemon so a missed shutdown cannot hang the process.
 */
public final class DaemonThreads {
    private DaemonThreads() {
    }

    public static Thread create(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    public static ThreadFactory factory(String namePrefix) {
        AtomicInteger index = new AtomicInteger();
        return runnable -> create(namePrefix + "-" + index.incrementAndGet(), runnable);
    }
}
