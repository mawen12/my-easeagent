package com.example.agent.metric;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Periodically prints all registered metrics to stdout.
 *
 * <p>Mirrors the role of a Dropwizard {@code ConsoleReporter} / EaseAgent JSON reporter:
 * it reads a snapshot from {@link MethodMetricRegistry} at each interval and formats it
 * as a human-readable table.</p>
 *
 * <p>The reporter runs on a single daemon thread so it never prevents JVM shutdown.</p>
 *
 * <h3>Columns</h3>
 * <ul>
 *   <li><b>count</b>  – total invocations since agent start</li>
 *   <li><b>errors</b> – invocations that threw an exception</li>
 *   <li><b>rate</b>   – calls / second over the last report interval (delta / interval)</li>
 *   <li><b>mean</b>   – mean execution time (ms) over all invocations</li>
 *   <li><b>min</b>    – minimum execution time (ms)</li>
 *   <li><b>max</b>    – maximum execution time (ms)</li>
 * </ul>
 */
public final class ConsoleMetricReporter {

    private final long intervalSec;
    private final ScheduledExecutorService scheduler;

    /** Keeps last-reported count per key so we can compute the per-interval rate. */
    private final Map<String, Long> lastCount = new ConcurrentHashMap<String, Long>();

    public ConsoleMetricReporter(long intervalSec) {
        this.intervalSec = intervalSec;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "agent-metric-reporter");
                t.setDaemon(true);
                return t;
            }
        });
    }

    /** Start the periodic reporting loop. */
    public void start() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                report();
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);

        System.out.println("[agent] metric reporter started, interval=" + intervalSec + "s");
    }

    /** Force-stop the reporter (optional). */
    public void stop() {
        scheduler.shutdownNow();
    }

    // -------------------------------------------------------------------------

    private void report() {
        Map<String, MethodMetrics> all = MethodMetricRegistry.INSTANCE.getAll();
        if (all.isEmpty()) {
            return;
        }

        System.out.println("[agent] ---- Metrics Report ----------------------------------------");
        System.out.printf("[agent]   %-55s %6s %6s %8s %8s %8s %8s%n",
            "method", "count", "errors", "rate/s", "mean(ms)", "min(ms)", "max(ms)");
        System.out.println("[agent]   " + repeat('-', 107));

        for (Map.Entry<String, MethodMetrics> entry : all.entrySet()) {
            String key        = entry.getKey();
            MethodMetrics m   = entry.getValue();

            long  count       = m.getCount();
            long  errors      = m.getErrorCount();
            double meanMs     = m.getMeanMs();
            double minMs      = m.getMinMs();
            double maxMs      = m.getMaxMs();

            // rate = delta over this interval
            long prev         = lastCount.containsKey(key) ? lastCount.get(key) : 0L;
            double ratePerSec = (count - prev) / (double) intervalSec;
            lastCount.put(key, count);

            System.out.printf("[agent]   %-55s %6d %6d %8.2f %8.2f %8.2f %8.2f%n",
                shorten(key, 55), count, errors, ratePerSec, meanMs, minMs, maxMs);
        }

        System.out.println("[agent] -----------------------------------------------------------");
    }

    /** Trim a string to at most {@code maxLen} characters with "..." suffix if needed. */
    private static String shorten(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(s.length() - maxLen + 3) + "...";
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}

