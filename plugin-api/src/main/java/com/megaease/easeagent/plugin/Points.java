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

package com.megaease.easeagent.plugin;

import com.megaease.easeagent.plugin.matcher.IClassMatcher;
import com.megaease.easeagent.plugin.matcher.IMethodMatcher;
import com.megaease.easeagent.plugin.matcher.loader.ClassLoaderMatcher;
import com.megaease.easeagent.plugin.matcher.loader.IClassLoaderMatcher;

import java.util.Collections;
import java.util.Set;

/**
 * Pointcut can be defined by ProbeDefine implementation
 * and also can be defined through @OnClass and @OnMethod annotation
 *
 * 顶层的插件切入点接口。
 * async -> com.megaease.easeagent.plugin.advice.CrossThreadAdvice
 *          com.megaease.easeagent.plugin.advice.ReactSchedulersAdvice
 * dubbo -> com.megaease.easeagent.plugin.dubbo.advice.AlibabaDubboAdvice
 *          com.megaease.easeagent.plugin.dubbo.advice.AlibabaDubboResponseFutureAdvice
 *          com.megaease.easeagent.plugin.dubbo.advice.ApacheDubboAdvice
 * elasticsearch -> com.megaease.easeagent.plugin.elasticsearch.advice.SpringElasticsearchAdvice
 *                  com.megaease.easeagent.plugin.elasticsearch.points.ElasticsearchPerformRequestAsyncPoints
 *                  com.megaease.easeagent.plugin.elasticsearch.points.ElasticsearchPerformRequestPoints
 * healthy -> com.megaease.easeagent.plugin.healthy.SpringApplicationAdminMXBeanRegistrarAdvice
 * httpclient -> com.megaease.easeagent.plugin.httpclient.advice.HttpClient5AsyncAdvice
 *               com.megaease.easeagent.plugin.httpclient.advice.HttpClient5DoExecuteAdvice
 *               com.megaease.easeagent.plugin.httpclient.advice.HttpClientDoExecuteAdvice
 * httpservlet -> com.megaease.easeagent.plugin.httpservlet.advice.DoFilterPoints
 * jdbc -> com.megaease.easeagent.plugin.jdbc.advice.HikariDataSourceAdvice
 *         com.megaease.easeagent.plugin.jdbc.advice.JdbcConnectionAdvice
 *         com.megaease.easeagent.plugin.jdbc.advice.JdbcDataSourceAdvice
 *         com.megaease.easeagent.plugin.jdbc.advice.JdbcStatementAdvice
 * kafka -> com.megaease.easeagent.plugin.kafka.advice.KafkaConsumerAdvice
 *          com.megaease.easeagent.plugin.kafka.advice.KafkaConsumerConfigAdvice
 *          com.megaease.easeagent.plugin.kafka.advice.KafkaConsumerRecordAdvice
 *          com.megaease.easeagent.plugin.kafka.advice.KafkaMessageListenerAdvice
 *          com.megaease.easeagent.plugin.kafka.advice.KafkaProducerAdvice
 *          com.megaease.easeagent.plugin.kafka.advice.KafkaProducerConfigAdvice
 * log4j2 -> com.megaease.easeagent.log4j2.points.AbstractLoggerPoints
 * logback -> com.megaease.easeagent.logback.points.LoggerPoints
 * mongodb -> com.megaease.easeagent.plugin.mongodb.points.MongoAsyncMongoClientsPoints
 *            com.megaease.easeagent.plugin.mongodb.points.MongoClientImplPoints
 *            com.megaease.easeagent.plugin.mongodb.points.MongoDBInternalConnectionPoints
 *            com.megaease.easeagent.plugin.mongodb.points.MongoRedirectPoints
 * motan -> com.megaease.easeagent.plugin.motan.advice.MotanConsumerAdvice
 *          com.megaease.easeagent.plugin.motan.advice.MotanProviderAdvice
 * okhttp -> com.megaease.easeagent.plugin.okhttp.advice.OkHttpAdvice
 * rabbitMq -> com.megaease.easeagent.plugin.rabbitmq.spring.RabbitMqMessageListenerAdvice
 *             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqChannelAdvice
 *             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqConfigFactoryAdvice
 *             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqConsumerAdvice
 *             com.megaease.easeagent.plugin.rabbitmq.v5.advice.RabbitMqPropertyAdvice
 * redis -> com.megaease.easeagent.plugin.redis.advice.JedisAdvice
 *          com.megaease.easeagent.plugin.redis.advice.JedisConstructorAdvice
 *          com.megaease.easeagent.plugin.redis.advice.LettuceRedisClientAdvice
 *          com.megaease.easeagent.plugin.redis.advice.RedisChannelWriterAdvice
 *          com.megaease.easeagent.plugin.redis.advice.RedisClusterClientAdvice
 *          com.megaease.easeagent.plugin.redis.advice.RedisPropertiesAdvice
 *          com.megaease.easeagent.plugin.redis.advice.RedisPropertiesClusterAdvice
 *          com.megaease.easeagent.plugin.redis.advice.StatefulRedisConnectionAdvice
 * servicename -> com.megaease.easeagent.plugin.servicename.advice.FeignBlockingLoadBalancerClientAdvice
 *                com.megaease.easeagent.plugin.servicename.advice.FeignLoadBalancerAdvice
 *                com.megaease.easeagent.plugin.servicename.advice.FilteringWebHandlerAdvice
 *                com.megaease.easeagent.plugin.servicename.advice.LoadBalancerFeignClientAdvice
 *                com.megaease.easeagent.plugin.servicename.advice.RestTemplateInterceptAdvice
 *                com.megaease.easeagent.plugin.servicename.advice.WebClientFilterAdvice
 * sofarpc -> com.megaease.easeagent.plugin.sofarpc.adivce.BoltFutureInvokeCallbackConstructAdvice
 *            com.megaease.easeagent.plugin.sofarpc.adivce.ConsumerAdvice
 *            com.megaease.easeagent.plugin.sofarpc.adivce.FutureInvokeCallbackConstructAdvice
 *            com.megaease.easeagent.plugin.sofarpc.adivce.ProviderAdvice
 *            com.megaease.easeagent.plugin.sofarpc.adivce.ResponseCallbackAdvice
 *            com.megaease.easeagent.plugin.sofarpc.adivce.ResponseFutureAdvice
 * spring-gateway -> easeagent.plugin.spring.gateway.advice.AgentGlobalFilterAdvice
 *                   easeagent.plugin.spring.gateway.advice.HttpHeadersFilterAdvice
 *                   easeagent.plugin.spring.gateway.advice.InitGlobalFilterAdvice
 * springweb -> com.megaease.easeagent.plugin.springweb.advice.ClientHttpRequestAdvice
 *              com.megaease.easeagent.plugin.springweb.advice.FeignClientAdvice
 *              com.megaease.easeagent.plugin.springweb.advice.WebClientBuilderAdvice
 *              com.megaease.easeagent.plugin.springweb.advice.WebClientFilterAdvice
 */
