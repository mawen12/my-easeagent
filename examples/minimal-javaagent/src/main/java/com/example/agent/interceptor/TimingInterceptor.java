package com.example.agent.interceptor;

public final class TimingInterceptor implements MethodInterceptor {
    private final long warnThresholdMs;

    public TimingInterceptor(long warnThresholdMs) {
        this.warnThresholdMs = warnThresholdMs;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void after(MethodInfo methodInfo) {
        long elapsedMs = methodInfo.elapsedNanos() / 1_000_000L;
        if (elapsedMs >= warnThresholdMs) {
            System.out.println("[agent] slow " + methodInfo.method() + " took=" + elapsedMs + "ms");
        }
    }
}

