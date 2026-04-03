/*
 * Copyright (c) 2017, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent.zipkin.logging;

import brave.baggage.CorrelationScopeDecorator;
import brave.internal.CorrelationContext;
import brave.internal.Nullable;
import brave.propagation.CurrentTraceContext;
import com.megaease.easeagent.plugin.bridge.EaseAgent;

public class AgentMDCScopeDecorator {
    static final CurrentTraceContext.ScopeDecorator INSTANCE = new BuilderApp().build();
    static final CurrentTraceContext.ScopeDecorator INSTANCE_V2 = new BuilderEaseLogger().build();
    static final CurrentTraceContext.ScopeDecorator INSTANCE_EASEAGENT_LOADER = new BuilderAgentLoader().build();

    public static CurrentTraceContext.ScopeDecorator get() {
        return INSTANCE;
    }

    public static CurrentTraceContext.ScopeDecorator getV2() {
        return INSTANCE_V2;
    }

    public static CurrentTraceContext.ScopeDecorator getAgentDecorator() {
        return INSTANCE_EASEAGENT_LOADER;
    }

    static final class BuilderApp extends CorrelationScopeDecorator.Builder {
        BuilderApp() {
            super(MDCContextApp.INSTANCE);
        }
    }

    static final class BuilderEaseLogger extends CorrelationScopeDecorator.Builder {
        BuilderEaseLogger() {
            super(MDCContextEaseLogger.INSTANCE);
        }
    }

    static final class BuilderAgentLoader extends CorrelationScopeDecorator.Builder {
        BuilderAgentLoader() {
            super(MDCContextAgentLoader.INSTANCE);
        }
    }

    // brave 的上下文关联工具，此处用于与 slf4j 中的值进行同步
    enum MDCContextAgentLoader implements CorrelationContext {
        INSTANCE;

        @Override
        public String getValue(String name) {
            return org.slf4j.MDC.get(name);
        }

        @Override
        public boolean update(String name, @Nullable String value) {
            if (value != null) {
                org.slf4j.MDC.put(name, value);
            } else {
                org.slf4j.MDC.remove(name);
            }
            return true;
        }
    }

    // brave 的上下文关联工具，此处用于与 AgentLogMDC 中的值进行同步
    // 底层是 目标 JVM 中日志 log4j/logback 的 MDC
    enum MDCContextApp implements CorrelationContext {
        INSTANCE;

        @Override
        public String getValue(String name) {
            ClassLoader classLoader = getUserClassLoader();
            AgentLogMDC agentLogMDC = AgentLogMDC.create(classLoader);
            if (agentLogMDC == null) {
                return null;
            }
            // 从 AgentLogMDC 中获取值，保持与 brave 的上下文关联工具中的值同步
            return agentLogMDC.get(name);
        }

        @Override
        public boolean update(String name, @Nullable String value) {
            ClassLoader classLoader = getUserClassLoader();
            AgentLogMDC agentLogMDC = AgentLogMDC.create(classLoader);
            if (agentLogMDC == null) {
                return true;
            }
            // 更新 AgentLogMDC 中的值，保持与 brave 的上下文关联工具中的值同步
            if (value != null) {
                agentLogMDC.put(name, value);
            } else {
                agentLogMDC.remove(name);
            }
            return true;
        }

        private ClassLoader getUserClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    // brave 的上下文关联工具，此处用于与 ease agent 的 MDC 中的值进行同步
    // 底层是 easeagent 自身使用的 log4j 的 MDC
    enum MDCContextEaseLogger implements CorrelationContext {
        INSTANCE;

        @Override
        public String getValue(String name) {
            return EaseAgent.loggerMdc.get(name);
        }

        @Override
        public boolean update(String name, @Nullable String value) {
            if (value != null) {
                EaseAgent.loggerMdc.put(name, value);
            } else {
                EaseAgent.loggerMdc.remove(name);
            }
            return true;
        }
    }
}
