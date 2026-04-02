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

import com.megaease.easeagent.plugin.api.metric.Counter;
import com.megaease.easeagent.plugin.api.metric.Meter;
import com.megaease.easeagent.plugin.api.metric.MetricRegistry;
import com.megaease.easeagent.plugin.api.metric.ServiceMetric;
import com.megaease.easeagent.plugin.api.metric.name.*;
import com.megaease.easeagent.plugin.tools.metrics.LastMinutesCounterGauge;
import com.megaease.easeagent.plugin.utils.ImmutableMap;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Elasticsearch 监控点如下：
 * - 执行时间：min/max/mean/p25/p50/p75/p95/p98/p99/p999
 * - 执行总数：count/err_count
 * - 速率：m1/m5/m15/m1_err/m5_err/m15_err
 */
public class ElasticsearchMetric extends ServiceMetric {
    public ElasticsearchMetric(@Nonnull MetricRegistry metricRegistry, @Nonnull NameFactory nameFactory) {
        super(metricRegistry, nameFactory);
    }

    public static NameFactory nameFactory() {
        return NameFactory.createBuilder()
            // 耗时性能监控
            .timerType(MetricSubType.DEFAULT,
                ImmutableMap.<MetricField, MetricValueFetcher>builder()
                    // 最小执行时间
                    .put(MetricField.MIN_EXECUTION_TIME, MetricValueFetcher.SnapshotMinValue)
                    // 最大执行时间
                    .put(MetricField.MAX_EXECUTION_TIME, MetricValueFetcher.SnapshotMaxValue)
                    // 平均执行时间
                    .put(MetricField.MEAN_EXECUTION_TIME, MetricValueFetcher.SnapshotMeanValue)
                    // p25
                    .put(MetricField.P25_EXECUTION_TIME, MetricValueFetcher.Snapshot25Percentile)
                    // p50
                    .put(MetricField.P50_EXECUTION_TIME, MetricValueFetcher.Snapshot50PercentileValue)
                    // p75
                    .put(MetricField.P75_EXECUTION_TIME, MetricValueFetcher.Snapshot75PercentileValue)
                    // p95
                    .put(MetricField.P95_EXECUTION_TIME, MetricValueFetcher.Snapshot95PercentileValue)
                    // p98
                    .put(MetricField.P98_EXECUTION_TIME, MetricValueFetcher.Snapshot98PercentileValue)
                    // p99
                    .put(MetricField.P99_EXECUTION_TIME, MetricValueFetcher.Snapshot99PercentileValue)
                    // p999
                    .put(MetricField.P999_EXECUTION_TIME, MetricValueFetcher.Snapshot999PercentileValue)
                    .build())
            .gaugeType(MetricSubType.DEFAULT, new HashMap<>())
            // 请求速率
            .meterType(MetricSubType.DEFAULT,
                ImmutableMap.<MetricField, MetricValueFetcher>builder()
                    // 每分钟请求数
                    .put(MetricField.M1_RATE, MetricValueFetcher.MeteredM1Rate)
                    // 每5分钟请求数
                    .put(MetricField.M5_RATE, MetricValueFetcher.MeteredM5Rate)
                    // 每15分钟请求数
                    .put(MetricField.M15_RATE, MetricValueFetcher.MeteredM15Rate)
                    // 平均请求数
                    .put(MetricField.MEAN_RATE, MetricValueFetcher.MeteredMeanRate)
                    .build())
            // 请求错误速率
            .meterType(MetricSubType.ERROR,
                ImmutableMap.<MetricField, MetricValueFetcher>builder()
                    // 每分钟请求错误数
                    .put(MetricField.M1_ERROR_RATE, MetricValueFetcher.MeteredM1Rate)
                    // 每5分钟请求错误数
                    .put(MetricField.M5_ERROR_RATE, MetricValueFetcher.MeteredM5Rate)
                    // 每15分钟请求错误数
                    .put(MetricField.M15_ERROR_RATE, MetricValueFetcher.MeteredM15Rate)
                    .build())
            // 请求总数
            .counterType(MetricSubType.ERROR, ImmutableMap.<MetricField, MetricValueFetcher>builder()
                // 累计执行错误次数
                .put(MetricField.EXECUTION_ERROR_COUNT, MetricValueFetcher.CountingCount)
                .build())
            // 请求错误总数
            .counterType(MetricSubType.DEFAULT, ImmutableMap.<MetricField, MetricValueFetcher>builder()
                // 累计执行次数
                .put(MetricField.EXECUTION_COUNT, MetricValueFetcher.CountingCount)
                .build())
            .build();
    }

    public void collectMetric(String key, long duration, boolean success) {

        metricRegistry.timer(this.nameFactory.timerName(key, MetricSubType.DEFAULT)).update(duration, TimeUnit.MILLISECONDS);

        final Meter defaultMeter = metricRegistry.meter(nameFactory.meterName(key, MetricSubType.DEFAULT));

        // 请求总数 counter，key 实际是指操作的索引
        final Counter defaultCounter = metricRegistry.counter(nameFactory.counterName(key, MetricSubType.DEFAULT));

        if (!success) {
            final Meter errorMeter = metricRegistry.meter(nameFactory.meterName(key, MetricSubType.ERROR));
            // 错误请求 counter
            final Counter errorCounter = metricRegistry.counter(nameFactory.counterName(key, MetricSubType.ERROR));
            // 当前错误数+1,用于统计 reate
            errorMeter.mark();
            // 错误总数+1
            errorCounter.inc();
        }
        // 请求数+1,用于统计 reate
        defaultMeter.mark();

        // 请求总数+1
        defaultCounter.inc();

        MetricName gaugeName = nameFactory.gaugeNames(key).get(MetricSubType.DEFAULT);
        // 通过 defaultMeter 来计算 m1_count/m5_count/m15_count
        metricRegistry.gauge(gaugeName.name(), () -> () ->
            LastMinutesCounterGauge.builder()
                .m1Count((long) (defaultMeter.getOneMinuteRate() * 60))
                .m5Count((long) (defaultMeter.getFiveMinuteRate() * 60 * 5))
                .m15Count((long) (defaultMeter.getFifteenMinuteRate() * 60 * 15))
                .build());
    }
}
