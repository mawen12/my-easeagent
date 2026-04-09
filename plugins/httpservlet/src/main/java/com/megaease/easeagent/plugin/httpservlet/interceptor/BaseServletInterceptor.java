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

package com.megaease.easeagent.plugin.httpservlet.interceptor;

import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.httpservlet.utils.InternalAsyncListener;
import com.megaease.easeagent.plugin.httpservlet.utils.ServletUtils;
import com.megaease.easeagent.plugin.interceptor.NonReentrantInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class BaseServletInterceptor implements NonReentrantInterceptor {

    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) methodInfo.getArgs()[0];
        ServletUtils.startTime(httpServletRequest);
    }

    @Override
    public void doAfter(MethodInfo methodInfo, Context context) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) methodInfo.getArgs()[0];
        // 读取 start time
        final long start = ServletUtils.startTime(httpServletRequest);
        // 检查请求头上是否有 mark 标志，如果有则说明已经被处理过了，直接返回
        if (ServletUtils.markProcessed(httpServletRequest, getAfterMark())) {
            return;
        }
        // 读取 http 路由
        String httpRoute = ServletUtils.getHttpRouteAttributeFromRequest(httpServletRequest);
        // 使用 method + http 路由作为 key
        final String key = httpServletRequest.getMethod() + " " + httpRoute;
        // 读取第二个参数
        HttpServletResponse httpServletResponse = (HttpServletResponse) methodInfo.getArgs()[1];
        if (methodInfo.getThrowable() != null) {
            // 如果失败了，则直接调用 internalAfter 方法，并传入异常信息
            internalAfter(methodInfo.getThrowable(), key, httpServletRequest, httpServletResponse, start);
        } else if (httpServletRequest.isAsyncStarted()) {
            // 注册监听器，处理异步场景
            httpServletRequest.getAsyncContext().addListener(new InternalAsyncListener(
                    asyncEvent -> {
                        HttpServletResponse suppliedResponse = (HttpServletResponse) asyncEvent.getSuppliedResponse();
                        // 异步请求完成后，调用 internalAfter 方法，并传入异常信息（如果有的话）
                        internalAfter(asyncEvent.getThrowable(), key, httpServletRequest, suppliedResponse, start);
                    }

                )
            );
        } else {
            internalAfter(null, key, httpServletRequest, httpServletResponse, start);
        }
    }

    abstract String getAfterMark();

    abstract void internalAfter(Throwable throwable, String key, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, long start);
}
