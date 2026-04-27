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

package com.megaease.easeagent.plugin.jdbc.common;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.async.ScheduleHelper;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.utils.common.DataSize;
import com.megaease.easeagent.plugin.utils.common.StringUtils;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 具备上报功能的基于 MD5，且带有缓存的 SQL 压缩实现
 */
public class MD5SQLCompression implements SQLCompression, RemovalListener<String, String> {
    private static final Logger logger = EaseAgent.getLogger(MD5SQLCompression.class);

    // 单条SQL最长 32 KB
    public static final DataSize MAX_SQL_SIZE = DataSize.ofKilobytes(32);//32KB

    private static final AtomicReference<MD5SQLCompression> INSTANCE = new AtomicReference<>();

    // 构造最大容纳 1000 条记录的缓存，MD5 -> cutSQL，用于数据上报
    private final Cache<String, String> dictionary = CacheBuilder.newBuilder().maximumSize(1000)
        .removalListener(this).build();

    // 构造最大容纳 1000 条记录的缓存，cutSQl -> MD5
    private final Cache<String, String> md5Cache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private final Consumer<Map<String, String>> reportConsumer;

    public MD5SQLCompression(Consumer<Map<String, String>> reportConsumer) {
        this.reportConsumer = reportConsumer;
        // 启动一个线程，用于每隔5秒上报
        ScheduleHelper.DEFAULT.nonStopExecute(10, 5, this::pushItems);
    }

    public static MD5SQLCompression getInstance() {
        // 检查后实例是否存在
        MD5SQLCompression md5SQLCompression = INSTANCE.get();
        if (md5SQLCompression != null) {
            return md5SQLCompression;
        }
        // 双重锁定检查
        synchronized (INSTANCE) {
            md5SQLCompression = INSTANCE.get();
            if (md5SQLCompression != null) {
                return md5SQLCompression;
            }
            //
            MD5SQLCompression instance = new MD5SQLCompression(new MD5ReportConsumer());
            INSTANCE.set(instance);
            return instance;
        }
    }

    /**
     * 将指定字符串编码为 MD5
     * @param str
     * @return
     */
    private String cacheLoad(String str) {
        return DigestUtils.md5Hex(str);
    }

    @Override
    public String compress(String origin) {
        try {
            // 截取指定长度的字符串
            String cutStr = StringUtils.cutStrByDataSize(origin, MAX_SQL_SIZE);
            // 读取截取后字符串的MD5，如果不存在，则创建该字符串的MD5缓存
            String md5 = md5Cache.get(cutStr, () -> cacheLoad(cutStr));
            // 读取字典对应的值
            String value = dictionary.getIfPresent(md5);
            if (value == null) {
                // 写入 md5 -> curStr
                dictionary.put(md5, cutStr);
            }
            // 返回 MD5
            return md5;
        } catch (Exception e) {
            logger.warn("compress content[{}] failure", origin, e);
            return origin;
        }
    }

    private void pushItems() {
        // 读取辞典缓存
        ConcurrentMap<String, String> map = this.dictionary.asMap();
        if (map.isEmpty()) {
            return;
        }
        // 将数据上报
        this.reportConsumer.accept(map);
    }

    @Override
    public void onRemoval(RemovalNotification<String, String> notification) {
        logger.info("remove md5 dictionary item. cause: {}, md5: {}, content: {}",
            notification.getCause().toString(), notification.getKey(), notification.getValue());
        Map<String, String> map = new HashMap<>();
        map.put(notification.getKey(), notification.getValue());
        reportConsumer.accept(map);
    }
}
