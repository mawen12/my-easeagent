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

package com.megaease.easeagent.plugin.tools.trace;

import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.context.RequestContext;
import com.megaease.easeagent.plugin.interceptor.NonReentrantInterceptor;

public abstract class BaseHttpClientTracingInterceptor implements NonReentrantInterceptor {

    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
        // 读取请求
        HttpRequest request = getRequest(methodInfo, context);
        // 创建带有 span 和请求信息的请求上下文
        RequestContext requestContext = context.clientRequest(request);
        // 对 span 进行标记并启动
        HttpUtils.handleReceive(requestContext.span().start(), request);
        // 将请求上下文保存到 Context 中，以便在方法执行后使用
        context.put(getProgressKey(), requestContext);
    }

    @Override
    public void doAfter(MethodInfo methodInfo, Context context) {
        // 从 Context 中获取之前保存的请求上下文
        RequestContext requestContext = context.remove(getProgressKey());
        if (requestContext == null) {
            // 如果没有找到请求上下文，说明在 doBefore 中没有正确创建或保存，直接返回
            return;
        }
        try {
            // 读取响应
            HttpResponse responseWrapper = getResponse(methodInfo, context);
            // 记录请求状态码，错误信息
            HttpUtils.save(requestContext.span(), responseWrapper);
            // 完成 span
            requestContext.finish(responseWrapper);
        } finally {
            // 关闭请求上下文的 Scope，确保资源正确释放
            requestContext.scope().close();
        }
    }

    @Override
    public int order() {
        return Order.TRACING.getOrder();
    }

    public abstract Object getProgressKey();

    protected abstract HttpRequest getRequest(MethodInfo methodInfo, Context context);

    protected abstract HttpResponse getResponse(MethodInfo methodInfo, Context context);
}
