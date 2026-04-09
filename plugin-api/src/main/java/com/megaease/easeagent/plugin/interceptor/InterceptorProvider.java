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

package com.megaease.easeagent.plugin.interceptor;

import com.megaease.easeagent.plugin.interceptor.Interceptor;

import java.util.function.Supplier;

/**
 * used in autogenerate code
 *
 * plugin 特定，位于 plugin/<xxx>/META-INF/services/com.megaease.easeagent.plugin.interceptor.InterceptorProvider
 *
 * 该类的内容是由 PluginProcessor 借助 google 的 AutoService 自动生成的，而非手动生成的
 * 需要通过反编译才能看到，javap -c <xxxx>.class
 * 拦截器提供者的接口，
 * async -> com.megaease.easeagent.plugin.interceptor.RunnableInterceptor$Provider0
 *          com.megaease.easeagent.plugin.interceptor.RunnableInterceptor$Provider1
 * dubbo -> com.megaease.easeagent.plugin.dubbo.interceptor.metrics.alibaba.AlibabaDubboAsyncMetricsInterceptor$Provider0
 *          com.megaease.easeagent.plugin.dubbo.interceptor.metrics.alibaba.AlibabaDubboMetricsInterceptor$Provider0
 *          com.megaease.easeagent.plugin.dubbo.interceptor.metrics.apache.ApacheDubboMetricsInterceptor$Provider0
 *          com.megaease.easeagent.plugin.dubbo.interceptor.trace.alibaba.AlibabaDubboAsyncTraceInterceptor$Provider0
 *          com.megaease.easeagent.plugin.dubbo.interceptor.trace.alibaba.AlibabaDubboTraceInterceptor$Provider0
 *          com.megaease.easeagent.plugin.dubbo.interceptor.trace.apache.ApacheDubboTraceInterceptor$Provider0
 * elasticsearch -> com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestAsync4MetricsInterceptor$Provider0
 *                  com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestAsync4TraceInterceptor$Provider0
 *                  com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestMetricsInterceptor$Provider0
 *                  com.megaease.easeagent.plugin.elasticsearch.interceptor.ElasticsearchPerformRequestTraceInterceptor$Provider0
 *                  com.megaease.easeagent.plugin.elasticsearch.interceptor.redirect.SpringElasticsearchInterceptor$Provider0
 * healthy -> com.megaease.easeagent.plugin.healthy.OnApplicationEventInterceptor$Provider0
 * httpclient -> com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5AsyncForwardedInterceptor$Provider0
 *               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5AsyncTracingInterceptor$Provider0
 *               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5DoExecuteForwardedInterceptor$Provider0
 *               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClient5DoExecuteInterceptor$Provider0
 *               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClientDoExecuteForwardedInterceptor$Provider0
 *               com.megaease.easeagent.plugin.httpclient.interceptor.HttpClientDoExecuteInterceptor$Provider0
 * httpservlet -> com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterForwardedInterceptor$Provider0
 *                com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterMetricInterceptor$Provider0
 *                com.megaease.easeagent.plugin.httpservlet.interceptor.DoFilterTraceInterceptor$Provider0
 *                com.megaease.easeagent.plugin.httpservlet.interceptor.ServletHttpLogInterceptor$Provider0
 * jdbc -> com.megaease.easeagent.plugin.jdbc.interceptor.JdbConPrepareOrCreateStmInterceptor$Provider0
 *         com.megaease.easeagent.plugin.jdbc.interceptor.JdbcStmPrepareSqlInterceptor$Provider0
 *         com.megaease.easeagent.plugin.jdbc.interceptor.JdbcStmPrepareSqlInterceptor$Provider1
 *         com.megaease.easeagent.plugin.jdbc.interceptor.metric.JdbcDataSourceMetricInterceptor$Provider0
 *         com.megaease.easeagent.plugin.jdbc.interceptor.metric.JdbcStmMetricInterceptor$Provider0
 *         com.megaease.easeagent.plugin.jdbc.interceptor.redirect.HikariSetPropertyInterceptor$Provider0
 *         com.megaease.easeagent.plugin.jdbc.interceptor.tracing.JdbcStmTracingInterceptor$Provider0
 * kafka -> com.megaease.easeagent.plugin.kafka.interceptor.initialize.ConsumerRecordInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.initialize.KafkaConsumerConstructInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.initialize.KafkaConsumerPollInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.initialize.KafkaProducerConstructInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.metric.KafkaConsumerMetricInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.metric.KafkaMessageListenerMetricInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.metric.KafkaProducerMetricInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.redirect.KafkaConsumerConfigConstructInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.redirect.KafkaProducerConfigConstructInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.tracing.KafkaConsumerTracingInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.tracing.KafkaMessageListenerTracingInterceptor$Provider0
 *          com.megaease.easeagent.plugin.kafka.interceptor.tracing.KafkaProducerDoSendInterceptor$Provider0
 * log4j2 -> com.megaease.easeagent.log4j2.interceptor.Log4j2AppenderInterceptor$Provider0
 * logback -> com.megaease.easeagent.logback.interceptor.LogbackAppenderInterceptor$Provider0
 * mongodb -> com.megaease.easeagent.plugin.mongodb.interceptor.MongoClientConstruct4MetricInterceptor$Provider0
 *            com.megaease.easeagent.plugin.mongodb.interceptor.MongoClientConstruct4TraceInterceptor$Provider0
 *            com.megaease.easeagent.plugin.mongodb.interceptor.MongoDbRedirectInterceptor$Provider0
 *            com.megaease.easeagent.plugin.mongodb.interceptor.MongoInternalConnectionSendAndReceiveAsync4MetricInterceptor$Provider0
 *            com.megaease.easeagent.plugin.mongodb.interceptor.MongoInternalConnectionSendAndReceiveAsync4TraceInterceptor$Provider0
 *            com.megaease.easeagent.plugin.mongodb.interceptor.MongoReactiveInitMetricInterceptor$Provider0
 *            com.megaease.easeagent.plugin.mongodb.interceptor.MongoReactiveInitTraceInterceptor$Provider0
 * motan -> com.megaease.easeagent.plugin.motan.interceptor.metrics.MotanMetricsInterceptor$Provider0
 *          com.megaease.easeagent.plugin.motan.interceptor.trace.consumer.MotanConsumerTraceInterceptor$Provider0
 *          com.megaease.easeagent.plugin.motan.interceptor.trace.provider.MotanProviderTraceInterceptor$Provider0
 * okhttp -> com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpAsyncTracingInterceptor$Provider0
 *           com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpForwardedInterceptor$Provider0
 *           com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpForwardedInterceptor$Provider1
 *           com.megaease.easeagent.plugin.okhttp.interceptor.OkHttpTracingInterceptor$Provider0
 * rabbitMq -> com.megaease.easeagent.plugin.rabbitmq.spring.interceptor.RabbitMqMessageListenerOnMessageInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.spring.interceptor.RabbitMqOnMessageMetricInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.spring.interceptor.RabbitMqOnMessageTracingInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqChannelConsumeInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqChannelConsumerDeliveryInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqChannelPublishInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.RabbitMqConsumerHandleDeliveryInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.metirc.RabbitMqConsumerMetricInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.metirc.RabbitMqProducerMetricInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.redirect.RabbitMqConfigFactoryInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.redirect.RabbitMqPropertyInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.tracing.RabbitMqChannelPublishTracingInterceptor$Provider0
 *             com.megaease.easeagent.plugin.rabbitmq.v5.interceptor.tracing.RabbitMqConsumerTracingInterceptor$Provider0
 * redis -> com.megaease.easeagent.plugin.redis.interceptor.initialize.RedisClientInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.initialize.RedisClusterClientInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.metric.JedisMetricInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.metric.LettuceMetricInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.redirect.JedisConstructorInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.redirect.LettuceRedisClientConstructInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.redirect.RedisPropertiesClusterSetNodesInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.redirect.RedisPropertiesSetPropertyInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.tracing.JedisTracingInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.tracing.LettuceTracingInterceptor$Provider0
 *          com.megaease.easeagent.plugin.redis.interceptor.tracing.StatefulRedisConnectionInterceptor$Provider0
 * servicename -> com.megaease.easeagent.plugin.servicename.interceptor.FeignBlockingLoadBalancerClientInterceptor$Provider0
 *                com.megaease.easeagent.plugin.servicename.interceptor.FeignLoadBalancerInterceptor$Provider0
 *                com.megaease.easeagent.plugin.servicename.interceptor.FilteringWebHandlerInterceptor$Provider0
 *                com.megaease.easeagent.plugin.servicename.interceptor.LoadBalancerFeignClientInterceptor$Provider0
 *                com.megaease.easeagent.plugin.servicename.interceptor.RestTemplateInterceptInterceptor$Provider0
 *                com.megaease.easeagent.plugin.servicename.interceptor.WebClientFilterInterceptor$Provider0
 * sofarpc -> com.megaease.easeagent.plugin.sofarpc.interceptor.initalize.SofaRpcFutureInvokeCallbackConstructInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.initalize.SofaRpcFutureInvokeCallbackConstructInterceptor$Provider1
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.metrics.callback.SofaRpcResponseCallbackMetricsInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.metrics.common.SofaRpcMetricsInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.metrics.future.SofaRpcResponseFutureMetricsInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.callback.SofaRpcResponseCallbackTraceInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.common.SofaRpcConsumerTraceInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.common.SofaRpcProviderTraceInterceptor$Provider0
 *            com.megaease.easeagent.plugin.sofarpc.interceptor.trace.future.SofaRpcResponseFutureTraceInterceptor$Provider0
 * spring-gateway -> easeagent.plugin.spring.gateway.interceptor.initialize.GatewayServerForwardedInterceptor$Provider0
 *                   easeagent.plugin.spring.gateway.interceptor.initialize.GlobalFilterInterceptor$Provider0
 *                   easeagent.plugin.spring.gateway.interceptor.metric.GatewayMetricsInterceptor$Provider0
 *                   easeagent.plugin.spring.gateway.interceptor.metric.log.GatewayAccessLogInterceptor$Provider0
 *                   easeagent.plugin.spring.gateway.interceptor.tracing.GatewayServerTracingInterceptor$Provider0
 *                   easeagent.plugin.spring.gateway.interceptor.tracing.HttpHeadersFilterTracingInterceptor$Provider0
 * springweb -> com.megaease.easeagent.plugin.springweb.interceptor.forwarded.FeignClientForwardedInterceptor$Provider0
 *              com.megaease.easeagent.plugin.springweb.interceptor.forwarded.RestTemplateForwardedInterceptor$Provider0
 *              com.megaease.easeagent.plugin.springweb.interceptor.forwarded.WebClientFilterForwardedInterceptor$Provider0
 *              com.megaease.easeagent.plugin.springweb.interceptor.initialize.WebClientBuildInterceptor$Provider0
 *              com.megaease.easeagent.plugin.springweb.interceptor.tracing.ClientHttpRequestInterceptor$Provider0
 *              com.megaease.easeagent.plugin.springweb.interceptor.tracing.FeignClientTracingInterceptor$Provider0
 *              com.megaease.easeagent.plugin.springweb.interceptor.tracing.WebClientFilterTracingInterceptor$Provider0
 */
public interface InterceptorProvider {
    // 获取拦截其
    Supplier<Interceptor> getInterceptorProvider();

    // 获取要被增强的方法名称，注意是权限定的名称
    String getAdviceTo();

    // 获取插件类名
    String getPluginClassName();
}
