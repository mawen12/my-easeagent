package com.megaease.easeagent.sniffer;

import brave.Tracing;
import brave.propagation.StrictCurrentTraceContext;
import com.megaease.easeagent.core.interceptor.*;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class BaseSnifferTest {
    StrictCurrentTraceContext currentTraceContext = StrictCurrentTraceContext.create();

    protected Tracing tracing() {
        Tracing tracing = Tracing.newBuilder()
                .currentTraceContext(currentTraceContext)
                .build();
        tracing.tracer();
        return tracing;
    }

    protected void verifyInvokeTimes(AgentInterceptorChainInvoker chainInvoker, int n) {
        verify(chainInvoker, times(n))
                .doBefore(any(AgentInterceptorChain.Builder.class), any(MethodInfo.class),
                        any(Map.class));

        verify(chainInvoker, times(n))
                .doAfter(any(AgentInterceptorChain.Builder.class), any(MethodInfo.class),
                        any(Map.class));
    }

    protected void verifyInterceptorTimes(AgentInterceptor agentInterceptor, int n, boolean verifyBefore) {
        if (verifyBefore) {
            verify(agentInterceptor, times(n))
                    .before(any(MethodInfo.class), any(Map.class),
                            any(AgentInterceptorChain.class));
        }
        verify(agentInterceptor, times(n))
                .after(any(MethodInfo.class), any(Map.class),
                        any(AgentInterceptorChain.class));
    }

    protected void initMock(AgentInterceptorChain.BuilderFactory builderFactory) {
        when(builderFactory.create()).thenAnswer((Answer<AgentInterceptorChain.Builder>) invocation -> {
            AgentInterceptorChain.Builder builder = mock(AgentInterceptorChain.Builder.class);
            when(builder.addInterceptor(any(AgentInterceptor.class))).thenReturn(builder);
            when(builder.build()).thenReturn(new DefaultAgentInterceptorChain(new ArrayList<>()));
            return builder;
        });
    }

    protected Supplier<AgentInterceptorChain.Builder> mockSupplier() {
        return (Supplier<AgentInterceptorChain.Builder>) () -> new DefaultAgentInterceptorChain.Builder().addInterceptor(new MockAgentInterceptor());
    }
}
