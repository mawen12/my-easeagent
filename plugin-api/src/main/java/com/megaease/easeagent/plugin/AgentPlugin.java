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

package com.megaease.easeagent.plugin;

import com.megaease.easeagent.plugin.enums.Order;

/**
 * 顶层的 agent 插件接口。
 * async -> com.megaease.easeagent.plugin.AsyncPlugin
 * dubbo -> com.megaease.easeagent.plugin.dubbo.DubboPlugin
 * elasticsearch -> com.megaease.easeagent.plugin.elasticsearch.ElasticsearchRedirectPlugin,
 *                  com.megaease.easeagent.plugin.elasticsearch.ElasticsearchPlugin
 * healthy -> com.megaease.easeagent.plugin.healthy.HealthPlugin
 * httpclient -> com.megaease.easeagent.plugin.httpclient.HttpClientPlugin
 *               com.megaease.easeagent.plugin.httpclient.ForwardedPlugin
 * httpservlet -> com.megaease.easeagent.plugin.httpservlet.ForwardedPlugin
 *                com.megaease.easeagent.plugin.httpservlet.HttpServletPlugin
 *                com.megaease.easeagent.plugin.httpservlet.AccessPlugin
 * jdbc -> com.megaease.easeagent.plugin.jdbc.JdbcConnectionMetricPlugin
 *         com.megaease.easeagent.plugin.jdbc.JdbcDataSourceMetricPlugin
 *         com.megaease.easeagent.plugin.jdbc.JdbcRedirectPlugin
 *         com.megaease.easeagent.plugin.jdbc.JdbcTracingPlugin
 * kafka -> com.megaease.easeagent.plugin.kafka.KafkaPlugin
 *          com.megaease.easeagent.plugin.kafka.KafkaRedirectPlugin
 * log4j -> com.megaease.easeagent.log4j2.Log4j2Plugin
 * logback -> com.megaease.easeagent.logback.LogbackPlugin
 * mongodb -> com.megaease.easeagent.plugin.mongodb.MongoRedirectPlugin
 *            com.megaease.easeagent.plugin.mongodb.MongoPlugin
 * motan -> com.megaease.easeagent.plugin.motan.MotanPlugin
 * okhttp -> com.megaease.easeagent.plugin.okhttp.OkHttpPlugin
 *           com.megaease.easeagent.plugin.okhttp.ForwardedPlugin
 * rabbitMq -> com.megaease.easeagent.plugin.rabbitmq.RabbitMqPlugin
 *             com.megaease.easeagent.plugin.rabbitmq.RabbitMqRedirectPlugin
 * redis -> com.megaease.easeagent.plugin.redis.RedisRedirectPlugin
 *          com.megaease.easeagent.plugin.redis.RedisPlugin
 * servicename -> com.megaease.easeagent.plugin.servicename.ServiceNamePlugin
 * sofarpc -> com.megaease.easeagent.plugin.sofarpc.SofaRpcPlugin
 * spring-gateway -> easeagent.plugin.spring.gateway.AccessPlugin
 *                         easeagent.plugin.spring.gateway.SpringGatewayPlugin
 *                         easeagent.plugin.spring.gateway.ForwardedPlugin
 * springweb -> com.megaease.easeagent.plugin.springweb.RestTemplatePlugin
 *               com.megaease.easeagent.plugin.springweb.ForwardedPlugin
 *               com.megaease.easeagent.plugin.springweb.SpringWebPlugin
 *               com.megaease.easeagent.plugin.springweb.WebClientPlugin
 *               com.megaease.easeagent.plugin.springweb.FeignClientPlugin
 */
public interface AgentPlugin extends Ordered {
    /**
     * define the plugin name, avoiding conflicts with others
     * it will be used as namespace when get configuration.
     *
     * namespace 用于区分不同的插件，比如 async, jdbc, redis 等
     */
    String getNamespace();

    /**
     * define the plugin domain,
     * it will be used to get configuration when loaded:
     *
     * domain 用于区分不同的功能，比如 OBSERVABILITY, INTEGRABILITY
     */
    String getDomain();

    /**
     * 当一个 advice 被应用了多个插件时，order 用于区分其优先级，确定执行顺序
     *
     * @return
     */
    default int order() {
        return Order.HIGH.getOrder();
    }
}


