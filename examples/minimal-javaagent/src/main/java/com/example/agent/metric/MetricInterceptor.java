package com.example.agent.metric;

import com.example.agent.interceptor.MethodInfo;
import com.example.agent.interceptor.MethodInterceptor;

/**
 * A {@link MethodInterceptor} that records per-method metrics into {@link MethodMetricRegistry}.
 *
 * <p>Mirrors the role of {@code JdbcDataSourceMetricInterceptor} in EaseAgent:</p>
 * <ul>
 *   <li>{@link #before} – nothing to do (start time is already captured by {@link MethodInfo}).</li>
 *   <li>{@link #after}  – resolve the key (method signature), determine success/failure,
 *       then delegate to {@link MethodMetrics#record(long, boolean)}.</li>
 * </ul>
 */
public final class MetricInterceptor implements MethodInterceptor {

    /** Run after logging (10) and timing (20). */
    @Override
    public int order() {
        return 30;
    }

    @Override
    public void before(MethodInfo methodInfo) {
        // start time is already stored in MethodInfo.startNanos — nothing extra needed
    }

    @Override
    public void after(MethodInfo methodInfo) {
        long elapsedNanos = methodInfo.elapsedNanos();
        boolean success   = methodInfo.throwable() == null;

        // key = fully-qualified "ClassName.methodName" (same as methodInfo.method())
        MethodMetricRegistry.INSTANCE
            .getOrCreate(methodInfo.method())
            .record(elapsedNanos, success);
    }
}

