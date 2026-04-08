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

package com.megaease.easeagent.metrics.converter;

import com.codahale.metrics.*;
import com.codahale.metrics.Timer;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.api.metric.name.MetricSubType;
import com.megaease.easeagent.plugin.api.metric.name.Tags;
import lombok.SneakyThrows;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class AbstractConverter implements Converter {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractConverter.class);

    @SuppressWarnings("unused")
    private final String rateUnit;
    @SuppressWarnings("unused")
    private final String durationUnit;
    final Long durationFactor;
    final Long rateFactor;
    private final Tags tags;
    // 其底层是 MetricsAdditionalAttributes
    private final Supplier<Map<String, Object>> additionalAttributes;

    AbstractConverter(String category, String type, String keyFieldName, Supplier<Map<String, Object>> additionalAttributes) {
        this(additionalAttributes, new Tags(category, type, keyFieldName));
    }

    AbstractConverter(Supplier<Map<String, Object>> additionalAttributes, Tags tags) {
        this.rateFactor = TimeUnit.SECONDS.toSeconds(1);
        this.rateUnit = calculateRateUnit();
        this.durationFactor = TimeUnit.MILLISECONDS.toNanos(1);
        this.durationUnit = TimeUnit.MILLISECONDS.toString().toLowerCase(Locale.US);
        this.additionalAttributes = additionalAttributes;
        this.tags = tags;
    }

    private String calculateRateUnit() {
        final String s = TimeUnit.SECONDS.toString().toLowerCase(Locale.US);
        return s.substring(0, s.length() - 1);
    }

    // 将需要上报的指标信息转换为 Map<String, Object> 的格式，包含五种指标、timestamp, host_ipv4, host_name, system, service 全局的 tag，以及 category, type, tags 上下文相关的指标信息
    @SneakyThrows
    @SuppressWarnings("rawtypes")
    public List<Map<String, Object>> convertMap(SortedMap<String, Gauge> gauges,
                                                SortedMap<String, Counter> counters,
                                                SortedMap<String, Histogram> histograms,
                                                SortedMap<String, Meter> meters,
                                                SortedMap<String, Timer> timers) {

        // 从提供的五种 dropwizcard 的指标中，读取并汇总 key
        List<String> keys = keysFromMetrics(gauges, counters, histograms, meters, timers);
        // 保存了该 key 需要上报的所有信息，包含五种指标、timestamp, host_ipv4, host_name, system, service 全局的 tag，以及 category, type, tags 上下文相关的指标信息
        final List<Map<String, Object>> result = new ArrayList<>();
        for (String k : keys) {
            try {
                // 写入 timestamp, host_ipv4, host_name, system, service 等全局的 tag
                Map<String, Object> output = buildMap();
                // 写入 keyFieldName 和 key
                writeKey(output, k);
                // 写入 category, type, tags
                writeTag(output);
                // 写入 key 对应的所有 gauges 的指标
                writeGauges(k, null, gauges, output);
                // 写入 key 对应的所有 counters 的指标
                writeCounters(k, null, counters, output);
                // 写入 key 对应的所有 historgrams 的指标
                writeHistograms(k, null, histograms, output);
                // 写入 key 对应的所有 meters 的指标
                writeMeters(k, null, meters, output);
                // 写入 key 对应的所有 timers 的指标
                writeTimers(k, null, timers, output);
                // 将该 key 的数据汇总到 result 中
                result.add(output);
            } catch (IgnoreOutputException exception) {
                LOGGER.trace("convert key of " + k + " error: " + exception.getMessage());
            }
        }
        return result;
    }

    // 写入 timestamp, host_ipv4, host_name, system, service 等全局的 tag
    private Map<String, Object> buildMap() {
        Map<String, Object> map = new HashMap<>();
        // 写入 timestamp
        map.put("timestamp", System.currentTimeMillis());
        // 写入全局的tag: host_ipv4, host_name, system, service
        map.putAll(additionalAttributes.get());
        return map;
    }


    // 写入 category, type, tags
    private void writeTag(Map<String, Object> output) {
        // 写入 category
        output.put(Tags.CATEGORY, tags.getCategory());
        // 写入 type
        output.put(Tags.TYPE, tags.getType());
        // 写入 tags
        output.putAll(tags.getTags());
    }

    // 写入 keyFieldName 和 key
    private void writeKey(Map<String, Object> output, String key) {
        // 写入 key 的键名和值
        // 比如对于 jdbc-connection 来说，keyFieldName 就是 "url"，key 就是 url 的具体值
        output.put(tags.getKeyFieldName(), key);
    }

    // 从提供的五种 dropwizcard 的指标中，读取并汇总 key
    @SuppressWarnings("rawtypes")
    protected abstract List<String> keysFromMetrics(
        SortedMap<String, Gauge> gauges,
        SortedMap<String, Counter> counters,
        SortedMap<String, Histogram> histograms,
        SortedMap<String, Meter> meters,
        SortedMap<String, Timer> timers);


    @SuppressWarnings("rawtypes")
    protected abstract void writeGauges(String key, MetricSubType metricSubType, SortedMap<String, Gauge> gauges, Map<String, Object> output);

    protected abstract void writeCounters(String key, MetricSubType metricSubType, SortedMap<String, Counter> counters, Map<String, Object> output);

    protected abstract void writeHistograms(String key, MetricSubType metricSubType, SortedMap<String, Histogram> histograms, Map<String, Object> output);

    protected abstract void writeMeters(String key, MetricSubType metricSubType, SortedMap<String, Meter> meters, Map<String, Object> output);

    protected abstract void writeTimers(String key, MetricSubType metricSubType, SortedMap<String, Timer> timers, Map<String, Object> output);
}
