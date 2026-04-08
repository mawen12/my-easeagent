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

package com.megaease.easeagent.metrics;

import com.codahale.metrics.MetricRegistry;
import com.megaease.easeagent.metrics.config.MetricsConfig;
import com.megaease.easeagent.metrics.converter.Converter;
import com.megaease.easeagent.plugin.report.EncodedData;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 其本身主要支持配置刷新时，组合新参数和现有配置，重建 AgentScheduledReporter。
 * 内部封装了 AgentScheduledReporter 实现定时 report。
 */
public class AutoRefreshReporter implements Runnable {
    // 提供了刷新的间隔，该间隔支持动态刷新
    private final MetricsConfig config;
    // 底层用于指标上报的类
    private AgentScheduledReporter reporter;

    // ================== 当配置刷新时，重建 reporter ==================
    private final Converter converter;
    // 其底层是 DefaultMetricReporter
    private final Consumer<EncodedData> consumer;
    private final MetricRegistry metricRegistry;


    public AutoRefreshReporter(MetricRegistry metricRegistry,
                               MetricsConfig config,
                               Converter converter,
                               Consumer<EncodedData> consumer) {
        this.metricRegistry = metricRegistry;
        this.config = config;
        this.consumer = consumer;
        this.converter = converter;
        config.setIntervalChangeCallback(this); // 将该类设置为配置更新时，触发 run 方法的回调对象
    }

    @Override
    public synchronized void run() {
        // config changed 配置变更是触发
        if (reporter != null) {
            reporter.close();
            reporter = null;
        }
        reporter = AgentScheduledReporter.forRegistry(metricRegistry)
            .outputTo(consumer)
            .enabled(config::isEnabled)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();
        reporter.setConverter(converter);
        // 在内容使用 zipkin 的 ScheduleReporter，内部启动单线程的调度器，以配置的间隔固定上报指标数据
        reporter.start(config.getInterval(), config.getIntervalUnit());
    }

    public AgentScheduledReporter getReporter() {
        return reporter;
    }
}
