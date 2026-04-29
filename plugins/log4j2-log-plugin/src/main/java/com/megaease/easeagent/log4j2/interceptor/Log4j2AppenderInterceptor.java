/*
 * Copyright (c) 2022, MegaEase
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
 *
 */
package com.megaease.easeagent.log4j2.interceptor;

import com.megaease.easeagent.log4j2.points.AbstractLoggerPoints;
import com.megaease.easeagent.plugin.annotation.AdviceTo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.api.config.PluginConfigChangeListener;
import com.megaease.easeagent.plugin.api.otlp.common.AgentLogData;
import com.megaease.easeagent.plugin.api.otlp.common.LogMapper;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.interceptor.NonReentrantInterceptor;
import com.megaease.easeagent.plugin.tools.loader.AgentHelperClassLoader;
import com.megaease.easeagent.plugin.utils.common.StringUtils;
import com.megaease.easeagent.plugin.utils.common.WeakConcurrentMap;
import org.apache.logging.log4j.Level;

import java.lang.reflect.InvocationTargetException;

@AdviceTo(AbstractLoggerPoints.class)
public class Log4j2AppenderInterceptor implements NonReentrantInterceptor, PluginConfigChangeListener {
    static WeakConcurrentMap<ClassLoader, LogMapper> logMappers = new WeakConcurrentMap<>();
    // config[level] -> default[INFO] 要记录的日志级别
    int collectLevel = Level.INFO.intLevel();
    @Override
    public void init(IPluginConfig config, int uniqueIndex) {
        // read level config
        String lv = config.getString("level");
        if (StringUtils.isNotEmpty(lv)) {
            // 如果未配置，则默认使用 OFF 级别
            collectLevel = Level.toLevel(lv, Level.OFF).intLevel();
        }

        config.addChangeListener(this); // 支持配置变更

        AgentHelperClassLoader.registryUrls(this.getClass());
    }

    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
        // 读取执行目标方法的类加载器
        ClassLoader appLoader = methodInfo.getInvoker().getClass().getClassLoader();
        // 读取该类加载器的
        LogMapper mapper = logMappers.getIfPresent(appLoader);

        if (mapper == null) {
            // 构造 ClassLoader，为了解决 Message 找不到的问题
            // 该 ClassLoader 以用户app的ClassLoader 为主，确保能够加载到用户app的 log4j2 的 Message/Level/Logger/ThreadContext
            ClassLoader help = AgentHelperClassLoader.getClassLoader(appLoader, EaseAgent.getAgentClassLoader());
            try {
                // 加载 Log4jLogMapper，实际上该类 appClassLoader 就可以读取
                Class<?> cls = help.loadClass("com.megaease.easeagent.log4j2.log.Log4jLogMapper");
                // 初始化
                mapper = (LogMapper) cls.getConstructor().newInstance();
                // 保存
                logMappers.putIfProbablyAbsent(appLoader, mapper);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException | InstantiationException e) {
                return;
            }
        }

        // 将日志转换为 AgentLogData
        AgentLogData log = mapper.mapLoggingEvent(methodInfo, this.collectLevel, context.getConfig());
        if (log != null) {
            // 上报日志
            EaseAgent.getAgentReport().report(log);
        }
    }

    @Override
    public String getType() {
        return Order.LOG.getName();
    }

    @Override
    public int order() {
        return Order.LOG.getOrder();
    }

    @Override
    public void onChange(IPluginConfig oldConfig, IPluginConfig newConfig) {
        // 读取新的日志配置
        String lv = newConfig.getString("level");

        // 更新配置值
        if (!StringUtils.isEmpty(lv)) {
            this.collectLevel = Level.toLevel(lv, Level.OFF).intLevel();
        } else {
            this.collectLevel = Level.OFF.intLevel();
        }
    }
}
