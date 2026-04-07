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

package com.megaease.easeagent.plugin.api;

import com.megaease.easeagent.plugin.utils.common.StringUtils;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

public class ProgressFields {
    public static final String EASEAGENT_PROGRESS_FORWARDED_HEADERS_CONFIG = "easeagent.progress.forwarded.headers";
    public static final String OBSERVABILITY_TRACINGS_TAG_RESPONSE_HEADERS_CONFIG = "observability.tracings.tag.response.headers.";
    public static final String OBSERVABILITY_TRACINGS_SERVICE_TAGS_CONFIG = "observability.tracings.service.tags.";
    private static volatile Fields responseHoldTagFields = build(OBSERVABILITY_TRACINGS_TAG_RESPONSE_HEADERS_CONFIG, Collections.emptyMap());
    private static volatile Fields serviceTags = build(OBSERVABILITY_TRACINGS_SERVICE_TAGS_CONFIG, Collections.emptyMap());
    private static final Set<String> forwardHeaderSet = new HashSet<>();


    // changeListener 使用
    public static Consumer<Map<String, String>> changeListener() {
        return values -> new Change().putAll(values).flush();
    }

    public static boolean isProgressFields(String key) {
        return isForwardedHeader(key) || isResponseHoldTagKey(key) || isServerTags(key);
    }

    private static boolean isForwardedHeader(String key) {
        return key.equals(EASEAGENT_PROGRESS_FORWARDED_HEADERS_CONFIG);
    }

    private static boolean isResponseHoldTagKey(String key) {
        return key.startsWith(OBSERVABILITY_TRACINGS_TAG_RESPONSE_HEADERS_CONFIG);
    }

    private static boolean isServerTags(String key) {
        return key.startsWith(OBSERVABILITY_TRACINGS_SERVICE_TAGS_CONFIG);
    }

    // buildForwardedHeaderSet 将值通过 , 拆分，并保存到 forwardHeaderSet 中。
    private static void buildForwardedHeaderSet(String value) {
        String[] split = StringUtils.split(value, ",");
        forwardHeaderSet.clear();
        if (split == null || split.length == 0) {
            return;
        }
        forwardHeaderSet.addAll(Arrays.asList(split));
    }

    // setResponseHoldTagFields 剪除 key 中的 keyPrefix，然后更新保存
    @SuppressWarnings("all")
    private static void setResponseHoldTagFields(Map<String, String> fields) {
        responseHoldTagFields = responseHoldTagFields.rebuild(fields);
    }

    @SuppressWarnings("all")
    private static void setServiceTags(Map<String, String> tags) {
        serviceTags = serviceTags.rebuild(tags);
    }

    public static boolean isEmpty(String[] fields) {
        return fields == null || fields.length == 0;
    }

    public static Set<String> getForwardedHeaders() {
        return forwardHeaderSet;
    }

    public static String[] getResponseHoldTagFields() {
        return responseHoldTagFields.values;
    }

    public static Map<String, String> getServiceTags() {
        return serviceTags.keyValues;
    }


    // build 将给定 map 中 key 去除掉 keyPrefix，然后将其作为新的 key
    // 这是因为已经有了 keyPrefix 这一通用前缀存在
    private static Fields build(@Nonnull String keyPrefix, @Nonnull Map<String, String> map) {
        if (map.isEmpty()) {
            return new Fields(keyPrefix, Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());
        }
        Map<String, String> keyValues = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            // 移除原先 key 中的 keyPrefix 前缀
            String key = entry.getKey().replace(keyPrefix, "");
            keyValues.put(key, entry.getValue());
        }
        return new Fields(keyPrefix, Collections.unmodifiableSet(new HashSet<>(map.values())), keyValues, map);
    }

    public static class Fields {
        private final String keyPrefix;
        private final String[] values;
        private final Map<String, String> keyValues;
        private final Map<String, String> map;

        private Fields(@Nonnull String keyPrefix, @Nonnull Set<String> fieldSet, Map<String, String> keyValues, @Nonnull Map<String, String> map) {
            this.keyPrefix = keyPrefix;
            this.values = fieldSet.toArray(new String[0]);
            this.keyValues = keyValues;
            this.map = map;
        }

        // rebuild 将给定的 map 中的简直对与当前 map 中的值进行合并，
        // 如果为空，则代表该配置被删除。如果存在，则更新值。
        Fields rebuild(@Nonnull Map<String, String> map) {
            if (this.map.isEmpty()) {
                map.entrySet().removeIf(stringStringEntry -> StringUtils.isEmpty(stringStringEntry.getValue()));
                return build(keyPrefix, map);
            }
            Map<String, String> newMap = new HashMap<>(this.map);

            // 合并给定的 map
            for (Map.Entry<String, String> entry : map.entrySet()) {
                // 如果给定的 map 值为空，代表该配置被删除，因此需要从 newMap 中移除该 key
                if (StringUtils.isEmpty(entry.getValue())) {
                    newMap.remove(entry.getKey());
                // 如果给定的 map 值不为空，则使用最新值
                } else {
                    newMap.put(entry.getKey(), entry.getValue());
                }
            }
            // 移除 newMap 中 key 中 keyPrefix 的部分
            return build(keyPrefix, Collections.unmodifiableMap(newMap));
        }
    }

    static class Change {
        private final Map<String, String> responseHoldTags = new HashMap<>();
        private final Map<String, String> serverTags = new HashMap<>();

        // putAll 将给定的 map 中的所有键值对放入到 Change 中。
        public Change putAll(Map<String, String> map) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public void put(String key, String value) {
            // 当为 easeagent.progress.forwarded.headers 时
            if (ProgressFields.isForwardedHeader(key)) { // easeagent.progress.forwarded.headers
                // 将值按 , 拆分，并保存到 forwardHeaderSet 中
                buildForwardedHeaderSet(value);
            // 当为 observability.tracings.tag.response.headers 开头时，保存到 responseHoldTags
            } else if (ProgressFields.isResponseHoldTagKey(key)) { // observability.tracings.tag.response.headers.
                responseHoldTags.put(key, value);
            // 当为 observability.tracings.service.tags 开头时，保存到 serverTags
            } else if (ProgressFields.isServerTags(key)) { // observability.tracings.service.tags.
                serverTags.put(key, value);
            }
        }

        // flush 对 responseHoldTags 和 serverTags 中的 key 进行格式化处理
        private void flush() {
            // 剪除 responseHoldTags 的 key 中的 keyPrefix，然后更新保存
            if (!responseHoldTags.isEmpty()) {
                setResponseHoldTagFields(responseHoldTags);
            }
            // 剪除 serverTags 的 key 中的 keyPrefix，然后更新保存
            if (!serverTags.isEmpty()) {
                setServiceTags(serverTags);
            }
        }
    }
}
