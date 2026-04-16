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

package com.megaease.easeagent.plugin.redis.interceptor.tracing;

import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.middleware.MiddlewareConstants;
import com.megaease.easeagent.plugin.api.middleware.Redirect;
import com.megaease.easeagent.plugin.api.middleware.RedirectProcessor;
import com.megaease.easeagent.plugin.api.middleware.Type;
import com.megaease.easeagent.plugin.api.trace.Span;
import com.megaease.easeagent.plugin.interceptor.NonReentrantInterceptor;

/**
 * 适用于 jedis 和 lettuce 的 trace
 */
public abstract class CommonRedisTracingInterceptor implements NonReentrantInterceptor {
    private static final Object ENTER = new Object();
    private static final Object SPAN_KEY = new Object();


    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
        // 读取当前已经存在的 span
        Span currentSpan = context.currentTracing().currentSpan();
        if (currentSpan.isNoop()) {
            // 对于 noop 的 span，直接跳过
            return;
        }
        //
        doTraceBefore(methodInfo, context);
    }

    @Override
    public Object getEnterKey(MethodInfo methodInfo, Context context) {
        return ENTER;
    }

    @Override
    public void doAfter(MethodInfo methodInfo, Context context) {
        this.finishTracing(methodInfo.getThrowable(), context);
    }

    @Override
    public int order() {
        return Order.TRACING.getOrder();
    }

    public abstract void doTraceBefore(MethodInfo methodInfo, Context context);

    protected void startTracing(Context context, String name, String uri, String cmd) {
        // 使用 cmd 作为 span 名称，生成新的 span
        Span span = context.nextSpan().name(name).start();
        // 标记为 client
        span.kind(Span.Kind.CLIENT);
        // 远程标识为 redis
        span.remoteServiceName("redis");
        // 保存到 context
        context.put(SPAN_KEY, span);
        if (cmd != null) {
            // 如果 cmd 不为 null，则记录到 redis.method 中
            span.tag("redis.method", cmd);
        }
        // 记录component.type -> redis
        span.tag(MiddlewareConstants.TYPE_TAG_NAME, Type.REDIS.getRemoteType());

        RedirectProcessor.setTagsIfRedirected(Redirect.REDIS, span);
    }

    protected void finishTracing(Throwable throwable, Context context) {
        try {
            // 获取 span
            Span span = context.get(SPAN_KEY);
            if (span == null) {
                return;
            }
            // 记录异常
            if (throwable != null) {
                span.error(throwable);
            }
            // 结束
            span.finish();
            // 移除 span
            context.remove(SPAN_KEY);
        } catch (Exception ignored) {
        }
    }
}
