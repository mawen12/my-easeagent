/*
 * Copyright (c) 2021, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent.context;

import com.megaease.easeagent.config.Configs;
import com.megaease.easeagent.config.PluginConfigManager;
import com.megaease.easeagent.context.log.LoggerFactoryImpl;
import com.megaease.easeagent.context.log.LoggerMdc;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.api.InitializeContext;
import com.megaease.easeagent.plugin.api.Reporter;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.api.context.IContextManager;
import com.megaease.easeagent.plugin.api.logging.ILoggerFactory;
import com.megaease.easeagent.plugin.api.logging.Mdc;
import com.megaease.easeagent.plugin.api.metric.MetricProvider;
import com.megaease.easeagent.plugin.api.metric.MetricRegistry;
import com.megaease.easeagent.plugin.api.metric.MetricRegistrySupplier;
import com.megaease.easeagent.plugin.api.metric.name.NameFactory;
import com.megaease.easeagent.plugin.api.metric.name.Tags;
import com.megaease.easeagent.plugin.api.trace.ITracing;
import com.megaease.easeagent.plugin.api.trace.TracingProvider;
import com.megaease.easeagent.plugin.api.trace.TracingSupplier;
import com.megaease.easeagent.plugin.bridge.*;
import com.megaease.easeagent.plugin.utils.NoNull;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

public class ContextManager implements IContextManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextManager.class.getName());
    private static final ThreadLocal<SessionContext> LOCAL_SESSION_CONTEXT = ThreadLocal.withInitial(SessionContext::new);
    private final PluginConfigManager pluginConfigManager;
    private final Supplier<InitializeContext> sessionContextSupplier;
    private final GlobalContext globalContext;
    private volatile TracingSupplier tracingSupplier = (supplier) -> null;
    private volatile MetricRegistrySupplier metric = NoOpMetrics.NO_OP_METRIC_SUPPLIER;


    private ContextManager(@Nonnull Configs conf, @Nonnull PluginConfigManager pluginConfigManager, @Nonnull ILoggerFactory loggerFactory, @Nonnull Mdc mdc) {
        this.pluginConfigManager = pluginConfigManager;
        this.sessionContextSupplier = new SessionContextSupplier();
        this.globalContext = new GlobalContext(conf, new MetricRegistrySupplierImpl(), loggerFactory, mdc);
    }

    // build 基于配置初始化 ContextManager，主要核心是：配置，插件管理器，日志工厂，Mdc
    public static ContextManager build(Configs conf) {
        LOGGER.info("build context manager.");
        // 读取 ProgressFields 相关的配置，并将其保存到静态字段中，同时向配置注册监听器
        ProgressFieldsManager.init(conf);
        // 构建 PluginConfigManager 实例，负责管理插件配置，并监听配置变化
        PluginConfigManager pluginConfigManager = PluginConfigManager.builder(conf).build();
        // 初始化日志工厂
        LoggerFactoryImpl loggerFactory = LoggerFactoryImpl.build();
        ILoggerFactory iLoggerFactory = NoOpLoggerFactory.INSTANCE;
        Mdc mdc = NoOpLoggerFactory.NO_OP_MDC_INSTANCE;
        // 如果有可用的日志工厂，则使用它，并从中获取 MDC 示例
        if (loggerFactory != null) {
            iLoggerFactory = loggerFactory;
            mdc = new LoggerMdc(loggerFactory.factory().mdc());
        }
        // 创建上下文管理器
        ContextManager contextManager = new ContextManager(conf, pluginConfigManager, iLoggerFactory, mdc);
        // 将相关信息保存到 EaseAgent 的静态字段中，以便全局使用
        EaseAgent.loggerFactory = contextManager.globalContext.getLoggerFactory();
        EaseAgent.loggerMdc = contextManager.globalContext.getMdc();
        EaseAgent.initializeContextSupplier = contextManager;
        EaseAgent.metricRegistrySupplier = contextManager.globalContext.getMetric();
        EaseAgent.configFactory = contextManager.pluginConfigManager;
        return contextManager;
    }

    @Override
    public InitializeContext getContext() {
        return this.sessionContextSupplier.get();
    }

    public void setTracing(@Nonnull TracingProvider tracing) {
        LOGGER.info("set tracing supplier function.");
        this.tracingSupplier = tracing.tracingSupplier();
    }

    public void setMetric(@Nonnull MetricProvider metricProvider) {
        LOGGER.info("set metric supplier function.");
        this.metric = metricProvider.metricSupplier();
    }

    // SessionContextSupplier 复用当前线程中已有的上下文，并且初始化 tracing
    private class SessionContextSupplier implements Supplier<InitializeContext> {
        @Override
        public InitializeContext get() {
            // 尝试从 ThreadLocal 中获取当前调用的上下文，如果没有则会创建一个
            SessionContext context = LOCAL_SESSION_CONTEXT.get();
            // 读取 tracing，对于刚进入的调用， tracing 是没有的
            ITracing tracing = context.getTracing();
            if (tracing == null || tracing.isNoop()) {
                // 使用全局的 tracing supplier 来创建一个 tracing，并设置到本地的上下文中
                context.setCurrentTracing(NoNull.of(tracingSupplier.get(this), NoOpTracer.NO_OP_TRACING));
            }
            // 将 supplier 设置为自己
            if (context.getSupplier() == null) {
                context.setSupplier(this);
            }
            return context;
        }
    }

    public class MetricRegistrySupplierImpl implements MetricRegistrySupplier {

        @Override
        public MetricRegistry newMetricRegistry(IPluginConfig config, NameFactory nameFactory, Tags tags) {
            return NoNull.of(metric.newMetricRegistry(config, nameFactory, tags), NoOpMetrics.NO_OP_METRIC);
        }

        @Override
        public Reporter reporter(IPluginConfig config) {
            return NoNull.of(metric.reporter(config), NoOpReporter.NO_OP_REPORTER);
        }
    }
}
