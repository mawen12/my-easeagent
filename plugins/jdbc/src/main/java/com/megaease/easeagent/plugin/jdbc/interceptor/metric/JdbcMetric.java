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

package com.megaease.easeagent.plugin.jdbc.interceptor.metric;

import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.ImmutableList;
import com.megaease.easeagent.plugin.api.Context;
import com.megaease.easeagent.plugin.api.context.ContextUtils;
import com.megaease.easeagent.plugin.api.logging.Logger;
import com.megaease.easeagent.plugin.api.metric.*;
import com.megaease.easeagent.plugin.api.metric.name.*;
import com.megaease.easeagent.plugin.api.middleware.Redirect;
import com.megaease.easeagent.plugin.api.middleware.RedirectProcessor;
import com.megaease.easeagent.plugin.bridge.EaseAgent;
import com.megaease.easeagent.plugin.tools.metrics.LastMinutesCounterGauge;
import com.megaease.easeagent.plugin.utils.ImmutableMap;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;

public class JdbcMetric extends ServiceMetric implements RemovalListener<String, String> {
    private final Logger logger = EaseAgent.getLogger(JdbcMetric.class);

    public static final ServiceMetricSupplier<JdbcMetric> METRIC_SUPPLIER = new ServiceMetricSupplier<JdbcMetric>() {
        @Override
        public NameFactory newNameFactory() {
            return JdbcMetric.nameFactory();
        }

        @Override
        public JdbcMetric newInstance(MetricRegistry metricRegistry, NameFactory nameFactory) {
            return new JdbcMetric(metricRegistry, nameFactory);
        }
    };

    public JdbcMetric(@Nonnull MetricRegistry metricRegistry, @Nonnull NameFactory nameFactory) {
        super(metricRegistry, nameFactory);
    }

    public static Tags newConnectionTags() {
        Tags tags = new Tags("application", "jdbc-connection", "url");
        RedirectProcessor.setTagsIfRedirected(Redirect.DATABASE, tags);
        return tags;
    }

    public static Tags newStmTags() {
        Tags tags = new Tags("application", "jdbc-statement", "signature");
        RedirectProcessor.setTagsIfRedirected(Redirect.DATABASE, tags);
        return tags;
    }

    public static NameFactory nameFactory() {
        return NameFactory.createBuilder()
            // timer 计时器，记录执行时间相关的指标
            .timerType(MetricSubType.DEFAULT,
                ImmutableMap.<MetricField, MetricValueFetcher>builder()
                    .put(MetricField.MIN_EXECUTION_TIME, MetricValueFetcher.SnapshotMinValue)
                    .put(MetricField.MAX_EXECUTION_TIME, MetricValueFetcher.SnapshotMaxValue)
                    .put(MetricField.MEAN_EXECUTION_TIME, MetricValueFetcher.SnapshotMeanValue)
                    .put(MetricField.P25_EXECUTION_TIME, MetricValueFetcher.Snapshot25Percentile)
                    .put(MetricField.P50_EXECUTION_TIME, MetricValueFetcher.Snapshot50PercentileValue)
                    .put(MetricField.P75_EXECUTION_TIME, MetricValueFetcher.Snapshot75PercentileValue)
                    .put(MetricField.P95_EXECUTION_TIME, MetricValueFetcher.Snapshot95PercentileValue)
                    .put(MetricField.P98_EXECUTION_TIME, MetricValueFetcher.Snapshot98PercentileValue)
                    .put(MetricField.P99_EXECUTION_TIME, MetricValueFetcher.Snapshot99PercentileValue)
                    .put(MetricField.P999_EXECUTION_TIME, MetricValueFetcher.Snapshot999PercentileValue)
                    .build())
            // gauge 仪表，记录瞬时值相关的指标
            .gaugeType(MetricSubType.DEFAULT, new HashMap<>())
            // meter 速率计，记录事件发生频率相关的指标
            .meterType(MetricSubType.DEFAULT,
                ImmutableMap.<MetricField, MetricValueFetcher>builder()
                    .put(MetricField.M1_RATE, MetricValueFetcher.MeteredM1RateIgnoreZero)
                    .put(MetricField.M5_RATE, MetricValueFetcher.MeteredM5Rate)
                    .put(MetricField.M15_RATE, MetricValueFetcher.MeteredM15Rate)
                    .build())
            .meterType(MetricSubType.ERROR,
                ImmutableMap.<MetricField, MetricValueFetcher>builder()
                    .put(MetricField.M1_ERROR_RATE, MetricValueFetcher.MeteredM1Rate)
                    .put(MetricField.M5_ERROR_RATE, MetricValueFetcher.MeteredM5Rate)
                    .put(MetricField.M15_ERROR_RATE, MetricValueFetcher.MeteredM15Rate)
                    .build())
            // counter 计数器，记录事件发生次数相关的指标
            .counterType(MetricSubType.DEFAULT, ImmutableMap.<MetricField, MetricValueFetcher>builder()
                .put(MetricField.EXECUTION_COUNT, MetricValueFetcher.CountingCount)
                .build())
            .counterType(MetricSubType.ERROR, ImmutableMap.<MetricField, MetricValueFetcher>builder()
                .put(MetricField.EXECUTION_ERROR_COUNT, MetricValueFetcher.CountingCount)
                .build())
            .build();
    }

