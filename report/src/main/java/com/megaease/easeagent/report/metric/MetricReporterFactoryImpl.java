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

package com.megaease.easeagent.report.metric;

import com.megaease.easeagent.plugin.api.Reporter;
import com.megaease.easeagent.plugin.api.config.ChangeItem;
import com.megaease.easeagent.plugin.api.config.Config;
import com.megaease.easeagent.plugin.api.config.ConfigChangeListener;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.report.ByteWrapper;
import com.megaease.easeagent.plugin.report.EncodedData;
import com.megaease.easeagent.plugin.report.metric.MetricReporterFactory;
import com.megaease.easeagent.report.plugin.ReporterRegistry;
import com.megaease.easeagent.report.sender.SenderWithEncoder;
import com.megaease.easeagent.report.util.Utils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MetricReporterFactoryImpl implements MetricReporterFactory, ConfigChangeListener {
    // namespace -> reporter，基于namespace进行上报隔离
    private final ConcurrentHashMap<String, DefaultMetricReporter> reporters;
    // 所有上报器共享的配置
    private final Config reportConfig;

    public MetricReporterFactoryImpl(Config reportConfig) {
        this.reporters = new ConcurrentHashMap<>();
        this.reportConfig = reportConfig;
        this.reportConfig.addChangeListener(this);
    }

    public static MetricReporterFactory create(Config reportConfig) {
        return new MetricReporterFactoryImpl(reportConfig);
    }

    @Override
    public Reporter reporter(IPluginConfig pluginConfig) {
        DefaultMetricReporter reporter = reporters.get(pluginConfig.namespace());
        if (reporter != null) {
            return reporter;
        }
        synchronized (reporters) {
            reporter = reporters.get(pluginConfig.namespace());
            if (reporter != null) {
                return reporter;
            }
            // 注册一个 reporter
            reporter = new DefaultMetricReporter(pluginConfig, this.reportConfig);
            reporters.put(pluginConfig.namespace(), reporter);
            return reporter;
        }
    }

    @Override
    public void onChange(List<ChangeItem> list) {
        Map<String, String> changes = filterChanges(list);
        if (changes.isEmpty()) {
            return;
        }
        this.reportConfig.updateConfigs(changes);
    }

    private Map<String, String> filterChanges(List<ChangeItem> list) {
        Map<String, String> cfg = new HashMap<>();
        list.forEach(one -> cfg.put(one.getFullName(), one.getNewValue()));

        return cfg;
    }

    public static class DefaultMetricReporter implements Reporter, ConfigChangeListener {
        private MetricProps metricProps;
        // 实际的底层上报器
        // 底层是：
        //  kafka -> com.megaease.easeagent.report.sender.AgentKafkaSender
        //  console -> com.megaease.easeagent.report.sender.AgentLoggerSender
        //  noop -> com.megaease.easeagent.report.sender.NoOpSender
        //  metricKafka -> com.megaease.easeagent.report.sender.metric.MetricKafkaSender
        //  http -> com.megaease.easeagent.report.sender.okhttp.HttpSender
        private SenderWithEncoder sender;
        private final IPluginConfig pluginConfig;
        private final Config reportConfig;
        private final Config metricConfig;

        public DefaultMetricReporter(IPluginConfig pluginConfig, Config reportConfig) {
            this.pluginConfig = pluginConfig;

            this.reportConfig = reportConfig;
            this.reportConfig.addChangeListener(this);

            this.metricProps = Utils.extractMetricProps(this.pluginConfig, reportConfig);
            this.metricConfig = this.metricProps.asReportConfig();

            // 根据 metricProps 中的 senderName 获取对应的 sender 实例
            this.sender = ReporterRegistry.getSender(this.metricProps.getSenderPrefix(), this.metricConfig);
        }

        // report 只要触发 report 方法，其将直接上报
        public void report(String context) {
            try {
                // 使用 report 进行上报
                sender.send(new ByteWrapper(context.getBytes())).execute();
            } catch (IOException e) {
                log.warn("send error. {}", e.getMessage());
            }
        }

        // report 只要触发 report 方法，其将直接上报
        @Override
        public void report(EncodedData encodedData) {
            try {
                sender.send(encodedData).execute();
            } catch (IOException e) {
                log.warn("send error. {}", e.getMessage());
            }
        }

        public Config getMetricConfig() {
            return this.metricConfig;
        }

        public MetricProps getMetricProps() {
            return this.metricProps;
        }

        public SenderWithEncoder getSender() {
            return this.sender;
        }

        @Override
        public void onChange(List<ChangeItem> list) {
            if (list.isEmpty()) {
                return;
            }

            String senderName = this.metricProps.getSenderName();
            this.metricProps = Utils.extractMetricProps(pluginConfig, this.reportConfig);
            Config changedConfig = this.metricProps.asReportConfig();

            this.metricConfig.updateConfigs(changedConfig.getConfigs());

            if (!metricProps.getSenderName().equals(senderName)) {
                this.sender = ReporterRegistry.getSender(this.metricProps.getSenderPrefix(), this.metricConfig);
            }
        }
    }
}
