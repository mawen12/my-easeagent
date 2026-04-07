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
package com.megaease.easeagent.report.plugin;

import com.megaease.easeagent.plugin.report.Encoder;
import com.megaease.easeagent.plugin.report.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public class ReporterLoader {
    static Logger logger = LoggerFactory.getLogger(ReporterLoader.class);

    private ReporterLoader() {}

    // load 使用 ServiceLoader 机制，读取 Encoder 和 Sender，并注册到 ReporterRegistry 中
    public static void load() {
        encoderLoad();
        senderLoad();
    }

    public static void encoderLoad() {
        // 使用 ServiceLoader 从 META-INF/services/com.megaease.easeagent.plugin.report.Encoder 读取文件内容
        // 具体位于 easeagent.jar/lib/build-x.x.x.jar中，其中实现有：
        // com.megaease.easeagent.report.encoder.log.AccessLogJsonEncoder
        // com.megaease.easeagent.report.encoder.log.LogDataJsonEncoder
        // com.megaease.easeagent.report.encoder.metric.MetricJsonEncoder
        // com.megaease.easeagent.report.encoder.span.SpanJsonEncoder
        // com.megaease.easeagent.report.encoder.span.okhttp.HttpSpanJsonEncoder
        for (Encoder<?> encoder : load(Encoder.class)) {
            try {
                Constructor<? extends Encoder> constructor = encoder.getClass().getConstructor();
                Supplier<Encoder<?>> encoderSupplier = () -> {
                    try {
                        return constructor.newInstance();
                    } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                        logger.warn("unable to load sender: {}", encoder.name());
                        return null;
                    }
                };
                // 注册 encoder 和对应实例化器
                ReporterRegistry.registryEncoder(encoder.name(), encoderSupplier);
            } catch (NoSuchMethodException e) {
                    logger.warn("Sender load fail:{}", e.getMessage());
            }
        }
    }

    public static void senderLoad() {
        // 使用 ServiceLoader 从 META-INF/services/com.megaease.easeagent.plugin.report.Sender 读取文件内容
        // 具体位于 easeagent.jar/lib/build-x.x.x.jar中，其中实现有：
        // com.megaease.easeagent.report.sender.AgentKafkaSender
        // com.megaease.easeagent.report.sender.AgentLoggerSender 写入控制台
        // com.megaease.easeagent.report.sender.NoOpSender
        // com.megaease.easeagent.report.sender.metric.MetricKafkaSender
        // com.megaease.easeagent.report.sender.okhttp.HttpSender
        for (Sender sender : load(Sender.class)) {
            try {
                // 读取它们的构造器
                Constructor<? extends Sender> constructor = sender.getClass().getConstructor();
                Supplier<Sender> senderSupplier = () -> {
                    try {
                        // 通过构造器实例化 Sender 对象
                        return constructor.newInstance();
                    } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
                        logger.warn("unable to load sender: {}", sender.name());
                        return null;
                    }
                };
                // 注册 sender 和对应实例化器
                ReporterRegistry.registrySender(sender.name(), senderSupplier);
            } catch (NoSuchMethodException e) {
                logger.warn("Sender load fail:{}", e.getMessage());
            }
        }
    }

    // load 使用 ServiceLoader 从 META-INF/services/<serviceClass> 读取文件内容
    // 对于存在UnsupportedClassVersionError的错误，忽略错误
    private static <T> List<T> load(Class<T> serviceClass) {
        List<T> result = new ArrayList<>();
        // 使用 ServiceLoader 从 META-INF/services/<serviceClass> 读取文件内容
        // TODO 是否考虑使用 BaseLaoder 来替代该方法
        java.util.ServiceLoader<T> services = ServiceLoader.load(serviceClass);
        for (Iterator<T> it = services.iterator(); it.hasNext(); ) {
            try {
                result.add(it.next());
            } catch (UnsupportedClassVersionError e) {
                logger.info("Unable to load class: {}", e.getMessage());
                logger.info("Please check the plugin compile Java version configuration,"
                    + " and it should not latter than current JVM runtime");
            }
        }
        return result;
    }
}
