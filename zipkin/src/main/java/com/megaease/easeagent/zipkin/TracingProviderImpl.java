/*
 * Copyright (c) 2021, MegaEase
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.megaease.easeagent.zipkin;

import brave.Tracing;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.BoundarySampler;
import brave.sampler.CountingSampler;
import brave.sampler.RateLimitingSampler;
import brave.sampler.Sampler;
import com.megaease.easeagent.config.AutoRefreshConfigItem;
import com.megaease.easeagent.config.ConfigAware;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.annotation.Injection;
import com.megaease.easeagent.plugin.api.config.Config;
import com.megaease.easeagent.plugin.api.config.ConfigConst;
import com.megaease.easeagent.plugin.api.trace.ITracing;
import com.megaease.easeagent.plugin.api.trace.TracingProvider;
import com.megaease.easeagent.plugin.api.trace.TracingSupplier;
import com.megaease.easeagent.plugin.bean.AgentInitializingBean;
import com.megaease.easeagent.plugin.bean.BeanProvider;
import com.megaease.easeagent.plugin.report.AgentReport;
import com.megaease.easeagent.plugin.report.tracing.ReportSpan;
import com.megaease.easeagent.plugin.utils.AdditionalAttributes;
import com.megaease.easeagent.report.AgentReportAware;
import com.megaease.easeagent.zipkin.impl.TracingImpl;
import com.megaease.easeagent.zipkin.logging.AgentMDCScopeDecorator;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.ConvertZipkinSpanHandler;

public class TracingProviderImpl implements BeanProvider, AgentReportAware, ConfigAware, AgentInitializingBean, TracingProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(TracingProviderImpl.class);
    private static final String ENV_ZIPKIN_SERVER_URL = "ZIPKIN_SERVER_URL";

    public static final String SAMPLER_TYPE_COUNTING = "counting";
    public static final String SAMPLER_TYPE_RATE_LIMITING = "rate_limiting";
    public static final String SAMPLER_TYPE_BOUNDARY = "boundary";
    // brave 的 Tracing
    private Tracing tracing;
    private volatile ITracing iTracing;
    private AgentReport agentReport;
    private Config config;
    private AutoRefreshConfigItem<String> serviceName;


    @Override
    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public void setAgentReport(AgentReport report) {
        this.agentReport = report;
    }

    @Override
    public void afterPropertiesSet() {
        // brave 的 Tracing 需要一个 CurrentTraceContext 来管理当前的 trace 上下文，Thread
        ThreadLocalCurrentTraceContext traceContext = ThreadLocalCurrentTraceContext.newBuilder()
            // brave 的上下文关联工具，此处用于与 AgentLogMDC 中的值进行同步
            // 底层是 目标 JVM 中日志 log4j/logback 的 MDC
            .addScopeDecorator(AgentMDCScopeDecorator.get())
            // brave 的上下文关联工具，此处用于与 ease agent 的 MDC 中的值进行同步
            // 底层是 easeagent 自身使用的 log4j 的 MDC
            .addScopeDecorator(AgentMDCScopeDecorator.getV2())
            // brave 的上下文关联工具，此处用于与 slf4j 中的值进行同步
            .addScopeDecorator(AgentMDCScopeDecorator.getAgentDecorator())
            .build();

        // 读取 Config#name 属性的值，且该支持变更时，自动刷新
        // 当前从 agent.properties 读取的值为 demo-service
        serviceName = new AutoRefreshConfigItem<>(config, ConfigConst.SERVICE_NAME, Config::getString);

        Reporter<ReportSpan> reporter;
        reporter = span -> agentReport.report(span);

        // brave tracing 的创建
        this.tracing = Tracing.newBuilder()
            // 设置本地的 service name
            .localServiceName(getServiceName())
            // 使用 64 位，即 8字节的 traceId
            .traceId128Bit(false)
            // 确定 trace 的采样策略，默认为总是采样
            .sampler(getSampler())
            // 写入自定义的 span tag
            .addSpanHandler(new CustomTagsSpanHandler(this::getServiceName, AdditionalAttributes.getHostName()))
            // 写入上报的 zipkin
            .addSpanHandler(ConvertZipkinSpanHandler
                .builder(reporter)
                .alwaysReportSpans(true)
                .build()
            )
            .currentTraceContext(traceContext)
            .build();
    }


    // getSampler 获取决定该 trace 是否被采样的 Sampler 示例
    // 默认使用 ALWAYS_SAMPLE，总是采样
    protected Sampler getSampler() {
        // 读取 Config#observability.tracings.sampledType 的值
        String sampledType = this.config.getString(ConfigConst.Observability.TRACE_SAMPLED_TYPE);
        if (sampledType == null) {
            // 默认没有，则使用 always
            return Sampler.ALWAYS_SAMPLE;
        }
        // 读取 Config#observability.tracings.sampled 的值
        Double probability = this.config.getDouble(ConfigConst.Observability.TRACE_SAMPLED);
        if (probability == null) {
            // 默认没有，则使用 always
            return Sampler.ALWAYS_SAMPLE;
        }
        try {
            switch (sampledType) {
                // counting
                case SAMPLER_TYPE_COUNTING:
                    return CountingSampler.create(probability.floatValue());
                // rate_limiting
                case SAMPLER_TYPE_RATE_LIMITING:
                    return RateLimitingSampler.create(probability.intValue());
                // boundary
                case SAMPLER_TYPE_BOUNDARY:
                    return BoundarySampler.create(probability.floatValue());
                // always
                default:
                    return Sampler.ALWAYS_SAMPLE;
            }
        } catch (IllegalArgumentException e) {
            LOGGER.warn("observability.tracings.sampled error, use Sampler.ALWAYS_SAMPLE for.", e.getMessage());
            return Sampler.ALWAYS_SAMPLE;
        }
    }


    @Injection.Bean
    public Tracing tracing() {
        return tracing;
    }

    @Override
    public TracingSupplier tracingSupplier() {
        return supplier -> {
            if (iTracing != null) {
                return iTracing;
            }
            synchronized (TracingProviderImpl.class) {
                if (iTracing != null) {
                    return iTracing;
                }
                iTracing = TracingImpl.build(supplier, tracing);
            }
            return iTracing;
        };
    }

    private String getServiceName() {
        return this.serviceName.getValue();
    }
}
