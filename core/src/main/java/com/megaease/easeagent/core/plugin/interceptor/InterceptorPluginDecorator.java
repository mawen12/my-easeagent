/*
 * Copyright (c) 2021, MegaEase
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

package com.megaease.easeagent.core.plugin.interceptor;

import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.AgentPlugin;
import com.megaease.easeagent.plugin.interceptor.Interceptor;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.InitializeContext;
import com.megaease.easeagent.plugin.api.config.AutoRefreshPluginConfigImpl;
import com.megaease.easeagent.plugin.api.config.AutoRefreshPluginConfigRegistry;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.bridge.NoOpIPluginConfig;

import java.util.function.Supplier;

/**
 * 封装了原始的拦截器，主要检查指定插件是否开启了配置
 */
public class InterceptorPluginDecorator implements Interceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterceptorPluginDecorator.class);
    /**
     * 原始的拦截器
     */
    private final Interceptor interceptor;
    /**
     * 插件
     */
    private final AgentPlugin plugin;
    private final AutoRefreshPluginConfigImpl config;

    public InterceptorPluginDecorator(Interceptor interceptor, AgentPlugin plugin) {
        this.interceptor = interceptor;
        this.plugin = plugin;
        this.config = AutoRefreshPluginConfigRegistry.getOrCreate(plugin.getDomain(), plugin.getNamespace(), interceptor.getType());
    }

    public IPluginConfig getConfig() {
        return this.config.getConfig();
    }

    @Override
    public void before(MethodInfo methodInfo, Context context) {
        // 读取配置
        IPluginConfig cfg = this.config.getConfig();
        InitializeContext innerContext = (InitializeContext) context;
        // 将配置保存到栈顶
        innerContext.pushConfig(cfg);
        // 如果插件未设置、开启了功能、则运行执行
        if (cfg == null || cfg.enabled() || cfg instanceof NoOpIPluginConfig) {
            innerContext.pushRetBound();
            this.interceptor.before(methodInfo, context);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("plugin.{}.{}.{} is not enabled", config.domain(), config.namespace(), config.id());
        }
    }

    @Override
    public void after(MethodInfo methodInfo, Context context) {
        IPluginConfig cfg = context.getConfig();
        InitializeContext innerContext = (InitializeContext) context;
        try {
            if (cfg == null || cfg.enabled() || cfg instanceof NoOpIPluginConfig) {
                try {
                    this.interceptor.after(methodInfo, context);
                } finally {
                    innerContext.popToBound();
                    innerContext.popRetBound();
                }
            }
        } finally {
            innerContext.popConfig();
        }
    }

    @Override
    public String getType() {
        return this.interceptor.getType();
    }

    @Override
    public void init(IPluginConfig config, String type, String method, String methodDescriptor) {
        this.interceptor.init(config, type, method, methodDescriptor);
    }

    @Override
    public void init(IPluginConfig config, int uniqueIndex) {
        this.interceptor.init(config, uniqueIndex);
    }

    @Override
    public int order() {
        int pluginOrder = this.plugin.order();
        int interceptorOrder = this.interceptor.order();
        return (interceptorOrder << 8) + pluginOrder;
    }

    public static Supplier<Interceptor> getInterceptorSupplier(final AgentPlugin plugin, final Supplier<Interceptor> supplier) {
        return () -> new InterceptorPluginDecorator(supplier.get(), plugin);
    }
}
