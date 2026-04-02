package com.example.agent.interceptor;

public final class MethodInfo {
    private final String method;
    private final long startNanos;
    private Object returnValue;
    private Throwable throwable;

    public MethodInfo(String method, long startNanos) {
        this.method = method;
        this.startNanos = startNanos;
    }

    public String method() {
        return method;
    }

    public long startNanos() {
        return startNanos;
    }

    public long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }

    public Object returnValue() {
        return returnValue;
    }

    public void returnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    public Throwable throwable() {
        return throwable;
    }

    public void throwable(Throwable throwable) {
        this.throwable = throwable;
    }
}

