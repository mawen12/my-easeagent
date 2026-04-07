package com.example.agent.metric;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds per-method metric state: call count, error count, total/min/max execution time.
 * All fields are updated in a lock-free, thread-safe manner via AtomicLong.
 */
public final class MethodMetrics {

    final AtomicLong totalCount = new AtomicLong(0);
    final AtomicLong errorCount = new AtomicLong(0);
    /** Accumulated execution time in nanoseconds (for computing mean). */
    final AtomicLong totalNanos = new AtomicLong(0);
    final AtomicLong minNanos   = new AtomicLong(Long.MAX_VALUE);
    final AtomicLong maxNanos   = new AtomicLong(0);

    /**
     * Record one method invocation.
     *
     * @param elapsedNanos elapsed time in nanoseconds
     * @param success      false when the method threw an exception
     */
    public void record(long elapsedNanos, boolean success) {
        totalCount.incrementAndGet();
        totalNanos.addAndGet(elapsedNanos);
        casMin(elapsedNanos);
        casMax(elapsedNanos);
        if (!success) {
            errorCount.incrementAndGet();
        }
    }

    // ---------- helpers ----------

    private void casMin(long v) {
        long cur;
        do {
            cur = minNanos.get();
            if (v >= cur) return;
        } while (!minNanos.compareAndSet(cur, v));
    }

    private void casMax(long v) {
        long cur;
        do {
            cur = maxNanos.get();
            if (v <= cur) return;
        } while (!maxNanos.compareAndSet(cur, v));
    }

    // ---------- snapshot (consistent enough for console reporting) ----------

    public long getCount()      { return totalCount.get(); }
    public long getErrorCount() { return errorCount.get(); }

    public double getMeanMs() {
        long c = totalCount.get();
        return c == 0 ? 0.0 : totalNanos.get() / (double) c / 1_000_000.0;
    }

    public double getMinMs() {
        long v = minNanos.get();
        return v == Long.MAX_VALUE ? 0.0 : v / 1_000_000.0;
    }

    public double getMaxMs() {
        return maxNanos.get() / 1_000_000.0;
    }
}

