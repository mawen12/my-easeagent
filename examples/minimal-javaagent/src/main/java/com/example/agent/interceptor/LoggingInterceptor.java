package com.example.agent.interceptor;

public final class LoggingInterceptor implements MethodInterceptor {
    @Override
    public int order() {
        return 10;
    }

    @Override
    public void before(MethodInfo methodInfo) {
        System.out.println("[agent] enter " + methodInfo.method());
    }

    @Override
    public void after(MethodInfo methodInfo) {
        if (methodInfo.throwable() != null) {
            System.out.println("[agent] error " + methodInfo.method() + " -> " + methodInfo.throwable().getClass().getName());
            return;
        }
        System.out.println("[agent] exit  " + methodInfo.method());
    }
}

