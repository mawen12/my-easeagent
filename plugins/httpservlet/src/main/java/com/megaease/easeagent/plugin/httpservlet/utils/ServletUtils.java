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

package com.megaease.easeagent.plugin.httpservlet.utils;

import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterTraceInterceptor;
import com.megaease.easeagent.plugin.utils.ClassUtils;
import lombok.SneakyThrows;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ServletUtils {
    public static final Logger LOGGER = EaseAgent.getLogger(ServletUtils.class);
    public static final String START_TIME = ServletUtils.class.getName() + "$StartTime";
    public static final String PROGRESS_CONTEXT = DoFilterTraceInterceptor.class.getName() + ".RequestContext";
    public static final String HANDLER_MAPPING_CLASS = "org.springframework.web.servlet.HandlerMapping";
    public static final String BEST_MATCHING_PATTERN_ATTRIBUTE_FIELD_NAME = "BEST_MATCHING_PATTERN_ATTRIBUTE";
    public static final String BEST_MATCHING_PATTERN_ATTRIBUTE;

    // 用于获取请求的 route，比如 /api/comments/{id}
    static {
        String pattern = null;
        Object field = ClassUtils.getStaticField(HANDLER_MAPPING_CLASS, BEST_MATCHING_PATTERN_ATTRIBUTE_FIELD_NAME);
        if (field == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("class<{}>.{} not found ", HANDLER_MAPPING_CLASS, BEST_MATCHING_PATTERN_ATTRIBUTE_FIELD_NAME);
            }
            pattern = "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";
        } else if (field instanceof String) {
            pattern = (String) field;
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("class<{}>.{} is not String", HANDLER_MAPPING_CLASS, BEST_MATCHING_PATTERN_ATTRIBUTE_FIELD_NAME);
            }
            pattern = "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";
        }
        BEST_MATCHING_PATTERN_ATTRIBUTE = pattern;
    }

    public static String matchUrlBySpringWeb(HttpServletRequest request) {
        return (String) request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
    }

    public static String getHttpRouteAttributeFromRequest(HttpServletRequest request) {
        Object httpRoute = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
        return httpRoute != null ? httpRoute.toString() : "";
    }

    // 检查请求头上是否存在指定属性
    public static boolean markProcessed(HttpServletRequest request, String mark) {
        // 读取
        if (request.getAttribute(mark) != null) {
            return true;
        }
        request.setAttribute(mark, "m");
        return false;
    }

    // startTime 读取开始时间，如果没有则设置为当前时间
    public static long startTime(HttpServletRequest httpServletRequest) {
        // 读取请求头上的开始时间
        Object startObj = httpServletRequest.getAttribute(START_TIME);
        Long start = null;
        if (startObj == null) {// 没有则设置为当前时间
            start = System.currentTimeMillis();
            httpServletRequest.setAttribute(START_TIME, start);
        } else { // 有则返回
            start = (Long) startObj;
        }
        return start;
    }


    // getQueries 读取请求的 queryString
    @SneakyThrows
    public static Map<String, List<String>> getQueries(HttpServletRequest httpServletRequest) {
        Map<String, List<String>> map = new HashMap<>();
        // 获取查询字符串
        String queryString = httpServletRequest.getQueryString();
        if (queryString == null || queryString.isEmpty()) {
            return map;
        }
        // 使用 & 拆分
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            // 提取 key value
            int idx = pair.indexOf("=");
            String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
            if (!map.containsKey(key)) {
                map.put(key, new LinkedList<>());
            }
            // 处理多值场景
            String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
            map.get(key).add(value);
        }
        return map;
    }

    // getQueries4SingleValue 对 queryString 中的多值仅取首个值
    public static Map<String, String> getQueries4SingleValue(HttpServletRequest httpServletRequest) {
        Map<String, List<String>> map = getQueries(httpServletRequest);
        Map<String, String> singleValueMap = new HashMap<>();
        map.forEach((key, values) -> {
            if (values != null && values.size() > 0) {
                singleValueMap.put(key, values.get(0));
            }
        });
        return singleValueMap;
    }
}
