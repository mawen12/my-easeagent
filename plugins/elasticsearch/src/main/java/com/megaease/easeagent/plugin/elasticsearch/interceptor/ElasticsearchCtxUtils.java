/*
 * Copyright (c) 2021 MegaEase
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

package com.megaease.easeagent.plugin.elasticsearch.interceptor;

import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.middleware.MiddlewareConstants;
import com.megaease.easeagent.plugin.api.middleware.Type;
import com.megaease.easeagent.plugin.api.trace.Span;
import com.megaease.easeagent.plugin.interceptor.MethodInfo;
import com.megaease.easeagent.plugin.utils.common.StringUtils;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

import java.nio.charset.StandardCharsets;

public class ElasticsearchCtxUtils {
    private static final String SPAN = ElasticsearchCtxUtils.class.getName() + "-Span";
    public static final String REQUEST = ElasticsearchCtxUtils.class.getName() + "-Request";

    // initSpan
    @SneakyThrows
    public static void initSpan(MethodInfo methodInfo, Context context) {
        // 读取第一个参数，其使用的 Points 拦截的第一个参数都是 Request
        Request request = (Request) methodInfo.getArgs()[0];
        HttpEntity entity = request.getEntity();
        // 生成新的 span，用于对 Elasticsearch 执行的请求独立统计耗时
        Span span = context.nextSpan();
        // 为 span 添加标识，这是 elasticsearch client 侧发出的请求
        span.kind(Span.Kind.CLIENT);
        span.remoteServiceName("elasticsearch");
        // 记录 middleware tag
        span.tag(MiddlewareConstants.TYPE_TAG_NAME, Type.ELASTICSEARCH.getRemoteType());
        // 记录要访问的 es.index tag
        span.tag("es.index", getIndex(request.getEndpoint()));
        // 记录要访问的es的请求操作 tag
        span.tag("es.operation", request.getMethod() + " " + request.getEndpoint());
        if (entity != null) {
            String body = EntityUtils.toString(entity, StandardCharsets.UTF_8);
            // 记录请求体 es.body tag
            span.tag("es.body", body);
        }
        // 开始计时
        span.start();
        // 将 span 保存到上下文中，在请求结束时需要将 span 取出来
        context.put(SPAN, span);
        // 将 request 保存到上下文中，比如在请求结束时，在 metric 统计时，获取请求的 endpoint
        context.put(REQUEST, request);
    }

    public static String getIndex(String endpoint) {
        if (StringUtils.isEmpty(endpoint)) {
            return "";
        }
        String tmp = endpoint;
        if (!tmp.startsWith("/")) {
            tmp = "/" + tmp;
        }
        int end = tmp.indexOf("/", 1);
        String index;
        if (end < 0) {
            index = tmp.substring(1);
        } else if (end > 0) {
            index = tmp.substring(1, end);
        } else {
            index = tmp.substring(1);
        }
        if (index.startsWith("_") || index.startsWith("-") || index.startsWith("+")) {
            return "";
        }
        return index;
    }

    // checkSuccess 只有 200/201 才被视为成功
    public static boolean checkSuccess(Response response, Throwable throwable) {
        if (throwable != null) {
            return false;
        }
        if (response == null) {
            return false;
        }
        return response.getStatusLine().getStatusCode() == 200
            || response.getStatusLine().getStatusCode() == 201;
    }

    public static void finishSpan(Response response, Throwable throwable, Context context) {
        Span span = context.get(SPAN);
        // 没有 span 则不进行处理
        if (span == null) {
            return;
        }
        if (throwable != null) {
            // 在 span 中记录错误
            span.error(throwable);
            span.tag("error", throwable.getMessage());
        } else {
            if (!checkSuccess(response, null)) {
                if (response != null) {
                    // 在 span 中记录错误响应码
                    int statusCode = response.getStatusLine().getStatusCode();
                    span.tag("error", String.valueOf(statusCode));
                } else {
                    // 虽然没有异常，但是也没有响应
                    span.tag("error", "unknown");
                }

            }
        }
        // 结束底层 span
        span.finish();
        context.remove(SPAN);
    }
}
