package com.example.agent.interceptor;

public interface MethodInterceptor {
    default int order() {
        return 0;
    }

    default void before(MethodInfo methodInfo) {
    }

    default void after(MethodInfo methodInfo) {
    }
}

