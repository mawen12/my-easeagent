package com.example.agent.interceptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs interceptors in chain order for before, and reverse order for after.
 */
public final class AgentInterceptorChain {
    private final ArrayList<MethodInterceptor> interceptors;

    public AgentInterceptorChain(List<MethodInterceptor> interceptors) {
        this.interceptors = interceptors.stream()
            .sorted(Comparator.comparingInt(MethodInterceptor::order))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public void doBefore(MethodInfo methodInfo, int pos) {
        if (pos == interceptors.size()) {
            return;
        }
        MethodInterceptor interceptor = interceptors.get(pos);
        try {
            interceptor.before(methodInfo);
        } catch (Throwable t) {
            System.out.println("[agent] interceptor before error: " + t.getMessage());
        }
        doBefore(methodInfo, pos + 1);
    }

    public void doAfter(MethodInfo methodInfo, int pos) {
        if (pos < 0) {
            return;
        }
        MethodInterceptor interceptor = interceptors.get(pos);
        try {
            interceptor.after(methodInfo);
        } catch (Throwable t) {
            System.out.println("[agent] interceptor after error: " + t.getMessage());
        }
        doAfter(methodInfo, pos - 1);
    }

    public int size() {
        return interceptors.size();
    }
}

