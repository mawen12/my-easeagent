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

import com.megaease.easeagent.plugin.enums.Order;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.annotation.AdviceTo;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.context.RequestContext;
import com.megaease.easeagent.plugin.api.trace.Span;
import com.megaease.easeagent.plugin.httpservlet.HttpServletPlugin;
import com.megaease.easeagent.plugin.httpservlet.advice.DoFilterPoints;
import com.megaease.easeagent.plugin.httpservlet.utils.ServletUtils;
import com.megaease.easeagent.plugin.interceptor.NonReentrantInterceptor;
import com.megaease.easeagent.plugin.tools.trace.HttpRequest;
import com.megaease.easeagent.plugin.tools.trace.HttpResponse;
import com.megaease.easeagent.plugin.tools.trace.HttpUtils;
import com.megaease.easeagent.plugin.tools.trace.TraceConst;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletRequest;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 用于服务器接收请求的处理类，此处会创建 Kind=SERVER 的 span
 */
@AdviceTo(value = DoFilterPoints.class, plugin = HttpServletPlugin.class)
public class DoFilterTraceInterceptor implements NonReentrantInterceptor {
    private static final String AFTER_MARK = DoFilterTraceInterceptor.class.getName() + "$AfterMark";
    private static final String ERROR_KEY = "error";

    @Override
    public void doBefore(MethodInfo methodInfo, Context context) {
        // 以防已经有重复的存在了
        HttpServletRequest httpServletRequest = (HttpServletRequest) methodInfo.getArgs()[0];
        RequestContext requestContext = (RequestContext) httpServletRequest.getAttribute(ServletUtils.PROGRESS_CONTEXT);
        if (requestContext != null) {
            return;
        }

        HttpRequest httpRequest = new HttpServerRequest(httpServletRequest);
        // 创建带有 request + span(KIND=SERVER) + scope 的 context
        requestContext = context.serverReceive(httpRequest);
        httpServletRequest.setAttribute(ServletUtils.PROGRESS_CONTEXT, requestContext);
        // 开始 span
        HttpUtils.handleReceive(requestContext.span().start(), httpRequest);
    }

    @Override
    public void doAfter(MethodInfo methodInfo, Context context) {
        HttpServletRequest httpServletRequest = (HttpServletRequest) methodInfo.getArgs()[0];
        if (ServletUtils.markProcessed(httpServletRequest, AFTER_MARK)) {
            return;
        }
        HttpServletResponse httpServletResponse = (HttpServletResponse) methodInfo.getArgs()[1];
        RequestContext requestContext = (RequestContext) httpServletRequest.getAttribute(ServletUtils.PROGRESS_CONTEXT);
        try {
            Span span = requestContext.span();
            if (!httpServletRequest.isAsyncStarted()) { // 同步
                // 记录 http.route 到 tag
                span.tag(TraceConst.HTTP_TAG_ROUTE, ServletUtils.getHttpRouteAttributeFromRequest(httpServletRequest));
                // 结束 span
                HttpUtils.finish(span, new Response(methodInfo.getThrowable(), httpServletRequest, httpServletResponse));
            } else if (methodInfo.getThrowable() != null) { // 出现异常，就没有响应了
                // 记录异常
                span.error(methodInfo.getThrowable());
                // 结束 span
                span.finish();
            } else { // 异步
                // 使用异步监听器完成 span
                httpServletRequest.getAsyncContext().addListener(new TracingAsyncListener(requestContext), httpServletRequest, httpServletResponse);
            }
        } finally {
            requestContext.scope().close();
        }
    }

    @Override
    public int order() {
        return Order.TRACING.getOrder();
    }


    public static class Response implements HttpResponse {
        private final Throwable caught;
        private final HttpServletRequest httpServletRequest;
        private final HttpServletResponse httpServletResponse;

        public Response(Throwable caught, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
            this.caught = caught;
            this.httpServletRequest = httpServletRequest;
            this.httpServletResponse = httpServletResponse;
        }

        @Override
        public String method() {
            return httpServletRequest.getMethod();
        }

        @Override
        public String route() {
            Object maybeRoute = httpServletRequest.getAttribute(TraceConst.HTTP_ATTRIBUTE_ROUTE);
            return maybeRoute instanceof String ? (String) maybeRoute : null;
        }

        @Override
        public int statusCode() {
            if (httpServletResponse == null) {
                return 0;
            }
            int result = httpServletResponse.getStatus();
            if (caught != null && result == 200) {
                if (caught instanceof UnavailableException) {
                    return ((UnavailableException) caught).isPermanent() ? 404 : 503;
                } else {
                    return 500;
                }
            } else {
                return result;
            }
        }

        @Override
        public Throwable maybeError() {
            if (caught != null) {
                return caught;
            }
            Object maybeError = httpServletRequest.getAttribute(ERROR_KEY);
            if (maybeError instanceof Throwable) {
                return (Throwable) maybeError;
            } else {
                maybeError = httpServletRequest.getAttribute("javax.servlet.error.exception");
                return maybeError instanceof Throwable ? (Throwable) maybeError : null;
            }
        }

        @Override
        public String header(String name) {
            return httpServletResponse.getHeader(name);
        }
    }


    public static final class TracingAsyncListener implements AsyncListener {
        final RequestContext requestContext;
        // TODO 该方法存在多次并发调用的可能
        final AtomicBoolean sendHandled = new AtomicBoolean();

        TracingAsyncListener(RequestContext requestContext) {
            this.requestContext = requestContext;
        }

        public void onComplete(AsyncEvent e) {
            HttpServletRequest req = (HttpServletRequest) e.getSuppliedRequest();
            if (sendHandled.compareAndSet(false, true)) { // 仅允许一次
                HttpServletResponse res = (HttpServletResponse) e.getSuppliedResponse();
                Response response = new Response(e.getThrowable(), req, res);
                //
                HttpUtils.save(requestContext.span(), response);
                requestContext.finish(response);
            }

        }

        public void onTimeout(AsyncEvent e) {
            // 记录错误
            onError(e);
        }

        public void onError(AsyncEvent e) {
            // 记录错误
            ServletRequest request = e.getSuppliedRequest();
            if (request.getAttribute(ERROR_KEY) == null) {
                // 设置 error
                request.setAttribute(ERROR_KEY, e.getThrowable());
            }
        }

        public void onStartAsync(AsyncEvent e) {
            javax.servlet.AsyncContext eventAsyncContext = e.getAsyncContext();
            if (eventAsyncContext != null) {
                eventAsyncContext.addListener(this, e.getSuppliedRequest(), e.getSuppliedResponse());
            }
        }

        public String toString() {
            return "TracingAsyncListener{" + this.requestContext + "}";
        }
    }
}