    // collectMetric 收集指标
    public void collectMetric(String key, boolean success, Context ctx) {
        // 读取计时器，更新统计耗时
        Timer timer = this.metricRegistry.timer(this.nameFactory.timerName(key, MetricSubType.DEFAULT));
        timer.update(Duration.ofMillis(ContextUtils.getDuration(ctx)));

        // 读取计数器，增加统计
        Counter counter = this.metricRegistry.counter(this.nameFactory.counterName(key, MetricSubType.DEFAULT));
        // 读取速率计，增加统计
        Meter meter = this.metricRegistry.meter(this.nameFactory.meterName(key, MetricSubType.DEFAULT));
        meter.mark();
        counter.inc();

        // 失败时，读取错误计数器，增加统计，读取错误速率计，增加统计
        if (!success) {
            Counter errCounter = this.metricRegistry.counter(this.nameFactory.counterName(key, MetricSubType.ERROR));
            Meter errMeter = this.metricRegistry.meter(this.nameFactory.meterName(key, MetricSubType.ERROR));
            errMeter.mark();
            errCounter.inc();
        }

        // 读取仪表，更新统计
        MetricName gaugeName = this.nameFactory.gaugeNames(key).get(MetricSubType.DEFAULT);
        metricRegistry.gauge(gaugeName.name(), () -> () -> LastMinutesCounterGauge.builder()
            .m1Count((long) meter.getOneMinuteRate() * 60)
            .m5Count((long) meter.getFiveMinuteRate() * 60 * 5)
            .m15Count((long) meter.getFifteenMinuteRate() * 60 * 15)
            .build());
    }

    @SuppressWarnings("NullableProblems")
    public void onRemoval(RemovalNotification<String, String> notification) {
        try {
            String key = notification.getKey();
            ImmutableList<String> list = ImmutableList.of(
                Optional.ofNullable(this.nameFactory.counterName(key, MetricSubType.DEFAULT)).orElse(""),
                Optional.ofNullable(this.nameFactory.counterName(key, MetricSubType.ERROR)).orElse(""),
                Optional.ofNullable(this.nameFactory.meterName(key, MetricSubType.DEFAULT)).orElse(""),
                Optional.ofNullable(this.nameFactory.meterName(key, MetricSubType.ERROR)).orElse(""),
                Optional.ofNullable(this.nameFactory.timerName(key, MetricSubType.DEFAULT)).orElse(""),
                Optional.ofNullable(this.nameFactory.gaugeName(key, MetricSubType.DEFAULT)).orElse(""));

            list.forEach(metricRegistry::remove);
        } catch (Exception e) {
            logger.warn("remove lru cache failed: " + e.getMessage());
        }
    }
}
