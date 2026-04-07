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
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.metrics.converter.AbstractConverter;
import com.megaease.easeagent.metrics.converter.EaseAgentPrometheusExports;
import com.megaease.easeagent.plugin.api.metric.name.MetricName;
import com.megaease.easeagent.plugin.api.metric.name.Tags;
import io.prometheus.client.Collector;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class MetricRegistryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricRegistryService.class);
    public static final String METRIC_TYPE_LABEL_NAME = "MetricType";
    public static final String METRIC_SUB_TYPE_LABEL_NAME = "MetricSubType";
    public static final MetricRegistryService DEFAULT = new MetricRegistryService();

    // 仅用于保存 dropwizard 的指标注册器
    private static final List<MetricRegistry> REGISTRY_LIST = new ArrayList<>();

    // createMetricRegistry 创建 dropwizard 的指标注册器，prometheus 的采样器和上报器
    public MetricRegistry createMetricRegistry(AbstractConverter abstractConverter, Supplier<Map<String, Object>> additionalAttributes, Tags tags) {
        // 初始化 dropwizard 的指标注册器
        MetricRegistry registry = new MetricRegistry();
        // 保存到列表中
        REGISTRY_LIST.add(registry);
        // easeagent 的指标采样器
        EaseAgentSampleBuilder easeAgentSampleBuilder = new EaseAgentSampleBuilder(additionalAttributes, tags);
        // easeagent 的 prometheus 的上报器
        EaseAgentPrometheusExports easeAgentPrometheusExports = new EaseAgentPrometheusExports(registry, abstractConverter, easeAgentSampleBuilder);
        // 注册上报器
        easeAgentPrometheusExports.register();
        return registry;
    }

    /**
     * 重新从 easeagent 获取的指标信息上报到 prometheus 的采样器，
     * 主要是将属性和 tags 写入
     */
    static class EaseAgentSampleBuilder extends DefaultSampleBuilder {
        private final Supplier<Map<String, Object>> additionalAttributes;
        private final Tags tags;

        EaseAgentSampleBuilder(Supplier<Map<String, Object>> additionalAttributes, Tags tags) {
            this.additionalAttributes = additionalAttributes;
            this.tags = tags;
        }

        private void additionalAttributes(List<String> additionalLabelNames, List<String> additionalLabelValues) {
            if (additionalAttributes == null) {
                return;
            }
            Map<String, Object> labels = additionalAttributes.get();
            if (labels == null || labels.isEmpty()) {
                return;
            }
            for (Map.Entry<String, Object> entry : labels.entrySet()) {
                additionalLabelNames.add(entry.getKey());
                additionalLabelValues.add(entry.getValue().toString());
            }
        }

        private void tags(List<String> additionalLabelNames, List<String> additionalLabelValues) {
            if (tags == null) {
                return;
            }
            Map<String, String> other = tags.getTags();
            if (other == null || other.isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> entry : other.entrySet()) {
                additionalLabelNames.add(entry.getKey());
                additionalLabelValues.add(entry.getValue());
            }
        }

        @Override
        public Collector.MetricFamilySamples.Sample createSample(String dropwizardName, String nameSuffix, List<String> additionalLabelNames, List<String> additionalLabelValues, double value) {
            // 合并现有的 names
            List<String> newAdditionalLabelNames = new ArrayList<>(additionalLabelNames);
            // 合并现有的 values
            List<String> newAdditionalLabelValues = new ArrayList<>(additionalLabelValues);
            // 将额外的属性的 key 和 value 分别写入到 newAdditionalLabelNames, newAdditionalLabelValues 中
            additionalAttributes(newAdditionalLabelNames, newAdditionalLabelValues);
            // 将额外的 tags 的 key 和 value 分别写入到 newAdditionalLabelNames, newAdditionalLabelValues 中
            tags(newAdditionalLabelNames, newAdditionalLabelValues);
            // 调用父类的方法
            return super.createSample(rebuildName(dropwizardName, newAdditionalLabelNames, newAdditionalLabelValues), nameSuffix, newAdditionalLabelNames, newAdditionalLabelValues, value);
        }

        private String rebuildName(String name, List<String> additionalLabelNames, List<String> additionalLabelValues) {
            try {
                // 将 dropwizard 指标名称转换为 easeagent 的 指标名称
                MetricName metricName = MetricName.metricNameFor(name);
                // 写入 MetricType
                additionalLabelNames.add(METRIC_TYPE_LABEL_NAME);
                // 写入 MetricSubType
                additionalLabelNames.add(METRIC_SUB_TYPE_LABEL_NAME);
                // 写入 MetricType 的值 TimerType/HistogramType/MeterType/CounterType/GaugeType
                additionalLabelValues.add(metricName.getMetricType().name());
                // 写入 MetricSubType 的值 DEFAULT/ERROR/CHANNEL/CONSUMER/PRODUCER/CONSUMER_ERROR/PRODUCER_ERROR/NONE
                additionalLabelValues.add(metricName.getMetricSubType().name());
                // 写入 Tags#keyFieldName
                // 例如 JDBC: url
                additionalLabelNames.add(tags.getKeyFieldName());
                // 写入 dropwizard 指标名称
                additionalLabelValues.add(metricName.getKey());

                // 构造 prometheus 上报的名称：<category>.<type>
                // 例如 JDBC: application.jdbc-connection
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append(tags.getCategory());
                stringBuilder.append(".");
                stringBuilder.append(tags.getType());
                return stringBuilder.toString();
            } catch (Exception e) {
                LOGGER.error("rebuild metric name[{}] fail.{}", name, e);
                return name;
            }
        }

    }

}
