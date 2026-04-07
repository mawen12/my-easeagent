package com.example.agent.metric;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global singleton registry that maps a method key (e.g. "com.example.GreetingService.sayHello")
 * to its {@link MethodMetrics}.
 *
 * <p>Mirrors the role of {@code ServiceMetricRegistry} in EaseAgent: it provides a thread-safe,
 * lazily-created metric entry for each distinct key.</p>
 */
public final class MethodMetricRegistry {

    /** The single global instance, used by {@link MetricInterceptor}. */
    public static final MethodMetricRegistry INSTANCE = new MethodMetricRegistry();

    private final ConcurrentHashMap<String, MethodMetrics> registry = new ConcurrentHashMap<String, MethodMetrics>();

    private MethodMetricRegistry() {
    }

    /**
     * Return the existing {@link MethodMetrics} for {@code key}, or create and register a new one.
     *
     * @param key usually the fully-qualified "ClassName.methodName" string
     * @return never null
     */
    public MethodMetrics getOrCreate(String key) {
        MethodMetrics existing = registry.get(key);
        if (existing != null) {
            return existing;
        }
        registry.putIfAbsent(key, new MethodMetrics());
        return registry.get(key);
    }

    /** Read-only view of all registered metrics, keyed by method identifier. */
    public Map<String, MethodMetrics> getAll() {
        return Collections.unmodifiableMap(registry);
    }
}

