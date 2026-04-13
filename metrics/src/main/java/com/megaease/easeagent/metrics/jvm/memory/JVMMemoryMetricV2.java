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
 *
 */
package com.megaease.easeagent.metrics.jvm.memory;

import com.megaease.easeagent.metrics.model.JVMMemoryGaugeMetricModel;
import com.megaease.easeagent.plugin.api.config.AutoRefreshPluginConfigRegistry;
import com.megaease.easeagent.plugin.api.config.IPluginConfig;
import com.megaease.easeagent.plugin.api.metric.*;
import com.megaease.easeagent.plugin.api.metric.name.*;
import com.megaease.easeagent.plugin.async.ScheduleHelper;
import com.megaease.easeagent.plugin.async.ScheduleRunner;

import javax.annotation.Nonnull;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class JVMMemoryMetricV2 extends ServiceMetric implements ScheduleRunner {
    private static final ServiceMetricSupplier<JVMMemoryMetricV2> SUPPLIER = new ServiceMetricSupplier<JVMMemoryMetricV2>() {
        @Override
        public NameFactory newNameFactory() {
            return JVMMemoryMetricV2.nameFactory();
        }

        @Override
        public JVMMemoryMetricV2 newInstance(MetricRegistry metricRegistry, NameFactory nameFactory) {
            return new JVMMemoryMetricV2(metricRegistry, nameFactory);
        }
    };

    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");
    private static final String POOLS = "pools";
    private static IPluginConfig config;

    private JVMMemoryMetricV2(@Nonnull MetricRegistry metricRegistry, @Nonnull NameFactory nameFactory) {
        super(metricRegistry, nameFactory);
    }

    // getMetric 注册并启动 JVMMemory 的指标收集任务
    public static JVMMemoryMetricV2 getMetric() {
        // 读取 plugin.observability.jvmMemory.metric 的配置信息
        config = AutoRefreshPluginConfigRegistry.getOrCreate("observability", "jvmMemory", "metric");
        // 创建用于上报的之前名称前缀
        Tags tags = new Tags("application", "jvm-memory", "resource");

        // 创建 MetricRegistry
        JVMMemoryMetricV2 v2 = ServiceMetricRegistry.getOrCreate(config, tags, SUPPLIER);
        // 启动后延迟10s开始，然后每隔10s执行一次
        ScheduleHelper.DEFAULT.nonStopExecute(10, 10, v2::doJob);

        return v2;
    }

    static NameFactory nameFactory() {
        // 仅收集 gauge，即瞬时值
        return NameFactory.createBuilder()
            .gaugeType(MetricSubType.DEFAULT, new HashMap<>())
            .build();
    }

    @Override
    public void doJob() {
        // 如果未开启配置，则不触发
        if (!config.enabled()) {
            return;
        }
        // 通过 JMX 读取
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        // 遍历 MXBean
        // 对于 G1 收集器来说：
        // - pools.Code-Cache
        // - pools.Metaspace
        // - pools.Compressed-Class-Space
        // - pools.PS-Eden-Space
        // - pools.PS-Survivor-Space
        // - pools.PS-Old-Gen
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            String memoryPoolMXBeanName = memoryPoolMXBean.getName();

            // 指标名称为：pool.<name-name1>
            final String poolName = com.codahale.metrics.MetricRegistry.name(POOLS, WHITESPACE.matcher(memoryPoolMXBeanName).replaceAll("-"));

            // 获取该指标的配置信息
            Map<MetricSubType, MetricName> map = this.nameFactory.gaugeNames(poolName);
            for (Map.Entry<MetricSubType, MetricName> entry : map.entrySet()) {
                MetricName metricName = entry.getValue();

                // 收集内存使用情况：init, used, commited, max
                Gauge<JVMMemoryGaugeMetricModel> gauge = () -> new JVMMemoryGaugeMetricModel(
                    memoryPoolMXBean.getUsage().getInit(),
                    memoryPoolMXBean.getUsage().getUsed(),
                    memoryPoolMXBean.getUsage().getCommitted(),
                    memoryPoolMXBean.getUsage().getMax());

                // 收集
                this.metricRegistry.gauge(metricName.name(), () -> gauge);
            }
        }
    }
}
