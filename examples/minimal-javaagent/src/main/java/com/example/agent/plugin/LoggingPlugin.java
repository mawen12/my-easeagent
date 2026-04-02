package com.example.agent.plugin;

import com.example.agent.config.AgentConfig;
import com.example.agent.interceptor.AgentInterceptorChain;
import com.example.agent.interceptor.LoggingInterceptor;
import com.example.agent.interceptor.MethodInfo;
import com.example.agent.interceptor.MethodInterceptor;
import com.example.agent.interceptor.TimingInterceptor;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public final class LoggingPlugin implements AgentPlugin {
    private static volatile MethodChainRouter router = MethodChainRouter.empty();

    @Override
    public String id() {
        return "logging";
    }

    @Override
    public void apply(Instrumentation inst, AgentConfig config) {
        String targetClass = config.targetClass();
        Set<String> targetMethods = config.targetMethods();
        Map<String, List<String>> methodInterceptors = config.methodInterceptors();
        targetMethods.addAll(methodInterceptors.keySet());
        router = buildRouter(config, methodInterceptors);

        ElementMatcher.Junction<MethodDescription> methodMatcher = buildMethodMatcher(targetMethods);

        new AgentBuilder.Default()
            .ignore(nameStartsWith("net.bytebuddy.")
                .or(nameStartsWith("java."))
                .or(nameStartsWith("jdk."))
                .or(nameStartsWith("sun.")))
            .type(named(targetClass))
            .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                builder.visit(Advice.to(LogAdvice.class).on(isMethod().and(methodMatcher))))
            .installOn(inst);
    }

    private MethodChainRouter buildRouter(AgentConfig config, Map<String, List<String>> methodInterceptors) {
        List<String> defaultIds = config.defaultInterceptors();
        Map<String, AgentInterceptorChain> cache = new HashMap<String, AgentInterceptorChain>();
        AgentInterceptorChain defaultChain = buildChain(defaultIds, config, cache);

        Map<String, AgentInterceptorChain> byMethodName = new HashMap<String, AgentInterceptorChain>();
        for (Map.Entry<String, List<String>> entry : methodInterceptors.entrySet()) {
            AgentInterceptorChain chain = buildChain(entry.getValue(), config, cache);
            byMethodName.put(entry.getKey(), chain);
        }

        return new MethodChainRouter(defaultChain, byMethodName);
    }

    private AgentInterceptorChain buildChain(List<String> ids,
                                             AgentConfig config,
                                             Map<String, AgentInterceptorChain> cache) {
        String signature = String.join("|", ids);
        AgentInterceptorChain existing = cache.get(signature);
        if (existing != null) {
            return existing;
        }

        List<MethodInterceptor> interceptors = new ArrayList<MethodInterceptor>();
        for (String id : ids) {
            MethodInterceptor interceptor = createInterceptor(id, config);
            if (interceptor != null) {
                interceptors.add(interceptor);
            }
        }
        if (interceptors.isEmpty()) {
            interceptors.add(new LoggingInterceptor());
        }
        AgentInterceptorChain chain = new AgentInterceptorChain(interceptors);
        cache.put(signature, chain);
        return chain;
    }

    private MethodInterceptor createInterceptor(String id, AgentConfig config) {
        if ("logging".equals(id)) {
            return new LoggingInterceptor();
        }
        if ("timing".equals(id)) {
            return new TimingInterceptor(config.timingWarnMs());
        }
        System.out.println("[agent] unknown interceptor: " + id);
        return null;
    }

    private ElementMatcher.Junction<MethodDescription> buildMethodMatcher(Set<String> methodNames) {
        ElementMatcher.Junction<MethodDescription> matcher = null;
        for (String methodName : methodNames) {
            ElementMatcher.Junction<MethodDescription> namedMatcher = named(methodName);
            matcher = matcher == null ? namedMatcher : matcher.or(namedMatcher);
        }
        return matcher == null ? named(configDefaultMethodName()) : matcher;
    }

    private String configDefaultMethodName() {
        return "sayHello";
    }

    public static class LogAdvice {
        @Advice.OnMethodEnter
        public static AdviceState onEnter(@Advice.Origin("#t.#m") String method,
                                          @Advice.Origin("#m") String methodName) {
            MethodInfo methodInfo = new MethodInfo(method, System.nanoTime());
            AgentInterceptorChain chain = router.resolve(methodName);
            chain.doBefore(methodInfo, 0);
            return new AdviceState(methodInfo, chain);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void onExit(@Advice.Enter AdviceState state,
                                  @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object retValue,
                                  @Advice.Thrown(readOnly = false, typing = Assigner.Typing.DYNAMIC) Throwable throwable) {
            MethodInfo methodInfo = state.methodInfo;
            methodInfo.returnValue(retValue);
            methodInfo.throwable(throwable);
            state.chain.doAfter(methodInfo, state.chain.size() - 1);
            retValue = methodInfo.returnValue();
            throwable = methodInfo.throwable();
        }
    }

    private static final class AdviceState {
        private final MethodInfo methodInfo;
        private final AgentInterceptorChain chain;

        private AdviceState(MethodInfo methodInfo, AgentInterceptorChain chain) {
            this.methodInfo = methodInfo;
            this.chain = chain;
        }
    }

    private static final class MethodChainRouter {
        private final AgentInterceptorChain defaultChain;
        private final Map<String, AgentInterceptorChain> byMethodName;

        private MethodChainRouter(AgentInterceptorChain defaultChain,
                                  Map<String, AgentInterceptorChain> byMethodName) {
            this.defaultChain = defaultChain;
            this.byMethodName = byMethodName;
        }

        private static MethodChainRouter empty() {
            return new MethodChainRouter(new AgentInterceptorChain(new ArrayList<MethodInterceptor>()),
                Collections.<String, AgentInterceptorChain>emptyMap());
        }

        private AgentInterceptorChain resolve(String methodName) {
            AgentInterceptorChain chain = byMethodName.get(methodName);
            return chain == null ? defaultChain : chain;
        }
    }
}

