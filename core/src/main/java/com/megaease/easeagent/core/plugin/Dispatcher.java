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

package com.megaease.easeagent.core.plugin;

import com.google.auto.service.AutoService;
import com.megaease.easeagent.core.utils.AgentArray;
import com.megaease.easeagent.core.utils.ContextUtils;
import com.megaease.easeagent.plugin.AppendBootstrapLoader;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.api.InitializeContext;
import com.megaease.easeagent.plugin.interceptor.AgentInterceptorChain;

/**
 * 该类使用 AppendBootstrapLoader.class ，代表它会被注入到 bootstrap classloader 中。
 * 为什么该类要被注入到 bootstrap classloader 中，因为它会在 CommonInlineAdvice 中使用后，
 * 其中的 enter 和 exit 方法会被注入到用户类中。
 *
 * - 该类中使用 ContextUtils 也被注入到 bootstrap classloader 中
 * - 该类中使用 AgentArray 也被注入到 bootstrap classloader 中
 */
@AutoService(AppendBootstrapLoader.class)
public final class Dispatcher {

    private Dispatcher() {
    }

    static AgentArray<AgentInterceptorChain> chains = new AgentArray<>();

    /**
     * for chains only modified during related class loading process,
     * so it doesn't need to consider updating process
     * otherwise, chain should store in context, avoiding changed during enter and exit
     */
    public static void enter(int index, MethodInfo info, InitializeContext ctx) {
        // 获取拦截器链
        AgentInterceptorChain chain = chains.getUncheck(index);
        // 从开头开始执行
        int pos = 0;
        // 记录开始时间，用于在 exit 方法中计算方法的执行时间，之后用于 metric
        ContextUtils.setBeginTime(ctx);
        // 进入拦截器链的的第一个拦截器
        chain.doBefore(info, pos, ctx);
    }

    public static Object exit(int index, MethodInfo info, InitializeContext ctx) {
        // 获取拦截器链
        AgentInterceptorChain chain = chains.getUncheck(index);
        // 从最末尾开始执行
        int pos = chain.size() - 1;
        // 记录结束时间，用于计算方法的执行时间，之后用于 metric
        ContextUtils.setEndTime(ctx);
        // 进入拦截器链的最后一个拦截器
        return chain.doAfter(info, pos, ctx);
    }

    public static AgentInterceptorChain register(int index, AgentInterceptorChain chain) {
        return chains.putIfAbsent(index, chain);
    }

    // for interceptor
    public static AgentInterceptorChain getChain(int index) {
        return chains.get(index);
    }

    // updateChain 在运行时检测到链发生了变化时被调用，更新链中的拦截器列表
    // 比如 http 注册了一个 metric 拦截器，后面有注册了一个 tracing，这时候需要对两个进行合并
    public static boolean updateChain(int index, AgentInterceptorChain chain) {
        return chains.replace(index, chain) != null;
    }
}
