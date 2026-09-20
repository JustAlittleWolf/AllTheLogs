package me.wolfii.allthelogs.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Diagnostic timings for the single-threaded store worker and nested DuckDB work.
 * <p>
 * Each sample records how long a job sat in the worker queue ({@link Sample#waitNanos()}) versus how
 * long it held the connection ({@link Sample#runNanos()}). Queue wait is what makes the browser look
 * "not ready" while import, clustering, or a previous query is still running.
 */
public final class StoreAnalytics {
    public static final StoreAnalytics INSTANCE = new StoreAnalytics();
    public static final int HISTORY = 16;
    /** Quiet ops (live chat, session-end touch) are stored only if they exceed this. */
    public static final long QUIET_THRESHOLD_NANOS = 5_000_000L;
    /** Worker jobs at least this long are also written to the log sink. */
    public static final long LOG_THRESHOLD_NANOS = 10_000_000L;

    private final Object lock = new Object();
    private final ArrayDeque<Sample> recent = new ArrayDeque<>();
    private final Map<String, Totals> totals = new LinkedHashMap<>();
    private final AtomicInteger outstanding = new AtomicInteger();
    private volatile String current = "idle";
    private volatile Consumer<String> log = ignored -> {
    };

    public record Sample(String name, long waitNanos, long runNanos, int queueDepth, String detail) {
        public Sample {
            Objects.requireNonNull(name, "name");
            if (waitNanos < 0) waitNanos = 0;
            if (runNanos < 0) runNanos = 0;
            if (queueDepth < 0) queueDepth = 0;
        }

        public long waitMs() {
            return waitNanos / 1_000_000L;
        }

        public long runMs() {
            return runNanos / 1_000_000L;
        }

        public String line() {
            String extra = detail == null || detail.isBlank() ? "" : " " + detail;
            if (waitNanos > 0) {
                return "%s  wait %sms  run %sms  queued %s%s".formatted(
                    name, Long.toString(waitMs()), Long.toString(runMs()), Integer.toString(queueDepth), extra);
            }
            return "%s  run %sms%s".formatted(name, Long.toString(runMs()), extra);
        }
    }

    public record Totals(long count, long waitNanos, long runNanos) {
        public long waitMs() {
            return waitNanos / 1_000_000L;
        }

        public long runMs() {
            return runNanos / 1_000_000L;
        }
    }

    public void setLog(Consumer<String> log) {
        this.log = log == null ? ignored -> {
        } : log;
    }

    public String current() {
        return current;
    }

    public int outstanding() {
        return outstanding.get();
    }

    public List<Sample> recent() {
        synchronized (lock) {
            return List.copyOf(recent);
        }
    }

    public Map<String, Totals> totals() {
        synchronized (lock) {
            return Map.copyOf(totals);
        }
    }

    /**
     * Snapshot for the "?" tooltip and {@code /allthelogs perf}: current job, then recent samples
     * newest first, then totals for the slowest names.
     */
    public List<String> report() {
        List<String> lines = new ArrayList<>();
        lines.add("Store worker: " + current() + " (" + outstanding() + " queued)");
        List<Sample> samples = recent();
        for (int i = samples.size() - 1; i >= 0; i--) {
            lines.add(samples.get(i).line());
        }
        List<Map.Entry<String, Totals>> ranked = new ArrayList<>(totals().entrySet());
        ranked.sort((a, b) -> Long.compare(b.getValue().runNanos(), a.getValue().runNanos()));
        int shown = 0;
        for (Map.Entry<String, Totals> entry : ranked) {
            if (shown >= 6) break;
            Totals total = entry.getValue();
            if (total.count() == 0) continue;
            lines.add("total %s  n=%s  wait %sms  run %sms".formatted(
                entry.getKey(), Long.toString(total.count()), Long.toString(total.waitMs()),
                Long.toString(total.runMs())));
            shown++;
        }
        return lines;
    }

    public void clear() {
        synchronized (lock) {
            recent.clear();
            totals.clear();
        }
        current = "idle";
        outstanding.set(0);
    }

    /**
     * Marks a job as waiting on the worker. Call {@link Job#run(Callable)} on the worker thread.
     */
    public Job submit(String name) {
        int depth = outstanding.getAndIncrement();
        return new Job(name, System.nanoTime(), depth);
    }

    public <T> T measure(String name, Callable<T> work) throws Exception {
        return measure(name, null, work);
    }

    public <T> T measure(String name, String detail, Callable<T> work) throws Exception {
        Objects.requireNonNull(name, "name");
        String previous = current;
        current = name;
        long start = System.nanoTime();
        try {
            return work.call();
        } finally {
            finished(name, 0, System.nanoTime() - start, 0, detail);
            current = previous == null ? "idle" : previous;
        }
    }

    public void measure(String name, Runnable work) {
        try {
            measure(name, () -> {
                work.run();
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Like {@link #measure(String, Callable)} but omits history unless the run exceeds
     * {@link #QUIET_THRESHOLD_NANOS}.
     */
    public <T> T measureQuiet(String name, Callable<T> work) throws Exception {
        String previous = current;
        current = name;
        long start = System.nanoTime();
        try {
            return work.call();
        } finally {
            long run = System.nanoTime() - start;
            if (run >= QUIET_THRESHOLD_NANOS) {
                finished(name, 0, run, 0, null);
            }
            current = previous == null ? "idle" : previous;
        }
    }

    void finished(String name, long waitNanos, long runNanos, int queueDepth, String detail) {
        Sample sample = new Sample(name, waitNanos, runNanos, queueDepth, detail);
        synchronized (lock) {
            recent.addLast(sample);
            while (recent.size() > HISTORY) {
                recent.removeFirst();
            }
            Totals previous = totals.getOrDefault(name, new Totals(0, 0, 0));
            totals.put(name, new Totals(previous.count() + 1,
                previous.waitNanos() + sample.waitNanos(),
                previous.runNanos() + sample.runNanos()));
        }
        if (waitNanos >= LOG_THRESHOLD_NANOS || runNanos >= LOG_THRESHOLD_NANOS) {
            log.accept("[store-perf] " + sample.line());
        }
    }

    public final class Job {
        private final String name;
        private final long queuedAt;
        private final int queueDepth;

        private Job(String name, long queuedAt, int queueDepth) {
            this.name = name;
            this.queuedAt = queuedAt;
            this.queueDepth = queueDepth;
        }

        public <T> T run(Callable<T> work) throws Exception {
            current = name;
            long start = System.nanoTime();
            try {
                return work.call();
            } finally {
                outstanding.updateAndGet(value -> Math.max(0, value - 1));
                finished(name, start - queuedAt, System.nanoTime() - start, queueDepth, null);
                current = "idle";
            }
        }

        public void run(Runnable work) {
            try {
                run(() -> {
                    work.run();
                    return null;
                });
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
