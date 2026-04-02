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

package com.megaease.easeagent.core.plugin;

import com.megaease.easeagent.config.ConfigUtils;
import com.megaease.easeagent.config.Configs;
import com.megaease.easeagent.core.plugin.matcher.ClassTransformation;
import com.megaease.easeagent.core.plugin.matcher.MethodTransformation;
import com.megaease.easeagent.core.plugin.registry.PluginRegistry;
import com.megaease.easeagent.core.plugin.transformer.CompoundPluginTransformer;
import com.megaease.easeagent.core.plugin.transformer.DynamicFieldTransformer;
import com.megaease.easeagent.core.plugin.transformer.ForAdviceTransformer;
import com.megaease.easeagent.core.plugin.transformer.TypeFieldTransformer;
import com.megaease.easeagent.log4j2.Logger;
import com.megaease.easeagent.log4j2.LoggerFactory;
import com.megaease.easeagent.plugin.AgentPlugin;
import com.megaease.easeagent.plugin.CodeVersion;
import com.megaease.easeagent.plugin.Ordered;
import com.megaease.easeagent.plugin.Points;
import com.megaease.easeagent.plugin.field.AgentDynamicFieldAccessor;
import com.megaease.easeagent.plugin.interceptor.InterceptorProvider;
import com.megaease.easeagent.plugin.utils.common.StringUtils;
import net.bytebuddy.agent.builder.AgentBuilder;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PluginLoader {

    private PluginLoader() {
    }

    static Logger log = LoggerFactory.getLogger(PluginLoader.class);

    // load 读取用于进行增强的 Plugin, Points, InterceptorProvider,并生成 ClassTransformation，然后进行增强
    public static AgentBuilder load(AgentBuilder ab, Configs conf) {
        // 读取 AgentPlugin 并注册
        pluginLoad();
        // 读取 Points 并按CodeVersion注册
        pointsLoad(conf);
        // 读取 InterceptorProvider 并将其关联的 Point 存在的注册
        providerLoad();
        // 读取 Points 并基于其注册 ClassTransformation，返回所有的 ClassTransformation
        Set<ClassTransformation> sortedTransformations = classTransformationLoad();

        // 将要增强的类进行增强
        // 一个 transformation 底层对应一个组合的 Transformer
        for (ClassTransformation transformation : sortedTransformations) {
            //
            ab = ab.type(
                    transformation.getClassMatcher(), // 来源于 Points#classMatcher
                    transformation.getClassloaderMatcher() // 来源于 Points#methodMatcher
                )
                .transform(
                    compound(
                        transformation.isHasDynamicField(), // 来源于 Points#hasDynamicField
                        transformation.getMethodTransformations(), // 来源于 Points#methodMatcher
                        transformation.getTypeFieldAccessor() // 来源于 Points#typeFieldAccessor
                    ));
        }
        return ab;
    }

    // providerLoad 读取 InterceptorProvider 并将其关联的 Point 存在的注册
    public static void providerLoad() {
        // 使用 ServiceLoader 从 META-INF/services/com.megaease.easeagent.plugin.interceptor.InterceptorProvider 读取文件内容
        // async -> com.megaease.easeagent.plugin.interceptor.RunnableInterceptor$Provider0
        //          com.megaease.easeagent.plugin.interceptor.RunnableInterceptor$Provider1
        // dubbo -> com.megaease.easeagent.plugin.dubbo.interceptor.metrics.alibaba.AlibabaDubboAsyncMetricsInterceptor$Provider0
        //          com.megaease.easeagent.plugin.dubbo.interceptor.metrics.alibaba.AlibabaDubboMetricsInterceptor$Provider0
        //          com.megaease.easeagent.plugin.dubbo.interceptor.metrics.apache.ApacheDubboMetricsInterceptor$Provider0
        //          com.megaease.easeagent.plugin.dubbo.interceptor.trace.alibaba.AlibabaDubboAsyncTraceInterceptor$Provider0
        //          com.megaease.easeagent.plugin.dubbo.interceptor.trace.alibaba.AlibabaDubboTraceInterceptor$Provider0
        //          com.megaease.easeagent.plugin.dubbo.interceptor.trace.apache.ApacheDubboTraceInterceptor$Provider0
        // elasticsearch -> com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestAsync4MetricsInterceptor$Provider0
        //                  com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestAsync4TraceInterceptor$Provider0
        //                  com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestMetricsInterceptor$Provider0
        //                  com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestTraceInterceptor$Provider0
        //                  com.megaease.easeagent.plugin.elasticsearch.interceptor.redirect.SpringElasticsearchInterceptor$Provider0
        // healthy -> com.megaease.easeagent.plugin.healthy.OnApplicationEventInterceptor$Provider0
        // httpclient -> com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5AsyncForwardedInterceptor$Provider0
        //               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5AsyncTracingInterceptor$Provider0
        //               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5DoExecuteForwardedInterceptor$Provider0
        //               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5DoExecuteInterceptor$Provider0
        //               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClientDoExecuteForwardedInterceptor$Provider0
        //               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClientDoExecuteInterceptor$Provider0
        // httpservlet -> com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterForwardedInterceptor$Provider0
        //                com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterMetricInterceptor$Provider0
        //                com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterTraceInterceptor$Provider0
        //                com.megaease.easeagent.plugin.httpservlet.interceptor.ServletHttpLogInterceptor$Provider0
        // jdbc -> com.megaease.easeagent.plugin.jdbc.interceptor.JdbConPrepareOrCreateStmInterceptor$Provider0
        //         com.megaease.easeagent.plugin.jdbc.interceptor.JdbcStmPrepareSqlInterceptor$Provider0
        //         com.megaease.easeagent.plugin.jdbc.interceptor.JdbcStmPrepareSqlInterceptor$Provider1
        //         com.megaease.easeagent.plugin.jdbc.interceptor.metric.JdbcDataSourceMetricInterceptor$Provider0
        //         com.megaease.easeagent.plugin.jdbc.interceptor.metric.JdbcStmMetricInterceptor$Provider0
        //         com.megaease.easeagent.plugin.jdbc.interceptor.redirect.HikariSetPropertyInterceptor$Provider0
        //         com.megaease.easeagent.plugin.jdbc.interceptor.tracing.JdbcStmTracingInterceptor$Provider0
        // kafka -> com.megaease.easeagent.plugin.kafka.interceptor.initialize.ConsumerRecordInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.initialize.KafkaConsumerConstructInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.initialize.KafkaConsumerPollInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.initialize.KafkaProducerConstructInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.metric.KafkaConsumerMetricInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.metric.KafkaMessageListenerMetricInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.metric.KafkaProducerMetricInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.redirect.KafkaConsumerConfigConstructInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.redirect.KafkaProducerConfigConstructInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.tracing.KafkaConsumerTracingInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.tracing.KafkaMessageListenerTracingInterceptor$Provider0
        //          com.megaease.easeagent.plugin.kafka.interceptor.tracing.KafkaProducerDoSendInterceptor$Provider0
        // log4j2 -> com.megaease.easeagent.log4j2.interceptor.Log4j2AppenderInterceptor$Provider0
        // logback -> com.megaease.easeagent.logback.interceptor.LogbackAppenderInterceptor$Provider0
        // mongodb -> com.megaease.easeagent.plugin.mongodb.interceptor.MongoClientConstruct4MetricInterceptor$Provider0
        //            com.megaease.easeagent.plugin.mongodb.interceptor.MongoClientConstruct4TraceInterceptor$Provider0
        //            com.megaease.easeagent.plugin.mongodb.interceptor.MongoDbRedirectInterceptor$Provider0
        //            com.megaease.easeagent.plugin.mongodb.interceptor.MongoInternalConnectionSendAndReceiveAsync4MetricInterceptor$Provider0
        //            com.megaease.easeagent.plugin.mongodb.interceptor.MongoInternalConnectionSendAndReceiveAsync4TraceInterceptor$Provider0
        //            com.megaease.easeagent.plugin.mongodb.interceptor.MongoReactiveInitMetricInterceptor$Provider0
        //            com.megaease.easeagent.plugin.mongodb.interceptor.MongoReactiveInitTraceInterceptor$Provider0
        // motan -> com.megaease.easeagent.plugin.motan.interceptor.metrics.MotanMetricsInterceptor$Provider0
        //          com.megaease.easeagent.plugin.motan.interceptor.trace.consumer.MotanConsumerTraceInterceptor$Provider0
        //          com.megaease.easeagent.plugin.motan.interceptor.trace.provider.MotanProviderTraceInterceptor$Provider0
        // okhttp -> com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpAsyncTracingInterceptor$Provider0
        //           com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpForwardedInterceptor$Provider0
        //           com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpForwardedInterceptor$Provider1
        //           com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpTracingInterceptor$Provider0
        // rabbitMq -> com.megaease.easeagent.plugin.rabbitmq.spring.interceptor.RabbitMqMessageListenerOnMessageInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.spring.interceptor.RabbitMqOnMessageMetricInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.spring.interceptor.RabbitMqOnMessageTracingInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqChannelConsumeInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqChannelConsumerDeliveryInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqChannelPublishInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqConsumerHandleDeliveryInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.metirc.RabbitMqConsumerMetricInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.metirc.RabbitMqProducerMetricInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.redirect.RabbitMqConfigFactoryInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.redirect.RabbitMqPropertyInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.tracing.RabbitMqChannelPublishTracingInterceptor$Provider0
        //             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.tracing.RabbitMqConsumerTracingInterceptor$Provider0
        // redis -> com.megaease.easeagent.plugin.redis.interceptor.initialize.RedisClientInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.initialize.RedisClusterClientInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.metric.JedisMetricInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.metric.LettuceMetricInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.redirect.JedisConstructorInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.redirect.LettuceRedisClientConstructInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.redirect.RedisPropertiesClusterSetNodesInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.redirect.RedisPropertiesSetPropertyInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.tracing.JedisTracingInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.tracing.LettuceTracingInterceptor$Provider0
        //          com.megaease.easeagent.plugin.redis.interceptor.tracing.StatefulRedisConnectionInterceptor$Provider0
        // servicename -> com.megaease.easeagent.plugin.servicename.interceptor.FeignBlockingLoadBalancerClientInterceptor$Provider0
        //                com.megaease.easeagent.plugin.servicename.interceptor.FeignLoadBalancerInterceptor$Provider0
        //                com.megaease.easeagent.plugin.servicename.interceptor.FilteringWebHandlerInterceptor$Provider0
        //                com.megaease.easeagent.plugin.servicename.interceptor.LoadBalancerFeignClientInterceptor$Provider0
        //                com.megaease.easeagent.plugin.servicename.interceptor.RestTemplateInterceptInterceptor$Provider0
        //                com.megaease.easeagent.plugin.servicename.interceptor.WebClientFilterInterceptor$Provider0
        // sofarpc -> com.megaease.easeagent.plugin.sofarpc.interceptor.initalize.SofaRpcFutureInvokeCallbackConstructInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.initalize.SofaRpcFutureInvokeCallbackConstructInterceptor$Provider1
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.metrics.callback.SofaRpcResponseCallbackMetricsInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.metrics.common.SofaRpcMetricsInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.metrics.future.SofaRpcResponseFutureMetricsInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.callback.SofaRpcResponseCallbackTraceInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.common.SofaRpcConsumerTraceInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.common.SofaRpcProviderTraceInterceptor$Provider0
        //            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.future.SofaRpcResponseFutureTraceInterceptor$Provider0
        // spring-gateway -> easeagent.plugin.spring.gateway.interceptor.initialize.GatewayServerForwardedInterceptor$Provider0
        //                   easeagent.plugin.spring.gateway.interceptor.initialize.GlobalFilterInterceptor$Provider0
        //                   easeagent.plugin.spring.gateway.interceptor.metric.GatewayMetricsInterceptor$Provider0
        //                   easeagent.plugin.spring.gateway.interceptor.metric.log.GatewayAccessLogInterceptor$Provider0
        //                   easeagent.plugin.spring.gateway.interceptor.tracing.GatewayServerTracingInterceptor$Provider0
        //                   easeagent.plugin.spring.gateway.interceptor.tracing.HttpHeadersFilterTracingInterceptor$Provider0
        // springweb -> com.megaease.easeagent.plugin.springweb.interceptor.forwarded.FeignClientForwardedInterceptor$Provider0
        //              com.megaease.easeagent.plugin.springweb.interceptor.forwarded.RestTemplateForwardedInterceptor$Provider0
        //              com.megaease.easeagent.plugin.springweb.interceptor.forwarded.WebClientFilterForwardedInterceptor$Provider0
        //              com.megaease.easeagent.plugin.springweb.interceptor.initialize.WebClientBuildInterceptor$Provider0
        //              com.megaease.easeagent.plugin.springweb.interceptor.tracing.ClientHttpRequestInterceptor$Provider0
        //              com.megaease.easeagent.plugin.springweb.interceptor.tracing.FeignClientTracingInterceptor$Provider0
        //              com.megaease.easeagent.plugin.springweb.interceptor.tracing.WebClientFilterTracingInterceptor$Provider0
        for (InterceptorProvider provider : BaseLoader.load(InterceptorProvider.class)) {
            // 读取其所在的 Point 类名
            String pointsClassName = PluginRegistry.getPointsClassName(provider.getAdviceTo());
            // 获取其 Point 类名
            Points points = PluginRegistry.getPoints(pointsClassName);
            if (points == null) {
                log.debug("Unload provider:{}, can not found Points<{}>", provider.getClass().getName(), pointsClassName);
                continue;
            } else {
                log.debug("Loading provider:{}", provider.getClass().getName());
            }

            try {
                log.debug("provider for:{} at {}",
                    provider.getPluginClassName(), provider.getAdviceTo());
                // 注册 拦截器提供者
                PluginRegistry.register(provider);
            } catch (Exception | LinkageError e) {
                log.error(
                    "Unable to load provider in [class {}]",
                    provider.getClass().getName(),
                    e);
            }
        }
    }

    // classTransformationLoad 读取 Points 并基于其注册 ClassTransformation，返回所有的 ClassTransformation
    public static Set<ClassTransformation> classTransformationLoad() {
        // 获取所有 Points
        Collection<Points> points = PluginRegistry.getPoints();
        return points.stream().map(point -> {
                try {
                    // 基于 Points 注册 ClassTransformation
                    return PluginRegistry.registerClassTransformation(point);
                } catch (Exception e) {
                    log.error(
                        "Unable to load classTransformation in [class {}]",
                        point.getClass().getName(),
                        e);
                    return null;
                }
            }).filter(Objects::nonNull)
            .sorted(Comparator.comparing(Ordered::order))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // pluginLoad 读取 AgentPlugin 并注册
    public static void pluginLoad() {
        // 使用 ServiceLoader 从 META-INF/services/com.megaease.easeagent.plugin.AgentPlugin 读取文件内容
        // 具体位于 easeagent.jar/plugins/xxx.jar中，其中实现有：
        // async -> com.megaease.easeagent.plugin.AsyncPlugin
        // dubbo -> com.megaease.easeagent.plugin.dubbo.DubboPlugin
        // elasticsearch -> com.megaease.easeagent.plugin.elasticsearch.ElasticsearchRedirectPlugin,
        //                  com.megaease.easeagent.plugin.elasticsearch.ElasticsearchPlugin
        // healthy -> com.megaease.easeagent.plugin.healthy.HealthPlugin
        // httpclient -> com.megaease.easeagent.plugin.httpclient.HttpClientPlugin
        //               com.megaease.easeagent.plugin.httpclient.ForwardedPlugin
        // httpservlet -> com.megaease.easeagent.plugin.httpservlet.ForwardedPlugin
        //                com.megaease.easeagent.plugin.httpservlet.HttpServletPlugin
        //                com.megaease.easeagent.plugin.httpservlet.AccessPlugin
        // jdbc -> com.megaease.easeagent.plugin.jdbc.JdbcConnectionMetricPlugin
        //         com.megaease.easeagent.plugin.jdbc.JdbcDataSourceMetricPlugin
        //         com.megaease.easeagent.plugin.jdbc.JdbcRedirectPlugin
        //         com.megaease.easeagent.plugin.jdbc.JdbcTracingPlugin
        // kafka -> com.megaease.easeagent.plugin.kafka.KafkaPlugin
        //          com.megaease.easeagent.plugin.kafka.KafkaRedirectPlugin
        // log4j -> com.megaease.easeagent.log4j2.Log4j2Plugin
        // logback -> com.megaease.easeagent.logback.LogbackPlugin
        // mongodb -> com.megaease.easeagent.plugin.mongodb.MongoRedirectPlugin
        //            com.megaease.easeagent.plugin.mongodb.MongoPlugin
        // motan -> com.megaease.easeagent.plugin.motan.MotanPlugin
        // okhttp -> com.megaease.easeagent.plugin.okhttp.OkHttpPlugin
        //           com.megaease.easeagent.plugin.okhttp.ForwardedPlugin
        // rabbitMq -> com.megaease.easeagent.plugin.rabbitmq.RabbitMqPlugin
        //             com.megaease.easeagent.plugin.rabbitmq.RabbitMqRedirectPlugin
        // redis -> com.megaease.easeagent.plugin.redis.RedisRedirectPlugin
        //          com.megaease.easeagent.plugin.redis.RedisPlugin
        // servicename -> com.megaease.easeagent.plugin.servicename.ServiceNamePlugin
        // sofarpc -> com.megaease.easeagent.plugin.sofarpc.SofaRpcPlugin
        // spring-gateway -> easeagent.plugin.spring.gateway.AccessPlugin
        //                         easeagent.plugin.spring.gateway.SpringGatewayPlugin
        //                         easeagent.plugin.spring.gateway.ForwardedPlugin
        // springweb -> com.megaease.easeagent.plugin.springweb.RestTemplatePlugin
        //               com.megaease.easeagent.plugin.springweb.ForwardedPlugin
        //               com.megaease.easeagent.plugin.springweb.SpringWebPlugin
        //               com.megaease.easeagent.plugin.springweb.WebClientPlugin
        //               com.megaease.easeagent.plugin.springweb.FeignClientPlugin
        for (AgentPlugin plugin : BaseLoader.loadOrdered(AgentPlugin.class)) {
            log.info(
                "Loading plugin {}:{} [class {}]",
                plugin.getDomain(),
                plugin.getNamespace(),
                plugin.getClass().getName());

            try {
                // 注册插件
                PluginRegistry.register(plugin);
            } catch (Exception | LinkageError e) {
                log.error(
                    "Unable to load extension {}:{} [class {}]",
                    plugin.getDomain(),
                    plugin.getNamespace(),
                    plugin.getClass().getName(),
                    e);
            }
        }
    }

    // pointsLoad 读取 Points 并按CodeVersion注册
    public static void pointsLoad(Configs conf) {
        // 使用 ServiceLoader 从 META-INF/services/com.megaease.easeagent.plugin.Points 读取文件内容
        // 具体位于 easeagent.jar/plugins/xxx.jar中，其中实现有：
        // async -> com.megaease.easeagent.plugin.advice.CrossThreadAdvice
        //          com.megaease.easeagent.plugin.advice.ReactSchedulersAdvice
        // dubbo -> com.megaease.easeagent.plugin.dubbo.advice.AlibabaDubboAdvice
        //          com.megaease.easeagent.plugin.dubbo.advice.AlibabaDubboResponseFutureAdvice
        //          com.megaease.easeagent.plugin.dubbo.advice.ApacheDubboAdvice
        // elasticsearch -> com.megaease.easeagent.plugin.elasticsearch.advice.SpringElasticsearchAdvice
        //                  com.megaease.easeagent.plugin.elasticsearch.points.ElasticsearchPerformRequestAsyncPoints
        //                  com.megaease.easeagent.plugin.elasticsearch.points.ElasticsearchPerformRequestPoints
        // healthy -> com.megaease.easeagent.plugin.healthy.SpringApplicationAdminMXBeanRegistrarAdvice
        // httpclient -> com.megaease.easeagent.plugin.httpclient.advice.HttpClient5AsyncAdvice
        //               com.megaease.easeagent.plugin.httpclient.advice.HttpClient5DoExecuteAdvice
        //               com.megaease.easeagent.plugin.httpclient.advice.HttpClientDoExecuteAdvice
        // httpservlet -> com.megaease.easeagent.plugin.httpservlet.advice.DoFilterPoints
        // jdbc -> com.megaease.easeagent.plugin.jdbc.advice.HikariDataSourceAdvice
        //         com.megaease.easeagent.plugin.jdbc.advice.JdbcConnectionAdvice
        //         com.megaease.easeagent.plugin.jdbc.advice.JdbcDataSourceAdvice
        //         com.megaease.easeagent.plugin.jdbc.advice.JdbcStatementAdvice
        // kafka -> com.megaease.easeagent.plugin.kafka.advice.KafkaConsumerAdvice
        //          com.megaease.easeagent.plugin.kafka.advice.KafkaConsumerConfigAdvice
        //          com.megaease.easeagent.plugin.kafka.advice.KafkaConsumerRecordAdvice
        //          com.megaease.easeagent.plugin.kafka.advice.KafkaMessageListenerAdvice
        //          com.megaease.easeagent.plugin.kafka.advice.KafkaProducerAdvice
        //          com.megaease.easeagent.plugin.kafka.advice.KafkaProducerConfigAdvice
        // log4j2 -> com.megaease.easeagent.log4j2.points.AbstractLoggerPoints
        // logback -> com.megaease.easeagent.logback.points.LoggerPoints
        // mongodb -> com.megaease.easeagent.plugin.mongodb.points.MongoAsyncMongoClientsPoints
        //            com.megaease.easeagent.plugin.mongodb.points.MongoClientImplPoints
        //            com.megaease.easeagent.plugin.mongodb.points.MongoDBInternalConnectionPoints
        //            com.megaease.easeagent.plugin.mongodb.points.MongoRedirectPoints
        // motan -> com.megaease.easeagent.plugin.motan.advice.MotanConsumerAdvice
        //          com.megaease.easeagent.plugin.motan.advice.MotanProviderAdvice
        // okhttp -> com.megaease.easeagent.plugin.okhttp.advice.OkHttpAdvice
        // rabbitMq -> com.megaease.easeagent.plugin.rabbitmq.spring.RabbitMqMessageListenerAdvice
        //             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqChannelAdvice
        //             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqConfigFactoryAdvice
        //             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqConsumerAdvice
        //             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqPropertyAdvice
        // redis -> com.megaease.easeagent.plugin.redis.advice.JedisAdvice
        //          com.megaease.easeagent.plugin.redis.advice.JedisConstructorAdvice
        //          com.megaease.easeagent.plugin.redis.advice.LettuceRedisClientAdvice
        //          com.megaease.easeagent.plugin.redis.advice.RedisChannelWriterAdvice
        //          com.megaease.easeagent.plugin.redis.advice.RedisClusterClientAdvice
        //          com.megaease.easeagent.plugin.redis.advice.RedisPropertiesAdvice
        //          com.megaease.easeagent.plugin.redis.advice.RedisPropertiesClusterAdvice
        //          com.megaease.easeagent.plugin.redis.advice.StatefulRedisConnectionAdvice
        // servicename -> com.megaease.easeagent.plugin.servicename.advice.FeignBlockingLoadBalancerClientAdvice
        //                com.megaease.easeagent.plugin.servicename.advice.FeignLoadBalancerAdvice
        //                com.megaease.easeagent.plugin.servicename.advice.FilteringWebHandlerAdvice
        //                com.megaease.easeagent.plugin.servicename.advice.LoadBalancerFeignClientAdvice
        //                com.megaease.easeagent.plugin.servicename.advice.RestTemplateInterceptAdvice
        //                com.megaease.easeagent.plugin.servicename.advice.WebClientFilterAdvice
        // sofarpc -> com.megaease.easeagent.plugin.sofarpc.adivce.BoltFutureInvokeCallbackConstructAdvice
        //            com.megaease.easeagent.plugin.sofarpc.adivce.ConsumerAdvice
        //            com.megaease.easeagent.plugin.sofarpc.adivce.FutureInvokeCallbackConstructAdvice
        //            com.megaease.easeagent.plugin.sofarpc.adivce.ProviderAdvice
        //            com.megaease.easeagent.plugin.sofarpc.adivce.ResponseCallbackAdvice
        //            com.megaease.easeagent.plugin.sofarpc.adivce.ResponseFutureAdvice
        // spring-gateway -> easeagent.plugin.spring.gateway.advice.AgentGlobalFilterAdvice
        //                   easeagent.plugin.spring.gateway.advice.HttpHeadersFilterAdvice
        //                   easeagent.plugin.spring.gateway.advice.InitGlobalFilterAdvice
        // springweb -> com.megaease.easeagent.plugin.springweb.advice.ClientHttpRequestAdvice
        //              com.megaease.easeagent.plugin.springweb.advice.FeignClientAdvice
        //              com.megaease.easeagent.plugin.springweb.advice.WebClientBuilderAdvice
        //              com.megaease.easeagent.plugin.springweb.advice.WebClientFilterAdvice
        for (Points points : BaseLoader.load(Points.class)) {
            if (!isCodeVersion(points, conf)) {
                continue;
            } else {
                log.info("Loading points [class Points<{}>]", points.getClass().getName());
            }

            try {
                // 注入切入点
                PluginRegistry.register(points);
            } catch (Exception | LinkageError e) {
                log.error(
                    "Unable to load extension [class {}]",
                    points.getClass().getName(),
                    e);
            }
        }
    }

    // isCodeVersion
    public static boolean isCodeVersion(Points points, Configs conf) {
        // 获取该 point 要求的 jdk 版本
        CodeVersion codeVersion = points.codeVersions();
        // 为空则代表满足
        if (codeVersion.isEmpty()) {
            return true;
        }
        String versionKey = ConfigUtils.buildCodeVersionKey(codeVersion.getKey());
        Set<String> versions = new HashSet<>(conf.getStringList(versionKey));
        if (versions.isEmpty()) {
            versions = Points.DEFAULT_VERSIONS;
        }
        Set<String> pointVersions = codeVersion.getVersions();
        for (String version : versions) {
            if (pointVersions.contains(version)) {
                return true;
            }
        }
        log.info("Unload points [class Points<{}>], the config [{}={}] is not in Points.codeVersions()=[{}:{}]",
            points.getClass().getCanonicalName(), versionKey, String.join(",", versions),
            codeVersion.getKey(), String.join(",", codeVersion.getVersions()));
        return false;
    }


    /**
     * 组合 方法transforms
     *
     * @param methodTransformations method matchers under a special classMatcher
     * @return transform
     */
    public static AgentBuilder.Transformer compound(boolean hasDynamicField,
                                                    Iterable<MethodTransformation> methodTransformations, String typeFieldAccessor) {
        // 从 Points#methodMatcher(业务定义，示例存储) -> MethodTransformation(中间态) -> ForAdviceTransformer(byte buddy)
        List<AgentBuilder.Transformer> agentTransformers = StreamSupport
            .stream(methodTransformations.spliterator(), false)
            .map(ForAdviceTransformer::new)
            .collect(Collectors.toList());

        // 处理 添加动态字段
        if (hasDynamicField) {
            // hasDynamicField -> DynamicFieldTransformer，其字段名固定为：ease_agent_dynamic_$$$_data
            agentTransformers.add(new DynamicFieldTransformer(AgentDynamicFieldAccessor.DYNAMIC_FIELD_NAME));
        }

        // 处理 读取内部字段
        if (StringUtils.hasText(typeFieldAccessor)) {
            // typeFieldAccessor -> TypeFieldTransformer
            agentTransformers.add(new TypeFieldTransformer(typeFieldAccessor));
        }

        // 将多个 transformer 合并
        return new CompoundPluginTransformer(agentTransformers);
    }
}