public interface Points {
    String DEFAULT_VERSION = "default";
    Set<String> DEFAULT_VERSIONS = Collections.singleton(DEFAULT_VERSION);

    CodeVersion EMPTY_VERSION = CodeVersion.builder().build();


    /**
     * eg.
     * versions=CodeVersion.builder().key("jdk").add("default").add("jdk8").build()
     * do not set or set the following value to load: runtime.code.version.points.jdk=jdk8
     * <p>
     * when set for not load: runtime.code.version.points.jdk=jdk17
     * but load from Points: versions=CodeVersion.builder().key("jdk").add("jdk17").build()
     *
     * @see CodeVersion
     * @return CodeVersion code of versions for control whether to load, If EMPTY_VERSIONS is returned, it means it will load forever
     */
    default CodeVersion codeVersions() {
        return EMPTY_VERSION;
    }

    /**
     * return the defined class matcher matching a class or a group of classes
     * eg.
     * ClassMatcher.builder()
     * .hadInterface(A)
     * .isPublic()
     * .isAbstract()
     * .or()
     * .hasSuperClass(B)
     * .isPublic()
     * .build()
     */
    IClassMatcher getClassMatcher();

    /**
     * return the defined method matcher
     * eg.
     * MethodMatcher.builder().named("execute")
     * .isPublic()
     * .argNum(2)
     * .arg(1, "java.lang.String")
     * .build().toSet()
     * or
     * MethodMatcher.multiBuilder()
     * .match(MethodMatcher.builder().named("<init>")
     * .argsLength(3)
     * .arg(0, "org.apache.kafka.clients.consumer.ConsumerConfig")
     * .qualifier("constructor")
     * .build())
     * .match(MethodMatcher.builder().named("poll")
     * .argsLength(1)
     * .arg(0, "java.time.Duration")
     * .qualifier("poll")
     * .build())
     * .build();
     */
    Set<IMethodMatcher> getMethodMatcher();

    /**
     * 如果返回true，则添加一个字段和访问器。通过 AgentDynamicFieldAccessor 来设置和读取
     *
     * when return true, the transformer will add a Object field and a accessor
     * The dynamically added member can be accessed by AgentDynamicFieldAccessor:
     * <p>
     * AgentDynamicFieldAccessor.setDynamicFieldValue(instance, value)
     * value = AgentDynamicFieldAccessor.getDynamicFieldValue(instance)
     */
    default boolean isAddDynamicField() {
        return false;
    }

    /**
     * 返回一个该类内部的字段名，通过 TypeFieldGetter.get(instance) 来读取
     *
     * When a non-null string is returned, the converter will add an accessor to get the member variables inside the class.
     * Get method: value = TypeFieldGetter.get(instance)
     * @see com.megaease.easeagent.plugin.field.TypeFieldGetter#get(Object)
     * @return String field name
     */
    default String getTypeFieldAccessor() {
        return null;
    }

    /**
     * Only match classes loaded by the ClassLoaderMatcher
     * default as all classloader
     *
     * @return classloader matcher
     */
    default IClassLoaderMatcher getClassLoaderMatcher() {
        return ClassLoaderMatcher.ALL;
    }
}
